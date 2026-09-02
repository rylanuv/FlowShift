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
    private var lastScrollFromIndex = -1
    private var currentPackage: String? = null
    private var isCurrentlyInShorts = false
    private var lastShortsDetectionAtMillis = 0L

    private var overlayFadeJob: Job? = null

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
                        serviceScope.launch {
                            updateOverlayOnMainThread(false)
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
        const val NODE_SCAN_LIMIT = 600
    }

    /**
     * Lightweight check: walk up the parent chain of a scroll source node
     * looking for YouTube Shorts-specific view IDs. O(depth) ≈ ~10-15 nodes,
     * much cheaper than the full BFS scan in isShortFormContentPresent().
     */
    private fun isYouTubeShortsAncestry(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 15) {
            val id = current.viewIdResourceName?.toString()?.lowercase() ?: ""
            if (id.contains("reel_recycler") || id.contains("reel_watch") ||
                id.contains("shorts_container") || id.contains("shorts_player") ||
                id.contains("reel_player_page") || id.contains("reel_watch_pager") ||
                id.contains("shorts_view_pager")
            ) {
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
        }

        val now = System.currentTimeMillis()
        
        if (isShortFormSupportedApp(packageName) && eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val className = event.className?.toString() ?: ""
            var isMainScroll = className.contains("RecyclerView") || className.contains("ViewPager") || className.contains("ScrollView") || className.contains("ListView") || className.contains("GridView")
            
            var isExplicitShortsView = false
            var isCommentView = false
            val scrollSource = event.source
            if (scrollSource != null) {
                val id = scrollSource.viewIdResourceName?.toString()?.lowercase() ?: ""
                if (id.contains("comment") || id.contains("reply")) {
                    isCommentView = true
                }
                if (id.contains("shorts") || id.contains("reel") || id.contains("clips") || id.contains("tiktok")) {
                    isMainScroll = true
                    if (!isCommentView) {
                        isExplicitShortsView = true
                    }
                }
                // For YouTube: if the direct ID didn't match, do a lightweight
                // parent-chain check before giving up. This catches cases where
                // the RecyclerView has a generic ID but is nested inside a Shorts container.
                if (!isExplicitShortsView && !isCommentView &&
                    packageName == "com.google.android.youtube"
                ) {
                    if (isYouTubeShortsAncestry(scrollSource)) {
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
                
                // Reels are full-screen items. If more than 2 items are visible,
                // it's a list of smaller items (like comments), so we ignore it.
                if (fromIndex != -1 && toIndex != -1) {
                    val visibleItemCount = (toIndex - fromIndex) + 1
                    if (visibleItemCount > 3) {
                        isCommentOrList = true
                    }
                }

                // If it's an explicit full-screen shorts container view, override the list heuristic
                if (isExplicitShortsView) {
                    isCommentOrList = false
                } else if (packageName == "com.instagram.android") {
                    // Instagram's main reels container always has an explicit ID (e.g. clips_video_container).
                    // A generic RecyclerView without a known ID (e.g. the comments bottom sheet opening) should be ignored.
                    isCommentOrList = true
                }

                if (!isCommentOrList) {
                    var isNewItem = false
                    
                    if (packageName == "com.google.android.youtube") {
                        if (isExplicitShortsView) {
                            // Shorts container scroll: indices often don't change
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
                            isNewItem = true
                        }
                    } else if (fromIndex != -1) {
                        val changed = fromIndex != lastScrollFromIndex
                        if (changed) {
                            lastScrollFromIndex = fromIndex
                            isNewItem = true
                        }
                    } else {
                        // Fallback if fromIndex isn't reported by the app
                        isNewItem = !hasZeroDelta
                    }

                    if (isNewItem) {
                        if (now - lastReelScrollTime > 800L) { // Increased debounce for reels
                            lastReelScrollTime = now
                            serviceScope.launch {
                                val startOfDay = java.util.Calendar.getInstance().apply {
                                    timeInMillis = now
                                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    set(java.util.Calendar.MINUTE, 0)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }.timeInMillis
                                reelCount = settingsDataStore.incrementReelCount(startOfDay)
                                
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
                if (shortsResult.isPresent) {
                    lastShortsDetectionAtMillis = System.currentTimeMillis()
                }
                val recentlyInShorts = (System.currentTimeMillis() - lastShortsDetectionAtMillis) < 5000L
                isCurrentlyInShorts = shortsResult.isPresent || recentlyInShorts

                if (isCurrentlyInShorts) {
                    if (isShortFormSupportedApp(packageName)) {
                        val showReelCount = settingsDataStore.showReelCount.first()
                        if (showReelCount) {
                            val startOfDay = java.util.Calendar.getInstance().apply {
                                timeInMillis = System.currentTimeMillis()
                                set(java.util.Calendar.HOUR_OF_DAY, 0)
                                set(java.util.Calendar.MINUTE, 0)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                            }.timeInMillis
                            reelCount = settingsDataStore.getReelCountToday(startOfDay)
                        } else {
                            updateOverlayOnMainThread(false)
                        }
                    }

                    if (shouldBlockApp(packageName)) {
                        
                        var homeAction = HomeTabAction.NOT_FOUND
                        val newRoot = rootInActiveWindow
                        if (newRoot != null) {
                            homeAction = clickSafeTab(newRoot, packageName)
                        }

                        if (homeAction == HomeTabAction.ALREADY_ON_HOME && !shortsResult.isFullScreen) {
                            // False positive from bottom nav bar, we are on the Home feed
                            return@launch
                        }

                        android.util.Log.d("AppTracking", "Accessibility blocking short-form content in: $packageName")
                        lastBlockerLaunchAtMillis = System.currentTimeMillis()

                        if (homeAction == HomeTabAction.CLICKED) {
                            android.util.Log.d("AppTracking", "Successfully switched to Home tab for $packageName")
                            recordIntervention(packageName)
                        } else {
                            android.util.Log.d("AppTracking", "Could not find Home tab, performing BACK action to exit Reels")
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            recordIntervention(packageName)
                        }
                    }
                } else {
                    if (isShortFormSupportedApp(packageName)) {
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
                    if (lastDisplayedReelCount != reelCount) {
                        reelCountText?.text = getFormattedCountText(reelCount)
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

    private data class ShortsDetectionResult(val isPresent: Boolean, val isFullScreen: Boolean)

    private fun isShortFormContentPresent(rootNode: AccessibilityNodeInfo, packageName: String): ShortsDetectionResult {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.addFirst(rootNode)
        var count = 0
        var found = false
        var isFullScreen = false
        
        while (queue.isNotEmpty() && count < NODE_SCAN_LIMIT) {
            val node = queue.removeFirst()
            count++
            
            var isShorts = false
            
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val id = node.viewIdResourceName?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            
            // 1. Check for selected short-form tabs
            if (node.isSelected) {
                if (desc == "reels" || desc == "shorts" || desc == "spotlight" || desc == "for you") {
                    isShorts = true
                    isFullScreen = true
                }
            }
            
            // 2. Check for specific short-form video player containers
            if (!isShorts && id.isNotEmpty()) {
                if (id.contains("clips_video_container") || 
                    id.contains("shorts_player") || 
                    id.contains("reel_viewer") ||
                    id.contains("reels_viewer") ||
                    id.contains("tiktok_video") ||
                    (packageName == "com.zhiliaoapp.musically" && id.contains("vertical_view_pager")) ||
                    (packageName == "com.google.android.youtube" && (
                        id.contains("reel_recycler") ||
                        id.contains("reel_player") ||
                        id.contains("reel_watch") ||
                        id.contains("reel_video") ||
                        id.contains("reel_container") ||
                        id.contains("reel_holder") ||
                        id.contains("reel_body") ||
                        id.contains("reel_page") ||
                        id.contains("reel_item") ||
                        id.contains("shorts_container") ||
                        id.contains("shorts_player") ||
                        id.contains("shorts_root") ||
                        id.contains("shorts_view_pager") ||
                        id.contains("reel_player_overlay") ||
                        id.contains("reel_player_page") ||
                        id.contains("reel_watch_pager")
                    )) ||
                    (packageName == "com.facebook.katana" && (
                        id.contains("fb_shorts") || 
                        id.contains("reels_video") ||
                        id.contains("reel_video") ||
                        id.contains("reels_playback") ||
                        id.contains("short_video")
                    ))
                ) {
                    isShorts = true
                    // Determine if it's explicitly full-screen
                    if (packageName == "com.google.android.youtube") {
                        if (id.contains("reel_recycler") || id.contains("reel_watch_pager") || id.contains("reel_player_page") || id.contains("shorts_player")) {
                            isFullScreen = true
                        }
                    } else if (packageName == "com.instagram.android") {
                        if (id.contains("clips_video_container") || id.contains("reel_viewer")) {
                            isFullScreen = true
                        }
                    } else if (packageName == "com.zhiliaoapp.musically") {
                        isFullScreen = true // TikTok is always full screen
                    }
                }
            }

            // 3. Check for specific content descriptions that indicate video playing
            if (!isShorts && desc.isNotEmpty()) {
                if (desc.contains("short video") || desc.contains("tiktok video")) {
                    isShorts = true
                    isFullScreen = true
                }
                // YouTube Shorts content descriptions
                if (!isShorts && packageName == "com.google.android.youtube") {
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
                // Relaxed text checks specifically for Facebook since its view IDs are heavily obfuscated
                if (!isShorts && packageName == "com.facebook.katana") {
                    val isExactReel = desc == "reels" || desc == "reel"
                    if (isExactReel) {
                        isShorts = true
                        // Note: We deliberately do NOT set isFullScreen = true here to avoid
                        // false positives with Reels shelves on the Facebook home feed.
                    }
                }
            }
            
            if (isShorts) {
                found = true
                if (isFullScreen) {
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
        
        // Recycle any remaining nodes in the queue to prevent memory leaks
        while (queue.isNotEmpty()) {
            queue.removeFirst().recycle()
        }
        return ShortsDetectionResult(found, isFullScreen)
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
        
        while (queue.isNotEmpty() && count < NODE_SCAN_LIMIT) {
            val node = queue.removeFirst()
            count++
            
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            
            val isSafeTab = when (packageName) {
                "com.zhiliaoapp.musically" -> desc == "profile" || text == "profile"
                "com.snapchat.android" -> desc.contains("chat") || text.contains("chat") || desc.contains("camera") || text.contains("camera")
                else -> desc == "home" || desc == "feed" || desc.contains("home tab") || text == "home"
            }

            if (isSafeTab) {
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
