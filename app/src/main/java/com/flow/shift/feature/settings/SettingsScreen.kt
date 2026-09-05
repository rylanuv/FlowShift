package com.flow.shift.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.shift.theme.*
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.flow.shift.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import androidx.compose.ui.layout.layout
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import java.util.Calendar
import androidx.compose.ui.zIndex

@Composable
fun SettingsScreen(
    onNavigateToAppSelection: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateToTroubleshoot: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    showBackground: Boolean = false
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val blockingModeStr by viewModel.blockingMode.collectAsStateWithLifecycle()
    val currentMode = com.flow.shift.feature.modes.BlockingMode.fromString(blockingModeStr)
    
    var showTargetScreenTimeDialog by remember { mutableStateOf(false) }
    var showLockTargetDialog by remember { mutableStateOf(false) }
    var showReelCountExplainerDialog by remember { mutableStateOf(false) }

    val protectedAppCount = state.blockedApps.size
    val enabledProtectionSignals = listOf(
        state.protection.strictModeOn,
        state.protection.preventAppUninstall,
        state.protection.lockDuringFocus,
        state.breakRules.requireChallengeForBreak,
        state.breakRules.autoResumeProtection,
        protectedAppCount > 0
    ).count { it }
    val protectionScore = (enabledProtectionSignals * 100 / 6).coerceIn(0, 100)

    val context = androidx.compose.ui.platform.LocalContext.current
    val devicePolicyManager = remember { context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager }
    val componentName = remember { android.content.ComponentName(context, com.flow.shift.core.receivers.AppDeviceAdminReceiver::class.java) }

    val adminLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.setPreventAppUninstall(true)
        } else {
            // User denied or cancelled
            viewModel.setPreventAppUninstall(false)
        }
    }

    var showBlockSettingsWarning by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (showBackground) {
                    Modifier
                        .paint(
                            painter = painterResource(id = currentMode.backgroundImageRes),
                            contentScale = ContentScale.Crop,
                            alpha = 0.9f
                        )
                        .background(SurfaceBlack.copy(alpha = 0.25f))
                } else {
                    Modifier.background(Color.Transparent)
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // ── Header ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column {
                    Text(
                        text = "Settings",
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Customize your protection and experience",
                        color = TextSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = 14.sp
                    )
                }
            }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Advanced Protection ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Shield,
            title = "ADVANCED PROTECTION",
            subtitle = "Keep you locked in"
        ) {
            SettingsToggleRow(icon = Icons.Default.Delete, label = "Prevent App Uninstall", checked = state.protection.preventAppUninstall) { isChecked ->
                if (isChecked) {
                    if (!devicePolicyManager.isAdminActive(componentName)) {
                        val intent = android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                            putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "We need this permission to prevent the app from being uninstalled during focus sessions.")
                        }
                        adminLauncher.launch(intent)
                    } else {
                        viewModel.setPreventAppUninstall(true)
                    }
                } else {
                    devicePolicyManager.removeActiveAdmin(componentName)
                    viewModel.setPreventAppUninstall(false)
                }
            }
            SettingsToggleRow(
                icon = Icons.Default.Settings,
                label = "Block Settings Access",
                checked = state.protection.blockSettingsAccess
            ) { isChecked ->
                if (isChecked) {
                    showBlockSettingsWarning = true
                } else {
                    viewModel.setBlockSettingsAccess(false)
                }
            }
            SettingsToggleRow(
                icon = Icons.Default.Lock,
                label = "Prevent Disabling Focus Mode",
                checked = state.protection.preventDisablingFocusMode,
                subtitle = "Cannot stop Focus Mode once started",
                onCheckedChange = { viewModel.setPreventDisablingFocusMode(it) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Block Type ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Block,
            title = "BLOCK TYPE",
            subtitle = "What to block in apps"
        ) {
            SegmentedControl(
                options = listOf("Reels Only" to if (isPremium) Icons.Default.VideoLibrary else Icons.Default.Lock, "Whole App" to Icons.Default.Apps),
                selectedOption = if (state.blockType == "REELS") "Reels Only" else "Whole App",
                onOptionSelected = { 
                    if (it == "Reels Only") {
                        if (isPremium) {
                            viewModel.setBlockType("REELS")
                        } else {
                            onNavigateToSubscription()
                        }
                    } else {
                        viewModel.setBlockType("WHOLE_APP")
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            SettingsToggleRow(
                icon = Icons.Default.Pin,
                label = "Show Reel Count",
                checked = state.appearance.showReelCount,
                subtitle = "Display reel count on top of screen",
                onCheckedChange = { isChecked -> 
                    if (isChecked) {
                        showReelCountExplainerDialog = true
                    } else {
                        viewModel.setShowReelCount(false)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Target Screen Time ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Timer,
            title = "TARGET SCREEN TIME",
            subtitle = "Your daily goal"
        ) {
            SettingsNavRow(
                icon = Icons.Default.Flag,
                label = "Daily Target",
                value = state.targetScreenTime,
                valueColor = Amber500,
                onClick = {
                    if (System.currentTimeMillis() < state.targetScreenTimeLockedUntil && !state.bypassTargetLock) {
                        Toast.makeText(context, "Target is locked and cannot be adjusted", Toast.LENGTH_SHORT).show()
                    } else {
                        showTargetScreenTimeDialog = true
                    }
                }
            )
            SettingsToggleRow(
                icon = Icons.Default.Lock,
                label = "Lock Target",
                checked = state.targetScreenTimeLockedUntil > System.currentTimeMillis(),
                subtitle = if (state.targetScreenTimeLockedUntil > System.currentTimeMillis()) {
                    if (state.targetScreenTimeLockedUntil == Long.MAX_VALUE) "Locked forever"
                    else "Locked until ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(state.targetScreenTimeLockedUntil))}"
                } else "Prevent adjusting daily target",
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        showLockTargetDialog = true
                    } else {
                        if (System.currentTimeMillis() < state.targetScreenTimeLockedUntil && !state.bypassTargetLock) {
                            Toast.makeText(context, "Cannot unlock before the time expires", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.setTargetScreenTimeLockedUntil(0L)
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Troubleshooting ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Build,
            title = "TROUBLESHOOTING",
            subtitle = "Fix app issues"
        ) {
            SettingsNavRow(
                icon = Icons.Default.BugReport,
                label = "Check Permissions",
                onClick = onNavigateToTroubleshoot
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Premium ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.WorkspacePremium,
            title = "PREMIUM",
            subtitle = if (isPremium) "Your Pro membership is active" else "Unlock your full potential",
            titleColor = if (isPremium) PremiumGold else Amber500,
            iconColor = if (isPremium) PremiumGold else Amber500
        ) {
            // Subscription status card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = if (isPremium) {
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF2C220B), // Warm rich gold tint
                                    Color(0xFF141005)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    SurfaceCardLight,
                                    SurfaceCardDarker
                                )
                            )
                        }
                    )
                    .border(
                        1.dp,
                        if (isPremium) PremiumGold.copy(alpha = 0.5f) else SurfaceCardBorder,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(
                        enabled = !isPremium,
                        onClick = onNavigateToSubscription
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPremium) PremiumGold.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                1.dp,
                                if (isPremium) PremiumGold.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPremium) Icons.Default.WorkspacePremium else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isPremium) PremiumGold else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (isPremium) "FlowShift Pro" else "Free Plan",
                            color = if (isPremium) PremiumGold else TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = AppFontFamily
                        )
                        if (!isPremium) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 5.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(TextMuted.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Not Subscribed • Tap to Upgrade",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = AppFontFamily,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isPremium) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(StatusGreen.copy(alpha = 0.15f))
                            .border(1.dp, StatusGreen.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "ACTIVE",
                                color = StatusGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                fontFamily = AppFontFamily
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Amber500, Color(0xFFFBBF24))
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "UPGRADE",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp,
                                fontFamily = AppFontFamily
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Manage Subscription
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onNavigateToSubscription
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPremium) "Manage Subscription" else "Upgrade to Pro",
                    color = Amber500,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Amber500,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Support ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Favorite,
            title = "SUPPORT",
            subtitle = "Get help and support us"
        ) {
            SettingsNavRow(
                icon = Icons.Default.Star,
                label = "Rate our app",
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${context.packageName}"))
                    try {
                        context.startActivity(intent)
                    } catch (e: android.content.ActivityNotFoundException) {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            SettingsNavRow(
                icon = Icons.Default.Email,
                label = "Send Feedback",
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:rylpix.app@gmail.com")
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "FlowShift Feedback")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            SettingsNavRow(
                icon = Icons.Default.Share,
                label = "Share with friends",
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "FlowShift App")
                        putExtra(android.content.Intent.EXTRA_TEXT, "Check out FlowShift, the best app to stop doomscrolling! https://play.google.com/store/apps/details?id=${context.packageName}")
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Share via"))
                }
            )
        }
    }

        if (showBlockSettingsWarning) {
            AlertDialog(
                onDismissRequest = { showBlockSettingsWarning = false },
                containerColor = SurfaceCard,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                title = { Text("Block Settings Access") },
                text = { Text("After turning this on you will be locked out of settings. To access them again, you will have to wait 24 hours after clicking on the settings button.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.setBlockSettingsAccess(true)
                            showBlockSettingsWarning = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                    ) {
                        Text("Turn On")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showBlockSettingsWarning = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        if (showReelCountExplainerDialog) {
            ReelCountExplainerDialog(
                onDismiss = { showReelCountExplainerDialog = false },
                onEnableClick = {
                    if (isPremium) {
                        viewModel.setShowReelCount(true)
                        showReelCountExplainerDialog = false
                    } else {
                        Toast.makeText(context, "Available for Premium users only", Toast.LENGTH_SHORT).show()
                        onNavigateToSubscription()
                        showReelCountExplainerDialog = false
                    }
                }
            )
        }
        
        if (showTargetScreenTimeDialog) {
            TargetScreenTimeDialog(
                currentValue = state.targetScreenTime,
                onDismiss = { showTargetScreenTimeDialog = false },
                onConfirm = { time ->
                    viewModel.setTargetScreenTime(time)
                    showTargetScreenTimeDialog = false
                }
            )
        }
        
        if (showLockTargetDialog) {
            LockTargetDurationDialog(
                onDismiss = { showLockTargetDialog = false },
                onConfirm = { durationMillis ->
                    viewModel.setTargetScreenTimeLockedUntil(durationMillis)
                    showLockTargetDialog = false
                }
            )
        }
    }
}

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

private fun parseTargetScreenTimeToSliderValue(time: String): Float {
    if (time == "0m") return 0f
    
    var h = 0
    var m = 0
    val parts = time.split(" ")
    for (part in parts) {
        if (part.endsWith("h")) h = part.dropLast(1).toIntOrNull() ?: 0
        if (part.endsWith("m")) m = part.dropLast(1).toIntOrNull() ?: 0
    }
    
    val totalMinutes = h * 60 + m
    return if (totalMinutes <= 360) {
        (totalMinutes / 15f)
    } else {
        24f + ((totalMinutes - 360) / 30f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetScreenTimeDialog(
    currentValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val initialValue = parseTargetScreenTimeToSliderValue(currentValue)
    var sliderValue by remember { mutableFloatStateOf(initialValue) }
    
    val index = sliderValue.roundToInt().coerceIn(0, 36)
    val fraction = index / 36f
    val formattedTime = formatTargetScreenTime(sliderValue)

    // Colors
    val sliderStartColor = Color(0xFF00E5FF)
    val sliderMidColor = Color(0xFF99F2FF)
    val sliderEndColor = Color(0xFFFFFFFF)

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily Target", fontFamily = AppFontFamily) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Hour display
                val currentTextSize = (32f + (fraction * 16f)).sp
                Box(
                    modifier = Modifier
                        .height(80.dp)
                        .scale(scale.value),
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

                Spacer(modifier = Modifier.height(32.dp))

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
                        placeables.forEachIndexed { i, placeable ->
                            val x = (width * fractions[i] - placeable.width / 2f).roundToInt()
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(formattedTime) }) {
                Text("Save", color = Amber500, fontFamily = AppFontFamily)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, fontFamily = AppFontFamily)
            }
        },
        containerColor = SurfaceBlack,
        titleContentColor = TextPrimary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockTargetDurationDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val options = listOf(
        "1 day" to 1,
        "3 days" to 3,
        "1 week" to 7,
        "1 month" to 30,
        "6 months" to 180,
        "1 year" to 365,
        "Forever" to -1
    )
    var selectedOption by remember { mutableStateOf(options.first().second) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock Target", fontFamily = AppFontFamily) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Select how long to lock your daily target. You will not be able to adjust it until this time expires.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = AppFontFamily
                )
                Spacer(modifier = Modifier.height(16.dp))
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = value }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == value,
                            onClick = { selectedOption = value },
                            colors = RadioButtonDefaults.colors(selectedColor = Amber500, unselectedColor = TextMuted)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = TextPrimary,
                            fontFamily = AppFontFamily,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val durationMillis = if (selectedOption == -1) {
                    Long.MAX_VALUE
                } else {
                    System.currentTimeMillis() + selectedOption * 24L * 60L * 60L * 1000L
                }
                onConfirm(durationMillis)
            }) {
                Text("Save", color = Amber500, fontFamily = AppFontFamily)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, fontFamily = AppFontFamily)
            }
        },
        containerColor = SurfaceBlack,
        titleContentColor = TextPrimary
    )
}

