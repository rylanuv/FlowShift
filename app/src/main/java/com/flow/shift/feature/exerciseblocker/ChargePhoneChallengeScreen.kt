package com.flow.shift.feature.exerciseblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.shift.theme.Amber500
import com.flow.shift.theme.AppFontFamily
import com.flow.shift.theme.SurfaceCardDarker
import com.flow.shift.theme.TextPrimary
import com.flow.shift.theme.TextSecondary

@Composable
fun ChargePhoneChallengeScreen(
    onChargeDetected: () -> Unit
) {
    val context = LocalContext.current
    var isCharging by remember { mutableStateOf(false) }

    val currentOnChargeDetected by rememberUpdatedState(onChargeDetected)

    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                
                if (intent?.action == Intent.ACTION_POWER_CONNECTED || charging) {
                    if (!isCharging) {
                        isCharging = true
                        currentOnChargeDetected()
                    }
                } else if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                    isCharging = false
                }
            }
        }
        context.registerReceiver(receiver, filter)

        // Initial check
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isChargingNow: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL
        if (isChargingNow && !isCharging) {
            isCharging = true
            currentOnChargeDetected()
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCardDarker),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Amber500.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = Amber500,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Plug in your charger",
                color = TextPrimary,
                fontSize = 24.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Connect your device to a power source to earn your scroll time.",
                color = TextSecondary,
                fontSize = 16.sp,
                fontFamily = AppFontFamily,
                textAlign = TextAlign.Center
            )
        }
    }
}
