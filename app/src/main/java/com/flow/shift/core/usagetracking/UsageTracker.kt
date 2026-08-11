package com.flow.shift.core.usagetracking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.app.AppOpsManager
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private var lastKnownForegroundApp: String? = null

    fun getForegroundApp(): String? {
        val timeNow = System.currentTimeMillis()
        // Query the last 60 seconds to account for Android batching UsageEvents
        val sinceTime = timeNow - 60_000 
        val events = usageStatsManager.queryEvents(sinceTime, timeNow)
        
        var currentApp: String? = null
        val event = UsageEvents.Event()
        var hasNewEvents = false

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentApp = event.packageName
                hasNewEvents = true
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                if (currentApp == event.packageName) {
                    currentApp = null
                }
                hasNewEvents = true
            }
        }
        
        if (hasNewEvents) {
            if (currentApp != null) {
                lastKnownForegroundApp = currentApp
            } else {
                // Keep the last known app even if the last event in the 60s window was a pause, 
                // because they might just be on the home screen, or transitioning. 
                // Actually, if it's explicitly paused and nothing else resumed, they left the app.
                lastKnownForegroundApp = null
            }
        }
        
        return lastKnownForegroundApp
    }

    fun getUsageMillisByPackage(
        sinceTime: Long,
        untilTime: Long,
        packageNames: Set<String>
    ): Map<String, Long> {
        if (packageNames.isEmpty()) return emptyMap()

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

    fun getAppOpensByPackage(
        sinceTime: Long,
        untilTime: Long,
        packageNames: Set<String>
    ): Map<String, Int> {
        if (packageNames.isEmpty()) return emptyMap()

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

    fun hasUsageStatsAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

