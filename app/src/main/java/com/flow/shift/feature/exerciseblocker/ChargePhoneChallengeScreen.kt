package com.flow.shift.feature.exerciseblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.shift.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ChargePhoneChallengeScreen(
    requiredMinutes: Int,
    onComplete: () -> Unit,
    onCancel: () -> Unit = {}
) {
    val context = LocalContext.current
    val totalRequiredMillis = (requiredMinutes * 60L * 1000L).coerceAtLeast(1000L)
    val totalRequiredSeconds = (totalRequiredMillis / 1000L).toInt()

    var isCharging by rememberSaveable { mutableStateOf(false) }
    var batteryPercentage by rememberSaveable { mutableStateOf<Int?>(null) }
    var accumulatedChargingMillis by rememberSaveable { mutableLongStateOf(0L) }
    var lastChargeStartTime by rememberSaveable { mutableLongStateOf(0L) }
    var hasCompleted by rememberSaveable { mutableStateOf(false) }
    var currentTimeMillis by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    val currentOnComplete by rememberUpdatedState(onComplete)

    val startCharging: () -> Unit = {
        if (!isCharging) {
            val now = SystemClock.elapsedRealtime()
            isCharging = true
            lastChargeStartTime = now
            currentTimeMillis = now
        }
    }

    val stopCharging: () -> Unit = {
        if (isCharging) {
            val now = SystemClock.elapsedRealtime()
            if (lastChargeStartTime > 0L) {
                accumulatedChargingMillis += (now - lastChargeStartTime)
            }
            lastChargeStartTime = 0L
            isCharging = false
            currentTimeMillis = now
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    batteryPercentage = (level * 100) / scale
                }

                if (action == Intent.ACTION_POWER_CONNECTED || charging) {
                    startCharging()
                } else if (action == Intent.ACTION_POWER_DISCONNECTED || !charging) {
                    stopCharging()
                }
            }
        }
        context.registerReceiver(receiver, filter)

        // Initial check via sticky broadcast
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isChargingNow: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            batteryPercentage = (level * 100) / scale
        }

        if (isChargingNow) {
            startCharging()
        } else {
            stopCharging()
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Live countdown loop while charging
    LaunchedEffect(isCharging, hasCompleted) {
        if (isCharging && !hasCompleted) {
            while (!hasCompleted) {
                val now = SystemClock.elapsedRealtime()
                currentTimeMillis = now
                val currentSegment = if (lastChargeStartTime > 0L) (now - lastChargeStartTime) else 0L
                val totalElapsed = accumulatedChargingMillis + currentSegment
                if (totalElapsed >= totalRequiredMillis) {
                    hasCompleted = true
                    currentOnComplete()
                    break
                }
                delay(250L)
            }
        }
    }

    // Dynamic calculations for progress and remaining time
    val currentSegmentMillis = if (isCharging && lastChargeStartTime > 0L) {
        (currentTimeMillis - lastChargeStartTime).coerceAtLeast(0L)
    } else {
        0L
    }
    val totalElapsedMillis = (accumulatedChargingMillis + currentSegmentMillis).coerceAtLeast(0L)
    val remainingMillis = (totalRequiredMillis - totalElapsedMillis).coerceAtLeast(0L)
    val remainingSeconds = kotlin.math.ceil(remainingMillis / 1000.0).toInt()
    val progress = (totalElapsedMillis.toFloat() / totalRequiredMillis.toFloat()).coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "charging_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCardDarker),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            // Header Tag
            Text(
                text = "PHONE CHARGE CHALLENGE",
                color = ModeStrictAccent,
                fontSize = 13.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Dynamic Title
            Text(
                text = when {
                    isCharging -> "Charging in Progress"
                    accumulatedChargingMillis > 0L -> "Charging Paused"
                    else -> "Plug In Your Charger"
                },
                color = TextPrimary,
                fontSize = 28.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dynamic Subtitle
            Text(
                text = when {
                    isCharging -> "Keep your phone connected to power to unlock your break."
                    accumulatedChargingMillis > 0L -> "Charger disconnected! Plug back in to continue."
                    else -> "Connect your phone to a power source to begin your $requiredMinutes-minute charge."
                },
                color = TextSecondary,
                fontSize = 15.sp,
                fontFamily = AppFontFamily,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Circular Countdown Progress
            Box(
                modifier = Modifier.size(190.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCharging) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Amber500.copy(alpha = pulseAlpha))
                    )
                }

                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = if (isCharging) Amber500 else Amber500.copy(alpha = 0.5f),
                    strokeWidth = 10.dp,
                    trackColor = SurfaceCardLight,
                    strokeCap = StrokeCap.Round
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = if (isCharging) Amber500 else DangerRed,
                        modifier = Modifier.size(36.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = formatRemainingTime(remainingSeconds),
                        color = TextPrimary,
                        fontSize = if (remainingSeconds >= 3600) 28.sp else 34.sp,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (isCharging) "REMAINING" else if (accumulatedChargingMillis > 0L) "PAUSED" else "$requiredMinutes MIN GOAL",
                        color = if (isCharging) TextSecondary else if (accumulatedChargingMillis > 0L) DangerRed else TextMuted,
                        fontSize = 11.sp,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Battery Status Pill Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isCharging) StatusGreen.copy(alpha = 0.15f)
                        else DangerRed.copy(alpha = 0.12f)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isCharging) StatusGreen else DangerRed)
                )
                Text(
                    text = buildString {
                        if (batteryPercentage != null) {
                            append("${batteryPercentage}% • ")
                        }
                        append(if (isCharging) "Charging Active" else "Disconnected")
                    },
                    color = if (isCharging) StatusGreen else DangerRed,
                    fontSize = 13.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Cancel / Bailout Button
            TextButton(onClick = onCancel) {
                Text(
                    text = "Cancel & Close App",
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun formatRemainingTime(totalSeconds: Int): String {
    val hrs = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
