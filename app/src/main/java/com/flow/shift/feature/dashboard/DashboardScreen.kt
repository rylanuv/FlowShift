package com.flow.shift.feature.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.shift.R
import com.flow.shift.core.database.UserGamificationEntity
import com.flow.shift.core.designsystem.rememberAppIconBitmap
import com.flow.shift.feature.modes.BlockingMode
import com.flow.shift.theme.*

// ── Glass border brush for card edges ──
private val GlassBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.30f),
        Color.White.copy(alpha = 0.10f)
    )
)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    showBackground: Boolean = false,
    onNavigateToApps: () -> Unit,
    onNavigateToModes: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (showBackground) {
                    val currentMode = (uiState as? DashboardUiState.Success)?.settings?.blockingMode
                        ?: BlockingMode.STRICT
                    val bgRes = currentMode.backgroundImageRes
                    val bgAlpha = currentMode.backgroundAlpha
                    Modifier
                        .paint(
                            painter = painterResource(id = bgRes),
                            contentScale = ContentScale.Crop,
                            alpha = bgAlpha
                        )
                        .background(SurfaceBlack.copy(alpha = 0.85f))
                } else {
                    Modifier.background(Color.Transparent)
                }
            )
    ) {
        when (val state = uiState) {
            is DashboardUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Amber500
                )
            }
            is DashboardUiState.Success -> {
                DashboardContent(
                    data = state.data,
                    settings = state.settings,
                    metrics = state.metrics,
                    onNavigateToApps = onNavigateToApps,
                    onNavigateToModes = onNavigateToModes,
                    onNavigateToStats = onNavigateToStats,
                    onNavigateToSubscription = onNavigateToSubscription,
                    onEnableReelCount = { viewModel.setShowReelCount(true) },
                    onStartFocusMode = { viewModel.startFocusMode(it) },
                    onStopFocusMode = { viewModel.stopFocusMode() }
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    data: UserGamificationEntity,
    settings: DashboardSettings,
    metrics: DashboardMetrics,
    onNavigateToApps: () -> Unit,
    onNavigateToModes: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToSubscription: () -> Unit = {},
    onEnableReelCount: () -> Unit = {},
    onStartFocusMode: (Int) -> Unit,
    onStopFocusMode: () -> Unit
) {
    val context = LocalContext.current
    val blockingMode = settings.blockingMode
    val challengeType = settings.challengeType
    val challengeAmount = settings.challengeAmount
    val breakDurationMinutes = settings.breakDurationMinutes

    // Mode-specific accent color
    val modeAccent = when (blockingMode) {
        BlockingMode.STRICT -> ModeStrictAccent
        BlockingMode.HARDCORE -> ModeHardcoreAccent
    }

    // Real-time ticker for countdowns
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            now = System.currentTimeMillis()
        }
    }

    val isFocusModeActive = metrics.focusModeUntilMillis > now && blockingMode != BlockingMode.HARDCORE
    val isBreakActive = metrics.unlockExpiresAtMillis != null && metrics.unlockExpiresAtMillis > now
    val isTargetReached = settings.dailyReelLimit > 0 && metrics.totalReelsToday >= settings.dailyReelLimit

    val reelProgress = if (settings.dailyReelLimit > 0) {
        (metrics.totalReelsToday.toFloat() / settings.dailyReelLimit.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isBreakActive || isFocusModeActive) 1f else reelProgress,
        animationSpec = tween(durationMillis = 1200, delayMillis = 300),
        label = "progress"
    )

    val heroCenterText = when {
        isFocusModeActive -> formatRemainingDurationShort(metrics.focusModeUntilMillis - now)
        isBreakActive -> formatRemainingDurationShort(metrics.unlockExpiresAtMillis!! - now)
        else -> "${metrics.totalReelsToday}/${settings.dailyReelLimit}"
    }
    val heroFontSize = when {
        heroCenterText.length > 7 -> 32.sp
        heroCenterText.length > 5 -> 38.sp
        else -> 44.sp
    }
    val topLabel = when {
        isFocusModeActive -> "FOCUS MODE"
        isBreakActive -> "BREAK TIME"
        else -> "REELS SCROLLED"
    }

    val activeColor by animateColorAsState(
        targetValue = if (isFocusModeActive) Color(0xFFD32F2F) else modeAccent,
        animationSpec = tween(500),
        label = "activeColor"
    )

    // Motivational status message
    val statusMessage = when {
        isFocusModeActive -> "Stay focused \uD83D\uDD12"
        isBreakActive -> "Enjoy your break ☕"
        isTargetReached -> "Limit reached! \uD83D\uDEAB"
        reelProgress >= 0.75f -> "Almost there, stay strong \uD83D\uDCAA"
        reelProgress >= 0.5f -> "Halfway through your limit"
        reelProgress > 0f -> "Keep going strong \uD83D\uDCAA"
        else -> "No reels scrolled yet ✨"
    }

    var showAddMoreBottomSheet by remember { mutableStateOf(false) }
    var showStopFocusModeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // ═══════════════════════════════════════════
        // ── 1. GREETING HEADER + MODE BADGE ──
        // ═══════════════════════════════════════════
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = TextPrimary)) {
                        append("Reels")
                    }
                    withStyle(style = SpanStyle(color = modeAccent)) {
                        append("Rot")
                    }
                },
                fontFamily = AppFontFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }

        // ═══════════════════════════════════════════
                // ??? 2. HERO PROGRESS RING ???
        // ---------------------------------------------------------------------------
        val blobPath = remember { Path() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Wavy Watery Background
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .drawBehind {
                        blobPath.reset()
                        val w = size.width
                        val h = size.height
                        val radius = size.minDimension / 2f * 0.90f
                        val center = Offset(w / 2f, h / 2f)
                        
                        for (i in 0..360 step 2) {
                            val rad = Math.toRadians(i.toDouble()).toFloat()
                            // Organic irregularity using sum of sines to simulate a watery blob
                            val r = radius + 
                                    radius * 0.06f * kotlin.math.sin(rad * 4f) + 
                                    radius * 0.04f * kotlin.math.cos(rad * 3f) +
                                    radius * 0.03f * kotlin.math.sin(rad * 7f)
                                    
                            val x = center.x + r * kotlin.math.cos(rad)
                            val y = center.y + r * kotlin.math.sin(rad)
                            
                            if (i == 0) blobPath.moveTo(x, y)
                            else blobPath.lineTo(x, y)
                        }
                        blobPath.close()

                        // Semi-transparent glassy dark fill
                        drawPath(
                            path = blobPath,
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color(0xFF0F151A).copy(alpha = 0.6f)
                                ),
                                center = Offset(w * 0.3f, h * 0.3f),
                                radius = w * 0.8f
                            )
                        )
                        
                        // Thin glassy highlight border
                        drawPath(
                            path = blobPath,
                            color = Color.White.copy(alpha = 0.35f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
            )

            // Original Progress Ring
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .drawBehind {
                        val strokeWidth = 16.dp.toPx()
                        val glowStroke = 30.dp.toPx()

                        // Outer glow layer (semi-transparent, wider stroke)
                        if (animatedProgress > 0f) {
                            drawArc(
                                color = activeColor.copy(alpha = 0.12f),
                                startAngle = 140f,
                                sweepAngle = animatedProgress * 260f,
                                useCenter = false,
                                style = Stroke(width = glowStroke, cap = StrokeCap.Round)
                            )
                        }

                        // Background track
                        drawArc(
                            color = Color.White.copy(alpha = 0.35f),
                            startAngle = 140f,
                            sweepAngle = 260f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        
                        // Progress arc
                        if (animatedProgress > 0f) {
                            withTransform({
                                rotate(140f, Offset(size.width / 2f, size.height / 2f))
                            }) {
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        0.0f to activeColor.copy(alpha = 0.05f),
                                        (animatedProgress * (260f/360f)) to activeColor,
                                        center = Offset(size.width / 2f, size.height / 2f)
                                    ),
                                    startAngle = 0f,
                                    sweepAngle = animatedProgress * 260f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }


                        // Bright tip dot at the end of the arc
                        if (animatedProgress > 0.02f) {
                            val angle = Math.toRadians((140.0 + animatedProgress * 260.0))
                            val radius = (size.minDimension / 2f)
                            val dotX = center.x + radius * kotlin.math.cos(angle).toFloat()
                            val dotY = center.y + radius * kotlin.math.sin(angle).toFloat()
                            drawCircle(
                                color = Color.White.copy(alpha = 0.9f),
                                radius = strokeWidth * 0.35f,
                                center = Offset(dotX, dotY)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Top Icon Glass Box
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartDisplay,
                            contentDescription = null,
                            tint = activeColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // Status label
                    Text(
                        text = topLabel,
                        color = activeColor,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = activeColor,
                                blurRadius = 24f
                            )
                        ),
                        fontFamily = AppFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Main value
                    Text(
                        text = heroCenterText,
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = heroFontSize,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1.5).sp,
                        maxLines = 1
                    )
                    // "reels" sub-label for normal state
                    if (!isFocusModeActive && !isBreakActive) {
                        Text(
                            text = "reels",
                            color = TextPrimary.copy(alpha = 0.85f),
                            fontFamily = AppFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                                        // Motivational status pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = statusMessage,
                            color = Color.White.copy(alpha = 0.9f),
                            fontFamily = AppFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // ── Reels Scrolled Pill (optional) ──
        if (settings.showReelCount) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                        .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                        .clickable { onNavigateToStats() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartDisplay,
                            contentDescription = null,
                            tint = if (metrics.totalReelsToday > 0) Amber500 else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reels Scrolled: ",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = metrics.totalReelsToday.toString(),
                            color = if (metrics.totalReelsToday > 0) Amber500 else TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }


        // ═══════════════════════════════════════════
        // ── 4. BREAK BUTTON ──
        // ═══════════════════════════════════════════
        when (blockingMode) {
            BlockingMode.STRICT -> {
                BreakButton(
                    text = "Take a Break",
                    subtext = if (!isTargetReached) {
                        "You still have reels left, enjoy them"
                    } else {
                        when (challengeType) {
                            "CHARGE_PHONE" -> "Charge phone ($challengeAmount min)"
                            "MATH", "ADVANCED_MATH" -> "Solve math first"
                            "SQUATS" -> "Complete squats first"
                            else -> "Complete pushups first"
                        }
                    },
                    accentColor = modeAccent,
                    enabled = isTargetReached,
                    reelsProgress = reelProgress,
                    onClick = {
                        val intent = android.content.Intent(
                            context,
                            com.flow.shift.feature.exerciseblocker.BlockerActivity::class.java
                        ).apply {
                            putExtra("TARGET_PACKAGE", "ALL_APPS")
                            putExtra("REQUIRED_REPS", challengeAmount)
                            putExtra("BREAK_DURATION_MINUTES", breakDurationMinutes)
                            putExtra("CHALLENGE_TYPE", challengeType)
                        }
                        context.startActivity(intent)
                    }
                )
            }
            BlockingMode.HARDCORE -> {
                BreakButton(
                    text = "Breaks Disabled",
                    subtext = "Hardcore mode active",
                    accentColor = TextMuted,
                    enabled = false,
                    onClick = { }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ═══════════════════════════════════════════
        // ── 5. MODE CONFIGURATION CARD ──
        // ═══════════════════════════════════════════
        when (blockingMode) {
            BlockingMode.STRICT -> {
                val (title, icon) = when (challengeType) {
                    "MATH" -> "$challengeAmount Maths" to Icons.Default.Psychology
                    "ADVANCED_MATH" -> "$challengeAmount Adv Maths" to Icons.Default.Psychology
                    "SQUATS" -> "$challengeAmount Squats" to Icons.Default.FitnessCenter
                    "CHARGE_PHONE" -> "$challengeAmount min Charge" to Icons.Default.BatteryChargingFull
                    else -> "$challengeAmount Push-ups" to Icons.Default.FitnessCenter
                }
                NextChallengeCard(
                    icon = icon,
                    iconTint = modeAccent,
                    label = "CURRENT CHALLENGE",
                    title = title,
                    subtitle = "Complete to unlock a ${breakDurationMinutes}min break",
                    accentColor = modeAccent,
                    onClick = onNavigateToModes
                )
            }
            BlockingMode.HARDCORE -> {
                NextChallengeCard(
                    icon = Icons.Default.Lock,
                    iconTint = modeAccent,
                    label = "CONFIGURE HARDCORE MODE",
                    title = "No Breaks",
                    subtitle = "Stay focused. No breaks allowed.",
                    accentColor = modeAccent,
                    onClick = onNavigateToModes
                )
            }
        }

        // ═══════════════════════════════════════════
        // ── 6. FOCUS MODE SECTION ──
        // ═══════════════════════════════════════════
        if (blockingMode != BlockingMode.HARDCORE) {
            Spacer(modifier = Modifier.height(16.dp))
            if (isFocusModeActive) {
                BreakButton(
                    text = if (settings.preventDisablingFocusMode) "Focus Mode Active" else "Stop Focus Mode",
                    subtext = if (settings.preventDisablingFocusMode) null else "Unblock apps instantly",
                    accentColor = StatusRed,
                    enabled = !settings.preventDisablingFocusMode,
                    disabledTint = StatusRed,
                    onClick = { showStopFocusModeDialog = true }
                )
            } else {
                FocusModeSliderButton(
                    accentColor = modeAccent,
                    enabled = !isTargetReached,
                    onStartFocusMode = { minutes -> onStartFocusMode(minutes) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ═══════════════════════════════════════════
        // ── 7. TODAY'S ACTIVITY CARD ──
        // ═══════════════════════════════════════════
        TodaysActivityCard(
            totalUsageMillis = metrics.totalUsageTodayMillis,
            interventionsToday = metrics.interventionsToday,
            sessionsToday = metrics.sessionsToday,
            accentColor = modeAccent,
            onClick = onNavigateToStats
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ═══════════════════════════════════════════
        // ── 8. PROTECTED APPS SECTION ──
        // ═══════════════════════════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceCard.copy(alpha = 0.30f))
                .border(1.dp, GlassBorderBrush, RoundedCornerShape(18.dp))
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Protected Apps",
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Add More",
                    color = modeAccent,
                    fontFamily = AppFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showAddMoreBottomSheet = true }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (metrics.protectedApps.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No apps protected yet",
                            color = TextMuted,
                            fontFamily = AppFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap \"Add More\" to get started",
                            color = TextMuted.copy(alpha = 0.6f),
                            fontFamily = AppFontFamily,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(metrics.protectedApps, key = { it.packageName }) { app ->
                        ProtectedAppIcon(name = app.appName, packageName = app.packageName)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    // ── Bottom Sheets & Dialogs ──
    if (showAddMoreBottomSheet) {
        com.flow.shift.feature.appselection.AppSelectionBottomSheet(
            onDismissRequest = { showAddMoreBottomSheet = false }
        )
    }

    if (showStopFocusModeDialog) {
        AlertDialog(
            onDismissRequest = { showStopFocusModeDialog = false },
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = "Stop Focus Mode?",
                    color = TextPrimary,
                    fontFamily = AppFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to stop Focus Mode?",
                    color = TextSecondary,
                    fontFamily = AppFontFamily,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onStopFocusMode()
                        showStopFocusModeDialog = false
                    }
                ) {
                    Text("Stop", color = StatusRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopFocusModeDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}



// ═══════════════════════════════════════════════════════
// ── COMPOSABLE: Today's Activity Card ──
// ═══════════════════════════════════════════════════════

@Composable
private fun TodaysActivityCard(
    totalUsageMillis: Long,
    interventionsToday: Int,
    sessionsToday: Int,
    accentColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard.copy(alpha = 0.30f))
            .border(1.dp, GlassBorderBrush, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Activity",
                color = TextPrimary,
                fontFamily = AppFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Details",
                    color = TextMuted,
                    fontFamily = AppFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MiniStat(
                label = "Screen Time",
                value = formatDurationShort(totalUsageMillis),
                accentColor = accentColor
            )
            // Vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            MiniStat(
                label = "Blocks",
                value = interventionsToday.toString(),
                accentColor = StatusRed
            )
            // Vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            MiniStat(
                label = "Breaks",
                value = sessionsToday.toString(),
                accentColor = StatusGreen
            )
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    accentColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = accentColor,
            fontFamily = AppFontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = TextMuted,
            fontFamily = AppFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════
// ── COMPOSABLE: Next Challenge Card ──
// ═══════════════════════════════════════════════════════

@Composable
private fun NextChallengeCard(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard.copy(alpha = 0.30f))
            .border(1.dp, GlassBorderBrush, RoundedCornerShape(18.dp))
            .drawBehind {
                // Left accent stripe
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(0f, size.height * 0.15f),
                    size = Size(4.dp.toPx(), size.height * 0.7f),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = accentColor.copy(alpha = 0.8f),
                fontFamily = AppFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontFamily = AppFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontFamily = AppFontFamily,
                fontSize = 13.sp
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted
        )
    }
}

// ═══════════════════════════════════════════════════════
// ── COMPOSABLE: Break Button ──
// ═══════════════════════════════════════════════════════

@Composable
private fun BreakButton(
    text: String,
    subtext: String?,
    accentColor: Color,
    enabled: Boolean,
    disabledTint: Color? = null,
    reelsProgress: Float = 0f,
    onClick: () -> Unit
) {
    val darkTextColor = Color(0xFF111318)
    val shape = RoundedCornerShape(22.dp)

    val modifier = if (enabled) {
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = accentColor.copy(alpha = 0.4f),
                spotColor = accentColor.copy(alpha = 0.8f)
            )
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        accentColor,
                        accentColor.copy(alpha = 0.85f)
                    )
                )
            )
            .clickable { onClick() }
    } else {
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(disabledTint?.copy(alpha = 0.12f) ?: Color.White.copy(alpha = 0.04f))
            .border(
                1.dp,
                disabledTint?.let { SolidColor(it.copy(alpha = 0.25f)) } ?: GlassBorderBrush,
                shape
            )
            .then(
                // Progress bar along bottom for disabled state
                if (!enabled && reelsProgress > 0f && disabledTint == null) {
                    Modifier.drawBehind {
                        val barHeight = 3.dp.toPx()
                        val barWidth = size.width * reelsProgress
                        drawRoundRect(
                            color = accentColor.copy(alpha = 0.3f),
                            topLeft = Offset(0f, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barHeight / 2)
                        )
                    }
                } else Modifier
            )
    }

    Row(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) Color.Black.copy(alpha = 0.15f)
                        else (disabledTint?.copy(alpha = 0.12f) ?: Color.White.copy(alpha = 0.05f))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (enabled) Icons.Default.LocalCafe
                    else (if (disabledTint != null) Icons.Default.Block else Icons.Default.LocalCafe),
                    contentDescription = null,
                    tint = if (enabled) darkTextColor
                    else (disabledTint?.copy(alpha = 0.7f) ?: TextMuted),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = text,
                    color = if (enabled) darkTextColor
                    else (disabledTint?.copy(alpha = 0.9f) ?: TextMuted),
                    fontFamily = AppFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtext != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtext,
                        color = if (enabled) darkTextColor.copy(alpha = 0.75f)
                        else (disabledTint?.copy(alpha = 0.5f) ?: TextMuted.copy(alpha = 0.6f)),
                        fontFamily = AppFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (enabled) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(darkTextColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Start",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// ── COMPOSABLE: Protected App Icon (Circular) ──
// ═══════════════════════════════════════════════════════

@Composable
private fun ProtectedAppIcon(
    name: String,
    packageName: String
) {
    val iconBitmap = rememberAppIconBitmap(packageName)
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (iconBitmap == null) colorForPackage(packageName) else Color.Transparent)
            .border(1.5.dp, Color.White.copy(alpha = 0.10f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = name,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontFamily = AppFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// ── Custom App Icons Drawn with Canvas ──
// ═══════════════════════════════════════════════════════

@Composable
fun ReelsIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.18f, h * 0.22f),
            size = Size(w * 0.64f, h * 0.56f),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )

        drawLine(
            color = Color.White,
            start = Offset(w * 0.18f, h * 0.38f),
            end = Offset(w * 0.82f, h * 0.38f),
            strokeWidth = strokeWidth
        )

        drawLine(color = Color.White, start = Offset(w * 0.28f, h * 0.22f), end = Offset(w * 0.38f, h * 0.38f), strokeWidth = strokeWidth)
        drawLine(color = Color.White, start = Offset(w * 0.48f, h * 0.22f), end = Offset(w * 0.58f, h * 0.38f), strokeWidth = strokeWidth)
        drawLine(color = Color.White, start = Offset(w * 0.68f, h * 0.22f), end = Offset(w * 0.78f, h * 0.38f), strokeWidth = strokeWidth)

        val playPath = Path().apply {
            moveTo(w * 0.44f, h * 0.45f)
            lineTo(w * 0.62f, h * 0.55f)
            lineTo(w * 0.44f, h * 0.65f)
            close()
        }
        drawPath(path = playPath, color = Color.White)
    }
}

@Composable
fun ShortsIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val shapePath = Path().apply {
            moveTo(w * 0.35f, h * 0.25f)
            cubicTo(w * 0.5f, h * 0.15f, w * 0.75f, h * 0.25f, w * 0.7f, h * 0.45f)
            lineTo(w * 0.65f, h * 0.55f)
            cubicTo(w * 0.5f, h * 0.85f, w * 0.25f, h * 0.75f, w * 0.3f, h * 0.55f)
            close()
        }
        drawPath(path = shapePath, color = Color.White)

        val playPath = Path().apply {
            moveTo(w * 0.46f, h * 0.42f)
            lineTo(w * 0.61f, h * 0.50f)
            lineTo(w * 0.46f, h * 0.58f)
            close()
        }
        drawPath(path = playPath, color = Color.Red)
    }
}

@Composable
fun RedditIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color = Color.White, radius = w * 0.25f, center = Offset(w * 0.5f, h * 0.55f))
        drawLine(color = Color.White, start = Offset(w * 0.5f, h * 0.3f), end = Offset(w * 0.6f, h * 0.15f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color = Color.White, radius = w * 0.05f, center = Offset(w * 0.6f, h * 0.15f))
        drawCircle(color = Color.White, radius = w * 0.08f, center = Offset(w * 0.25f, h * 0.45f))
        drawCircle(color = Color.White, radius = w * 0.08f, center = Offset(w * 0.75f, h * 0.45f))
        drawCircle(color = Color(0xFFFF4500), radius = w * 0.05f, center = Offset(w * 0.4f, h * 0.5f))
        drawCircle(color = Color(0xFFFF4500), radius = w * 0.05f, center = Offset(w * 0.6f, h * 0.5f))
    }
}

@Composable
fun XIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawLine(color = Color.White, start = Offset(w * 0.3f, h * 0.2f), end = Offset(w * 0.7f, h * 0.8f), strokeWidth = 4.dp.toPx())
        drawLine(color = Color.White, start = Offset(w * 0.7f, h * 0.2f), end = Offset(w * 0.3f, h * 0.8f), strokeWidth = 4.dp.toPx())
    }
}

@Composable
fun FacebookIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val fPath = Path().apply {
            moveTo(w * 0.6f, h * 0.8f)
            lineTo(w * 0.6f, h * 0.5f)
            lineTo(w * 0.7f, h * 0.5f)
            lineTo(w * 0.7f, h * 0.4f)
            lineTo(w * 0.6f, h * 0.4f)
            lineTo(w * 0.6f, h * 0.3f)
            cubicTo(w * 0.6f, h * 0.25f, w * 0.65f, h * 0.2f, w * 0.7f, h * 0.2f)
            lineTo(w * 0.7f, h * 0.1f)
            cubicTo(w * 0.5f, h * 0.1f, w * 0.4f, h * 0.2f, w * 0.4f, h * 0.35f)
            lineTo(w * 0.4f, h * 0.4f)
            lineTo(w * 0.3f, h * 0.4f)
            lineTo(w * 0.3f, h * 0.5f)
            lineTo(w * 0.4f, h * 0.5f)
            lineTo(w * 0.4f, h * 0.8f)
            close()
        }
        drawPath(path = fPath, color = Color.White)
    }
}

@Composable
fun InstagramGradientIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.15f, h * 0.15f),
            size = Size(w * 0.7f, h * 0.7f),
            cornerRadius = CornerRadius(w * 0.22f, h * 0.22f),
            style = Stroke(width = strokeWidth)
        )

        drawCircle(
            color = Color.White,
            radius = w * 0.18f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = strokeWidth)
        )

        drawCircle(
            color = Color.White,
            radius = w * 0.05f,
            center = Offset(w * 0.72f, h * 0.28f)
        )
    }
}

@Composable
private fun AppInitialIcon(
    name: String,
    packageName: String,
    size: Int,
    fontSize: Int
) {
    val iconBitmap = rememberAppIconBitmap(packageName)
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (iconBitmap == null) colorForPackage(packageName) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = name,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
            )
        } else {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun colorForPackage(packageName: String): Color {
    val palette = listOf(
        Color(0xFFE1306C),
        Color(0xFFFF0000),
        Color(0xFFFF4500),
        Color(0xFF1877F2),
        Color(0xFF0EA5E9),
        Color(0xFF22C55E),
        Color(0xFFA855F7)
    )
    val index = kotlin.math.abs(packageName.hashCode()) % palette.size
    return palette[index]
}
