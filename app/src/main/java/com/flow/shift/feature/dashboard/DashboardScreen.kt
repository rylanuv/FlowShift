package com.flow.shift.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
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
    onNavigateToHardcoreModeSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (showBackground) {
                    val bgRes = (uiState as? DashboardUiState.Success)?.settings?.blockingMode?.backgroundImageRes ?: R.drawable.main_screen
                    Modifier
                        .paint(
                            painter = painterResource(id = bgRes),
                            contentScale = ContentScale.Crop
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
    onStartFocusMode: (Int) -> Unit,
    onStopFocusMode: () -> Unit
) {
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

    val now = System.currentTimeMillis()
    val isFocusModeActive = metrics.focusModeUntilMillis > now
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
    var showAddMoreBottomSheet by remember { mutableStateOf(false) }
    var showFocusModeDialog by remember { mutableStateOf(false) }

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
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Hero Card: Screen Time Ring ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(SurfaceCard.copy(alpha = 0.55f))
                .border(1.dp, GlassBorderBrush, RoundedCornerShape(28.dp))
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
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
                            color = modeAccent,
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
                        color = modeAccent,
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
        }

        Spacer(modifier = Modifier.height(32.dp))

        val context = LocalContext.current
        when (blockingMode) {
            BlockingMode.EASY -> {
                val easyWait = settings.easyModeWaitSeconds
                BreakButton(
                    text = "Take a Break",
                    subtext = "${easyWait}s wait required",
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
                    subtext = if (challengeType == "MATH") "Solve math first" else "Complete pushups first",
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

        Spacer(modifier = Modifier.height(16.dp))
        if (isFocusModeActive) {
            BreakButton(
                text = "Stop Focus Mode",
                subtext = "Unblock apps instantly",
                accentColor = StatusRed,
                enabled = true,
                onClick = { onStopFocusMode() }
            )
        } else {
            BreakButton(
                text = "Start Focus Mode",
                subtext = "Block apps instantly",
                accentColor = Color(0xFF6366F1), // Indigo
                enabled = true,
                onClick = { showFocusModeDialog = true }
            )
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
                val (title, icon) = if (challengeType == "MATH") {
                    "$challengeAmount Math Problems" to Icons.Default.Psychology
                } else {
                    "$challengeAmount Push-ups" to Icons.Default.FitnessCenter
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

        Spacer(modifier = Modifier.height(32.dp))

        // ── Stat & Insight Cards Row 1 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Most Time Spent On ──
            DashboardGridCard(
                modifier = Modifier
                    .weight(1f)
                    .height(145.dp),
                label = "MOST TIME SPENT ON",
                labelColor = modeAccent
            ) {
                val topUsage = metrics.topUsage
                if (topUsage != null && topUsage.usageMillis > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppInitialIcon(
                            name = topUsage.appName,
                            packageName = topUsage.packageName,
                            size = 40,
                            fontSize = 14
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = topUsage.appName,
                                color = TextPrimary,
                                fontFamily = AppFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${formatDurationShort(topUsage.usageMillis)} today",
                                color = TextSecondary,
                                fontFamily = AppFontFamily,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Column {
                        Text(
                            text = if (!metrics.hasUsageAccess) "Permission needed" else "No usage yet",
                            color = TextPrimary,
                            fontFamily = AppFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (!metrics.hasUsageAccess) "Grant usage access" else "0m today",
                            color = TextSecondary,
                            fontFamily = AppFontFamily,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ── What Scrolling Is Costing You ──
            DashboardGridCard(
                modifier = Modifier
                    .weight(1f)
                    .height(145.dp),
                label = "WHAT SCROLLING IS COSTING YOU",
                labelColor = Amber500
            ) {
                val totalUsageText = when {
                    !metrics.hasUsageAccess -> "Permission needed"
                    else -> formatDurationShort(metrics.totalUsageTodayMillis)
                }

                Column {
                    Text(
                        text = "You've spent",
                        color = TextSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (!metrics.hasUsageAccess) "--" else totalUsageText,
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (!metrics.hasUsageAccess) "Grant usage access" else "today",
                        color = TextSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Stat & Insight Cards Row 2 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Most App Opened ──
            DashboardGridCard(
                modifier = Modifier
                    .weight(1f)
                    .height(145.dp),
                label = "MOST APP OPENED",
                labelColor = Amber500
            ) {
                val topOpened = metrics.topOpened
                if (topOpened != null && topOpened.openCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppInitialIcon(
                            name = topOpened.appName,
                            packageName = topOpened.packageName,
                            size = 40,
                            fontSize = 14
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = topOpened.appName,
                                color = TextPrimary,
                                fontFamily = AppFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${topOpened.openCount} ${if (topOpened.openCount == 1) "time" else "times"} today",
                                color = TextSecondary,
                                fontFamily = AppFontFamily,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Column {
                        Text(
                            text = if (!metrics.hasUsageAccess) "Permission needed" else "No apps opened",
                            color = TextPrimary,
                            fontFamily = AppFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (!metrics.hasUsageAccess) "Grant usage access" else "0 times today",
                            color = TextSecondary,
                            fontFamily = AppFontFamily,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ── Last Intervention ──
            DashboardGridCard(
                modifier = Modifier
                    .weight(1f)
                    .height(145.dp),
                label = "LAST INTERVENTION",
                labelColor = StatusGreen
            ) {
                val lastIntervention = metrics.lastIntervention

                if (lastIntervention != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppInitialIcon(
                            name = lastIntervention.appName,
                            packageName = lastIntervention.packageName,
                            size = 40,
                            fontSize = 14
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = lastIntervention.appName,
                                color = TextPrimary,
                                fontFamily = AppFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatClockTime(lastIntervention.timestampMillis),
                                color = TextMuted,
                                fontFamily = AppFontFamily,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Column {
                        Text(
                            text = "No interventions yet",
                            color = TextPrimary,
                            fontFamily = AppFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Clear today",
                            color = TextSecondary,
                            fontFamily = AppFontFamily,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Stat & Insight Cards Row 3 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Breaks Taken ──
            DashboardGridCard(
                modifier = Modifier
                    .weight(1f)
                    .height(145.dp),
                label = "BREAKS TAKEN",
                labelColor = modeAccent
            ) {
                Column {
                    Text(
                        text = metrics.sessionsToday.toString(),
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (metrics.sessionsToday == 1) "break today" else "breaks today",
                        color = TextSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = 12.sp
                    )
                }
            }

            // ── Additional Time Spent ──
            DashboardGridCard(
                modifier = Modifier
                    .weight(1f)
                    .height(145.dp),
                label = "ADDITIONAL TIME SPENT",
                labelColor = Amber500
            ) {
                Column {
                    Text(
                        text = formatDurationShort(metrics.earnedMillisToday),
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "from breaks today",
                        color = TextSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

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

    if (showFocusModeDialog) {
        AlertDialog(
            onDismissRequest = { showFocusModeDialog = false },
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = "Start Focus Mode",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Select how long you want to block all protected apps.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val options = listOf(15, 30, 45, 60, 120)
                    options.forEach { minutes ->
                        Text(
                            text = "$minutes minutes",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onStartFocusMode(minutes)
                                    showFocusModeDialog = false
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFocusModeDialog = false }) {
                    Text("Cancel", color = TextSecondary)
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
    subtext: String,
    accentColor: Color,
    enabled: Boolean,
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
            .background(SurfaceCardLight.copy(alpha = 0.4f))
            .border(1.dp, TextMuted.copy(alpha = 0.2f), shape)
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
                        else Color.White.copy(alpha = 0.05f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (enabled) Icons.Default.LocalCafe else Icons.Default.Block,
                    contentDescription = null,
                    tint = if (enabled) darkTextColor else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = text,
                    color = if (enabled) darkTextColor else TextMuted,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtext,
                    color = if (enabled) darkTextColor.copy(alpha = 0.8f) else TextMuted.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
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

// ── Dashboard Grid Card (Fixed Size) ──
@Composable
private fun DashboardGridCard(
    modifier: Modifier = Modifier,
    label: String,
    labelColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard.copy(alpha = 0.55f))
            .border(1.dp, GlassBorderBrush, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            text = label,
            color = labelColor,
            fontFamily = AppFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 2,
            lineHeight = 13.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

// ── Insight Card ──
@Composable
private fun InsightCard(
    modifier: Modifier = Modifier,
    label: String,
    labelColor: Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard.copy(alpha = 0.55f))
            .border(1.dp, GlassBorderBrush, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            text = label,
            color = labelColor,
            fontFamily = AppFontFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

// ── Stat Card ──
@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    trendLabel: String,
    trendUp: Boolean,
    trendSuffix: String = " this week",
    iconColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCardLight.copy(alpha = 0.55f))
            .border(1.dp, GlassBorderBrush, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (trendUp) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = if (trendUp) StatusGreen else StatusRed,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = trendLabel,
                color = if (trendUp) StatusGreen else StatusRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = trendSuffix,
                color = TextMuted,
                fontSize = 12.sp
            )
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

