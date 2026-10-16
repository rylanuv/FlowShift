package com.flow.shift.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    onNavigateToAccount: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    showBackground: Boolean = false
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val blockingModeStr by viewModel.blockingMode.collectAsStateWithLifecycle()
    val currentMode = com.flow.shift.feature.modes.BlockingMode.fromString(blockingModeStr)
    val modeAccent = when (currentMode) {
        com.flow.shift.feature.modes.BlockingMode.EASY -> ModeEasyAccent
        com.flow.shift.feature.modes.BlockingMode.STRICT -> ModeStrictAccent
        com.flow.shift.feature.modes.BlockingMode.HARDCORE -> ModeHardcoreAccent
    }
    
    var showTargetReelsDialog by remember { mutableStateOf(false) }
    var showLockTargetDialog by remember { mutableStateOf(false) }

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
    val authUser by viewModel.authUser.collectAsStateWithLifecycle()
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (showBackground) {
                    Modifier
                        .paint(
                            painter = painterResource(id = currentMode.backgroundImageRes),
                            contentScale = ContentScale.Crop,
                            alpha = currentMode.backgroundAlpha
                        )
                        .background(SurfaceBlack.copy(alpha = 0.35f))
                } else {
                    Modifier.background(Color.Transparent)
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // ── Header ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                if (showBackground) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, GlassBorderBrush, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Column {
                    Text(
                        text = "Settings",
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
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

            Spacer(modifier = Modifier.height(12.dp))

        // ── Advanced Protection ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Shield,
            title = "ADVANCED PROTECTION",
            subtitle = "Keep you locked in",
            accentColor = modeAccent
        ) {
            SettingsToggleRow(
                icon = Icons.Default.Delete,
                label = "Prevent App Uninstall",
                checked = state.protection.preventAppUninstall,
                accentColor = modeAccent
            ) { isChecked ->
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
                checked = state.protection.blockSettingsAccess,
                accentColor = modeAccent
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
                accentColor = modeAccent,
                onCheckedChange = { viewModel.setPreventDisablingFocusMode(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Block Type ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Block,
            title = "BLOCK TYPE",
            subtitle = "What to block in apps",
            accentColor = modeAccent
        ) {
            SegmentedControl(
                options = listOf(
                    SegmentOption(
                        text = "Reels Only",
                        icon = Icons.Default.VideoLibrary,
                        isLocked = !isPremium
                    ),
                    SegmentOption(
                        text = "Whole App",
                        icon = Icons.Default.Apps,
                        isLocked = false
                    )
                ),
                selectedOption = if (state.blockType == "REELS") "Reels Only" else "Whole App",
                activeColor = modeAccent,
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

        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Target Reels Amount ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.PlayCircle,
            title = "TARGET REELS AMOUNT",
            subtitle = "Your daily goal",
            accentColor = modeAccent
        ) {
            SettingsNavRow(
                icon = Icons.Default.Flag,
                label = "Daily Target",
                value = "${state.dailyReelLimit} reels",
                valueColor = modeAccent,
                onClick = {
                    if (System.currentTimeMillis() < state.targetScreenTimeLockedUntil && !state.bypassTargetLock) {
                        Toast.makeText(context, "Target is locked and cannot be adjusted", Toast.LENGTH_SHORT).show()
                    } else {
                        showTargetReelsDialog = true
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
                accentColor = modeAccent,
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

        Spacer(modifier = Modifier.height(16.dp))

        // ── Troubleshooting ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Build,
            title = "TROUBLESHOOTING",
            subtitle = "Fix app issues",
            accentColor = modeAccent
        ) {
            SettingsNavRow(
                icon = Icons.Default.BugReport,
                label = "Check Permissions",
                onClick = onNavigateToTroubleshoot
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Premium ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.WorkspacePremium,
            title = "PREMIUM",
            subtitle = if (isPremium) "Your Pro membership is active" else "Unlock your full potential",
            accentColor = modeAccent
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
                                    modeAccent.copy(alpha = 0.12f),
                                    SurfaceCard.copy(alpha = 0.40f)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    SurfaceCard.copy(alpha = 0.5f),
                                    SurfaceCard.copy(alpha = 0.25f)
                                )
                            )
                        }
                    )
                    .border(
                        1.dp,
                        if (isPremium) SolidColor(modeAccent.copy(alpha = 0.35f)) else GlassBorderBrush,
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
                                if (isPremium) modeAccent.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                1.dp,
                                if (isPremium) modeAccent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPremium) Icons.Default.WorkspacePremium else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isPremium) modeAccent else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (isPremium) "ReelsRot Pro" else "Free Plan",
                            color = if (isPremium) modeAccent else TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = AppFontFamily
                        )
                        if (!isPremium) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(TextMuted.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tap to Upgrade",
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
                                    colors = listOf(modeAccent, modeAccent.copy(alpha = 0.8f))
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

            if (!isPremium) {
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
                        text = "Upgrade to Pro",
                        color = modeAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = AppFontFamily
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = modeAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Support ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Favorite,
            title = "SUPPORT",
            subtitle = "Get help and support us",
            accentColor = modeAccent
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
            Spacer(modifier = Modifier.height(12.dp))
            SettingsNavRow(
                icon = Icons.Default.Email,
                label = "Send Feedback",
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:rylpix.app@gmail.com")
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "ReelsRot Feedback")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsNavRow(
                icon = Icons.Default.Share,
                label = "Share with friends",
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "ReelsRot App")
                        putExtra(android.content.Intent.EXTRA_TEXT, "Check out ReelsRot, the best app to track and block reels! https://play.google.com/store/apps/details?id=${context.packageName}")
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Share via"))
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Account / Google Sign-In ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.AccountCircle,
            title = "ACCOUNT",
            subtitle = if (authUser != null) "Manage your account" else "Backup and sync your progress",
            accentColor = modeAccent
        ) {
            if (authUser != null) {
                val displayName = authUser?.displayName?.takeIf { it.isNotBlank() } ?: "Google User"
                val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "G"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCardLight.copy(alpha = 0.5f))
                        .border(1.dp, GlassBorderBrush, RoundedCornerShape(12.dp))
                        .clickable { onNavigateToAccount() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(modeAccent, modeAccent.copy(alpha = 0.6f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            color = Color.Black,
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = displayName,
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        painter = painterResource(id = R.drawable.ic_google_logo),
                        contentDescription = "Google",
                        modifier = Modifier.size(18.dp),
                        tint = Color.Unspecified
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Account Settings",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Text(
                    text = "Sign in with Google to securely save your stats, streaks, and custom settings.",
                    color = TextSecondary,
                    fontFamily = AppFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (!isSigningIn) {
                            viewModel.signInWithGoogle(context) { success, error ->
                                if (success) {
                                    Toast.makeText(context, "Signed in successfully!", Toast.LENGTH_SHORT).show()
                                } else if (!error.isNullOrBlank()) {
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceCardLight.copy(alpha = 0.6f),
                        contentColor = TextPrimary
                    ),
                    border = BorderStroke(1.dp, GlassBorderBrush),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    ),
                    enabled = !isSigningIn
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = modeAccent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Signing in...",
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google_logo),
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(18.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sign in with Google",
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
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

        
        if (showTargetReelsDialog) {
            TargetReelsDialog(
                currentLimit = state.dailyReelLimit,
                accentColor = modeAccent,
                onDismiss = { showTargetReelsDialog = false },
                onConfirm = { limit ->
                    viewModel.setDailyReelLimit(limit)
                    showTargetReelsDialog = false
                }
            )
        }
        
        if (showLockTargetDialog) {
            LockTargetDurationDialog(
                accentColor = modeAccent,
                onDismiss = { showLockTargetDialog = false },
                onConfirm = { durationMillis ->
                    viewModel.setTargetScreenTimeLockedUntil(durationMillis)
                    showLockTargetDialog = false
                }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetReelsDialog(
    currentLimit: Int,
    accentColor: Color = ModeStrictAccent,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val presetValues = listOf(10, 25, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1000)
    val initialIndex = presetValues.indexOf(currentLimit).takeIf { it >= 0 }
        ?: presetValues.indexOf(presetValues.minByOrNull { kotlin.math.abs(it - currentLimit) } ?: 200)

    var sliderValue by remember { mutableFloatStateOf(initialIndex.toFloat()) }
    var isCustom by remember { mutableStateOf(currentLimit !in presetValues) }
    var customLimitText by remember { mutableStateOf(if (currentLimit !in presetValues) currentLimit.toString() else "") }

    val index = sliderValue.roundToInt().coerceIn(0, presetValues.size - 1)
    val fraction = index / (presetValues.size - 1).toFloat()
    val selectedReels = if (isCustom) {
        customLimitText.toIntOrNull() ?: currentLimit
    } else {
        presetValues[index]
    }

    // Colors
    val sliderStartColor = accentColor
    val sliderMidColor = accentColor
    val sliderEndColor = Color(0xFFFFFFFF)

    val currentColor = if (fraction <= 0.5f) {
        val f = fraction / 0.5f
        androidx.compose.ui.graphics.lerp(sliderStartColor, sliderMidColor, f)
    } else {
        val f = (fraction - 0.5f) / 0.5f
        androidx.compose.ui.graphics.lerp(sliderMidColor, sliderEndColor, f)
    }

    var prevReels by remember { mutableIntStateOf(selectedReels) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(selectedReels) {
        val increased = selectedReels > prevReels
        prevReels = selectedReels
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
                
                // Reels display
                val currentTextSize = (36f + (fraction * 12f)).sp
                Box(
                    modifier = Modifier
                        .height(84.dp)
                        .scale(scale.value),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$selectedReels",
                            color = currentColor,
                            fontFamily = AppFontFamily,
                            fontSize = currentTextSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedReels == 1) "reel per day" else "reels per day",
                            color = TextSecondary,
                            fontFamily = AppFontFamily,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Reel markers
                androidx.compose.ui.layout.Layout(
                    content = {
                        Text("10", color = TextMuted, fontFamily = AppFontFamily, fontSize = 12.sp)
                        Text("100", color = TextMuted, fontFamily = AppFontFamily, fontSize = 12.sp)
                        Text("250", color = TextMuted, fontFamily = AppFontFamily, fontSize = 12.sp)
                        Text("500", color = TextMuted, fontFamily = AppFontFamily, fontSize = 12.sp)
                        Text("1k", color = TextMuted, fontFamily = AppFontFamily, fontSize = 12.sp)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                ) { measurables, constraints ->
                    val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
                    layout(constraints.maxWidth, placeables.maxOf { it.height }) {
                        val width = constraints.maxWidth
                        val fractions = listOf(0f, 4f / 12f, 7f / 12f, 10f / 12f, 1f)
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
                if (!isCustom) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..12f,
                        steps = 11,
                        modifier = Modifier.fillMaxWidth(),
                        colors = sliderColors,
                    track = { sliderState ->
                        val trackFraction = (sliderState.value / 12f).coerceIn(0f, 1f)
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
                                                colors = listOf(sliderStartColor, currentColor)
                                            )
                                        )
                                )
                            }
                        }
                    }
                )
                } else {
                    OutlinedTextField(
                        value = customLimitText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 5) {
                                customLimitText = input
                            }
                        },
                        label = { Text("Custom reels amount") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        isCustom = !isCustom
                        if (!isCustom) {
                            val parsed = customLimitText.toIntOrNull() ?: 200
                            val closestIdx = presetValues.indexOf(presetValues.minByOrNull { kotlin.math.abs(it - parsed) } ?: 200)
                            sliderValue = closestIdx.toFloat()
                        } else {
                            customLimitText = selectedReels.toString()
                        }
                    }
                ) {
                    Text(
                        text = if (isCustom) "Use presets slider" else "Enter custom amount",
                        color = accentColor,
                        fontSize = 13.sp,
                        fontFamily = AppFontFamily
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalLimit = if (isCustom) {
                        customLimitText.toIntOrNull()?.coerceAtLeast(1) ?: selectedReels
                    } else {
                        selectedReels
                    }
                    onConfirm(finalLimit)
                }
            ) {
                Text("Save", color = accentColor, fontFamily = AppFontFamily)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, fontFamily = AppFontFamily)
            }
        },
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockTargetDurationDialog(
    accentColor: Color = ModeStrictAccent,
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
                            colors = RadioButtonDefaults.colors(selectedColor = accentColor, unselectedColor = TextMuted)
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
                    System.currentTimeMillis() + selectedOption * 24L * 60L * 1000L * 60L
                }
                onConfirm(durationMillis)
            }) {
                Text("Save", color = accentColor, fontFamily = AppFontFamily)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, fontFamily = AppFontFamily)
            }
        },
        containerColor = SurfaceCard,
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
    accentColor: Color = ModeStrictAccent,
    titleColor: Color = accentColor,
    iconColor: Color = accentColor,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard.copy(alpha = 0.35f))
            .border(1.dp, GlassBorderBrush, RoundedCornerShape(20.dp))
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
private fun ProBadge(
    modifier: Modifier = Modifier,
    accentColor: Color = ModeStrictAccent
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(accentColor.copy(alpha = 0.15f))
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.5.dp)
    ) {
        Text(
            text = "PRO",
            color = accentColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector?,
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    isLocked: Boolean = false,
    accentColor: Color = ModeStrictAccent,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    color = TextPrimary,
                    fontFamily = AppFontFamily,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isLocked) {
                    Spacer(modifier = Modifier.width(6.dp))
                    ProBadge(accentColor = accentColor)
                }
            }
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
                checkedTrackColor = accentColor,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceCardDarker,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

private data class SegmentOption(
    val text: String,
    val icon: ImageVector,
    val isLocked: Boolean = false
)

@Composable
private fun SegmentedControl(
    options: List<SegmentOption>,
    selectedOption: String,
    activeColor: Color = ModeStrictAccent,
    onOptionSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard.copy(alpha = 0.25f))
            .border(1.dp, GlassBorderBrush, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        options.forEach { option ->
            val isSelected = selectedOption == option.text
            val backgroundColor = if (isSelected) SurfaceCard.copy(alpha = 0.5f) else Color.Transparent
            val contentColor = if (isSelected) activeColor else TextSecondary
            val borderModifier = if (isSelected) Modifier.border(1.dp, GlassBorderBrush, RoundedCornerShape(12.dp)) else Modifier

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .then(borderModifier)
                    .clickable { onOptionSelected(option.text) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = option.text,
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = AppFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (option.isLocked) {
                    Spacer(modifier = Modifier.width(5.dp))
                    ProBadge(accentColor = activeColor)
                }
            }
        }
    }
}

@Composable
internal fun SettingsNavRow(
    icon: ImageVector? = null,
    label: String,
    value: String? = null,
    valueColor: Color = ModeStrictAccent,
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
    activeColor: Color = ModeStrictAccent,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SurfaceCard.copy(alpha = 0.5f) else SurfaceCard.copy(alpha = 0.2f))
            .border(
                1.dp,
                if (selected) SolidColor(activeColor) else GlassBorderBrush,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = activeColor,
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
                tint = if (selected) activeColor else TextMuted,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                color = TextPrimary,
                fontFamily = AppFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 12.sp
            )
        }
    }
}

