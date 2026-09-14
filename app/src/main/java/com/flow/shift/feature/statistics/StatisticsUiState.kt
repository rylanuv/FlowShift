package com.flow.shift.feature.statistics

enum class StatsTimePeriod {
    TODAY,
    PAST_7_DAYS,
    PAST_MONTH
}

data class DailyUsageItem(
    val dateMillis: Long,
    val dayLabel: String,
    val dateNumber: String,
    val usageMillis: Long,
    val isToday: Boolean,
    val interventionCount: Int
)

data class AppStatItem(
    val packageName: String,
    val appName: String,
    val usageMillis: Long,
    val openCount: Int,
    val interventionCount: Int,
    val percentageOfTotal: Float,
    val reelCount: Int = 0
)

data class TimeOfDayDistribution(
    val morningCount: Int,   // 6 AM - 12 PM
    val afternoonCount: Int, // 12 PM - 6 PM
    val eveningCount: Int,   // 6 PM - 10 PM
    val nightCount: Int      // 10 PM - 6 AM
) {
    val total: Int get() = morningCount + afternoonCount + eveningCount + nightCount
}

data class StatisticsMetrics(
    val selectedPeriod: StatsTimePeriod = StatsTimePeriod.TODAY,
    val hasUsageAccess: Boolean = false,
    val totalProtectedUsageMillis: Long = 0L,
    val targetScreenTimeMillis: Long = 0L,
    val totalInterventions: Int = 0,
    val totalBreaksTaken: Int = 0,
    val totalRepsCompleted: Int = 0,
    val currentStreakDays: Int = 0,
    val currentLevel: Int = 1,
    val totalXp: Int = 0,
    val dailyUsageList: List<DailyUsageItem> = emptyList(),
    val appStatsList: List<AppStatItem> = emptyList(),
    val timeOfDayDistribution: TimeOfDayDistribution = TimeOfDayDistribution(0, 0, 0, 0),
    val mostBlockedAppName: String? = null,
    val averageDailyUsageMillis: Long = 0L,
    val topUsage: com.flow.shift.feature.dashboard.AppUsageSummary? = null,
    val topOpened: com.flow.shift.feature.dashboard.AppOpensSummary? = null,
    val lastIntervention: com.flow.shift.feature.dashboard.LastInterventionSummary? = null,
    val earnedMillisToday: Long = 0L,
    val totalReelsCount: Int = 0,
    val topReelsAppName: String? = null,
    val topReelsCount: Int = 0,
    val appReelsMap: Map<String, Int> = emptyMap()
)

sealed interface StatisticsUiState {
    object Loading : StatisticsUiState
    data class Success(val metrics: StatisticsMetrics) : StatisticsUiState
}
