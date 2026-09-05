package com.flow.shift.feature.onboarding

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.shift.core.AppSortingHelper
import com.flow.shift.core.designsystem.AppIcon
import com.flow.shift.R
import com.flow.shift.feature.modes.BlockingMode
import com.flow.shift.theme.*
import kotlin.math.roundToInt



/**
 * Shared background layer: the onboarding image with blur + dark scrim overlay.
 */
@Composable
private fun OnboardingBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.onboarding_bg_2),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Dark scrim for contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.40f),
                            Color.Black.copy(alpha = 0.60f)
                        )
                    )
                )
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  ONBOARDING SCREEN — 7-Step Guided Setup
// ════════════════════════════════════════════════════════════════════

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onFinish: () -> Unit
) {
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val selectedScreenTime by viewModel.selectedScreenTime.collectAsStateWithLifecycle()
    val selectedTargetScreenTime by viewModel.selectedTargetScreenTime.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val selectedChallengeType by viewModel.selectedChallengeType.collectAsStateWithLifecycle()
    val challengeAmount by viewModel.challengeAmount.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var hasUsagePermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember { mutableStateOf(hasAccessibilityPermission(context)) }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    
    // OEM specific permissions
    val isChineseOEM = Build.MANUFACTURER.lowercase() in listOf("xiaomi", "redmi", "poco", "oppo", "vivo", "oneplus", "realme", "iqoo", "huawei", "honor")
    var hasAutostartPermission by remember { mutableStateOf(!isChineseOEM) }
    var hasBackgroundWindowsPermission by remember { mutableStateOf(hasBackgroundWindowPermission(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasUsagePermission = hasUsageStatsPermission(context)
        hasOverlayPermission = Settings.canDrawOverlays(context)
        hasAccessibilityPermission = hasAccessibilityPermission(context)
        hasCameraPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasBackgroundWindowsPermission = hasBackgroundWindowPermission(context)
    }

    // Can proceed to next step?
    val canProceed = when (currentStep) {
        0 -> true // Welcome — always
        1 -> true // Screen time — slider always valid
        2 -> true // Target screen time — slider always valid
        3 -> true // Mode — always has a default (EASY)
        4 -> true // Apps — optional, can skip
        5 -> hasUsagePermission && hasOverlayPermission && hasAccessibilityPermission && hasAutostartPermission && hasBackgroundWindowsPermission
        6 -> true // All set — always
        else -> false
    }

    // Full-bleed background image behind everything
    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingBackground()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                AnimatedVisibility(
                    visible = currentStep > 0 && currentStep < 6,
                    enter = fadeIn(tween(400)),
                    exit = fadeOut(tween(400))
                ) {
                    OnboardingTopBar(
                        currentStep = currentStep,
                        totalSteps = 5,
                        onBackClick = { viewModel.previousStep() }
                    )
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = currentStep > 0 && currentStep < 6,
                    enter = fadeIn(tween(400)),
                    exit = fadeOut(tween(400))
                ) {
                    OnboardingBottomBar(
                        currentStep = currentStep,
                        canProceed = canProceed,
                        onNextClick = { viewModel.nextStep() }
                    )
                }
            }
        ) { paddingValues ->
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                },
                modifier = Modifier.fillMaxSize(),
                label = "onboarding_step"
            ) { step ->
                val stepPadding = if (step > 0 && step < 6) paddingValues else PaddingValues(0.dp)
                Box(modifier = Modifier.fillMaxSize().padding(stepPadding)) {
                    when (step) {
                        0 -> WelcomeStep(onGetStarted = { viewModel.nextStep() })
                    1 -> ScreenTimeSliderStep(
                        selectedHours = selectedScreenTime ?: 4f,
                        onSelectHours = { viewModel.selectScreenTime(it) }
                    )
                    2 -> TargetScreenTimeSliderStep(
                        selectedHours = selectedTargetScreenTime ?: 8f,
                        onSelectHours = { viewModel.selectTargetScreenTime(it) }
                    )
                    3 -> ModeSelectionStep(
                        selectedMode = selectedMode,
                        selectedChallengeType = selectedChallengeType,
                        challengeAmount = challengeAmount,
                        onSelectMode = { viewModel.selectMode(it) },
                        onSelectChallengeType = { viewModel.selectChallengeType(it) },
                        onSelectChallengeAmount = { viewModel.selectChallengeAmount(it) }
                    )
                    4 -> AppSelectionStep(
                        viewModel = viewModel
                    )
                    5 -> PermissionsStep(
                        context = context,
                        hasUsagePermission = hasUsagePermission,
                        hasOverlayPermission = hasOverlayPermission,
                        hasAccessibilityPermission = hasAccessibilityPermission,
                        hasAutostartPermission = hasAutostartPermission,
                        hasBackgroundWindowsPermission = hasBackgroundWindowsPermission,
                        onAutostartClick = {
                            hasAutostartPermission = true
                            openAutostartSettings(context)
                        },
                        onBackgroundWindowsClick = {
                            openBackgroundWindowsSettings(context)
                        }
                    )
                    6 -> AllSetStep(
                        selectedMode = selectedMode,
                        selectedScreenTime = "${(selectedScreenTime ?: 4f).toInt()}",
                        selectedTargetScreenTime = formatTargetScreenTime(selectedTargetScreenTime ?: 8f),
                        onFinish = {
                            viewModel.completeOnboarding {
                                onFinish()
                            }
                        }
                    )
                }
                } // Added missing brace for Box
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  TOP BAR — Back button + Progress
// ════════════════════════════════════════════════════════════════════

@Composable
private fun OnboardingTopBar(
    currentStep: Int,
    totalSteps: Int,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Segmented progress bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(totalSteps) { index ->
                val fraction by animateFloatAsState(
                    targetValue = when {
                        index < currentStep -> 1f
                        else -> 0f
                    },
                    animationSpec = tween(400, easing = FastOutSlowInEasing),
                    label = "progress_$index"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Amber500)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  BOTTOM BAR — Continue button
// ════════════════════════════════════════════════════════════════════

@Composable
private fun OnboardingBottomBar(
    currentStep: Int,
    canProceed: Boolean,
    onNextClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                    startY = 0f,
                    endY = 80f
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Button(
            onClick = onNextClick,
            enabled = canProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Amber500,
                contentColor = SurfaceBlack,
                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                disabledContentColor = TextMuted
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Continue",
                fontFamily = AppFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  STEP 1: WELCOME
// ════════════════════════════════════════════════════════════════════

@Composable
private fun WelcomeStep(onGetStarted: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onGetStarted()
    }

    val handleGetStarted = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                onGetStarted()
            } else {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onGetStarted()
        }
    }

    // Pulsing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Entry animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val titleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800, delayMillis = 300),
        label = "titleAlpha"
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800, delayMillis = 600),
        label = "subtitleAlpha"
    )
    val buttonAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800, delayMillis = 900),
        label = "buttonAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 32.dp)
                .padding(top = 32.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Glowing icon
            val neonCyan = Color(0xFF00E5FF)
            Box(contentAlignment = Alignment.Center) {
                // Outer glow
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .graphicsLayer { 
                            scaleX = glowScale
                            scaleY = glowScale
                            alpha = glowAlpha
                        }
                        .clip(CircleShape)
                        .background(neonCyan.copy(alpha = 0.15f))
                )
                // Inner frosted glass circle
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.15f),
                                    Color.White.copy(alpha = 0.06f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo_transparent),
                        contentDescription = "FlowShift Logo",
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = TextPrimary)) {
                        append("Welcome to\n")
                    }
                    withStyle(style = SpanStyle(color = Amber500)) {
                        append("FlowShift")
                    }
                },
                fontFamily = AppFontFamily,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 44.sp,
                modifier = Modifier.graphicsLayer { alpha = titleAlpha }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Reclaim your focus.\nBuild your body.\nGet your life back.",
                color = TextSecondary,
                fontFamily = AppFontFamily,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
                modifier = Modifier.graphicsLayer { alpha = subtitleAlpha }
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = handleGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer { alpha = buttonAlpha },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber500,
                    contentColor = SurfaceBlack
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Let's Go",
                    fontFamily = AppFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  STEP 2: SCREEN TIME — Slider-based
