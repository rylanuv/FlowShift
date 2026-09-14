package com.flow.shift.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.shift.core.database.BlockedAppDao
import com.flow.shift.core.database.BlockedAppEntity
import com.flow.shift.core.database.InterventionDao
import com.flow.shift.core.database.InterventionEntity
import com.flow.shift.core.database.ReelEventDao
import com.flow.shift.core.database.ReelEventEntity
import com.flow.shift.core.database.UserGamificationDao
import com.flow.shift.core.database.UserGamificationEntity
import com.flow.shift.core.database.WorkoutSessionDao
import com.flow.shift.core.database.WorkoutSessionEntity
import com.flow.shift.core.datastore.SettingsDataStore
import com.flow.shift.core.usagetracking.UsageTracker
import com.flow.shift.feature.dashboard.parseTargetScreenTimeMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ReelStoreData(val count: Int, val dateMillis: Long)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val blockedAppDao: BlockedAppDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val interventionDao: InterventionDao,
    private val userGamificationDao: UserGamificationDao,
    private val reelEventDao: ReelEventDao,
    private val settingsDataStore: SettingsDataStore,
    private val usageTracker: UsageTracker
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(StatsTimePeriod.TODAY)
    val selectedPeriod: StateFlow<StatsTimePeriod> = _selectedPeriod

    private val _refreshTrigger = MutableStateFlow(0)

    private val reelStoreFlow = combine(
        settingsDataStore.reelCountToday,
        settingsDataStore.reelCountDateMillis
    ) { count, dateMillis ->
        ReelStoreData(count, dateMillis)
    }

    private val userConfigFlow = combine(
        _selectedPeriod,
        _refreshTrigger,
        settingsDataStore.targetScreenTime
    ) { period, _, targetTime ->
        Pair(period, targetTime)
    }

    private val appAndSessionFlow = combine(
        blockedAppDao.getEnabledBlockedApps(),
        workoutSessionDao.getAllSessions(),
        reelStoreFlow
    ) { apps, sessions, reelStore ->
        Triple(apps, sessions, reelStore)
    }

    private val activityAndGamificationFlow = combine(
        interventionDao.getAllInterventions(),
        userGamificationDao.getUserGamification(),
        reelEventDao.getAllReelEvents()
    ) { interventions, gamification, reelEvents ->
        Triple(interventions, gamification, reelEvents)
    }

    val uiState: StateFlow<StatisticsUiState> = combine(
        userConfigFlow,
        appAndSessionFlow,
        activityAndGamificationFlow
    ) { config, appAndSession, activityAndGamification ->
        buildStatisticsMetrics(
            period = config.first,
            targetScreenTime = config.second,
            blockedApps = appAndSession.first,
            sessions = appAndSession.second,
            reelStore = appAndSession.third,
            interventions = activityAndGamification.first,
            gamification = activityAndGamification.second,
            reelEvents = activityAndGamification.third
        )
    }.flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StatisticsUiState.Loading
        )

    fun setTimePeriod(period: StatsTimePeriod) {
        _selectedPeriod.value = period
    }

    fun refresh() {
        _refreshTrigger.value += 1
    }

    private fun buildStatisticsMetrics(
        period: StatsTimePeriod,
        targetScreenTime: String,
        blockedApps: List<BlockedAppEntity>,
        sessions: List<WorkoutSessionEntity>,
        reelStore: ReelStoreData,
        interventions: List<InterventionEntity>,
        gamification: UserGamificationEntity?,
        reelEvents: List<ReelEventEntity>
    ): StatisticsUiState {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calendar.timeInMillis

        val sevenDaysAgoCal = (calendar.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -6)
        }
        val startOfSevenDaysAgo = sevenDaysAgoCal.timeInMillis

        val thirtyDaysAgoCal = (calendar.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -29)
        }
        val startOfThirtyDaysAgo = thirtyDaysAgoCal.timeInMillis

        val protectedPackages = blockedApps.map { it.packageName }.toSet()
        val hasUsageAccess = usageTracker.hasUsageStatsAccess()

        val sinceTime = when (period) {
            StatsTimePeriod.TODAY -> startOfToday
            StatsTimePeriod.PAST_7_DAYS -> startOfSevenDaysAgo
            StatsTimePeriod.PAST_MONTH -> startOfThirtyDaysAgo
        }
        val untilTime = now

        // Query real usage and opens for the selected period
        val usageByPackage = if (hasUsageAccess) {
            usageTracker.getUsageMillisByPackageUncached(sinceTime, untilTime, protectedPackages)
        } else {
            emptyMap()
        }

        val opensByPackage = if (hasUsageAccess) {
            usageTracker.getAppOpensByPackageUncached(sinceTime, untilTime, protectedPackages)
        } else {
            emptyMap()
        }

        val totalProtectedUsageMillis = usageByPackage.values.sum()

        // Filter sessions, interventions, and reel events for the selected period
        val periodSessions = sessions.filter { it.timestamp in sinceTime..untilTime }
        val periodInterventions = interventions.filter { it.timestamp in sinceTime..untilTime }
        val periodReelEvents = reelEvents.filter { it.timestamp in sinceTime..untilTime }

        val appReelsMap = periodReelEvents.groupBy { it.packageName }.mapValues { it.value.size }.toMutableMap()
        var periodTotalReels = periodReelEvents.size

        // If period is TODAY, reconcile with existing DataStore today counter (e.g. 27 reels from live device)
        if (period == StatsTimePeriod.TODAY && reelStore.dateMillis == startOfToday && reelStore.count > periodTotalReels) {
            val discrepancy = reelStore.count - periodTotalReels
            periodTotalReels = reelStore.count
            val primaryShortsApp = blockedApps.firstOrNull {
                it.packageName == "com.instagram.android" || it.packageName == "com.google.android.youtube"
            }?.packageName ?: blockedApps.firstOrNull()?.packageName ?: "com.instagram.android"
            appReelsMap[primaryShortsApp] = (appReelsMap[primaryShortsApp] ?: 0) + discrepancy
        }

        val topReelsEntry = appReelsMap.maxByOrNull { it.value }?.takeIf { it.value > 0 }
        val topReelsAppName = topReelsEntry?.let { entry ->
            blockedApps.firstOrNull { it.packageName == entry.key }?.appName ?: entry.key
        }
        val topReelsCount = topReelsEntry?.value ?: 0

        val totalInterventions = periodInterventions.size
        val totalBreaksTaken = periodSessions.size
        val totalRepsCompleted = periodSessions.sumOf { it.repsCompleted }

        // Daily usage for chart (7 days for TODAY / PAST_7_DAYS, 30 days for PAST_MONTH)
        val chartDaysCount = when (period) {
            StatsTimePeriod.TODAY -> 7
            StatsTimePeriod.PAST_7_DAYS -> 7
            StatsTimePeriod.PAST_MONTH -> 30
        }

        val dailyUsageHistory = if (hasUsageAccess) {
            usageTracker.getDailyUsageHistory(chartDaysCount, protectedPackages)
        } else {
            emptyMap()
        }

        val dailyUsageList = mutableListOf<DailyUsageItem>()
        val dayLabelFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dateNumberFormat = SimpleDateFormat("d", Locale.getDefault())

        for (i in (chartDaysCount - 1) downTo 0) {
            val dayCal = (calendar.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val dayStart = dayCal.timeInMillis
            val dayEnd = if (i == 0) now else dayStart + 24 * 60 * 60 * 1000L

            val dayUsage = dailyUsageHistory[dayStart] ?: 0L
            val dayInterventions = interventions.count { it.timestamp in dayStart until dayEnd }

            dailyUsageList.add(
                DailyUsageItem(
                    dateMillis = dayStart,
                    dayLabel = dayLabelFormat.format(Date(dayStart)),
                    dateNumber = dateNumberFormat.format(Date(dayStart)),
                    usageMillis = dayUsage,
                    isToday = (i == 0),
                    interventionCount = dayInterventions
                )
            )
        }

        val averageDailyUsageMillis = if (dailyUsageList.isNotEmpty()) {
            dailyUsageList.sumOf { it.usageMillis } / dailyUsageList.size
        } else {
            0L
        }

        // Build per-app breakdown
        val appStatsList = blockedApps.map { app ->
            val usageMillis = usageByPackage[app.packageName] ?: 0L
            val openCount = opensByPackage[app.packageName] ?: 0
            val interventionCount = periodInterventions.count { it.targetAppPackage == app.packageName }
            val appReelCount = appReelsMap[app.packageName] ?: 0
            val percentage = if (totalProtectedUsageMillis > 0L) {
                (usageMillis.toFloat() / totalProtectedUsageMillis.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            AppStatItem(
                packageName = app.packageName,
                appName = app.appName,
                usageMillis = usageMillis,
                openCount = openCount,
                interventionCount = interventionCount,
                percentageOfTotal = percentage,
                reelCount = appReelCount
            )
        }.sortedWith(
            compareByDescending<AppStatItem> { it.usageMillis }
                .thenByDescending { it.openCount }
                .thenByDescending { it.interventionCount }
                .thenByDescending { it.reelCount }
                .thenBy { it.appName.lowercase() }
        )

        // Time-of-day distribution of interventions
        var morning = 0
        var afternoon = 0
        var evening = 0
        var night = 0

        val checkCal = Calendar.getInstance()
        periodInterventions.forEach { intervention ->
            checkCal.timeInMillis = intervention.timestamp
            when (checkCal.get(Calendar.HOUR_OF_DAY)) {
                in 6..11 -> morning++
                in 12..17 -> afternoon++
                in 18..21 -> evening++
                else -> night++
            }
        }

        val timeOfDayDistribution = TimeOfDayDistribution(
            morningCount = morning,
            afternoonCount = afternoon,
            eveningCount = evening,
            nightCount = night
        )

        // Most blocked app
        val mostBlockedAppName = periodInterventions
            .groupBy { it.targetAppName }
            .maxByOrNull { it.value.size }
            ?.key

        val topUsage = appStatsList.firstOrNull { it.usageMillis > 0 }?.let {
            com.flow.shift.feature.dashboard.AppUsageSummary(
                packageName = it.packageName,
                appName = it.appName,
                usageMillis = it.usageMillis
            )
        }

        val topOpened = appStatsList.maxByOrNull { it.openCount }?.takeIf { it.openCount > 0 }?.let {
            com.flow.shift.feature.dashboard.AppOpensSummary(
                packageName = it.packageName,
                appName = it.appName,
                openCount = it.openCount
            )
        }

        val lastIntervention = periodInterventions.maxByOrNull { it.timestamp }?.let {
            com.flow.shift.feature.dashboard.LastInterventionSummary(
                packageName = it.targetAppPackage,
                appName = it.targetAppName,
                timestampMillis = it.timestamp
            )
        }

        val earnedMillisToday = periodSessions.sumOf {
            it.durationUnlockedMillis.takeIf { duration -> duration > 0 }
                ?: (it.timeUnlockedMillis - it.timestamp).coerceAtLeast(0L)
        }

        val targetScreenTimeMillis = parseTargetScreenTimeMillis(targetScreenTime)

        return StatisticsUiState.Success(
            metrics = StatisticsMetrics(
                selectedPeriod = period,
                hasUsageAccess = hasUsageAccess,
                totalProtectedUsageMillis = totalProtectedUsageMillis,
                targetScreenTimeMillis = targetScreenTimeMillis,
                totalInterventions = totalInterventions,
                totalBreaksTaken = totalBreaksTaken,
                totalRepsCompleted = totalRepsCompleted,
                currentStreakDays = gamification?.currentStreakDays ?: 0,
                currentLevel = gamification?.currentLevel ?: 1,
                totalXp = gamification?.totalXp ?: 0,
                dailyUsageList = dailyUsageList,
                appStatsList = appStatsList,
                timeOfDayDistribution = timeOfDayDistribution,
                mostBlockedAppName = mostBlockedAppName,
                averageDailyUsageMillis = averageDailyUsageMillis,
                topUsage = topUsage,
                topOpened = topOpened,
                lastIntervention = lastIntervention,
                earnedMillisToday = earnedMillisToday,
                totalReelsCount = periodTotalReels,
                topReelsAppName = topReelsAppName,
                topReelsCount = topReelsCount,
                appReelsMap = appReelsMap
            )
        )
    }
}