// ═══════════════════════════════════════════
// ── Reusable Composables ──
// ═══════════════════════════════════════════

@Composable
private fun SettingsSection(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: Color = Amber500,
    iconColor: Color = Amber500,
    backgroundColor: Color = SurfaceCard,
    borderColor: Color = SurfaceCardBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard.copy(alpha = 0.7f)) // Glassy look
            .border(1.dp, SurfaceCardBorder.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        // Section header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
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
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    color = titleColor,
                    fontFamily = AppFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontFamily = AppFontFamily,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        content()
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector?,
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TextPrimary,
                fontFamily = AppFontFamily,
                fontSize = 16.sp,
                lineHeight = 20.sp
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontFamily = AppFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.75f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Amber500,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceCardDarker,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SegmentedControl(
    options: List<Pair<String, ImageVector>>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCardDarker)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        options.forEach { (text, icon) ->
            val isSelected = selectedOption == text
            val backgroundColor = if (isSelected) SurfaceCard else Color.Transparent
            val contentColor = if (isSelected) Amber500 else TextSecondary
            val borderModifier = if (isSelected) Modifier.border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp)) else Modifier

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .then(borderModifier)
                    .clickable { onOptionSelected(text) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    color = contentColor,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector? = null,
    label: String,
    value: String? = null,
    valueColor: Color = Amber500,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = TextPrimary,
            fontFamily = AppFontFamily,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
            lineHeight = 20.sp
        )
        if (value != null) {
            Text(
                text = value,
                color = valueColor,
                fontFamily = AppFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}



@Composable
private fun ChallengeCard(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SurfaceCardLight else SurfaceCardDarker)
            .border(
                1.dp,
                if (selected) Amber500 else SurfaceCardBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Amber500,
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.TopEnd)
                    .offset((-4).dp, 4.dp)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Amber500 else TextMuted,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
private fun ReelCountExplainerDialog(
    onDismiss: () -> Unit,
    onEnableClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("Reel Count Badge", fontFamily = AppFontFamily) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Visual explainer
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(color = SurfaceCardDarker, shape = RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    // Badge
                    Box(
                        modifier = Modifier
                            .background(color = DangerRed, shape = CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .zIndex(1f)
                    ) {
                        Text("142", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    // A simple rect to represent a reel in the app
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp, end = 16.dp)
                            .background(color = SurfaceCard, shape = RoundedCornerShape(8.dp))
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "A persistent badge will appear on your screen showing exactly how many reels or shorts you've scrolled through. This helps break the trance by bringing your awareness back to the present.",
                    fontFamily = AppFontFamily,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onEnableClick,
                colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = Color.White)
            ) {
                Text("Enable")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
            ) {
                Text("Cancel")
            }
        }
    )
}
