package com.flow.shift.core.usagetracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.flow.shift.core.database.BlockedAppDao
import com.flow.shift.core.database.InterventionDao
import com.flow.shift.core.database.InterventionEntity
import com.flow.shift.core.database.WorkoutSessionDao
import com.flow.shift.core.datastore.SettingsDataStore
import com.flow.shift.feature.exerciseblocker.BlockerActivity
import com.flow.shift.feature.exerciseblocker.WaitActivity
import com.flow.shift.feature.modes.BlockingMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppTrackingService : Service() {

    @Inject
    lateinit var usageTracker: UsageTracker

    @Inject
    lateinit var blockedAppDao: BlockedAppDao

    @Inject
    lateinit var workoutSessionDao: WorkoutSessionDao

    @Inject
    lateinit var interventionDao: InterventionDao

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var lastInterventionPackage: String? = null
    private var lastInterventionAtMillis: Long = 0L
    private var lastCheckedPackage: String? = null
    private var lastCheckedAtMillis: Long = 0L
    private var cachedTargetPackages: Set<String> = emptySet()
    private var cachedTargetReached = false
    private var cachedTargetCheckedAtMillis: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }



    override fun onCreate() {
        super.onCreate()
        isRunning = true
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            android.util.Log.e("AppTracking", "Failed to start foreground service", e)
        }
        startTracking()
        startAutoDowngradeCheck()
    }

    private fun startAutoDowngradeCheck() {
        serviceScope.launch {
            while (true) {
                try {
                    val autoDowngrade = settingsDataStore.autoDowngradeAtMidnight.first()
                    if (autoDowngrade) {
                        val currentModeStr = settingsDataStore.blockingMode.first()
                        val currentMode = BlockingMode.fromString(currentModeStr)
                        if (currentMode != BlockingMode.EASY) {
                            val lastAutoTime = settingsDataStore.lastAutoDowngradeTimeMillis.first()
                            val now = System.currentTimeMillis()
                            
                            var shouldDowngrade = false
                            if (lastAutoTime > 0L) {
                                val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = lastAutoTime }
                                val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = now }
                                if (cal1.get(java.util.Calendar.YEAR) != cal2.get(java.util.Calendar.YEAR) ||
                                    cal1.get(java.util.Calendar.DAY_OF_YEAR) != cal2.get(java.util.Calendar.DAY_OF_YEAR)) {
                                    shouldDowngrade = true
                                }
                            }

                            if (shouldDowngrade) {
                                val targetModeStr = settingsDataStore.downgradeRequestTarget.first()
                                val targetMode = targetModeStr?.let { BlockingMode.fromString(it) } ?: BlockingMode.EASY
                                android.util.Log.d("AppTracking", "Auto downgrading to $targetMode at midnight")
                                settingsDataStore.setBlockingMode(targetMode.name)
                                settingsDataStore.setLastDowngradeTime(now)
                                settingsDataStore.setLastAutoDowngradeTime(now)
                                settingsDataStore.setAutoDowngradeAtMidnight(false)
                                settingsDataStore.setDowngradeRequestTarget(null)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppTracking", "Error in auto-downgrade check", e)
                }
                delay(60_000) // Check every minute
            }
        }
    }

    private fun startTracking() {
        serviceScope.launch {
            while (true) {
                try {
                    val foregroundApp = usageTracker.getForegroundApp()
                    val now = System.currentTimeMillis()

                    if (
                        foregroundApp != null &&
                        foregroundApp != packageName &&
                        shouldEvaluateForegroundApp(foregroundApp, now)
                    ) {
                        checkIfAppIsBlocked(foregroundApp)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppTracking", "Error in tracking loop", e)
                }

                delay(TRACKING_POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun shouldEvaluateForegroundApp(packageName: String, nowMillis: Long): Boolean {
        if (
            lastCheckedPackage == packageName &&
            nowMillis - lastCheckedAtMillis < SAME_APP_RECHECK_INTERVAL_MILLIS
        ) {
            return false
        }

        lastCheckedPackage = packageName
        lastCheckedAtMillis = nowMillis
        return true
    }

    private suspend fun checkIfAppIsBlocked(packageName: String) {
        val blockedApp = blockedAppDao.getBlockedApp(packageName)
        if (blockedApp != null && blockedApp.isEnabled) {
            val focusModeUntilMillis = settingsDataStore.focusModeUntilMillis.first()
            val currentTime = System.currentTimeMillis()
            val isFocusModeActive = focusModeUntilMillis > currentTime

            if (!isFocusModeActive && !isTargetTimeReached(currentTime)) {
                return
            }

            val blockingMode = BlockingMode.fromString(settingsDataStore.blockingMode.first())
            val hasActiveTimeBank = !isFocusModeActive && blockingMode != BlockingMode.HARDCORE &&
                workoutSessionDao.getActiveUnlockCount(packageName, currentTime) > 0

            if (!hasActiveTimeBank) {
                val challengeType = settingsDataStore.strictChallengeType.first()
                val challengeAmount = blockedApp.customDifficulty ?: settingsDataStore.strictChallengeAmount.first()
                val breakDurationMinutes = settingsDataStore.breakDurationMinutes.first()

                if (!isDuplicateIntervention(packageName, currentTime)) {
                    interventionDao.insertIntervention(
                        InterventionEntity(
                            timestamp = currentTime,
                            targetAppPackage = packageName,
                            targetAppName = blockedApp.appName,
                            requiredReps = challengeAmount
                        )
                    )
                    lastInterventionPackage = packageName
                    lastInterventionAtMillis = currentTime
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
                
                try {
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        this@AppTrackingService,
                        packageName.hashCode(),
                        intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                        val options = android.app.ActivityOptions.makeBasic()
                        options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        pendingIntent.send(this@AppTrackingService, 0, null, null, null, null, options.toBundle())
                    } else {
                        pendingIntent.send()
                    }
                    delay(BLOCKER_LAUNCH_SETTLE_MILLIS)
                } catch (e: Exception) {
                    android.util.Log.e("AppTracking", "Failed to start activity", e)
                }
            }
        }
    }

    private suspend fun isTargetTimeReached(now: Long): Boolean {
        val blockedApps = blockedAppDao.getEnabledBlockedApps().first()
        val protectedPackages = blockedApps.map { it.packageName }.toSet()
        if (
            cachedTargetPackages == protectedPackages &&
            now - cachedTargetCheckedAtMillis < TARGET_RECHECK_INTERVAL_MILLIS
        ) {
            return cachedTargetReached
        }

        val targetScreenTimeStr = settingsDataStore.targetScreenTime.first()
        val targetMillis = com.flow.shift.feature.dashboard.parseTargetScreenTimeMillis(targetScreenTimeStr)
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

    private fun isDuplicateIntervention(packageName: String, nowMillis: Long): Boolean {
        return lastInterventionPackage == packageName &&
            nowMillis - lastInterventionAtMillis < DUPLICATE_INTERVENTION_WINDOW_MILLIS
    }

    private fun createNotification(): Notification {
        val channelId = "flowshift_tracking_channel"
        val channelName = "App Tracking Service"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("FlowShift is active")
            .setContentText("Guarding your focus...")
            .setSmallIcon(android.R.drawable.ic_secure) // Replace with your app's icon
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceJob.cancel()
    }

    companion object {
        var isRunning = false
            private set

        private const val NOTIFICATION_ID = 1001
        private const val DUPLICATE_INTERVENTION_WINDOW_MILLIS = 10_000L
        private const val TRACKING_POLL_INTERVAL_MILLIS = 2_000L
        private const val SAME_APP_RECHECK_INTERVAL_MILLIS = 5_000L
        private const val TARGET_RECHECK_INTERVAL_MILLIS = 15_000L
        private const val BLOCKER_LAUNCH_SETTLE_MILLIS = 1_000L
    }
}

