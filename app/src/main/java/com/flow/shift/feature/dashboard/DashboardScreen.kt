package com.flow.shift.feature.dashboard

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import com.flow.shift.core.designsystem.ReelCountExplainerDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.shift.feature.modes.BlockingMode
import com.flow.shift.theme.*
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.flow.shift.core.designsystem.rememberAppIconBitmap
import com.flow.shift.R

private val GlassBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.25f),
        Color.White.copy(alpha = 0.05f)
    )
)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    showBackground: Boolean = false,
    onNavigateToApps: () -> Unit,
    onNavigateToModes: () -> Unit = {},
    onNavigateToEasyModeSettings: () -> Unit = {},
    onNavigateToDisciplineModeSettings: () -> Unit = {},
    onNavigateToHardcoreModeSettings: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (showBackground) {
                    val currentMode = (uiState as? DashboardUiState.Success)?.settings?.blockingMode ?: BlockingMode.EASY
                    val bgRes = currentMode.backgroundImageRes
                    val bgAlpha = currentMode.backgroundAlpha
                    Modifier
                        .paint(
                            painter = painterResource(id = bgRes),
                            contentScale = ContentScale.Crop,
                            alpha = bgAlpha
                        )
                        .background(SurfaceBlack.copy(alpha = 0.25f))
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
                    onNavigateToEasyModeSettings = onNavigateToEasyModeSettings,
                    onNavigateToDisciplineModeSettings = onNavigateToDisciplineModeSettings,
                    onNavigateToHardcoreModeSettings = onNavigateToHardcoreModeSettings,
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
    data: com.flow.shift.core.database.UserGamificationEntity,
    settings: DashboardSettings,
    metrics: DashboardMetrics,
    onNavigateToApps: () -> Unit,
    onNavigateToModes: () -> Unit,
    onNavigateToEasyModeSettings: () -> Unit,
    onNavigateToDisciplineModeSettings: () -> Unit,
    onNavigateToHardcoreModeSettings: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToSubscription: () -> Unit = {},
    onEnableReelCount: () -> Unit = {},
    onStartFocusMode: (Int) -> Unit,
    onStopFocusMode: () -> Unit
) {
    val context = LocalContext.current
    var showReelCountExplainerDialog by remember { mutableStateOf(false) }
    val blockingMode = settings.blockingMode
    val challengeType = settings.challengeType
    val challengeAmount = settings.challengeAmount
    val breakDurationMinutes = settings.breakDurationMinutes

    // Mode-specific accent color
    val modeAccent = when (blockingMode) {
        BlockingMode.EASY -> ModeEasyAccent
        BlockingMode.STRICT -> ModeStrictAccent
        BlockingMode.HARDCORE -> ModeHardcoreAccent
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            now = System.currentTimeMillis()
        }
    }
    val isFocusModeActive = metrics.focusModeUntilMillis > now && blockingMode != BlockingMode.HARDCORE
    val isBreakActive = metrics.unlockExpiresAtMillis != null && metrics.unlockExpiresAtMillis > now

    val displayMillis = when {
        isFocusModeActive -> metrics.focusModeUntilMillis - now
        isBreakActive -> metrics.unlockExpiresAtMillis!! - now
        else -> metrics.remainingMillis
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isBreakActive || isFocusModeActive) 1f else metrics.remainingProgress,
        animationSpec = tween(durationMillis = 1200, delayMillis = 300),
        label = "progress"
    )
    val remainingLabel = formatRemainingDurationShort(displayMillis)
    val topLabel = when {
        isFocusModeActive -> "FOCUS MODE"
        isBreakActive -> "BREAK TIME"
        else -> "TIME REMAINING"
    }
    val nextLabel = when {
        isFocusModeActive -> "Stay focused"
        isBreakActive -> "Enjoy your break"
        metrics.protectedApps.isEmpty() -> "Add protected apps"
        else -> "Earn a break"
    }

    val activeColor by animateColorAsState(
        targetValue = if (isFocusModeActive) Color(0xFFD32F2F) /* Deep Red */ else modeAccent,
        animationSpec = tween(500),
        label = "activeColor"
    )
    val glowAlpha = if (isFocusModeActive) 0.4f else 0f

    var showAddMoreBottomSheet by remember { mutableStateOf(false) }
    var showStopFocusModeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // ── Header ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = TextPrimary)) {
                            append("Flow")
                        }
                        withStyle(style = SpanStyle(color = modeAccent)) {
                            append("Shift")
                        }
                    },
                    fontFamily = AppFontFamily,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Stay focused, stay free",
                    color = TextMuted,
                    fontFamily = AppFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            IconButton(
                onClick = onNavigateToStats,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceCard.copy(alpha = 0.65f))
                    .border(1.dp, GlassBorderBrush, RoundedCornerShape(14.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "Statistics",
                    tint = modeAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Hero Card: Screen Time Ring ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isFocusModeActive) 16.dp else 0.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = activeColor.copy(alpha = glowAlpha),
                    spotColor = activeColor.copy(alpha = glowAlpha)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(SurfaceCard.copy(alpha = 0.55f))
                .border(1.dp, if (isFocusModeActive) androidx.compose.ui.graphics.SolidColor(activeColor.copy(alpha = glowAlpha)) else GlassBorderBrush, RoundedCornerShape(28.dp))
                .padding(top = 28.dp, bottom = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress Ring
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .drawBehind {
                            val strokeWidth = 14.dp.toPx()
                            // Background track
                            drawArc(
                                color = Color.White.copy(alpha = 0.12f),
                                startAngle = 140f,
                                sweepAngle = 260f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            // Animated foreground — tinted per mode
                            val sweepAngle = animatedProgress * 260f
                            drawArc(
                                color = activeColor,
                                startAngle = 140f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = topLabel,
                            color = activeColor,
                            fontFamily = AppFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = remainingLabel,
                            color = TextPrimary,
                            fontFamily = AppFontFamily,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = nextLabel,
                            color = TextSecondary,
                            fontFamily = AppFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        // Mode Badge Pill
                        val (pillBgColor, pillBorderColor, pillText) = when (blockingMode) {
                            BlockingMode.EASY -> Triple(
                                Color(0xFF064E3B),
                                StatusGreen.copy(alpha = 0.3f),
                                "Easy Mode"
                            )
                            BlockingMode.STRICT -> Triple(
                                ModeStrictAccent.copy(alpha = 0.12f),
                                ModeStrictAccent.copy(alpha = 0.3f),
                                "Discipline Mode"
                            )
                            BlockingMode.HARDCORE -> Triple(
                                ModeHardcoreAccent.copy(alpha = 0.12f),
                                ModeHardcoreAccent.copy(alpha = 0.3f),
                                "Hardcore Mode"
                            )
                        }
                        val pillIconTint = when (blockingMode) {
                            BlockingMode.EASY -> StatusGreen
                            BlockingMode.STRICT -> ModeStrictAccent
                            BlockingMode.HARDCORE -> ModeHardcoreAccent
                        }
                        val pillIcon = when (blockingMode) {
                            BlockingMode.EASY -> Icons.Default.Timer
                            BlockingMode.STRICT -> Icons.Default.Shield
                            BlockingMode.HARDCORE -> Icons.Default.Lock
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(pillBgColor)
                                .border(1.dp, pillBorderColor, RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = pillIcon,
                                    contentDescription = null,
                                    tint = pillIconTint,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = pillText,
                                    color = pillIconTint,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Reels Scrolled / Prompt Section ──
                if (settings.showReelCount) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
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
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Amber500.copy(alpha = 0.10f))
                            .border(1.dp, Amber500.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .clickable { showReelCountExplainerDialog = true }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = Amber500,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reel count is off",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                fontFamily = AppFontFamily
                            )
                            Text(
                                text = " • Turn on",
                                color = Amber500,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFontFamily
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        when (blockingMode) {
            BlockingMode.EASY -> {
                val easyWait = settings.easyModeWaitSeconds
                BreakButton(
                    text = "Take a Break",
                    subtext = if (metrics.remainingMillis > 0L) "You still have time, enjoy it" else "${easyWait}s wait required",
                    accentColor = modeAccent,
                    enabled = metrics.remainingMillis <= 0L,
                    onClick = {
                        val intent = android.content.Intent(context, com.flow.shift.feature.exerciseblocker.WaitActivity::class.java).apply {
                            putExtra("TARGET_PACKAGE", "ALL_APPS")
                            putExtra("WAIT_SECONDS", easyWait)
                            putExtra("BREAK_DURATION_MINUTES", breakDurationMinutes)
                        }
                        context.startActivity(intent)
                    }
                )
            }
            BlockingMode.STRICT -> {
                BreakButton(
                    text = "Take a Break",
                    subtext = if (metrics.remainingMillis > 0L) {
                        "You still have time, enjoy it"
                    } else {
                        when (challengeType) {
                            "CHARGE_PHONE" -> "Charge phone ($challengeAmount min)"
                            "MATH", "ADVANCED_MATH" -> "Solve math first"
                            "SQUATS" -> "Complete squats first"
                            else -> "Complete pushups first"
                        }
                    },
                    accentColor = modeAccent,
                    enabled = metrics.remainingMillis <= 0L,
                    onClick = {
                        val intent = android.content.Intent(context, com.flow.shift.feature.exerciseblocker.BlockerActivity::class.java).apply {
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
                    enabled = metrics.remainingMillis > 0L,
                    onStartFocusMode = { minutes -> onStartFocusMode(minutes) }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Next Challenge Card — Mode Specific ──
        when (blockingMode) {
            BlockingMode.EASY -> {
                val easyWait = settings.easyModeWaitSeconds
                NextChallengeCard(
                    icon = Icons.Default.Timer,
                    iconBg = modeAccent.copy(alpha = 0.3f),
                    iconTint = modeAccent,
                    label = "CONFIGURE EASY MODE",
                    title = "${easyWait}s Wait",
                    subtitle = "Wait $easyWait seconds before your break",
                    accentColor = modeAccent,
                    onClick = onNavigateToEasyModeSettings
                )
            }
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
                    iconBg = modeAccent.copy(alpha = 0.3f),
                    iconTint = modeAccent,
                    label = "CONFIGURE DISCIPLINE MODE",
                    title = title,
                    subtitle = "Complete to unlock a ${breakDurationMinutes}min break",
                    accentColor = modeAccent,
                    onClick = onNavigateToDisciplineModeSettings
                )
            }
            BlockingMode.HARDCORE -> {
                NextChallengeCard(
                    icon = Icons.Default.Lock,
                    iconBg = modeAccent.copy(alpha = 0.3f),
                    iconTint = modeAccent,
                    label = "CONFIGURE HARDCORE MODE",
                    title = "No Breaks",
                    subtitle = "Stay focused. No breaks allowed.",
                    accentColor = modeAccent,
                    onClick = onNavigateToHardcoreModeSettings
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Protected Apps Section ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceCard.copy(alpha = 0.55f))
                .border(1.dp, GlassBorderBrush, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Protected Apps",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Add More",
                    color = modeAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showAddMoreBottomSheet = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(metrics.protectedApps, key = { it.packageName }) { app ->
                    ProtectedAppIcon(name = app.appName, packageName = app.packageName)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

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
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to stop Focus Mode?",
                    color = TextSecondary,
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

    if (showReelCountExplainerDialog) {
        ReelCountExplainerDialog(
            isPremium = settings.isPremium,
            onDismiss = { showReelCountExplainerDialog = false },
            onEnableClick = {
                if (settings.isPremium) {
                    onEnableReelCount()
                    showReelCountExplainerDialog = false
                } else {
                    android.widget.Toast.makeText(context, "Available for Premium users only", android.widget.Toast.LENGTH_SHORT).show()
                    onNavigateToSubscription()
                    showReelCountExplainerDialog = false
                }
            }
        )
    }
}

// ── Next Challenge Card ──
@Composable
private fun NextChallengeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    label: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCardLight.copy(alpha = 0.55f))
            .border(1.dp, GlassBorderBrush, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary
        )
    }
}

// ── Break Button ──
@Composable
private fun BreakButton(
    text: String,
    subtext: String?,
    accentColor: Color,
    enabled: Boolean,
    disabledTint: Color? = null,
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
                ambientColor = accentColor.copy(alpha = 0.5f),
                spotColor = accentColor
            )
            .clip(shape)
            .background(accentColor)
            .clickable { onClick() }
    } else {
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(disabledTint?.copy(alpha = 0.15f) ?: SurfaceCardLight.copy(alpha = 0.4f))
            .border(1.dp, disabledTint?.copy(alpha = 0.3f) ?: TextMuted.copy(alpha = 0.2f), shape)
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
                        else (disabledTint?.copy(alpha = 0.15f) ?: Color.White.copy(alpha = 0.05f))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (enabled) Icons.Default.LocalCafe else Icons.Default.Block,
                    contentDescription = null,
                    tint = if (enabled) darkTextColor else (disabledTint?.copy(alpha = 0.8f) ?: TextMuted),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = text,
                    color = if (enabled) darkTextColor else (disabledTint?.copy(alpha = 0.9f) ?: TextMuted),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtext != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtext,
                        color = if (enabled) darkTextColor.copy(alpha = 0.8f) else (disabledTint?.copy(alpha = 0.6f) ?: TextMuted.copy(alpha = 0.6f)),
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
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Start",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ProtectedAppIcon(
    name: String,
    packageName: String
) {
    val iconBitmap = rememberAppIconBitmap(packageName)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (iconBitmap == null) colorForPackage(packageName) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (iconBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = iconBitmap,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                )
            } else {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Custom App Icons Drawn with Canvas ──

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
        // Simple alien head representation
        drawCircle(color = Color.White, radius = w * 0.25f, center = Offset(w * 0.5f, h * 0.55f))
        // Antenna
        drawLine(color = Color.White, start = Offset(w * 0.5f, h * 0.3f), end = Offset(w * 0.6f, h * 0.15f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color = Color.White, radius = w * 0.05f, center = Offset(w * 0.6f, h * 0.15f))
        // Ears
        drawCircle(color = Color.White, radius = w * 0.08f, center = Offset(w * 0.25f, h * 0.45f))
        drawCircle(color = Color.White, radius = w * 0.08f, center = Offset(w * 0.75f, h * 0.45f))
        // Eyes (orange)
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
        // 'f' character
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

        // Outer rounded rectangle
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.15f, h * 0.15f),
            size = Size(w * 0.7f, h * 0.7f),
            cornerRadius = CornerRadius(w * 0.22f, h * 0.22f),
            style = Stroke(width = strokeWidth)
        )

        // Inner circle (lens)
        drawCircle(
            color = Color.White,
            radius = w * 0.18f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = strokeWidth)
        )

        // Small dot (flash)
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
            androidx.compose.foundation.Image(
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

