package com.flow.shift.core.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.flow.shift.core.usagetracking.AppTrackingService

/**
 * Restarts [AppTrackingService] after device boot or app update.
 *
 * Listens for:
 * - BOOT_COMPLETED — device reboot
 * - MY_PACKAGE_REPLACED — app update via Play Store
 *
 * Uses SharedPreferences directly (not DataStore) because BroadcastReceiver
 * has a ~10 second execution window and DataStore requires coroutines.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        // Check if onboarding is completed using SharedPreferences directly.
        // DataStore uses the file "settings.preferences_pb" but we can read the
        // boolean via the standard SharedPreferences backed file that DataStore writes to
        // at "datastore/settings.preferences_pb". However, the simplest reliable approach
        // is to read the same preferences DataStore file via SharedPreferences compat name.
        val prefs = context.getSharedPreferences(
            "settings_boot_check",
            Context.MODE_PRIVATE
        )
        // We write this flag from FlowShiftApplication / OnboardingViewModel when onboarding completes.
        val isOnboardingCompleted = prefs.getBoolean("is_onboarding_completed", false)

        if (!isOnboardingCompleted) {
            Log.d("BootReceiver", "Onboarding not completed, skipping service start")
            return
        }

        Log.d("BootReceiver", "Starting AppTrackingService after $action")
        try {
            val serviceIntent = Intent(context, AppTrackingService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e("BootReceiver", "Failed to start AppTrackingService", e)
        }
    }
}
