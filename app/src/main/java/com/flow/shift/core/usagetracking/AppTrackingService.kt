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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
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
                                android.util.Log.d("AppTracking", "Auto downgrading to EASY at midnight")
                                settingsDataStore.setBlockingMode(BlockingMode.EASY.name)
                                settingsDataStore.setLastDowngradeTime(now)
                                settingsDataStore.setLastAutoDowngradeTime(now)
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
        android.util.Log.d("AppTracking", "startTracking loop initiated")
        serviceScope.launch {
            while (true) {
                try {
                    val foregroundApp = usageTracker.getForegroundApp()
                    android.util.Log.d("AppTracking", "Polled foreground app: $foregroundApp")

                    if (foregroundApp != null && foregroundApp != packageName) {
                        checkIfAppIsBlocked(foregroundApp)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppTracking", "Error in tracking loop", e)
                }

                delay(500) // Poll every 500ms for fast detection
            }
        }
    }

    private suspend fun checkIfAppIsBlocked(packageName: String) {
        android.util.Log.d("AppTracking", "Checking if blocked: $packageName")
        val blockedApp = blockedAppDao.getBlockedApp(packageName)
        if (blockedApp != null && blockedApp.isEnabled) {
            android.util.Log.d("AppTracking", "$packageName is in blocked apps list")
            val blockedApps = blockedAppDao.getEnabledBlockedApps().first()
            val focusModeUntilMillis = settingsDataStore.focusModeUntilMillis.first()
            val currentTime = System.currentTimeMillis()
            val isFocusModeActive = focusModeUntilMillis > currentTime

            if (!isFocusModeActive && !isTargetTimeReached(blockedApps)) {
                android.util.Log.d("AppTracking", "Target time NOT reached and Focus Mode NOT active. Returning.")
                return
            }
            android.util.Log.d("AppTracking", "Target time reached or Focus Mode Active!")

            val blockingMode = BlockingMode.fromString(settingsDataStore.blockingMode.first())
            val recentSessions = workoutSessionDao.getAllSessions().first()
            val latestSessionForApp = recentSessions.find { it.targetAppPackage == packageName }
            val latestGlobalSession = recentSessions.find { it.targetAppPackage == "ALL_APPS" }

            val hasActiveTimeBank = !isFocusModeActive && blockingMode != BlockingMode.HARDCORE &&
                ((latestSessionForApp != null && latestSessionForApp.timeUnlockedMillis > currentTime) ||
                    (latestGlobalSession != null && latestGlobalSession.timeUnlockedMillis > currentTime))
            android.util.Log.d("AppTracking", "hasActiveTimeBank: $hasActiveTimeBank, focusModeActive: $isFocusModeActive")

            if (!hasActiveTimeBank) {
                android.util.Log.d("AppTracking", "Triggering blocker for $packageName!")

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
                    android.util.Log.d("AppTracking", "startActivity executed successfully")
                    // Brief pause after launching blocker to avoid spamming intents
                    delay(1000)
                } catch (e: Exception) {
                    android.util.Log.e("AppTracking", "Failed to start activity", e)
                }
            }
        } else {
            android.util.Log.d("AppTracking", "$packageName is NOT in blocked apps list or disabled")
        }
    }

    private suspend fun isTargetTimeReached(blockedApps: List<com.flow.shift.core.database.BlockedAppEntity>): Boolean {
        val targetScreenTimeStr = settingsDataStore.targetScreenTime.first()
        val targetMillis = com.flow.shift.feature.dashboard.parseTargetScreenTimeMillis(targetScreenTimeStr)
        
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
        serviceJob.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val DUPLICATE_INTERVENTION_WINDOW_MILLIS = 10_000L
    }
}

