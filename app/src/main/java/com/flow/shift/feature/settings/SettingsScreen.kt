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

@Composable
fun SettingsScreen(
    onNavigateToAppSelection: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateToTroubleshoot: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    showBackground: Boolean = false
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val blockingModeStr by viewModel.blockingMode.collectAsStateWithLifecycle()
    val currentMode = com.flow.shift.feature.modes.BlockingMode.fromString(blockingModeStr)

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (showBackground) {
                    Modifier
                        .paint(
                            painter = painterResource(id = currentMode.backgroundImageRes),
                            contentScale = ContentScale.Crop
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
            SettingsToggleRow(icon = Icons.Default.Settings, label = "Block Settings Access", checked = state.protection.blockSettingsAccess) { viewModel.setBlockSettingsAccess(it) }
            SettingsToggleRow(icon = Icons.Default.Lock, label = "Lock During Focus", checked = state.protection.lockDuringFocus) { viewModel.setLockDuringFocus(it) }
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

        // ── Block Type ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Block,
            title = "BLOCK TYPE",
            subtitle = "What to block in apps"
        ) {
            SegmentedControl(
                options = listOf("Reels Only" to Icons.Default.VideoLibrary, "Whole App" to Icons.Default.Apps),
                selectedOption = if (state.blockType == "REELS") "Reels Only" else "Whole App",
                onOptionSelected = { if (it == "Reels Only") viewModel.setBlockType("REELS") else viewModel.setBlockType("WHOLE_APP") }
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
                valueColor = Amber500
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Premium ──
        SettingsSection(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.WorkspacePremium,
            title = "PREMIUM",
            subtitle = "Unlock your full potential",
            titleColor = PremiumGold,
            iconColor = PremiumGold
        ) {
            // Premium badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2C220B), // Dark gold tint
                                Color(0xFF141005)
                            )
                        )
                    )
                    .border(1.dp, PremiumGold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FlowShift Pro",
                        color = PremiumGold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Lifetime Access",
                        color = PremiumGold.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(PremiumGold.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "UNLOCKED",
                        color = PremiumGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
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
                    text = "Manage Subscription",
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
    }
    }
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





