package com.flow.shift.feature.onboarding

import android.annotation.SuppressLint
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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.shift.core.AppSortingHelper
import com.flow.shift.core.designsystem.AppIcon
import com.flow.shift.R
import com.flow.shift.feature.modes.BlockingMode
import com.flow.shift.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch



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
//  ONBOARDING SCREEN — 6-Step Guided Setup
// ════════════════════════════════════════════════════════════════════

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onFinish: () -> Unit
) {
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val selectedDailyLimit by viewModel.selectedDailyLimit.collectAsStateWithLifecycle()
    val isCustomLimit by viewModel.isCustomLimit.collectAsStateWithLifecycle()
    val customLimitText by viewModel.customLimitText.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember { mutableStateOf(hasAccessibilityPermission(context)) }
    val manufacturer = remember { Build.MANUFACTURER.lowercase() }
    val brand = remember { Build.BRAND.lowercase() }
    val needsAutostartGuidance = remember {
        manufacturer in listOf("xiaomi", "redmi", "poco", "vivo", "iqoo") ||
        brand in listOf("xiaomi", "redmi", "poco", "vivo", "iqoo")
    }
    val autostartCheckSupported = remember { canCheckAutostart(context) }
    var userInteractedAutostart by remember { mutableStateOf(false) }
    var hasAutostartPermission by remember {
        mutableStateOf(
            if (!needsAutostartGuidance) true
            else if (autostartCheckSupported) hasAutostartPermission(context)
            else false
        )
    }

    val coroutineScope = rememberCoroutineScope()

    val refreshPermissions = remember(context, autostartCheckSupported, needsAutostartGuidance) {
        {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            hasAccessibilityPermission = hasAccessibilityPermission(context)
            if (needsAutostartGuidance) {
                if (autostartCheckSupported) {
                    hasAutostartPermission = hasAutostartPermission(context)
                } else if (userInteractedAutostart) {
                    hasAutostartPermission = true
                }
            } else {
                hasAutostartPermission = true
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshPermissions()
        // Staggered retries on resume to handle asynchronous OS/Settings permission persistence
        coroutineScope.launch {
            val retryDelays = listOf(150L, 300L, 600L, 1000L, 1500L, 2000L)
            for (d in retryDelays) {
                delay(d)
                refreshPermissions()
            }
        }
    }

    // Active polling while on the Permissions step (Step 3)
    // Ensures immediate detection even with delayed OS sync or without full activity pause/resume
    LaunchedEffect(currentStep) {
        if (currentStep == 3) {
            while (isActive) {
                refreshPermissions()
                delay(500)
            }
        }
    }

    // Can proceed to next step?
    val canProceed = when (currentStep) {
        0 -> true // Welcome — always
        1 -> selectedDailyLimit > 0 // Daily limit must be positive
        2 -> true // Apps — optional, can skip
        3 -> hasAccessibilityPermission && hasOverlayPermission && hasAutostartPermission
        4 -> true // All set — always
        else -> false
    }

    // Full-bleed background image behind everything
    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingBackground()

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                AnimatedVisibility(
                    visible = currentStep in 1..3,
                    enter = fadeIn(tween(400)),
                    exit = fadeOut(tween(400))
                ) {
                    OnboardingTopBar(
                        currentStep = currentStep,
                        totalSteps = 3,
                        onBackClick = { viewModel.previousStep() }
                    )
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = currentStep in 1..3,
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
                val stepPadding = if (step in 1..3) paddingValues else PaddingValues(0.dp)
                Box(modifier = Modifier.fillMaxSize().padding(stepPadding)) {
                    when (step) {
                        0 -> WelcomeStep(onGetStarted = { viewModel.nextStep() })
                        1 -> SetDailyLimitStep(
                            selectedLimit = selectedDailyLimit,
                            isCustom = isCustomLimit,
                            customLimitText = customLimitText,
                            presetValues = viewModel.presetValues,
                            onSelectPresetIndex = { viewModel.selectPresetIndex(it) },
                            onCustomLimitChange = { viewModel.setCustomLimitText(it) }
                        )
                        2 -> AppSelectionStep(
                            viewModel = viewModel
                        )
                        3 -> PermissionsStep(
                            context = context,
                            hasAccessibilityPermission = hasAccessibilityPermission,
                            hasOverlayPermission = hasOverlayPermission,
                            hasAutostartPermission = hasAutostartPermission,
                            showAutostartGuidance = needsAutostartGuidance,
                            onRefreshPermissions = refreshPermissions,
                            onAutostartClick = {
                                userInteractedAutostart = true
                                openAutostartSettings(context)
                            }
                        )
                        4 -> AllSetStep(
                            selectedMode = selectedMode,
                            selectedDailyLimit = selectedDailyLimit,
                            onFinish = {
                                viewModel.completeOnboarding {
                                    onFinish()
                                }
                            }
                        )
                    }
                }
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
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Button(
            onClick = onNextClick,
            enabled = canProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .shadow(
                    elevation = if (canProceed) 16.dp else 0.dp,
                    shape = CircleShape,
                    spotColor = Color(0xFFFF8C00),
                    ambientColor = Color(0xFFFF8C00)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFA000),
                contentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                disabledContentColor = TextMuted
            ),
            shape = CircleShape
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Continue",
                    fontFamily = AppFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canProceed) Color.Black else TextMuted
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (canProceed) Color.Black else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
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

    // Entry animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val iconAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800, delayMillis = 150),
        label = "iconAlpha"
    )
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
                .padding(top = 32.dp, bottom = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1.35f))

            // Watch icon without glow
            Canvas(
                modifier = Modifier
                    .size(82.dp)
                    .graphicsLayer {
                        alpha = iconAlpha
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
            ) {
                val w = size.width
                val h = size.height
                val gradient = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFC021),
                        Color(0xFFFF8800)
                    )
                )

                // Top crown button
                drawRoundRect(
                    brush = gradient,
                    topLeft = Offset(w * 0.36f, h * 0.08f),
                    size = Size(w * 0.28f, h * 0.07f),
                    cornerRadius = CornerRadius(h * 0.035f)
                )

                // Stem
                drawRect(
                    brush = gradient,
                    topLeft = Offset(w * 0.46f, h * 0.14f),
                    size = Size(w * 0.08f, h * 0.06f)
                )

                // 45-degree top-right stopwatch button
                withTransform({
                    rotate(degrees = 42f, pivot = Offset(w * 0.5f, h * 0.55f))
                }) {
                    drawRoundRect(
                        brush = gradient,
                        topLeft = Offset(w * 0.46f, h * 0.11f),
                        size = Size(w * 0.08f, h * 0.08f),
                        cornerRadius = CornerRadius(w * 0.03f)
                    )
                }

                // Main circular body
                drawCircle(
                    brush = gradient,
                    radius = w * 0.36f,
                    center = Offset(w * 0.5f, h * 0.55f)
                )

                // Center vertical slot cutout (transparent)
                drawRoundRect(
                    color = Color.Black,
                    blendMode = BlendMode.Clear,
                    topLeft = Offset(w * 0.465f, h * 0.38f),
                    size = Size(w * 0.07f, h * 0.17f),
                    cornerRadius = CornerRadius(w * 0.035f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color(0xFFFFA000))) {
                        append("Reels")
                    }
                    withStyle(style = SpanStyle(color = Color.White)) {
                        append(" in control")
                    }
                },
                fontFamily = AppFontFamily,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = titleAlpha }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Set a limit. Watch mindfully.\nGet your time back.",
                color = Color.White.copy(alpha = 0.75f),
                fontFamily = AppFontFamily,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.graphicsLayer { alpha = subtitleAlpha }
            )

            Spacer(modifier = Modifier.weight(1.1f))
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Button(
                onClick = handleGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .graphicsLayer { alpha = buttonAlpha }
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        spotColor = Color(0xFFFF8C00),
                        ambientColor = Color(0xFFFF8C00)
                    ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFA000),
                    contentColor = Color.Black
                ),
                shape = CircleShape
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Let's Go",
                        fontFamily = AppFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  STEP 2: SET YOUR DAILY LIMIT
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SetDailyLimitStep(
    selectedLimit: Int,
    isCustom: Boolean,
    customLimitText: String,
    presetValues: List<Int>,
    onSelectPresetIndex: (Int) -> Unit,
    onCustomLimitChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isCustom) {
        if (isCustom) {
            delay(150)
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
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
            text = "Set your daily limit",
            color = TextPrimary,
            fontFamily = AppFontFamily,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "How many reels per day?",
            color = TextSecondary,
            fontFamily = AppFontFamily,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        if (isCustom) {
            BasicTextField(
                value = customLimitText,
                onValueChange = onCustomLimitChange,
                modifier = Modifier.focusRequester(focusRequester),
                textStyle = TextStyle(
                    fontFamily = AppFontFamily,
                    fontSize = 44.sp,
                    color = Amber500,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                singleLine = true,
                cursorBrush = SolidColor(Amber500),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(200.dp)
                            .height(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(
                                width = 2.dp,
                                color = Amber500,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 16.dp)
                    ) {
                        if (customLimitText.isEmpty()) {
                            Text(
                                text = "Type a number",
                                fontFamily = AppFontFamily,
                                fontSize = 16.sp,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                        innerTextField()
                    }
                }
            )
        } else {
            Box(
                modifier = Modifier.height(72.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$selectedLimit",
                    fontFamily = AppFontFamily,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = Amber500,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "reels per day",
            color = TextSecondary,
            fontFamily = AppFontFamily,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        val selectedIndex = if (isCustom) presetValues.size else presetValues.indexOf(selectedLimit).takeIf { it >= 0 } ?: 6

        DailyLimitPicker(
            values = presetValues,
            selectedIndex = selectedIndex,
            onValueSelected = onSelectPresetIndex,
            hasCustom = true,
            onCustomClick = {
                focusRequester.requestFocus()
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DailyLimitPicker(
    values: List<Int>,
    selectedIndex: Int,
    onValueSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    hasCustom: Boolean = true,
    onCustomClick: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val totalCount = values.size + (if (hasCustom) 1 else 0)
    val initialPage = selectedIndex.coerceIn(0, (totalCount - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { totalCount }
    )

    // Sync external selectedIndex updates to pager
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in 0 until totalCount && pagerState.currentPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    // Automatically update selected limit as user scrolls through numbers
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page in 0 until totalCount) {
                    onValueSelected(page)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val chipWidth = 72.dp
        val horizontalPadding = ((maxWidth - chipWidth) / 2).coerceAtLeast(0.dp)

        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(chipWidth),
            pageSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            beyondViewportPageCount = 2,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val scale = 1f + (0.12f * (1f - pageOffset.coerceIn(0f, 1f)))
            val alpha = 0.45f + (0.55f * (1f - pageOffset.coerceIn(0f, 1f)))
            val isSelected = page == pagerState.currentPage

            if (page < values.size) {
                DailyLimitChip(
                    value = values[page],
                    isSelected = isSelected,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(page)
                        }
                    },
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                )
            } else {
                CustomDailyLimitChip(
                    isSelected = isSelected,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(page)
                        }
                        onCustomClick()
                    },
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                )
            }
        }
    }
}

@Composable
private fun DailyLimitChip(
    value: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(72.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) Amber500
                else Color.White.copy(alpha = 0.08f)
            )
            .border(
                width = if (isSelected) 0.dp else 1.dp,
                color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$value",
                fontFamily = AppFontFamily,
                style = if (value >= 1000) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        else MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) Color.Black else TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "reels",
                fontFamily = AppFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Color.Black.copy(alpha = 0.75f) else TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CustomDailyLimitChip(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(72.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) Amber500
                else Color.White.copy(alpha = 0.08f)
            )
            .border(
                width = if (isSelected) 0.dp else 1.dp,
                color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Custom",
            fontFamily = AppFontFamily,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected) Color.Black else TextPrimary,
            textAlign = TextAlign.Center
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  STEP 3: MODE SELECTION
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
//  STEP 4: APP SELECTION
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
//  STEP 5: PERMISSIONS
// ════════════════════════════════════════════════════════════════════

@Composable
private fun PermissionsStep(
    context: Context,
    hasAccessibilityPermission: Boolean,
    hasOverlayPermission: Boolean,
    hasAutostartPermission: Boolean,
    showAutostartGuidance: Boolean,
    onRefreshPermissions: () -> Unit,
    onAutostartClick: () -> Unit
) {
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var accessibilityDismissed by remember { mutableStateOf(false) }
    var hasAttemptedAccessibility by remember { mutableStateOf(false) }
    var hasAttemptedOverlay by remember { mutableStateOf(false) }
    var hasAttemptedAutostart by remember { mutableStateOf(false) }

    var isAppResumed by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isAppResumed = true
        onRefreshPermissions()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        isAppResumed = false
    }

    val launchAccessibility = {
        if (!hasAccessibilityPermission(context)) {
            accessibilityDismissed = false
            hasAttemptedAccessibility = true
            showAccessibilityDialog = true
        }
    }

    val launchOverlay = {
        hasAttemptedOverlay = true
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                context.startActivity(intent)
            } catch (e2: Exception) {
                openAppSettings(context)
            }
        }
    }

    val launchAutostart = {
        hasAttemptedAutostart = true
        onAutostartClick()
    }

    // Auto-launch permissions sequence: hold for half second then launch one by one
    LaunchedEffect(
        isAppResumed,
        hasAccessibilityPermission,
        hasOverlayPermission,
        hasAutostartPermission,
        showAutostartGuidance,
        showAccessibilityDialog,
        accessibilityDismissed
    ) {
        if (!isAppResumed || showAccessibilityDialog) return@LaunchedEffect

        // Hold for half a second before auto-launching
        delay(500)

        if (!isAppResumed || showAccessibilityDialog) return@LaunchedEffect

        if (!hasAccessibilityPermission) {
            if (!hasAttemptedAccessibility && !accessibilityDismissed) {
                launchAccessibility()
            }
        } else if (!hasOverlayPermission) {
            if (!hasAttemptedOverlay) {
                launchOverlay()
            }
        } else if (showAutostartGuidance && !hasAutostartPermission) {
            if (!hasAttemptedAutostart) {
                launchAutostart()
            }
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
            text = "ReelsRot needs these permissions to\ntrack and block apps effectively.",
            color = TextSecondary,
            fontFamily = AppFontFamily,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Accessibility
        if (showAccessibilityDialog) {
            com.flow.shift.core.designsystem.AccessibilityDisclosureDialog(
                onDismiss = {
                    showAccessibilityDialog = false
                    accessibilityDismissed = true
                },
                onAccept = {
                    showAccessibilityDialog = false
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        openAppSettings(context)
                    }
                }
            )
        }

        PermissionCard(
            icon = Icons.Default.Accessibility,
            title = "Accessibility",
            description = "Allows us to detect when you're scrolling Reels or Shorts.",
            isGranted = hasAccessibilityPermission,
            onClick = {
                if (hasAccessibilityPermission(context)) {
                    return@PermissionCard
                }
                launchAccessibility()
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Display Over Other Apps
        PermissionCard(
            icon = Icons.Default.Layers,
            title = "Display Over Other Apps",
            description = "Shows the blocker screen when you try to open a blocked app.",
            isGranted = hasOverlayPermission,
            onClick = {
                if (Settings.canDrawOverlays(context)) {
                    return@PermissionCard
                }
                launchOverlay()
            }
        )

        if (showAutostartGuidance) {
            Spacer(modifier = Modifier.height(14.dp))

            // Autostart
            PermissionCard(
                icon = Icons.Default.Settings,
                title = "Autostart",
                description = "Prevents your phone from stopping the app in the background.",
                isGranted = hasAutostartPermission,
                onClick = {
                    launchAutostart()
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
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
//  STEP 6: ALL SET
// ════════════════════════════════════════════════════════════════════

@Composable
private fun AllSetStep(
    selectedMode: BlockingMode,
    selectedDailyLimit: Int,
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
            text = "You committed to a daily limit of $selectedDailyLimit reels\nusing ${selectedMode.name.lowercase().replaceFirstChar { it.uppercase() }} Mode.",
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
                .height(58.dp)
                .alpha(contentAlpha)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    spotColor = Color(0xFFFF8C00),
                    ambientColor = Color(0xFFFF8C00)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFA000),
                contentColor = Color.Black
            ),
            shape = CircleShape
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Commit & Start",
                    fontFamily = AppFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
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


private fun canCheckAutostart(context: Context): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val brand = Build.BRAND.lowercase()
    if (manufacturer in listOf("xiaomi", "redmi", "poco") || brand in listOf("xiaomi", "redmi", "poco")) {
        return true
    }
    return try {
        AppOpsManager::class.java.getDeclaredField("OP_AUTO_START")
        true
    } catch (e: Exception) {
        false
    }
}

private fun hasAutostartPermission(context: Context): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val brand = Build.BRAND.lowercase()
    val isXiaomiFamily = manufacturer in listOf("xiaomi", "redmi", "poco") || brand in listOf("xiaomi", "redmi", "poco")

    if (!isXiaomiFamily) {
        val isOtherChineseOEM = manufacturer in listOf("oppo", "vivo", "oneplus", "realme", "iqoo", "huawei", "honor") ||
                brand in listOf("oppo", "vivo", "oneplus", "realme", "iqoo", "huawei", "honor")
        return !isOtherChineseOEM
    }

    try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val op = try {
            val field = AppOpsManager::class.java.getDeclaredField("OP_AUTO_START")
            field.getInt(null)
        } catch (e: Exception) {
            10008
        }

        val method = appOps.javaClass.getMethod("checkOpNoThrow", Int::class.java, Int::class.java, String::class.java)
        val mode = method.invoke(appOps, op, Process.myUid(), context.packageName) as Int
        return mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        return false
    }
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

