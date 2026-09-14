package com.flow.shift.core.usagetracking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.flow.shift.core.database.BlockedAppDao
import com.flow.shift.core.database.InterventionDao
import com.flow.shift.core.database.InterventionEntity
import com.flow.shift.core.database.ReelEventDao
import com.flow.shift.core.database.ReelEventEntity
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
    lateinit var reelEventDao: ReelEventDao

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
    private var lastScrollFromIndex = -1
    private var currentPackage: String? = null
    private var isCurrentlyInShorts = false
    private var lastShortsDetectionAtMillis = 0L

    private var overlayFadeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            settingsDataStore.showReelCount.collect { enabled ->
                if (!enabled) {
                    updateOverlayOnMainThread(false)
                }
            }
        }
    }

    private fun showOverlayTemporarily() {
        overlayFadeJob?.cancel()
        overlayFadeJob = serviceScope.launch {
            updateOverlayOnMainThread(true)
            kotlinx.coroutines.delay(1500L)
            fadeOutOverlay()
        }
    }

    private suspend fun fadeOutOverlay() {
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            overlayView?.let { view ->
                view.animate()
                    .alpha(0f)
                    .setDuration(300L)
                    .withEndAction {
                        if (overlayFadeJob?.isActive != true) {
                            serviceScope.launch {
                                updateOverlayOnMainThread(false)
                            }
                        }
                    }
                    .start()
            }
        }
    }

    // Minimum interval between blocker launches to prevent intent spam
    private companion object {
        const val BLOCKER_LAUNCH_COOLDOWN_MS = 800L
        const val INTERVENTION_DEDUP_WINDOW_MS = 10_000L
        const val MIN_EVENT_INTERVAL_MS = 180L
        const val CONTENT_EVENT_INTERVAL_MS = 700L
        const val TARGET_RECHECK_INTERVAL_MS = 15_000L
        // Older app builds expose deeper, less-pruned accessibility trees.
        // Keep the scan bounded, but allow enough nodes to reach their Reels container.
        const val NODE_SCAN_LIMIT = 1_400
    }

    /**
     * Lightweight check: walk up the parent chain of a scroll source node
     * looking for short-form specific view IDs. O(depth) ≈ ~10-15 nodes,
     * much cheaper than the full BFS scan in isShortFormContentPresent().
     */
    private fun isShortsAncestry(node: AccessibilityNodeInfo, packageName: String): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 15) {
            val id = current.viewIdResourceName?.toString()?.lowercase() ?: ""
            if (id.isNotEmpty() && isShortsContainerId(packageName, id)) {
                if (current !== node) current.recycle()
                return true
            }
            val parent = current.parent
            if (current !== node) current.recycle()
            current = parent
            depth++
        }
        if (current != null && current !== node) current.recycle()
        return false
    }

    private fun isShortFormSupportedApp(packageName: String): Boolean {
        return packageName == "com.instagram.android" || 
            packageName == "com.google.android.youtube" || 
            packageName == "com.facebook.katana" ||
            packageName == "com.zhiliaoapp.musically" ||
            packageName == "com.snapchat.android"
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

        if (packageName != currentPackage) {
            currentPackage = packageName
            lastScrollFromIndex = -1
            lastReelScrollTime = 0L
            // A Reels state belongs to one app window only. Retaining it while
            // switching apps can prevent fresh detection and corrupt the count.
            isCurrentlyInShorts = false
            lastShortsDetectionAtMillis = 0L
            serviceScope.launch { updateOverlayOnMainThread(false) }
        }

        val now = System.currentTimeMillis()
        
        // Immediate shorts presence check for supported apps so scroll events aren't missed
        if (isShortFormSupportedApp(packageName) && !isCurrentlyInShorts) {
            val root = rootInActiveWindow
            if (root != null) {
                val detection = isShortFormContentPresent(root, packageName)
                if (detection.isPresent) {
                    isCurrentlyInShorts = true
                    lastShortsDetectionAtMillis = now
                }
                root.recycle()
            }
        }

        if (isShortFormSupportedApp(packageName) && eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val className = event.className?.toString() ?: ""
            var isMainScroll = className.contains("RecyclerView") || className.contains("ViewPager") || 
                className.contains("ScrollView") || className.contains("ListView") || 
                className.contains("GridView") || className.contains("ViewGroup") || 
                className.contains("FrameLayout")
            
            var isExplicitShortsView = false
            var isCommentView = false
            val scrollSource = event.source
            if (scrollSource != null) {
                val id = scrollSource.viewIdResourceName?.toString()?.lowercase() ?: ""
                val desc = scrollSource.contentDescription?.toString()?.lowercase() ?: ""
                if (id.contains("comment") || id.contains("reply") || desc.contains("comment") || desc.contains("reply")) {
                    isCommentView = true
                }
                val isShortsId = isShortsContainerId(packageName, id) ||
                    (packageName == "com.google.android.youtube" && id.contains("shorts")) ||
                    (packageName == "com.zhiliaoapp.musically" && (id.contains("tiktok") || id.contains("vertical_view_pager"))) ||
                    (packageName == "com.snapchat.android" && id.contains("spotlight"))
                if (isShortsId) {
                    isMainScroll = true
                    if (!isCommentView) {
                        isExplicitShortsView = true
                    }
                }
                // If the direct ID didn't match, check ancestry
                if (!isExplicitShortsView && !isCommentView) {
                    if (isShortsAncestry(scrollSource, packageName)) {
                        isExplicitShortsView = true
                        isMainScroll = true
                    }
                }
                scrollSource.recycle()
            }

            // If we're currently in shorts and the user is scrolling, keep the
            // shorts detection alive so the grace period doesn't expire mid-scroll.
            if (isCurrentlyInShorts) {
                lastShortsDetectionAtMillis = now
            }
            
            val inShorts = isCurrentlyInShorts || isExplicitShortsView
            if (inShorts) {
                isMainScroll = true
            }
            
            if (inShorts && isMainScroll && !isCommentView) {
                
                var hasVerticalDelta = false
                var hasZeroDelta = true
                
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    val deltaY = event.scrollDeltaY
                    val deltaX = event.scrollDeltaX
                    if (deltaY != 0 || deltaX != 0) {
                         hasZeroDelta = false
                         hasVerticalDelta = Math.abs(deltaY) > Math.abs(deltaX)
                    }
                }

                // If delta is reported and it's primarily horizontal, ignore it (carousel swipe)
                if (!hasZeroDelta && !hasVerticalDelta) return

                val fromIndex = event.fromIndex
                val toIndex = event.toIndex
                
                var isCommentOrList = false
                
                // Reels are full-screen items. If more than 3 items are visible,
                // it's a list of smaller items (like comments or a grid), so we ignore it.
                if (fromIndex != -1 && toIndex != -1) {
                    val visibleItemCount = (toIndex - fromIndex) + 1
                    if (visibleItemCount > 3) {
                        isCommentOrList = true
                    }
                }

                if (!isCommentOrList) {
                    var isNewItem = false
                    
                    if (inShorts) {
                        // In full-screen Reels/Shorts, every vertical swipe transitions to a new video item.
                        // ViewPager indices frequently stay constant or are -1, and deltas are often 0.
                        // The 800ms debounce ensures one count per swipe gesture.
                        isNewItem = true
                    } else if (fromIndex != -1) {
                        val changed = fromIndex != lastScrollFromIndex
                        if (changed) {
                            lastScrollFromIndex = fromIndex
                            isNewItem = true
                        } else if (!hasZeroDelta && hasVerticalDelta) {
                            isNewItem = true
                        }
                    } else {
                        // Fallback if fromIndex isn't reported by the app
                        isNewItem = !hasZeroDelta
                    }

                    if (isNewItem) {
                        if (now - lastReelScrollTime > 800L) { // Debounce for reel swipes
                            lastReelScrollTime = now
                            serviceScope.launch {
                                // Enable reels counter only when there is time (not blocked)
                                if (shouldBlockApp(packageName)) {
                                    updateOverlayOnMainThread(false)
                                    return@launch
                                }

                                val startOfDay = java.util.Calendar.getInstance().apply {
                                    timeInMillis = now
                                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    set(java.util.Calendar.MINUTE, 0)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }.timeInMillis
                                reelCount = settingsDataStore.incrementReelCount(startOfDay)
                                try {
                                    reelEventDao.insertReelEvent(
                                        ReelEventEntity(
                                            timestamp = now,
                                            packageName = packageName
                                        )
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("AppTracking", "Failed to insert reel event", e)
                                }
                                
                                if (settingsDataStore.showReelCount.first()) {
                                    showOverlayTemporarily()
                                }
                            }
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

        if (now - lastBlockerLaunchAtMillis < BLOCKER_LAUNCH_COOLDOWN_MS) return

        eventProcessingJob = serviceScope.launch {
            val blockType = settingsDataStore.blockType.first()
            val isSupportedApp = isShortFormSupportedApp(packageName)

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
                val shouldEagerBlock = if (isSupportedApp && blockType == "REELS") {
                    false
                } else {
                    true
                }

                if (shouldEagerBlock && shouldBlockApp(packageName)) {
                    android.util.Log.d("AppTracking", "Accessibility blocking app: $packageName")
                    updateOverlayOnMainThread(false)
                    lastBlockerLaunchAtMillis = System.currentTimeMillis()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    triggerBlocker(packageName)
                    return@launch
                }
            }

            // Otherwise, check for short-form content specifically for unblocked apps or partial blocks
            if (isSupportedApp) {
                
                val rootNode = rootInActiveWindow
                if (rootNode == null) {
                    isCurrentlyInShorts = false
                    if (isShortFormSupportedApp(packageName)) updateOverlayOnMainThread(false)
                    return@launch
                }
                
                val shortsResult = isShortFormContentPresent(rootNode, packageName)
                if (shortsResult.isInSafeContext) {
                    lastShortsDetectionAtMillis = 0L // Cancel the grace period for scroll counting
                } else if (shortsResult.isPresent) {
                    lastShortsDetectionAtMillis = System.currentTimeMillis()
                }
                
                // Grace period is strictly for keeping the scroll counter active during transitions
                val recentlyInShorts = (System.currentTimeMillis() - lastShortsDetectionAtMillis) < 3000L
                isCurrentlyInShorts = shortsResult.isPresent || recentlyInShorts

                // Ensure we update overlay visibility if shorts state changes
                // The reels counter is enabled only when there is time (not blocked)
                if (isShortFormSupportedApp(packageName)) {
                    val showReelCount = settingsDataStore.showReelCount.first()
                    val willBeBlocked = shouldBlockApp(packageName)
                    if (isCurrentlyInShorts && showReelCount && !willBeBlocked) {
                        val startOfDay = java.util.Calendar.getInstance().apply {
                            timeInMillis = System.currentTimeMillis()
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        reelCount = settingsDataStore.getReelCountToday(startOfDay)
                    } else if (!isCurrentlyInShorts || willBeBlocked || !showReelCount) {
                        updateOverlayOnMainThread(false)
                    }
                }

                // STRICT BLOCKING LOGIC: Only block if we actively see the reel right now.
                // Do NOT use the grace period for blocking, otherwise leaving a reel 
                // will falsely trigger a block on the new tab.
                // A Reel card in Facebook's Home feed may autoplay, but it is not
                // the infinite-scroll viewer. Only enforce Facebook's Reels-only
                // block after the user opens the fullscreen Reel experience.
                val isBlockableShortForm = shortsResult.isPresent &&
                    !shortsResult.isInSafeContext &&
                    (packageName != "com.facebook.katana" || shortsResult.isFullScreen)

                if (isBlockableShortForm) {
                    if (shouldBlockApp(packageName)) {
                        updateOverlayOnMainThread(false)
                        isCurrentlyInShorts = false
                        lastShortsDetectionAtMillis = 0L

                        // TikTok is entirely short-form — there is no "safe" tab.
                        // Send the user to the home screen and launch the blocker.
                        if (packageName == "com.zhiliaoapp.musically") {
                            android.util.Log.d("AppTracking", "Accessibility blocking TikTok (entire app is reels): $packageName")
                            lastBlockerLaunchAtMillis = System.currentTimeMillis()
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            triggerBlocker(packageName)
                            return@launch
                        }

                        // For apps with safe tabs (Instagram, YouTube, Facebook, Snapchat):
                        // Try to click the safe tab first, then ALWAYS launch the blocker.
                        var homeAction = HomeTabAction.NOT_FOUND
                        val newRoot = rootInActiveWindow
                        if (newRoot != null) {
                            homeAction = clickSafeTab(newRoot, packageName)
                        }

                        if (homeAction == HomeTabAction.ALREADY_ON_HOME && !shortsResult.isFullScreen) {
                            // A selected Home tab is safe only when the detection is
                            // not fullscreen. YouTube keeps Home selected behind a
                            // Short opened from the Home feed.
                            return@launch
                        }

                        android.util.Log.d("AppTracking", "Accessibility blocking short-form content in: $packageName")
                        lastBlockerLaunchAtMillis = System.currentTimeMillis()

                        // Navigate away from reels first
                        if (homeAction != HomeTabAction.CLICKED) {
                            // Couldn't click a safe tab — send user to device home screen
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        }

                        // Always launch the blocker so user must complete challenge/wait
                        triggerBlocker(packageName)
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
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(48, 24, 48, 24)
                        elevation = 16f
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.parseColor("#99000000")) // Semi-transparent dark background
                            cornerRadius = 100f // Pill shape
                            setStroke(2, android.graphics.Color.parseColor("#33FFFFFF")) // Subtle border
                        }
                    }

                    val iconText = android.widget.TextView(context).apply {
                        text = "⚠️"
                        textSize = 16f
                        setPadding(0, 0, 16, 0)
                    }

                    reelCountText = android.widget.TextView(context).apply {
                        text = getFormattedCountText(reelCount)
                    }

                    linearLayout.addView(iconText)
                    linearLayout.addView(reelCountText)
                    overlayView = linearLayout

                    try {
                        windowManager?.addView(overlayView, params)
                    } catch (e: Exception) {
                        android.util.Log.e("AppTracking", "Failed to add reel overlay", e)
                    }
                    lastDisplayedReelCount = reelCount
                } else {
                    overlayView?.animate()?.cancel()
                    overlayView?.alpha = 1f
                    reelCountText?.text = getFormattedCountText(reelCount)
                    lastDisplayedReelCount = reelCount
                }
            } else {
                overlayFadeJob?.cancel()
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

    private fun getFormattedCountText(count: Int): android.text.SpannableString {
        val label = "Reels Scrolled: "
        val countStr = count.toString()
        val spannable = android.text.SpannableString(label + countStr)
        
        // Label styling (dimmed, normal weight)
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#CCCCCC")),
            0, label.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            android.text.style.AbsoluteSizeSpan(14, true),
            0, label.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        // Count styling (bold, bright red, larger)
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FF5252")),
            label.length, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            label.length, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            android.text.style.AbsoluteSizeSpan(16, true),
            label.length, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    private data class ShortsDetectionResult(val isPresent: Boolean, val isFullScreen: Boolean, val isInSafeContext: Boolean = false)

    private fun isShortFormContentPresent(rootNode: AccessibilityNodeInfo, packageName: String): ShortsDetectionResult {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.addFirst(rootNode)
        var count = 0
        var found = false
        var isFullScreen = false
        var isInSafeContext = false
        
        while (queue.isNotEmpty() && count < NODE_SCAN_LIMIT) {
            val node = queue.removeFirst()
            count++
            
            // Only consider nodes that are currently visible on the screen.
            // This is the critical fix for "background" Reels continuing to block 
            // when DMs or Profile fragments open on top.
            if (isVisibleInActiveWindow(node, rootNode)) {
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                val id = node.viewIdResourceName?.toString()?.lowercase() ?: ""
                val text = node.text?.toString()?.lowercase() ?: ""
                
                // 1. Check for Safe Contexts (Negative Markers)
                // If we detect we are in DMs, Profile, Settings, etc., or on a safe navigation tab, abort immediately.
                if (isSafeContext(node, packageName, text, desc, id, rootNode)) {
                    isInSafeContext = true
                    node.recycle()
                    break // Stop searching immediately, we are safe.
                }

                var isShorts = false
                
                // 2. Check for selected short-form tabs at the bottom navigation
                var isTabSelected = node.isSelected || desc.contains("selected")
                if (!isTabSelected) {
                    var p = node.parent
                    var pDepth = 0
                    while (p != null && pDepth < 3) {
                        val pDesc = p.contentDescription?.toString()?.lowercase() ?: ""
                        if (p.isSelected || pDesc.contains("selected")) {
                            isTabSelected = true
                            p.recycle()
                            break
                        }
                        val op = p
                        p = p.parent
                        op.recycle()
                        pDepth++
                    }
                    if (p != null && !isTabSelected) p.recycle()
                }

                val isReelsTabLabel = desc.contains("reels") || desc.contains("shorts") || 
                    desc.contains("spotlight") || desc.contains("for you") || 
                    id.contains("clips_tab") || id.contains("reels_tab")

                if (isTabSelected && isReelsTabLabel) {
                    val windowBounds = android.graphics.Rect()
                    rootNode.getBoundsInScreen(windowBounds)
                    val screenHeight = windowBounds.height()
                    if (screenHeight > 0) {
                        val tabBounds = android.graphics.Rect()
                        node.getBoundsInScreen(tabBounds)
                        // Must be in the bottom 25% of the screen (avoids profile tab false positives)
                        if (tabBounds.bottom > screenHeight * 0.75f) {
                            isShorts = true
                            isFullScreen = true
                        }
                    }
                }
                
                // 3. Check for specific short-form video player containers
                if (!isShorts && id.isNotEmpty()) {
                    if (isShortsContainerId(packageName, id)) {
                        val windowBounds = android.graphics.Rect()
                        rootNode.getBoundsInScreen(windowBounds)
                        val screenHeight = windowBounds.height()
                        
                        if (screenHeight > 0) {
                            val nodeBounds = android.graphics.Rect()
                            node.getBoundsInScreen(nodeBounds)
                            
                            val minHeightRatio = if (packageName == "com.instagram.android") 0.50f else 0.65f
                            
                            if (nodeBounds.height() > screenHeight * minHeightRatio) {
                                isShorts = true
                                isFullScreen = true
                            }
                        }
                    }
                }
                
                // 4. Check for specific content descriptions and texts
                if (!isShorts) {
                    if (desc.contains("short video") || desc.contains("tiktok video")) {
                        isShorts = true
                        isFullScreen = true
                    }
                    if (packageName == "com.google.android.youtube") {
                        if (desc.contains("dislike this video") ||
                            desc.contains("like this video") ||
                            desc.contains("remix this video") ||
                            desc == "remix" ||
                            desc.contains("sound used in this short") ||
                            desc.contains("use this sound") ||
                            desc.contains("shorts sound") ||
                            desc.contains("search shorts") ||
                            desc.contains("shorts camera")
                        ) {
                            isShorts = true
                            isFullScreen = true
                        }
                    }
                    if (packageName == "com.facebook.katana") {
                        if (desc == "reels" || desc == "reel") {
                            isShorts = true
                        }
                    }
                    if (packageName == "com.instagram.android") {
                        // In full-screen Reels viewer, the title header at the top says "Reels".
                        // Check that it's located in the top 15% of the screen so it doesn't match the bottom bar!
                        if (text == "reels" || desc == "reels" || text == "reel" || desc == "reel") {
                            val windowBounds = android.graphics.Rect()
                            rootNode.getBoundsInScreen(windowBounds)
                            if (windowBounds.height() > 0) {
                                val nodeBounds = android.graphics.Rect()
                                node.getBoundsInScreen(nodeBounds)
                                if (nodeBounds.top < windowBounds.height() * 0.15f) {
                                    isShorts = true
                                    isFullScreen = true
                                }
                            }
                        }
                    }
                    if (packageName == "com.snapchat.android" && (text == "spotlight" || desc.contains("spotlight"))) {
                        val windowBounds = android.graphics.Rect()
                        rootNode.getBoundsInScreen(windowBounds)
                        if (windowBounds.height() > 0) {
                            val nodeBounds = android.graphics.Rect()
                            node.getBoundsInScreen(nodeBounds)
                            if (nodeBounds.top < windowBounds.height() * 0.15f) {
                                isShorts = true
                                isFullScreen = true
                            }
                        }
                    }
                }
                
                if (isShorts) {
                    found = true
                    // We don't break immediately here because we want to exhaust the 
                    // tree slightly more in case a safe context (like a bottom sheet DM) 
                    // is layered over the Reels container.
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
        
        if (isInSafeContext) {
            return ShortsDetectionResult(false, false, true)
        }
        
        return ShortsDetectionResult(found, isFullScreen, false)
    }

    /**
     * Some Android 7-8 OEM accessibility implementations report false from
     * isVisibleToUser for every child in a hardware-accelerated app window.
     * For those versions, bounds intersection is the reliable visibility signal.
     */
    private fun isVisibleInActiveWindow(
        node: AccessibilityNodeInfo,
        rootNode: AccessibilityNodeInfo
    ): Boolean {
        if (node.isVisibleToUser) return true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) return false

        val rootBounds = android.graphics.Rect()
        rootNode.getBoundsInScreen(rootBounds)
        val nodeBounds = android.graphics.Rect()
        node.getBoundsInScreen(nodeBounds)
        return !rootBounds.isEmpty && !nodeBounds.isEmpty &&
            android.graphics.Rect.intersects(rootBounds, nodeBounds)
    }

    private fun isShortsContainerId(packageName: String, id: String): Boolean {
        if (packageName == "com.instagram.android") {
            return id.contains("clips_video_container") || 
                   id.contains("clips_viewer") ||
                   id.contains("reels_viewer") ||
                   // Older Instagram releases use singular `reel_*` IDs.
                   id.contains("reel_viewer") ||
                   id.contains("reel_item") ||
                   id.contains("reel_video_container") ||
                   id.contains("reel_view_pager") ||
                   id.contains("reel_swipe") ||
                   id.contains("clips_item") ||
                   id.contains("clips_swipe") ||
                   id.contains("clips_view_pager")
        }
        if (packageName == "com.google.android.youtube") {
            return id.contains("reel_recycler") ||
                   id.contains("reel_player") ||
                   id.contains("reel_watch") ||
                   id.contains("shorts_container") ||
                   id.contains("shorts_player") ||
                   id.contains("shorts_view_pager") ||
                   id.contains("reel_player_page") ||
                   id.contains("reel_watch_pager")
        }
        if (packageName == "com.facebook.katana") {
            return id.contains("fb_shorts") || 
                   id.contains("reels_video") ||
                   id.contains("reels_playback") ||
                   id.contains("short_video")
        }
        if (packageName == "com.zhiliaoapp.musically") {
            return id.contains("vertical_view_pager")
        }
        if (packageName == "com.snapchat.android") {
            return id.contains("spotlight") || id.contains("content_container")
        }
        return false
    }

    private fun isSafeContext(
        node: AccessibilityNodeInfo,
        packageName: String,
        text: String,
        desc: String,
        id: String,
        rootNode: AccessibilityNodeInfo
    ): Boolean {
        if (packageName == "com.instagram.android") {
            // Direct Messages
            if (text == "message..." || desc == "message..." || text == "type a message..." || desc == "type a message...") return true
            if (text == "direct" && id.contains("title")) return true
            if (text == "new message" || desc == "new message" || text == "messages" || desc == "messages") return true
            if (id.contains("direct_star") || id.contains("thread_title") || id.contains("message_content") || id.contains("row_thread")) return true

            // Profile
            if (text == "edit profile" || desc == "edit profile") return true
            if (text == "share profile" || desc == "share profile") return true
            if (desc.contains("profile tab, selected") || desc.contains("profile, selected")) return true
            if (id.contains("profile_tab") && (node.isSelected || desc.contains("selected"))) return true

            // Search / Explore
            if (text == "search" && id.contains("action_bar")) return true
            if (desc.contains("search tab, selected") || desc.contains("search and explore, selected") || desc.contains("search, selected")) return true
            if (id.contains("search_tab") && (node.isSelected || desc.contains("selected"))) return true

            // Home / Feed tab selected
            if (desc.contains("home tab, selected") || desc.contains("home, selected") || desc.contains("feed tab, selected")) return true
            if ((id.contains("feed_tab") || id.contains("home_tab") || id.contains("tab_home")) && (node.isSelected || desc.contains("selected"))) return true

            // Check if node is a selected safe bottom tab by checking bounds & selection
            val isTabOrLabel = desc.startsWith("home") || desc.startsWith("search") || desc.startsWith("profile") || desc == "feed"
            if (isTabOrLabel) {
                var isSel = node.isSelected || desc.contains("selected")
                if (!isSel) {
                    var p = node.parent
                    var depth = 0
                    while (p != null && depth < 3) {
                        val pDesc = p.contentDescription?.toString()?.lowercase() ?: ""
                        if (p.isSelected || pDesc.contains("selected")) {
                            isSel = true
                            p.recycle()
                            break
                        }
                        val op = p
                        p = p.parent
                        op.recycle()
                        depth++
                    }
                    if (p != null && !isSel) p.recycle()
                }
                if (isSel) {
                    val windowBounds = android.graphics.Rect()
                    rootNode.getBoundsInScreen(windowBounds)
                    val screenHeight = windowBounds.height()
                    if (screenHeight > 0) {
                        val tabBounds = android.graphics.Rect()
                        node.getBoundsInScreen(tabBounds)
                        if (tabBounds.bottom > screenHeight * 0.70f) {
                            return true
                        }
                    }
                }
            }

            // Create / Camera
            if (desc.contains("camera tab, selected") || (id.contains("camera_tab") && (node.isSelected || desc.contains("selected")))) return true

            // Settings
            if (text == "settings and activity" || desc == "settings and activity") return true
        }

        if (packageName == "com.google.android.youtube") {
            if (text == "notifications" || desc == "notifications") return true
            if (text == "search youtube" || desc == "search youtube") return true
            if (text == "history" && id.contains("title")) return true
            if (id.contains("bottom_bar") && desc.contains("you, selected")) return true // "You" tab
            // YouTube retains the source tab as selected while a Short opened from
            // that tab is playing. In particular, a Short opened from Home still
            // exposes "Home, selected", so that is not enough to prove that the
            // current screen is safe. Let the Shorts-player markers decide instead.
            if (desc.contains("subscriptions, selected")) return true
        }

        if (packageName == "com.zhiliaoapp.musically") {
            // TikTok: Profile, Inbox, Discover, Friends are safe
            if (desc.contains("profile, selected") || desc.contains("profile tab, selected")) return true
            if (text == "profile" && (node.isSelected || desc.contains("selected"))) return true
            if (desc.contains("inbox, selected") || desc.contains("inbox tab, selected")) return true
            if (text == "inbox" && (node.isSelected || desc.contains("selected"))) return true
            if (desc.contains("discover, selected") || desc.contains("discover tab, selected")) return true
            if (desc.contains("friends, selected") || desc.contains("friends tab, selected")) return true
            // Search / camera
            if (text == "search" && id.contains("search")) return true
        }

        if (packageName == "com.facebook.katana") {
            // Facebook's Home tab remains selected after opening a Reel from the
            // feed. Do not use it as a safe-context marker: the fullscreen-player
            // check above distinguishes an opened Reel from an inline feed card.
            // Marketplace, Menu, Notifications, and Gaming remain safe.
            if (desc.contains("menu, selected") || desc.contains("menu tab, selected")) return true
            if (desc.contains("marketplace, selected") || desc.contains("marketplace tab, selected")) return true
            if (desc.contains("notifications, selected") || desc.contains("notifications tab, selected")) return true
            if (desc.contains("gaming, selected") || desc.contains("gaming tab, selected")) return true
            if (text == "search" && id.contains("search")) return true
        }

        if (packageName == "com.snapchat.android") {
            // Snapchat: Chat, Camera, Map, Stories are safe (not Spotlight)
            if (desc.contains("chat, selected") || desc.contains("chat tab, selected")) return true
            if (desc.contains("camera, selected") || desc.contains("camera tab, selected")) return true
            if (desc.contains("map, selected") || desc.contains("map tab, selected")) return true
            if (desc.contains("stories, selected") || desc.contains("stories tab, selected")) return true
            if (text == "profile" || desc == "profile") return true
        }
        
        return false
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

    private fun clickSafeTab(rootNode: AccessibilityNodeInfo, packageName: String): HomeTabAction {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.addFirst(rootNode)
        var count = 0
        val windowBounds = android.graphics.Rect()
        rootNode.getBoundsInScreen(windowBounds)
        val screenHeight = windowBounds.height()
        
        while (queue.isNotEmpty() && count < NODE_SCAN_LIMIT) {
            val node = queue.removeFirst()
            count++
            
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val id = node.viewIdResourceName?.toString()?.lowercase() ?: ""
            
            val isSafeTab = when (packageName) {
                "com.zhiliaoapp.musically" -> desc == "profile" || text == "profile"
                "com.snapchat.android" -> desc.contains("chat") || text.contains("chat") || desc.contains("camera") || text.contains("camera")
                "com.instagram.android" -> {
                    val isHomeDesc = desc.startsWith("home") || desc.contains("home tab") || desc == "home" || desc == "feed" || text == "home" || text == "feed"
                    val isHomeId = id.contains("feed_tab") || id.contains("tab_home") || id.contains("home_tab")
                    if (screenHeight > 0) {
                        val nodeBounds = android.graphics.Rect()
                        node.getBoundsInScreen(nodeBounds)
                        (isHomeDesc || isHomeId) && nodeBounds.bottom > screenHeight * 0.70f
                    } else {
                        isHomeDesc || isHomeId
                    }
                }
                else -> desc == "home" || desc == "feed" || desc.contains("home tab") || text == "home"
            }

            if (isSafeTab) {
                var isSelected = node.isSelected || desc.contains("selected")
                if (!isSelected) {
                    var p = node.parent
                    var depth = 0
                    while (p != null && depth < 3) {
                        val pDesc = p.contentDescription?.toString()?.lowercase() ?: ""
                        if (p.isSelected || pDesc.contains("selected")) {
                            isSelected = true
                            p.recycle()
                            break
                        }
                        val op = p
                        p = p.parent
                        op.recycle()
                        depth++
                    }
                    if (p != null && !isSelected) p.recycle()
                }

                if (packageName == "com.facebook.katana") {
                    node.recycle()
                    while (queue.isNotEmpty()) queue.removeFirst().recycle()
                    return HomeTabAction.ALREADY_ON_HOME
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
                    var depth = 0
                    while (parent != null && depth < 4) {
                        if (parent.isClickable) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            clicked = true
                            parent.recycle()
                            break
                        }
                        val oldParent = parent
                        parent = parent.parent
                        oldParent.recycle()
                        depth++
                    }
                    if (parent != null && !clicked) parent.recycle()
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
                putExtra("CHALLENGE_DIFFICULTY", settingsDataStore.challengeDifficulty.first())
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
