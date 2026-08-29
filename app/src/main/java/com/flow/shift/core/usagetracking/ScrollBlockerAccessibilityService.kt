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
    private var eventProcessingJob: Job? = null
    private var lastHandledEventAtMillis: Long = 0L
    private var lastContentEventAtMillis: Long = 0L
    private var lastBlockerLaunchAtMillis: Long = 0L
    private var lastInterventionPackage: String? = null
    private var lastInterventionAtMillis: Long = 0L
    private var cachedTargetPackages: Set<String> = emptySet()
    private var cachedTargetReached = false
    private var cachedTargetCheckedAtMillis: Long = 0L

    private var windowManager: android.view.WindowManager? = null
    private var overlayView: android.view.View? = null
    private var reelCountText: android.widget.TextView? = null
    private var lastDisplayedReelCount = -1
    private var reelCount = 0
    private var lastReelScrollTime = 0L
    private var currentPackage: String? = null
    private var isCurrentlyInShorts = false

    // Minimum interval between blocker launches to prevent intent spam
    private companion object {
        const val BLOCKER_LAUNCH_COOLDOWN_MS = 800L
        const val INTERVENTION_DEDUP_WINDOW_MS = 10_000L
        const val MIN_EVENT_INTERVAL_MS = 180L
        const val CONTENT_EVENT_INTERVAL_MS = 700L
        const val TARGET_RECHECK_INTERVAL_MS = 15_000L
        const val NODE_SCAN_LIMIT = 600
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageName = event.packageName?.toString() ?: return
        val eventType = event.eventType
        
        // Never block our own app (FlowShift itself, including BlockerActivity/WaitActivity)
        if (packageName == applicationContext.packageName) return

        if (!AppTrackingService.isRunning) {
            try {
                val serviceIntent = Intent(this, AppTrackingService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppTracking", "Failed to restart AppTrackingService from accessibility", e)
            }
        }

        val now = System.currentTimeMillis()
        
        if (packageName == "com.instagram.android" && eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val className = event.className?.toString() ?: ""
            // Filter out internal scrolls (like text marquees or progress bars)
            val isMainScroll = className.contains("RecyclerView") || className.contains("ViewPager")
            
            if (isCurrentlyInShorts && isMainScroll) {
                if (now - lastReelScrollTime > 600L) { // Faster debounce for reels
                    reelCount++
                    lastReelScrollTime = now
                    serviceScope.launch {
                        if (settingsDataStore.showReelCount.first()) {
                            updateOverlayOnMainThread(true)
                        }
                    }
                }
            }
        }

        if (now - lastHandledEventAtMillis < MIN_EVENT_INTERVAL_MS) return
        if (
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            now - lastContentEventAtMillis < CONTENT_EVENT_INTERVAL_MS
        ) {
            return
        }
        if (eventProcessingJob?.isActive == true) return

        lastHandledEventAtMillis = now
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            lastContentEventAtMillis = now
        }

        if (packageName != currentPackage) {
            currentPackage = packageName
        }

        if (now - lastBlockerLaunchAtMillis < BLOCKER_LAUNCH_COOLDOWN_MS) return

        eventProcessingJob = serviceScope.launch {
            val blockType = settingsDataStore.blockType.first()
            val isShortFormSupportedApp = packageName == "com.instagram.android" || 
                packageName == "com.google.android.youtube" || 
                packageName == "com.facebook.katana"

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
                val shouldEagerBlock = if (isShortFormSupportedApp && blockType == "REELS") {
                    false
                } else {
                    true
                }

                if (shouldEagerBlock && shouldBlockApp(packageName)) {
                    android.util.Log.d("AppTracking", "Accessibility blocking app: $packageName")
                    lastBlockerLaunchAtMillis = System.currentTimeMillis()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    triggerBlocker(packageName)
                    return@launch
                }
            }

            // Otherwise, check for short-form content specifically for unblocked apps or partial blocks
            if (isShortFormSupportedApp) {
                
                val rootNode = rootInActiveWindow
                if (rootNode == null) {
                    isCurrentlyInShorts = false
                    if (packageName == "com.instagram.android") updateOverlayOnMainThread(false)
                    return@launch
                }
                
                val isShorts = isShortFormContentPresent(rootNode)
                isCurrentlyInShorts = isShorts

                if (isShorts) {
                    if (packageName == "com.instagram.android") {
                        val showReelCount = settingsDataStore.showReelCount.first()
                        if (showReelCount) {
                            updateOverlayOnMainThread(true)
                        } else {
                            updateOverlayOnMainThread(false)
                        }
                    }

                    if (shouldBlockApp(packageName)) {
                        
                        var homeAction = HomeTabAction.NOT_FOUND
                        if (packageName == "com.instagram.android") {
                            val newRoot = rootInActiveWindow
                            if (newRoot != null) {
                                homeAction = clickHomeTab(newRoot)
                            }
                        }

                        if (homeAction == HomeTabAction.ALREADY_ON_HOME) {
                            // False positive from bottom nav bar, we are on the Home feed
                            return@launch
                        }

                        android.util.Log.d("AppTracking", "Accessibility blocking short-form content in: $packageName")
                        lastBlockerLaunchAtMillis = System.currentTimeMillis()

                        if (homeAction == HomeTabAction.CLICKED) {
                            android.util.Log.d("AppTracking", "Successfully switched to Home tab for $packageName")
                            recordIntervention(packageName)
                        } else {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            triggerBlocker(packageName)
                        }
                    }
                } else {
                    if (packageName == "com.instagram.android") {
                        updateOverlayOnMainThread(false)
                    }
                }
            } else {
                updateOverlayOnMainThread(false)
            }
        }
    }

    private suspend fun updateOverlayOnMainThread(show: Boolean) {
        if (!show && overlayView == null) return

        kotlinx.coroutines.withContext(Dispatchers.Main) {
            if (show) {
                if (overlayView == null) {
                    windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
                    val params = android.view.WindowManager.LayoutParams(
                        android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                        android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                        android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN,
                        android.graphics.PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                        y = 100 // margin from top
                    }

                    val context = this@ScrollBlockerAccessibilityService
                    val linearLayout = android.widget.LinearLayout(context).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        setPadding(32, 16, 32, 16)
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.parseColor("#80000000"))
                            cornerRadius = 24f
                        }
                    }

                    reelCountText = android.widget.TextView(context).apply {
                        text = "Reels Scrolled: $reelCount"
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 14f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }

                    linearLayout.addView(reelCountText)
                    overlayView = linearLayout

                    try {
                        windowManager?.addView(overlayView, params)
                    } catch (e: Exception) {
                        android.util.Log.e("AppTracking", "Failed to add reel overlay", e)
                    }
                    lastDisplayedReelCount = reelCount
                } else {
                    if (lastDisplayedReelCount != reelCount) {
                        reelCountText?.text = "Reels Scrolled: $reelCount"
                        lastDisplayedReelCount = reelCount
                    }
                }
            } else {
                if (overlayView != null) {
                    try {
                        windowManager?.removeView(overlayView)
                    } catch (e: Exception) {
                        android.util.Log.e("AppTracking", "Failed to remove reel overlay", e)
                    }
                    overlayView = null
                    reelCountText = null
                    lastDisplayedReelCount = -1
                }
            }
        }
    }

    private fun isShortFormContentPresent(rootNode: AccessibilityNodeInfo): Boolean {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.addFirst(rootNode)
        var count = 0
        var found = false
        
        while (queue.isNotEmpty() && count < NODE_SCAN_LIMIT) {
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
        
        while (queue.isNotEmpty() && count < NODE_SCAN_LIMIT) {
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

    private enum class HomeTabAction {
        CLICKED, ALREADY_ON_HOME, NOT_FOUND
    }

    private fun clickHomeTab(rootNode: AccessibilityNodeInfo): HomeTabAction {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.addFirst(rootNode)
        var count = 0
        
        while (queue.isNotEmpty() && count < NODE_SCAN_LIMIT) {
            val node = queue.removeFirst()
            count++
            
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (desc == "home" || desc == "feed" || desc.contains("home tab")) {
                var isSelected = node.isSelected
                if (!isSelected) {
                    var p = node.parent
                    while (p != null) {
                        if (p.isSelected) {
                            isSelected = true
                            p.recycle()
                            break
                        }
                        val op = p
                        p = p.parent
                        op.recycle()
                    }
                }

                if (isSelected) {
                    node.recycle()
                    while (queue.isNotEmpty()) queue.removeFirst().recycle()
                    return HomeTabAction.ALREADY_ON_HOME
                }

                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                    while (queue.isNotEmpty()) queue.removeFirst().recycle()
                    return HomeTabAction.CLICKED
                } else {
                    var parent = node.parent
                    var clicked = false
                    while (parent != null) {
                        if (parent.isClickable) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            clicked = true
                            parent.recycle()
                            break
                        }
                        val oldParent = parent
                        parent = parent.parent
                        oldParent.recycle()
                    }
                    if (clicked) {
                        node.recycle()
                        while (queue.isNotEmpty()) queue.removeFirst().recycle()
                        return HomeTabAction.CLICKED
                    }
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
        return HomeTabAction.NOT_FOUND
    }

    private suspend fun isTargetTimeReached(now: Long): Boolean {
        val blockedApps = blockedAppDao.getEnabledBlockedApps().first()
        val protectedPackages = blockedApps.map { it.packageName }.toSet()
        if (
            cachedTargetPackages == protectedPackages &&
            now - cachedTargetCheckedAtMillis < TARGET_RECHECK_INTERVAL_MS
        ) {
            return cachedTargetReached
        }

        val targetScreenTimeStr = settingsDataStore.targetScreenTime.first()
        val targetMillis = parseTargetScreenTimeMillis(targetScreenTimeStr)
        val startOfDay = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val usageByPackage = usageTracker.getUsageMillisByPackage(startOfDay, now, protectedPackages)
        var totalUsageTodayMillis = usageByPackage.values.sum()
        
        // Fix for UsageStatsManager lag when RAM is cleared:
        // Always persist and retrieve the highest seen usage for today.
        totalUsageTodayMillis = settingsDataStore.updateHighestUsageSeenToday(startOfDay, totalUsageTodayMillis)

        cachedTargetPackages = protectedPackages
        cachedTargetReached = totalUsageTodayMillis >= targetMillis
        cachedTargetCheckedAtMillis = now

        return cachedTargetReached
    }

    private suspend fun shouldBlockApp(packageName: String): Boolean {
        val blockedApp = blockedAppDao.getBlockedApp(packageName)
        if (blockedApp == null || !blockedApp.isEnabled) {
            return false
        }

        val focusModeUntilMillis = settingsDataStore.focusModeUntilMillis.first()
        val currentTime = System.currentTimeMillis()
        val isFocusModeActive = focusModeUntilMillis > currentTime

        if (isFocusModeActive) {
            return true
        }

        val targetReached = isTargetTimeReached(currentTime)
        val activeBank = hasActiveTimeBank(packageName, currentTime)
        
        if (!targetReached) return false

        val blockingMode = BlockingMode.fromString(settingsDataStore.blockingMode.first())
        if (blockingMode == BlockingMode.HARDCORE) return true

        return !activeBank
    }

    private suspend fun hasActiveTimeBank(packageName: String, currentTime: Long): Boolean {
        return workoutSessionDao.getActiveUnlockCount(packageName, currentTime) > 0
    }

    private suspend fun recordIntervention(packageName: String) {
        val now = System.currentTimeMillis()
        val blockedApp = blockedAppDao.getBlockedApp(packageName)
        val challengeAmount = blockedApp?.customDifficulty ?: settingsDataStore.strictChallengeAmount.first()

        val appName = blockedApp?.appName ?: when (packageName) {
            "com.instagram.android" -> "Instagram Reels"
            "com.google.android.youtube" -> "YouTube Shorts"
            "com.facebook.katana" -> "Facebook Reels"
            else -> "Shorts/Reels"
        }

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
    }

    private suspend fun triggerBlocker(packageName: String) {
        recordIntervention(packageName)

        val blockedApp = blockedAppDao.getBlockedApp(packageName)
        val blockingMode = BlockingMode.fromString(settingsDataStore.blockingMode.first())
        val challengeType = settingsDataStore.strictChallengeType.first()
        val challengeAmount = blockedApp?.customDifficulty ?: settingsDataStore.strictChallengeAmount.first()
        val breakDurationMinutes = settingsDataStore.breakDurationMinutes.first()

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
