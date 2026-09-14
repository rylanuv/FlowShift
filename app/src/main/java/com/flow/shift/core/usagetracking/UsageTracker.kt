package com.flow.shift.core.usagetracking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.app.AppOpsManager
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private var lastKnownForegroundApp: String? = null
    private var lastForegroundQueryMillis: Long = 0L
    private var usageCache: UsageCacheEntry? = null
    private var opensCache: OpensCacheEntry? = null

    @Synchronized
    fun getForegroundApp(): String? {
        val timeNow = System.currentTimeMillis()
        val sinceTime = if (lastForegroundQueryMillis == 0L) {
            timeNow - INITIAL_FOREGROUND_QUERY_WINDOW_MS
        } else {
            (lastForegroundQueryMillis - FOREGROUND_QUERY_OVERLAP_MS)
                .coerceAtLeast(timeNow - INITIAL_FOREGROUND_QUERY_WINDOW_MS)
        }
        lastForegroundQueryMillis = timeNow
        val events = usageStatsManager.queryEvents(sinceTime, timeNow)
        
        var currentApp = lastKnownForegroundApp
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    currentApp = event.packageName
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (currentApp == event.packageName) {
                        currentApp = null
                    }
                }
            }
        }

        lastKnownForegroundApp = currentApp
        return lastKnownForegroundApp
    }

    @Synchronized
    fun getUsageMillisByPackage(
        sinceTime: Long,
        untilTime: Long,
        packageNames: Set<String>
    ): Map<String, Long> {
        if (packageNames.isEmpty()) return emptyMap()
        val requestedPackages = packageNames.toSet()
        usageCache?.let { cache ->
            if (
                cache.sinceTime == sinceTime &&
                cache.packageNames == requestedPackages &&
                untilTime >= cache.untilTime &&
                untilTime - cache.untilTime < USAGE_CACHE_TTL_MS
            ) {
                return cache.usage
            }
        }

        val events = usageStatsManager.queryEvents(sinceTime, untilTime)
        val usageMap = mutableMapOf<String, Long>()
        val lastResumeMap = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            if (pkg !in packageNames) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumeMap[pkg] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val resumedTime = lastResumeMap.remove(pkg)
                    if (resumedTime != null && event.timeStamp >= resumedTime) {
                        val duration = event.timeStamp - resumedTime
                        usageMap[pkg] = (usageMap[pkg] ?: 0L) + duration
                    }
                }
            }
        }
        lastResumeMap.forEach { (pkg, resumedTime) ->
            if (untilTime >= resumedTime) {
                usageMap[pkg] = (usageMap[pkg] ?: 0L) + (untilTime - resumedTime)
            }
        }

        usageCache = UsageCacheEntry(
            sinceTime = sinceTime,
            untilTime = untilTime,
            packageNames = requestedPackages,
            usage = usageMap.toMap()
        )
        return usageMap
    }

    @Synchronized
    fun getAppOpensByPackage(
        sinceTime: Long,
        untilTime: Long,
        packageNames: Set<String>
    ): Map<String, Int> {
        if (packageNames.isEmpty()) return emptyMap()
        val requestedPackages = packageNames.toSet()
        opensCache?.let { cache ->
            if (
                cache.sinceTime == sinceTime &&
                cache.packageNames == requestedPackages &&
                untilTime >= cache.untilTime &&
                untilTime - cache.untilTime < USAGE_CACHE_TTL_MS
            ) {
                return cache.opens
            }
        }

        val events = usageStatsManager.queryEvents(sinceTime, untilTime)
        val openMap = mutableMapOf<String, Int>()
        var lastResumedPackage: String? = null
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val pkg = event.packageName
                if (pkg != lastResumedPackage) {
                    if (pkg in packageNames) {
                        openMap[pkg] = (openMap[pkg] ?: 0) + 1
                    }
                    lastResumedPackage = pkg
                }
            }
        }
        opensCache = OpensCacheEntry(
            sinceTime = sinceTime,
            untilTime = untilTime,
            packageNames = requestedPackages,
            opens = openMap.toMap()
        )
        return openMap
    }

    @Synchronized
    fun getUsageMillisByPackageUncached(
        sinceTime: Long,
        untilTime: Long,
        packageNames: Set<String>
    ): Map<String, Long> {
        if (packageNames.isEmpty() || sinceTime >= untilTime) return emptyMap()
        val events = usageStatsManager.queryEvents(sinceTime, untilTime)
        val usageMap = mutableMapOf<String, Long>()
        val lastResumeMap = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            if (pkg !in packageNames) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumeMap[pkg] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val resumedTime = lastResumeMap.remove(pkg)
                    if (resumedTime != null && event.timeStamp >= resumedTime) {
                        val duration = event.timeStamp - resumedTime
                        usageMap[pkg] = (usageMap[pkg] ?: 0L) + duration
                    }
                }
            }
        }
        lastResumeMap.forEach { (pkg, resumedTime) ->
            if (untilTime >= resumedTime) {
                usageMap[pkg] = (usageMap[pkg] ?: 0L) + (untilTime - resumedTime)
            }
        }
        return usageMap
    }

    @Synchronized
    fun getAppOpensByPackageUncached(
        sinceTime: Long,
        untilTime: Long,
        packageNames: Set<String>
    ): Map<String, Int> {
        if (packageNames.isEmpty() || sinceTime >= untilTime) return emptyMap()
        val events = usageStatsManager.queryEvents(sinceTime, untilTime)
        val openMap = mutableMapOf<String, Int>()
        var lastResumedPackage: String? = null
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val pkg = event.packageName
                if (pkg != lastResumedPackage) {
                    if (pkg in packageNames) {
                        openMap[pkg] = (openMap[pkg] ?: 0) + 1
                    }
                    lastResumedPackage = pkg
                }
            }
        }
        return openMap
    }

    @Synchronized
    fun getDailyUsageHistory(
        daysCount: Int,
        packageNames: Set<String>
    ): Map<Long, Long> {
        if (packageNames.isEmpty() || daysCount <= 0) return emptyMap()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStarts = LongArray(daysCount)
        for (i in (daysCount - 1) downTo 0) {
            val dayCal = (calendar.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -i)
            }
            dayStarts[daysCount - 1 - i] = dayCal.timeInMillis
        }
        val resultMap = dayStarts.associateWith { 0L }.toMutableMap()
        val overallStart = dayStarts.first()
        val overallEnd = System.currentTimeMillis()

        val events = usageStatsManager.queryEvents(overallStart, overallEnd)
        val lastResumeMap = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()

        fun addUsageForSpan(pkg: String, startTime: Long, endTime: Long) {
            if (endTime <= startTime) return
            for (i in dayStarts.indices) {
                val dStart = dayStarts[i]
                val dEnd = if (i == dayStarts.lastIndex) overallEnd else dayStarts[i + 1]
                val overlapStart = maxOf(startTime, dStart)
                val overlapEnd = minOf(endTime, dEnd)
                if (overlapEnd > overlapStart) {
                    resultMap[dStart] = (resultMap[dStart] ?: 0L) + (overlapEnd - overlapStart)
                }
            }
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            if (pkg !in packageNames) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumeMap[pkg] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val resumedTime = lastResumeMap.remove(pkg)
                    if (resumedTime != null) {
                        addUsageForSpan(pkg, resumedTime, event.timeStamp)
                    }
                }
            }
        }
        lastResumeMap.forEach { (pkg, resumedTime) ->
            addUsageForSpan(pkg, resumedTime, overallEnd)
        }

        return resultMap
    }

    fun hasUsageStatsAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private data class UsageCacheEntry(
        val sinceTime: Long,
        val untilTime: Long,
        val packageNames: Set<String>,
        val usage: Map<String, Long>
    )

    private data class OpensCacheEntry(
        val sinceTime: Long,
        val untilTime: Long,
        val packageNames: Set<String>,
        val opens: Map<String, Int>
    )

    private companion object {
        const val INITIAL_FOREGROUND_QUERY_WINDOW_MS = 60_000L
        const val FOREGROUND_QUERY_OVERLAP_MS = 5_000L
        const val USAGE_CACHE_TTL_MS = 15_000L
    }
}

