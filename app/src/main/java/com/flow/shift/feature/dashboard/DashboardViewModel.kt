package com.flow.shift.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.shift.core.database.BlockedAppDao
import com.flow.shift.core.database.BlockedAppEntity
import com.flow.shift.core.database.InterventionDao
import com.flow.shift.core.database.InterventionEntity
import com.flow.shift.core.database.ReelEventDao
import com.flow.shift.core.database.UserGamificationDao
import com.flow.shift.core.database.UserGamificationEntity
import com.flow.shift.core.database.WorkoutSessionDao
import com.flow.shift.core.database.WorkoutSessionEntity
import com.flow.shift.core.datastore.SettingsDataStore
import com.flow.shift.core.usagetracking.UsageTracker
import com.flow.shift.feature.modes.BlockingMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    gamificationDao: UserGamificationDao,
    blockedAppDao: BlockedAppDao,
    workoutSessionDao: WorkoutSessionDao,
    interventionDao: InterventionDao,
    private val reelEventDao: ReelEventDao,
    private val settingsDataStore: SettingsDataStore,
    private val usageTracker: UsageTracker
) : ViewModel() {

    private val gamificationFlow = gamificationDao.getUserGamification()
        .map { entity ->
            entity ?: UserGamificationEntity(
                id = 1,
                totalXp = 0,
                currentLevel = 1,
                currentStreakDays = 0,
                lastActiveDateMillis = System.currentTimeMillis(),
                scrollCredits = 0
            ).also { defaultEntity ->
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    gamificationDao.insertOrUpdate(defaultEntity)
                }
            }
        }

    private val settingsFlow1 = combine(
        settingsDataStore.blockingMode,
        settingsDataStore.strictChallengeType,
        settingsDataStore.strictChallengeAmount
    ) { modeStr, challengeType, challengeAmount ->
        Triple(modeStr, challengeType, challengeAmount)
    }

    private val settingsFlow2 = combine(
        settingsDataStore.breakDurationMinutes,
        settingsDataStore.targetScreenTime,
        settingsDataStore.easyModeWaitSeconds
    ) { breakMinutes, targetTime, easyWaitSeconds ->
        Triple(breakMinutes, targetTime, easyWaitSeconds)
    }

    private val settingsFlow = combine(
        settingsFlow1,
        settingsFlow2,
        settingsDataStore.preventDisablingFocusMode,
        settingsDataStore.showReelCount,
        settingsDataStore.isPremium
    ) { t1, t2, preventDisabling, showReelCount, isPremium ->
        DashboardSettings(
            blockingMode = BlockingMode.fromString(t1.first),
            challengeType = t1.second,
            challengeAmount = t1.third,
            breakDurationMinutes = t2.first,
            targetScreenTime = t2.second,
            easyModeWaitSeconds = t2.third,
            preventDisablingFocusMode = preventDisabling,
            showReelCount = showReelCount,
            isPremium = isPremium
        )
    }

    private val reelsTodayFlow = combine(
        settingsDataStore.reelCountToday,
        settingsDataStore.reelCountDateMillis,
        reelEventDao.getAllReelEvents()
    ) { storeCount, storeDate, events ->
        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dbCount = events.count { it.timestamp >= startOfDay }
        val datastoreCount = if (storeDate == startOfDay) storeCount else 0
        maxOf(dbCount, datastoreCount)
    }.flowOn(Dispatchers.IO)

    private val metricsFlow = combine(
        blockedAppDao.getEnabledBlockedApps(),
        workoutSessionDao.getAllSessions(),
        interventionDao.getAllInterventions(),
        settingsDataStore.targetScreenTime,
        settingsDataStore.focusModeUntilMillis
    ) { blockedApps, sessions, interventions, targetTime, focusModeUntilMillis ->
        buildMetrics(blockedApps, sessions, interventions, targetTime, focusModeUntilMillis)
    }.flowOn(Dispatchers.IO)

    val uiState: StateFlow<DashboardUiState> = combine(
        gamificationFlow,
        settingsFlow,
        metricsFlow,
        reelsTodayFlow
    ) { entity, settings, metrics, reelsToday ->
        DashboardUiState.Success(
            data = entity,
            settings = settings,
            metrics = metrics.copy(totalReelsToday = reelsToday)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading
    )

    private fun buildMetrics(
        blockedApps: List<BlockedAppEntity>,
        sessions: List<WorkoutSessionEntity>,
        interventions: List<InterventionEntity>,
        targetScreenTime: String,
        focusModeUntilMillis: Long
    ): DashboardMetrics {
        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val protectedPackages = blockedApps.map { it.packageName }.toSet()
        val usageByPackage = usageTracker.getUsageMillisByPackage(startOfDay, now, protectedPackages)
        val topUsage = usageByPackage.maxByOrNull { it.value }?.let { (packageName, duration) ->
            val app = blockedApps.firstOrNull { it.packageName == packageName }
            AppUsageSummary(
                packageName = packageName,
                appName = app?.appName ?: packageName,
                usageMillis = duration
            )
        }

        val totalUsageTodayMillis = usageByPackage.values.sum()
        val opensByPackage = usageTracker.getAppOpensByPackage(startOfDay, now, protectedPackages)
        val topOpened = opensByPackage.maxByOrNull { it.value }?.let { (packageName, count) ->
            val app = blockedApps.firstOrNull { it.packageName == packageName }
            AppOpensSummary(
                packageName = packageName,
                appName = app?.appName ?: packageName,
                openCount = count
            )
        }
        val targetMillis = parseTargetScreenTimeMillis(targetScreenTime)
        val todaysSessions = sessions.filter { it.timestamp >= startOfDay }
        val todaysInterventions = interventions.filter { it.timestamp >= startOfDay }
        val activeSession = sessions
            .filter { it.timeUnlockedMillis > now }
            .maxByOrNull { it.timeUnlockedMillis }

        val remainingMillis = (targetMillis - totalUsageTodayMillis).coerceAtLeast(0L)
        val remainingProgress = if (targetMillis > 0L) {
            (remainingMillis.toFloat() / targetMillis.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        return DashboardMetrics(
            protectedApps = blockedApps
                .sortedWith(
                    compareBy<BlockedAppEntity> { com.flow.shift.core.AppSortingHelper.getAppPriority(it.packageName, it.appName) }
                        .thenBy { it.appName.lowercase() }
                )
                .map { ProtectedAppSummary(it.packageName, it.appName) },
            hasUsageAccess = usageTracker.hasUsageStatsAccess(),
            remainingMillis = remainingMillis,
            remainingProgress = remainingProgress,
            unlockExpiresAtMillis = activeSession?.timeUnlockedMillis,
            pushupsToday = todaysSessions.sumOf { it.repsCompleted },
            sessionsToday = todaysSessions.size,
            earnedMillisToday = todaysSessions.sumOf {
                it.durationUnlockedMillis.takeIf { duration -> duration > 0 }
                    ?: (it.timeUnlockedMillis - it.timestamp).coerceAtLeast(0L)
            },
            interventionsToday = todaysInterventions.size,
            totalUsageTodayMillis = totalUsageTodayMillis,
            topUsage = topUsage,
            topOpened = topOpened,
            lastIntervention = todaysInterventions.maxByOrNull { it.timestamp }?.let {
                LastInterventionSummary(
                    packageName = it.targetAppPackage,
                    appName = it.targetAppName,
                    timestampMillis = it.timestamp
                )
            },
            focusModeUntilMillis = focusModeUntilMillis
        )
    }

    fun startFocusMode(minutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val endTime = System.currentTimeMillis() + minutes * 60 * 1000L
            settingsDataStore.setFocusModeUntilMillis(endTime)
        }
    }

    fun stopFocusMode() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.setFocusModeUntilMillis(0L)
        }
    }

    fun setShowReelCount(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.setShowReelCount(enabled)
        }
    }
}

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    data class Success(
        val data: UserGamificationEntity,
        val settings: DashboardSettings = DashboardSettings(),
        val metrics: DashboardMetrics = DashboardMetrics()
    ) : DashboardUiState
}

