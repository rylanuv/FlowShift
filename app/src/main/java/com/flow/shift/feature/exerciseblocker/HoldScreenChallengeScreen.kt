package com.flow.shift.feature.exerciseblocker

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.shift.theme.Amber500
import com.flow.shift.theme.AppFontFamily
import com.flow.shift.theme.DangerRed
import com.flow.shift.theme.ModeStrictAccent
import com.flow.shift.theme.StatusGreen
import com.flow.shift.theme.SurfaceCardDarker
import com.flow.shift.theme.TextMuted
import com.flow.shift.theme.TextPrimary
import com.flow.shift.theme.TextSecondary
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HoldScreenChallengeScreen(
    requiredSeconds: Int,
    targetApp: String = "",
    onComplete: () -> Unit,
    onCancel: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currentOnComplete by rememberUpdatedState(onComplete)

    val safeRequiredSeconds = requiredSeconds.coerceAtLeast(1)
    val totalRequiredMillis = safeRequiredSeconds * 1000L

    var holdProgress by remember { mutableFloatStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }
    var holdCompleted by remember { mutableStateOf(false) }
    var showReleaseWarning by remember { mutableStateOf(false) }
    var lastTickedSecond by remember { mutableIntStateOf(-1) }

    // Pulsing animation while holding
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val targetPadScale = if (isHolding) 1.05f else 1f
    val animatedPadScale by animateFloatAsState(
        targetValue = targetPadScale,
        animationSpec = tween(150),
        label = "pad_scale"
    )

    // Progress counter and haptics
    LaunchedEffect(isHolding, holdCompleted) {
        if (holdCompleted) return@LaunchedEffect

        if (isHolding) {
            showReleaseWarning = false
            val startTime = SystemClock.elapsedRealtime()

            try {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Exception) {}

            while (isHolding && !holdCompleted) {
                val elapsed = SystemClock.elapsedRealtime() - startTime
                val progress = (elapsed.toFloat() / totalRequiredMillis.toFloat()).coerceIn(0f, 1f)
                holdProgress = progress

                val currentSec = (elapsed / 1000L).toInt()
                if (currentSec != lastTickedSecond && currentSec < safeRequiredSeconds) {
                    lastTickedSecond = currentSec
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } catch (_: Exception) {}
                }

                if (progress >= 1f) {
                    holdCompleted = true
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } catch (_: Exception) {}
                    delay(350)
                    currentOnComplete()
                    break
                }
                delay(16)
            }
        } else {
            if (holdProgress > 0.05f && !holdCompleted) {
                showReleaseWarning = true
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } catch (_: Exception) {}
            }
            holdProgress = 0f
            lastTickedSecond = -1
        }
    }

    val remainingSeconds = (safeRequiredSeconds * (1f - holdProgress)).coerceAtLeast(0f)
    val accentColor = if (holdCompleted) StatusGreen else ModeStrictAccent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCardDarker),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = "HOLD TO UNLOCK CHALLENGE",
                    color = ModeStrictAccent,
                    fontSize = 13.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = when {
                        holdCompleted -> "Challenge Completed!"
                        isHolding -> "Keep Holding..."
                        showReleaseWarning -> "Hold Released!"
                        else -> "Hold to Dismiss"
                    },
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        holdCompleted -> "Unlocking your break..."
                        isHolding -> "Don't lift your finger until the circle is complete."
                        showReleaseWarning -> "You let go too early! Press and hold continuously for $safeRequiredSeconds seconds."
                        else -> "Touch and hold the button below continuously for $safeRequiredSeconds seconds to dismiss this screen."
                    },
                    color = if (showReleaseWarning && !isHolding) DangerRed else TextSecondary,
                    fontSize = 15.sp,
                    fontFamily = AppFontFamily,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Central Touch Target Area
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(animatedPadScale),
                contentAlignment = Alignment.Center
            ) {
                // Expanding pulse glow when holding
                if (isHolding && !holdCompleted) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = pulseAlpha))
                    )
                }

                // Progress Arc Canvas
                Canvas(modifier = Modifier.size(230.dp)) {
                    val strokeWidth = 10.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                    val arcSize = Size(diameter, diameter)

                    // Track background
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active progress sweep
                    if (holdProgress > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.7f),
                                    accentColor,
                                    Color.White
                                )
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f * holdProgress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Inner Interactive Pad
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(CircleShape)
                        .background(
                            if (isHolding) accentColor.copy(alpha = 0.18f)
                            else Color.White.copy(alpha = 0.06f)
                        )
                        .pointerInput(safeRequiredSeconds, holdCompleted) {
                            if (holdCompleted) return@pointerInput
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                isHolding = true
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (!event.changes.any { it.pressed }) {
                                            break
                                        }
                                    }
                                } finally {
                                    isHolding = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (holdCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = StatusGreen,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "DONE",
                                color = StatusGreen,
                                fontSize = 16.sp,
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (isHolding) {
                            Text(
                                text = String.format(Locale.US, "%.1fs", remainingSeconds),
                                color = TextPrimary,
                                fontSize = 38.sp,
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${(holdProgress * 100).toInt()}%",
                                color = accentColor,
                                fontSize = 14.sp,
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = "Hold Screen",
                                tint = if (showReleaseWarning) DangerRed else accentColor,
                                modifier = Modifier.size(50.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "HOLD",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${safeRequiredSeconds}s",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Bottom Cancel Action
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                TextButton(onClick = onCancel) {
                    Text(
                        text = "I'm in public / Cancel & Close App",
                        color = TextMuted,
                        fontSize = 14.sp,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
