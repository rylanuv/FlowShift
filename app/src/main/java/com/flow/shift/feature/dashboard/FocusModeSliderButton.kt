package com.flow.shift.feature.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun FocusModeSliderButton(
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF6366F1), // Indigo
    enabled: Boolean = true,
    onStartFocusMode: (Int) -> Unit
) {
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val thumbSizeDp = 64.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val paddingPx = with(density) { 8.dp.toPx() }

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var pendingMinutes by remember { mutableStateOf<Int?>(null) }

    val maxDrag = if (width > 0) width - thumbSizePx - (paddingPx * 2) else 0f
    
    // Convert dragOffset to raw minutes (5 to 120), then snap to 5 min intervals
    val progress = if (maxDrag > 0) (dragOffset / maxDrag).coerceIn(0f, 1f) else 0f
    val rawMinutes = 5 + (progress * 115).roundToInt()
    val selectedMinutes = (rawMinutes / 5.0).roundToInt() * 5

    // Track 15 min intervals to trigger haptic ticks
    var lastHapticInterval by remember { mutableIntStateOf(selectedMinutes / 15) }
    LaunchedEffect(selectedMinutes) {
        val currentInterval = selectedMinutes / 15
        if (currentInterval != lastHapticInterval && isDragging) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastHapticInterval = currentInterval
        }
    }

    val animatedOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = if (isDragging) tween(0) else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "thumbOffset"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumbScale"
    )

    val shape = RoundedCornerShape(32.dp)
    val actualAccentColor = if (enabled) accentColor else Color.Gray
    val containerAlpha = if (enabled) 1f else 0.5f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(
                elevation = if (isDragging) 16.dp else if (enabled) 8.dp else 0.dp,
                shape = shape,
                ambientColor = actualAccentColor.copy(alpha = 0.5f),
                spotColor = actualAccentColor
            )
            .clip(shape)
            .background(Color(0xFF1E1E2E).copy(alpha = containerAlpha)) // Dark track background
            .border(1.dp, actualAccentColor.copy(alpha = 0.3f * containerAlpha), shape)
            .onSizeChanged { width = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedContent(
            targetState = pendingMinutes,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (fadeIn(animationSpec = tween(300))).togetherWith(fadeOut(animationSpec = tween(300)))
                    .using(SizeTransform(clip = false))
            },
            label = "confirmationTransition"
        ) { confirmationMinutes ->
            if (confirmationMinutes != null) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Start $confirmationMinutes min?",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable {
                                    pendingMinutes = null
                                    dragOffset = 0f
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(actualAccentColor)
                                .clickable {
                                    pendingMinutes?.let { onStartFocusMode(it) }
                                    pendingMinutes = null
                                    dragOffset = 0f
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Confirm", tint = Color.White)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Track Background Fill based on progress
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(with(density) { (animatedOffset + thumbSizePx + paddingPx * 2).toDp() })
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        actualAccentColor.copy(alpha = 0.5f * containerAlpha),
                                        actualAccentColor.copy(alpha = 0.15f * containerAlpha)
                                    )
                                )
                            )
                    )

                    // Text inside track
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = thumbSizeDp + 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AnimatedContent(
                            targetState = isDragging,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(300))).togetherWith(fadeOut(animationSpec = tween(300)))
                                    .using(SizeTransform(clip = false))
                            },
                            label = "textTransition"
                        ) { dragging ->
                            if (dragging) {
                                Text(
                                    text = "Release to start: $selectedMinutes min",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = if (enabled) "Slide to Focus" else "Focus Mode Unavailable",
                                    color = Color.White.copy(alpha = if (enabled) 0.7f else 0.4f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Draggable Thumb
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                            .size(thumbSizeDp)
                            .scale(thumbScale)
                            .shadow(elevation = if (isDragging) 8.dp else 4.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(actualAccentColor.copy(alpha = containerAlpha))
                            .then(
                                if (enabled) {
                                    Modifier.pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { 
                                                isDragging = true 
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragEnd = {
                                                isDragging = false
                                                if (dragOffset > 0f) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val currentProgress = if (maxDrag > 0) (dragOffset / maxDrag).coerceIn(0f, 1f) else 0f
                                                    val currentRaw = 5 + (currentProgress * 115).roundToInt()
                                                    val finalSelectedMinutes = (currentRaw / 5.0).roundToInt() * 5
                                                    pendingMinutes = finalSelectedMinutes
                                                } else {
                                                    dragOffset = 0f
                                                }
                                            },
                                            onDragCancel = {
                                                isDragging = false
                                                dragOffset = 0f
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset = (dragOffset + dragAmount).coerceIn(0f, maxDrag)
                                            }
                                        )
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