data class DashboardSettings(
    val blockingMode: BlockingMode = BlockingMode.EASY,
    val challengeType: String = "MATH",
    val challengeAmount: Int = 5,
    val breakDurationMinutes: Int = 5,
    val targetScreenTime: String = "2h",
    val easyModeWaitSeconds: Int = 90,
    val preventDisablingFocusMode: Boolean = false,
    val showReelCount: Boolean = false,
    val isPremium: Boolean = false
)

data class DashboardMetrics(
    val protectedApps: List<ProtectedAppSummary> = emptyList(),
    val hasUsageAccess: Boolean = false,
    val remainingMillis: Long = 0,
    val remainingProgress: Float = 0f,
    val unlockExpiresAtMillis: Long? = null,
    val pushupsToday: Int = 0,
    val sessionsToday: Int = 0,
    val earnedMillisToday: Long = 0,
    val interventionsToday: Int = 0,
    val totalUsageTodayMillis: Long = 0,
    val topUsage: AppUsageSummary? = null,
    val topOpened: AppOpensSummary? = null,
    val lastIntervention: LastInterventionSummary? = null,
    val focusModeUntilMillis: Long = 0L,
    val totalReelsToday: Int = 0
)

data class ProtectedAppSummary(
    val packageName: String,
    val appName: String
)

data class AppUsageSummary(
    val packageName: String,
    val appName: String,
    val usageMillis: Long
)

data class AppOpensSummary(
    val packageName: String,
    val appName: String,
    val openCount: Int
)

data class LastInterventionSummary(
    val packageName: String,
    val appName: String,
    val timestampMillis: Long
)

internal fun formatDurationShort(millis: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

internal fun formatRemainingDurationShort(millis: Long): String {
    if (millis <= 0L) return "0m"

    val totalMinutes = ((millis + 59_999L) / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

internal fun formatDurationLong(millis: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours hour${if (hours == 1L) "" else "s"} $minutes minute${if (minutes == 1L) "" else "s"}"
        hours > 0 -> "$hours hour${if (hours == 1L) "" else "s"}"
        minutes == 1L -> "1 minute"
        else -> "$minutes minutes"
    }
}

internal fun formatClockTime(millis: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
}

internal fun parseTargetScreenTimeMillis(targetStr: String): Long {
    if (targetStr.contains("-")) {
        val parts = targetStr.split("-")
        val hours = parts.lastOrNull()?.trim()?.toLongOrNull() ?: 2L
        return hours * 60L * 60L * 1000L
    }
    var totalMinutes = 0L
    val hoursMatch = Regex("(\\d+)\\s*h", RegexOption.IGNORE_CASE).find(targetStr)
    if (hoursMatch != null) {
        totalMinutes += (hoursMatch.groupValues[1].toLongOrNull() ?: 0L) * 60L
    }
    val minutesMatch = Regex("(\\d+)\\s*m", RegexOption.IGNORE_CASE).find(targetStr)
    if (minutesMatch != null) {
        totalMinutes += (minutesMatch.groupValues[1].toLongOrNull() ?: 0L) * 1L
    }
    if (hoursMatch == null && minutesMatch == null) {
        val raw = targetStr.trim().toLongOrNull()
        if (raw != null && raw >= 0) {
            totalMinutes = if (raw <= 24) raw * 60L else raw
        } else {
            totalMinutes = 120L // default 2 hours
        }
    }
    return totalMinutes * 60L * 1000L
}

