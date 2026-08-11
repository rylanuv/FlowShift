package com.flow.shift.core.usagetracking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.flow.shift.core.database.BlockedAppDao
import com.flow.shift.core.database.InterventionDao
import com.flow.shift.core.database.InterventionEntity
import com.flow.shift.core.database.WorkoutSessionDao
import com.flow.shift.core.datastore.SettingsDataStore
import com.flow.shift.feature.dashboard.parseTargetScreenTimeMillis
import com.flow.shift.feature.exerciseblocker.BlockerActivity
import com.flow.shift.feature.exerciseblocker.WaitActivity
import com.flow.shift.feature.modes.BlockingMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScrollBlockerAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var blockedAppDao: BlockedAppDao

    @Inject
    lateinit var workoutSessionDao: WorkoutSessionDao

    @Inject
    lateinit var interventionDao: InterventionDao

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var usageTracker: UsageTracker

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var lastBlockerLaunchAtMillis: Long = 0L
    private var lastInterventionPackage: String? = null
    private var lastInterventionAtMillis: Long = 0L

    // Minimum interval between blocker launches to prevent intent spam
    private companion object {
        const val BLOCKER_LAUNCH_COOLDOWN_MS = 800L
        const val INTERVENTION_DEDUP_WINDOW_MS = 10_000L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageName = event.packageName?.toString() ?: return
        
        // Never block our own app (FlowShift itself, including BlockerActivity/WaitActivity)
        if (packageName == applicationContext.packageName) return

        val now = System.currentTimeMillis()
        if (now - lastBlockerLaunchAtMillis < BLOCKER_LAUNCH_COOLDOWN_MS) return

        serviceScope.launch {
            // Check advanced protections first
            if (packageName == "com.android.settings" || packageName == "com.android.packageinstaller" || packageName == "com.google.android.packageinstaller" || packageName == "com.miui.securitycenter") {
                val blockSettings = settingsDataStore.blockSettingsAccess.first()
                if (blockSettings && packageName == "com.android.settings") {
                    android.util.Log.d("AppTracking", "Accessibility blocking settings access")
                    lastBlockerLaunchAtMillis = System.currentTimeMillis()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return@launch
                }

                val preventUninstall = settingsDataStore.preventAppUninstall.first()
                if (preventUninstall) {
                    val rootNode = rootInActiveWindow
                    if (rootNode != null && isAppUninstallAttempt(rootNode)) {
                        android.util.Log.d("AppTracking", "Accessibility blocking uninstall attempt")
                        lastBlockerLaunchAtMillis = System.currentTimeMillis()
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        return@launch
                    }
                }
            }

            // First, if it's a fully blocked app, any event (including scrolls) should enforce the block immediately
            val blockedApp = blockedAppDao.getBlockedApp(packageName)
            if (blockedApp != null && blockedApp.isEnabled) {
                if (shouldBlockApp(packageName)) {
                    android.util.Log.d("AppTracking", "Accessibility blocking app: $packageName")
                    lastBlockerLaunchAtMillis = System.currentTimeMillis()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    triggerBlocker(packageName)
                    return@launch
                }
            }

            // Otherwise, check for short-form content specifically for unblocked apps or partial blocks
            if (packageName == "com.instagram.android" || 
                packageName == "com.google.android.youtube" || 
                packageName == "com.facebook.katana") {
                
                val rootNode = rootInActiveWindow ?: return@launch
                
                if (isShortFormContentPresent(rootNode)) {
                    if (shouldBlockApp(packageName)) {
                        android.util.Log.d("AppTracking", "Accessibility blocking short-form content in: $packageName")
                        lastBlockerLaunchAtMillis = System.currentTimeMillis()
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        triggerBlocker(packageName)
                    }
                }
            }
        }
    }

    private fun isShortFormContentPresent(rootNode: AccessibilityNodeInfo): Boolean {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.addFirst(rootNode)
        var count = 0
        var found = false
        
        while (queue.isNotEmpty() && count < 2000) {
            val node = queue.removeFirst()
            count++
            
            var isShorts = false
            if (node.contentDescription != null) {
                val desc = node.contentDescription.toString().lowercase()
                if (desc.contains("reels") || desc.contains("shorts") || desc.contains("short video") || desc.contains("reel") || desc.contains("clip")) {
                    isShorts = true
                }
            }
            
            if (!isShorts && node.viewIdResourceName != null) {
                val id = node.viewIdResourceName.toString().lowercase()
                if (id.contains("clips_video_container") || 
                    id.contains("shorts_player") || 
                    id.contains("reel") ||
                    id.contains("shorts")
                ) {
                    isShorts = true
                }
            }
            
            if (isShorts) {
                node.recycle()
                found = true
                break
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.addFirst(child)
                }
            }
            
            node.recycle()
        }
        
        // Recycle any remaining nodes in the queue to prevent memory leaks
        while (queue.isNotEmpty()) {
            queue.removeFirst().recycle()
        }
        return found
    }

    private fun isAppUninstallAttempt(rootNode: AccessibilityNodeInfo): Boolean {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.addFirst(rootNode)
        var found = false
        var count = 0
        
        while (queue.isNotEmpty() && count < 2000) {
            val node = queue.removeFirst()
            count++
            
            if (node.text != null) {
                val text = node.text.toString().lowercase()
                if (text.contains("stop scrolling") || text.contains("flowshift")) {
                    found = true
                    node.recycle()
                    break
                }
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.addFirst(child)
                }
            }
            node.recycle()
        }
        
        while (queue.isNotEmpty()) {
            queue.removeFirst().recycle()
        }
        
        return found
    }

    private suspend fun isTargetTimeReached(): Boolean {
        val blockedApps = blockedAppDao.getEnabledBlockedApps().first()
        val targetScreenTimeStr = settingsDataStore.targetScreenTime.first()
        val targetMillis = parseTargetScreenTimeMillis(targetScreenTimeStr)
        
        val now = System.currentTimeMillis()
        val startOfDay = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val protectedPackages = blockedApps.map { it.packageName }.toSet()
        val usageByPackage = usageTracker.getUsageMillisByPackage(startOfDay, now, protectedPackages)
        val totalUsageTodayMillis = usageByPackage.values.sum()
        
        return totalUsageTodayMillis >= targetMillis
    }

    private suspend fun shouldBlockApp(packageName: String): Boolean {
        val focusModeUntilMillis = settingsDataStore.focusModeUntilMillis.first()
        val currentTime = System.currentTimeMillis()
        val isFocusModeActive = focusModeUntilMillis > currentTime

        if (isFocusModeActive) {
            android.util.Log.d("AppTracking", "shouldBlockApp: Focus Mode is active. Blocking $packageName.")
            return true
        }

        val targetReached = isTargetTimeReached()
        val activeBank = hasActiveTimeBank(packageName)
        android.util.Log.d("AppTracking", "shouldBlockApp evaluated. isTargetTimeReached: $targetReached, hasActiveTimeBank: $activeBank")
        
        if (!targetReached) return false

        val blockingMode = BlockingMode.fromString(settingsDataStore.blockingMode.first())
        if (blockingMode == BlockingMode.HARDCORE) return true

        return !activeBank
    }

    private suspend fun hasActiveTimeBank(packageName: String): Boolean {
        val recentSessions = workoutSessionDao.getAllSessions().first()
        val latestSessionForApp = recentSessions.find { it.targetAppPackage == packageName }
        val latestGlobalSession = recentSessions.find { it.targetAppPackage == "ALL_APPS" }

        val currentTime = System.currentTimeMillis()
        return (latestSessionForApp != null && latestSessionForApp.timeUnlockedMillis > currentTime) ||
               (latestGlobalSession != null && latestGlobalSession.timeUnlockedMillis > currentTime)
    }

    private suspend fun triggerBlocker(packageName: String) {
        val now = System.currentTimeMillis()

        val blockedApp = blockedAppDao.getBlockedApp(packageName)
        val blockingMode = BlockingMode.fromString(settingsDataStore.blockingMode.first())
        val challengeType = settingsDataStore.strictChallengeType.first()
        val challengeAmount = blockedApp?.customDifficulty ?: settingsDataStore.strictChallengeAmount.first()
        val breakDurationMinutes = settingsDataStore.breakDurationMinutes.first()

        val appName = blockedApp?.appName ?: when (packageName) {
            "com.instagram.android" -> "Instagram Reels"
            "com.google.android.youtube" -> "YouTube Shorts"
            "com.facebook.katana" -> "Facebook Reels"
            else -> "Shorts/Reels"
        }

        // Only record intervention if not a duplicate
        if (lastInterventionPackage != packageName || now - lastInterventionAtMillis >= INTERVENTION_DEDUP_WINDOW_MS) {
            interventionDao.insertIntervention(
                InterventionEntity(
                    timestamp = now,
                    targetAppPackage = packageName,
                    targetAppName = appName,
                    requiredReps = challengeAmount
                )
            )
            lastInterventionPackage = packageName
            lastInterventionAtMillis = now
        }

        val intent = when (blockingMode) {
            BlockingMode.EASY -> Intent(this, WaitActivity::class.java).apply {
                putExtra("TARGET_PACKAGE", packageName)
                putExtra("WAIT_SECONDS", 90)
                putExtra("BREAK_DURATION_MINUTES", breakDurationMinutes)
            }
            BlockingMode.STRICT -> Intent(this, BlockerActivity::class.java).apply {
                putExtra("TARGET_PACKAGE", packageName)
                putExtra("REQUIRED_REPS", challengeAmount)
                putExtra("BREAK_DURATION_MINUTES", breakDurationMinutes)
                putExtra("CHALLENGE_TYPE", challengeType)
            }
            BlockingMode.HARDCORE -> Intent(this, BlockerActivity::class.java).apply {
                putExtra("TARGET_PACKAGE", packageName)
                putExtra("IS_HARDCORE_LOCK", true)
            }
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        // Do nothing
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
