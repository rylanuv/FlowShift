package com.flow.shift

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.flow.shift.core.usagetracking.AppTrackingService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FlowShiftApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Restart AppTrackingService if onboarding was previously completed.
        // This handles the case where the app process is recreated by the system
        // (e.g., after a crash or low-memory kill) without the user opening an Activity.
        val prefs = getSharedPreferences("settings_boot_check", MODE_PRIVATE)
        val isOnboardingCompleted = prefs.getBoolean("is_onboarding_completed", false)

        if (isOnboardingCompleted && !AppTrackingService.isRunning) {
            try {
                val serviceIntent = Intent(this, AppTrackingService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
            } catch (e: Exception) {
                Log.e("FlowShiftApp", "Failed to restart AppTrackingService on Application create", e)
            }
        }
    }
}