// ════════════════════════════════════════════════════════════════════

private fun getScrollLabel(hours: Int): Pair<String, Color> = when {
    hours <= 2 -> "Casual" to Color(0xFF22C55E)
    hours <= 4 -> "Distracted" to Color(0xFFF59E0B)
    hours <= 8 -> "Addicted" to Color(0xFFFF6B35)
    else -> "Doomscroller" to Color(0xFFEF4444)
}

private fun getScrollIcon(hours: Int): ImageVector = when {
    hours <= 2 -> Icons.Default.PhoneAndroid
    hours <= 4 -> Icons.Default.Schedule
    hours <= 8 -> Icons.Default.Warning
    else -> Icons.Default.LocalFireDepartment
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenTimeSliderStep(
    selectedHours: Float,
    onSelectHours: (Float) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(selectedHours) }
    val hours = sliderValue.roundToInt()
    val fraction = (hours - 1f) / 17f

    // 3-stage color progression: Amber (1h) -> Vibrant Red (12h) -> Extreme Dark Red (18h)
    val sliderStartColor = Color(0xFFFBBF24) // Warm Golden Amber (1h)
    val sliderMidColor = Color(0xFFEF4444)   // Vibrant Red (12h)
    val sliderEndColor = Color(0xFFA42222)   // Deep Crimson Red (18h)

    val currentColor = if (hours <= 12) {
        val f = (hours - 1f) / 11f
        androidx.compose.ui.graphics.lerp(sliderStartColor, sliderMidColor, f)
    } else {
        val f = (hours - 12f) / 6f
        androidx.compose.ui.graphics.lerp(sliderMidColor, sliderEndColor, f)
    }

    var prevHours by remember { mutableIntStateOf(hours) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(hours) {
        val increased = hours > prevHours
        prevHours = hours
        if (increased) {
            scale.animateTo(
                targetValue = 1.25f,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "How much time do you\nspend scrolling?",
            color = TextPrimary,
            fontFamily = AppFontFamily,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Be honest — this helps us personalize\nyour experience.",
            color = TextSecondary,
            fontFamily = AppFontFamily,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Large hour display — dynamic sizing and color
        val currentTextSize = (32f + (fraction * 32f)).sp

        Box(
            modifier = Modifier
                .padding(vertical = 24.dp)
                .height(80.dp) // Fixed height prevents shifting layout
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${hours}h",
                color = currentColor,
                fontFamily = AppFontFamily,
                fontSize = currentTextSize,
                fontWeight = FontWeight.Bold
            )
        }


        Spacer(modifier = Modifier.height(56.dp))

        // Hour markers
        androidx.compose.ui.layout.Layout(
            content = {
                Text("1h", color = TextMuted, fontFamily = AppFontFamily, fontSize = 12.sp)
                Text("6h", color = TextMuted, fontFamily = AppFontFamily, fontSize = 12.sp)
                Text("12h", color = TextMuted, fontFamily = AppFontFamily, fontSize = 12.sp)
                Text("18h", color = TextMuted, fontFamily = AppFontFamily, fontSize = 12.sp)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
            layout(constraints.maxWidth, placeables.maxOf { it.height }) {
                val width = constraints.maxWidth
                val fractions = listOf(0f, 5f / 17f, 11f / 17f, 1f)
                placeables.forEachIndexed { index, placeable ->
                    val x = (width * fractions[index] - placeable.width / 2f).roundToInt()
                    placeable.placeRelative(x = x, y = 0)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Custom slider
        val sliderColors = SliderDefaults.colors(
            thumbColor = currentColor,
            inactiveTrackColor = Color.White.copy(alpha = 0.12f),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onSelectHours(sliderValue) },
            valueRange = 1f..18f,
            steps = 16,
            modifier = Modifier.fillMaxWidth(),
            colors = sliderColors,
            track = { sliderState ->
                val trackFraction = (sliderState.value - 1f) / 17f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    if (trackFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(trackFraction)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = if (hours <= 12) {
                                            listOf(sliderStartColor, currentColor)
                                        } else {
                                            listOf(sliderStartColor, sliderMidColor, currentColor)
                                        }
                                    )
                                )
                        )
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ════════════════════════════════════════════════════════════════════
//  STEP 3: TARGET SCREEN TIME — Cool Rehabilitation Vibe
// ════════════════════════════════════════════════════════════════════

private fun formatTargetScreenTime(sliderIndex: Float): String {
    val index = sliderIndex.roundToInt().coerceIn(0, 36)
    val totalMinutes = if (index <= 24) {
        index * 15
    } else {
        360 + (index - 24) * 30
    }
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h == 0 && m == 0 -> "0m"
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetScreenTimeSliderStep(
    selectedHours: Float,
    onSelectHours: (Float) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(selectedHours) }
    val index = sliderValue.roundToInt().coerceIn(0, 36)
    val fraction = index / 36f
    val formattedTime = formatTargetScreenTime(sliderValue)

    // 3-stage color progression: Vibrant Cyan (0m) -> Soft Ice Cyan (6h) -> Pure White (12h)
    val sliderStartColor = Color(0xFF00E5FF) // Vibrant Neon Cyan (0m)
    val sliderMidColor = Color(0xFF99F2FF)   // Soft Ice Cyan (6h)
    val sliderEndColor = Color(0xFFFFFFFF)   // Pure White (12h)

    val currentColor = if (index <= 24) {
        val f = index / 24f
        androidx.compose.ui.graphics.lerp(sliderStartColor, sliderMidColor, f)
    } else {
        val f = (index - 24) / 12f
        androidx.compose.ui.graphics.lerp(sliderMidColor, sliderEndColor, f)
    }

    var prevIndex by remember { mutableIntStateOf(index) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(index) {
        val increased = index > prevIndex
        prevIndex = index
        if (increased) {
            scale.animateTo(
                targetValue = 1.25f,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Set your target",
            color = TextPrimary,
            fontFamily = AppFontFamily,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "How much screen time do you\nactually want per day?",
            color = TextSecondary,
            fontFamily = AppFontFamily,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Large hour display — dynamic sizing and color
        val currentTextSize = (32f + (fraction * 16f)).sp

        Box(
            modifier = Modifier
                .padding(vertical = 24.dp)
                .height(80.dp) // Fixed height prevents shifting layout
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formattedTime,
                color = currentColor,
                fontFamily = AppFontFamily,
                fontSize = currentTextSize,
                fontWeight = FontWeight.Bold
            )
        }


        Spacer(modifier = Modifier.height(56.dp))

        // Hour markers
        androidx.compose.ui.layout.Layout(
            content = {
                Text("0m", color = TextMuted, fontFamily = AppFontFamily, fontSize = 12.sp)
                Text("3h", color = TextMuted, fontFamily = AppFontFamily, fontSize = 12.sp)
                Text("6h", color = TextMuted, fontFamily = AppFontFamily, fontSize = 12.sp)
                Text("12h", color = TextMuted, fontFamily = AppFontFamily, fontSize = 12.sp)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
            layout(constraints.maxWidth, placeables.maxOf { it.height }) {
                val width = constraints.maxWidth
                val fractions = listOf(0f, 12f / 36f, 24f / 36f, 1f)
                placeables.forEachIndexed { index, placeable ->
                    val x = (width * fractions[index] - placeable.width / 2f).roundToInt()
                    placeable.placeRelative(x = x, y = 0)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Custom slider
        val sliderColors = SliderDefaults.colors(
            thumbColor = currentColor,
            inactiveTrackColor = Color.White.copy(alpha = 0.12f),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onSelectHours(sliderValue) },
            valueRange = 0f..36f,
            steps = 35,
            modifier = Modifier.fillMaxWidth(),
            colors = sliderColors,
            track = { sliderState ->
                val trackFraction = (sliderState.value / 36f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    if (trackFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(trackFraction)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = if (index <= 24) {
                                            listOf(sliderStartColor, currentColor)
                                        } else {
                                            listOf(sliderStartColor, sliderMidColor, currentColor)
                                        }
                                    )
                                )
                        )
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ════════════════════════════════════════════════════════════════════
//  STEP 4: MODE SELECTION
// ════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelectionStep(
    selectedMode: BlockingMode,
    selectedChallengeType: String,
    challengeAmount: Int,
    onSelectMode: (BlockingMode) -> Unit,
    onSelectChallengeType: (String) -> Unit,
    onSelectChallengeAmount: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Choose your mode",
            color = TextPrimary,
            fontFamily = AppFontFamily,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "How strict should we be when you\ntry to open blocked apps?",
            color = TextSecondary,
            fontFamily = AppFontFamily,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Easy
        OnboardingModeCard(
            title = "Easy",
            description = "After the set screen time, hold for 90 seconds before you can proceed scrolling again.",
            icon = Icons.Default.Timer,
            accentColor = ModeEasyAccent,
            isSelected = selectedMode == BlockingMode.EASY,
            onClick = { onSelectMode(BlockingMode.EASY) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Discipline
        OnboardingModeCard(
            title = "Discipline",
            description = "Perform pushups or solve maths to earn scroll time.",
            icon = Icons.Default.Psychology,
            accentColor = ModeStrictAccent,
            isSelected = selectedMode == BlockingMode.STRICT,
            onClick = { onSelectMode(BlockingMode.STRICT) }
        )

        // Challenge picker — slides in when Strict is selected
        AnimatedVisibility(
            visible = selectedMode == BlockingMode.STRICT,
            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "You can configure your challenge later in the app.",
                    color = TextSecondary,
                    fontFamily = AppFontFamily,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hardcore
        OnboardingModeCard(
            title = "Hardcore",
            description = "Once the timer hits, you can't scroll anymore.",
            icon = Icons.Default.Lock,
            accentColor = ModeHardcoreAccent,
            isSelected = selectedMode == BlockingMode.HARDCORE,
            onClick = { onSelectMode(BlockingMode.HARDCORE) }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun OnboardingModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else accentColor.copy(alpha = 0.3f),
        animationSpec = tween(250),
        label = "modeBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = accentColor.copy(alpha = 0.2f),
        animationSpec = tween(250),
        label = "modeBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontFamily = AppFontFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = TextSecondary,
                fontFamily = AppFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Radio-style indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (isSelected) accentColor else TextMuted,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
        }
    }
}



// ════════════════════════════════════════════════════════════════════
//  STEP 5: APP SELECTION
// ════════════════════════════════════════════════════════════════════

@Composable
private fun AppSelectionStep(
    viewModel: OnboardingViewModel
) {
    val apps by viewModel.appList.collectAsStateWithLifecycle()
    val topApps by viewModel.topAppsList.collectAsStateWithLifecycle()
    val otherApps by viewModel.otherAppsList.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    
    val onSearchQueryChange: (String) -> Unit = { viewModel.updateSearchQuery(it) }
    val onToggleApp: (OnboardingAppItem, Boolean) -> Unit = { app, blocked -> viewModel.toggleAppBlocked(app, blocked) }

    var isMoreAppsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Block distracting apps",
                color = TextPrimary,
                fontFamily = AppFontFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select the apps that steal your time.\nYou can always change this later.",
                color = TextSecondary,
                fontFamily = AppFontFamily,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        "Search apps...",
                        color = TextMuted,
                        fontFamily = AppFontFamily,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber500,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                    cursorColor = Amber500,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // App list
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Amber500)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                if (searchQuery.isNotBlank()) {
                    items(apps, key = { it.packageName }, contentType = { "app_item" }) { app ->
                        OnboardingAppListItem(
                            app = app,
                            onToggle = { isBlocked -> onToggleApp(app, isBlocked) }
                        )
                    }
                } else {
                    items(topApps, key = { it.packageName }, contentType = { "app_item" }) { app ->
                        OnboardingAppListItem(
                            app = app,
                            onToggle = { isBlocked -> onToggleApp(app, isBlocked) }
                        )
                    }
                    if (otherApps.isNotEmpty()) {
                        item(key = "more_apps_header") {
                            ExpandableAppsHeader(
                                title = "More Apps",
                                count = otherApps.size,
                                isExpanded = isMoreAppsExpanded,
                                onToggle = { isMoreAppsExpanded = !isMoreAppsExpanded }
                            )
                        }
                        if (isMoreAppsExpanded) {
                            items(otherApps, key = { it.packageName }, contentType = { "app_item" }) { app ->
                                OnboardingAppListItem(
                                    app = app,
                                    onToggle = { isBlocked -> onToggleApp(app, isBlocked) }
                                )
                            }
                        }
                    }
                }
                // Bottom spacer for the bottom bar
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ExpandableAppsHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = Amber500,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "$title ($count)",
                color = TextPrimary,
                fontFamily = AppFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = TextSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun OnboardingAppListItem(
    app: OnboardingAppItem,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon
        AppIcon(
            packageName = app.packageName,
            appName = app.appName,
            modifier = Modifier.size(40.dp),
            cornerRadius = 10.dp,
            fallbackFontSize = 16.sp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = app.appName,
            color = TextPrimary,
            fontFamily = AppFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = app.isBlocked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Amber500,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  STEP 6: PERMISSIONS
// ════════════════════════════════════════════════════════════════════

@Composable
private fun PermissionsStep(
    context: Context,
    hasUsagePermission: Boolean,
    hasOverlayPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    hasAutostartPermission: Boolean,
    hasBackgroundWindowsPermission: Boolean,
    onAutostartClick: () -> Unit,
    onBackgroundWindowsClick: () -> Unit
) {
    var showBackgroundWindowsGuide by remember { mutableStateOf(false) }

    if (showBackgroundWindowsGuide) {
        BackgroundWindowsGuideDialog(
            onDismiss = { showBackgroundWindowsGuide = false },
            onProceed = {
                showBackgroundWindowsGuide = false
                onBackgroundWindowsClick()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Amber500,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Almost there!",
            color = TextPrimary,
            fontFamily = AppFontFamily,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "FlowShift needs these permissions to\ntrack and block apps effectively.",
            color = TextSecondary,
            fontFamily = AppFontFamily,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Usage Access
        PermissionCard(
            icon = Icons.Default.QueryStats,
            title = "Usage Access",
            description = "Detects when you open a distracting app so we can step in.",
            isGranted = hasUsagePermission,
            onClick = {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                intent.data = Uri.parse("package:${context.packageName}")
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Overlay
        PermissionCard(
            icon = Icons.Default.Layers,
            title = "Display Over Apps",
            description = "Shows the blocker screen when you try to open a blocked app.",
            isGranted = hasOverlayPermission,
            onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Accessibility
        var showAccessibilityDialog by remember { mutableStateOf(false) }

        if (showAccessibilityDialog) {
            com.flow.shift.core.designsystem.AccessibilityDisclosureDialog(
                onDismiss = { showAccessibilityDialog = false },
                onAccept = {
                    showAccessibilityDialog = false
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )
        }

        PermissionCard(
            icon = Icons.Default.Accessibility,
            title = "Accessibility",
            description = "Allows us to detect when you're scrolling Reels or Shorts.",
            isGranted = hasAccessibilityPermission,
            onClick = {
                showAccessibilityDialog = true
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        val isChineseOEM = Build.MANUFACTURER.lowercase() in listOf("xiaomi", "redmi", "poco", "oppo", "vivo", "oneplus", "realme", "iqoo", "huawei", "honor")
        
        if (isChineseOEM) {
            // Background Autostart
            PermissionCard(
                icon = Icons.Default.Settings,
                title = "Autostart",
                description = "Prevents your phone from stopping the app in the background.",
                isGranted = hasAutostartPermission,
                onClick = onAutostartClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Background Windows
            PermissionCard(
                icon = Icons.Default.OpenInNew,
                title = "Background Windows",
                description = "Allows the blocker screen to open from the background.",
                isGranted = hasBackgroundWindowsPermission,
                onClick = { showBackgroundWindowsGuide = true }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.05f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(enabled = !isGranted) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                contentDescription = null,
                tint = Amber500,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontFamily = AppFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = TextSecondary,
                fontFamily = AppFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (isGranted) {
            Text(
                text = "Granted",
                color = Amber500,
                fontFamily = AppFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber500,
                    contentColor = SurfaceBlack
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Grant",
                    fontFamily = AppFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  STEP 7: ALL SET
// ════════════════════════════════════════════════════════════════════

@Composable
private fun AllSetStep(
    selectedMode: BlockingMode,
    selectedScreenTime: String,
    selectedTargetScreenTime: String,
    onFinish: () -> Unit
) {
    // Entry animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val checkScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "checkScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 400),
        label = "contentAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Animated checkmark — glass circle
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(checkScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.06f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Amber500,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "You are committed.",
            color = TextPrimary,
            fontFamily = AppFontFamily,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(contentAlpha)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "You committed to reducing your $selectedScreenTime hrs/day\nof screen time toward $selectedTargetScreenTime per day using ${selectedMode.name.lowercase().replaceFirstChar { it.uppercase() }} Mode.",
            color = TextSecondary,
            fontFamily = AppFontFamily,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.alpha(contentAlpha)
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .alpha(contentAlpha),
            colors = ButtonDefaults.buttonColors(
                containerColor = Amber500,
                contentColor = SurfaceBlack
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Commit & Start",
                fontFamily = AppFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ════════════════════════════════════════════════════════════════════
//  HELPER
// ════════════════════════════════════════════════════════════════════

private fun hasAccessibilityPermission(context: Context): Boolean {
    var accessibilityEnabled = 0
    try {
        accessibilityEnabled = Settings.Secure.getInt(
            context.applicationContext.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED
        )
    } catch (e: Settings.SettingNotFoundException) {
        // Assume not enabled
    }

    if (accessibilityEnabled == 1) {
        val settingValue = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (settingValue != null) {
            val serviceName = context.packageName + "/" + com.flow.shift.core.usagetracking.ScrollBlockerAccessibilityService::class.java.canonicalName
            return settingValue.contains(serviceName) || settingValue.contains(context.packageName)
        }
    }
    return false
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun openAutostartSettings(context: Context) {
    val intents = listOf(
        Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
        Intent().setComponent(android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")),
        Intent().setComponent(android.content.ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
    )

    for (intent in intents) {
        try {
            if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                context.startActivity(intent)
                return
            }
        } catch (e: Exception) {
            // Ignore and try the next one
        }
    }

    openAppSettings(context)
}

private fun openBackgroundWindowsSettings(context: Context) {
    try {
        val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            putExtra("extra_pkgname", context.packageName)
        }
        if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            context.startActivity(intent)
            return
        }
    } catch (e: Exception) {
        // Fallback
    }
    
    openAppSettings(context)
}

private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback
    }
}

private fun hasBackgroundWindowPermission(context: Context): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    if (manufacturer !in listOf("xiaomi", "redmi", "poco", "oppo", "vivo", "oneplus", "realme", "iqoo", "huawei", "honor")) {
        return true
    }
    
    try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val op = try {
            val field = AppOpsManager::class.java.getDeclaredField("OP_BACKGROUND_START_ACTIVITY")
            field.getInt(null)
        } catch (e: Exception) {
            10021
        }
        
        val method = appOps.javaClass.getMethod("checkOpNoThrow", Int::class.java, Int::class.java, String::class.java)
        val mode = method.invoke(appOps, op, Process.myUid(), context.packageName) as Int
        return mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        // If we can't check it programmatically, default to true to not block the user
        return true
    }
}

@Composable
private fun BackgroundWindowsGuideDialog(
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    val manufacturer = Build.MANUFACTURER.lowercase()
    
    val guideText = when {
        manufacturer in listOf("xiaomi", "redmi", "poco") -> 
            "1. You will be taken to App Info.\n2. Tap on 'Other permissions'.\n3. Find 'Display pop-up windows while running in the background' and set it to 'Always allow'."
        manufacturer in listOf("oppo", "oneplus", "realme") -> 
            "1. You will be taken to App Info.\n2. Tap on 'Permissions'.\n3. Find 'Display over other apps' or similar and allow it."
        manufacturer in listOf("vivo", "iqoo") -> 
            "1. You will be taken to App Info.\n2. Tap on 'Permissions' or 'Single permission settings'.\n3. Enable 'Background pop-ups'."
        manufacturer in listOf("huawei", "honor") -> 
            "1. You will be taken to App Info.\n2. Tap on 'Permissions'.\n3. Allow 'Dropzone' or 'Display over other apps'."
        else -> 
            "Please find the permission to 'Display pop-up windows while running in the background' and allow it."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBlack,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Enable Background Windows",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Text(
                text = guideText,
                fontFamily = AppFontFamily,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onProceed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber500,
                    contentColor = SurfaceBlack
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Let's do it",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = TextMuted,
                    fontFamily = AppFontFamily
                )
            }
        }
    )
}
