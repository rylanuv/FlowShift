package com.flow.shift.feature.modes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
fun ModesScreen(
    viewModel: ModesViewModel = hiltViewModel(),
    showBackground: Boolean = false,
    onNavigateToDisciplineModeSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingChange by viewModel.pendingModeChange.collectAsStateWithLifecycle()
    val downgradeWaitRemaining by viewModel.downgradeWaitRemainingSeconds.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (showBackground) {
                    Modifier
                        .paint(
                            painter = painterResource(id = uiState.currentMode.backgroundImageRes),
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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // ── Header ──
            Text(
                text = "Blocking Modes",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose how strictly you want to be blocked",
                color = TextSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Easy Mode Card ──
            ModeCard(
                title = "Easy Mode",
                description = "After the set screen time, hold for 90 seconds before you can proceed scrolling again.",
                icon = Icons.Default.Timer,
                accentColor = ModeEasyAccent,
                bgAlpha = AmberGlow,
                isActive = uiState.currentMode == BlockingMode.EASY,
                onClick = { viewModel.requestModeChange(BlockingMode.EASY) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Discipline Mode Card ──
            ModeCard(
                title = "Discipline Mode",
                description = buildStrictDescription(uiState.strictChallengeType),
                icon = Icons.Default.Psychology,
                accentColor = ModeStrictAccent,
                bgAlpha = ModeStrictBg,
                isActive = uiState.currentMode == BlockingMode.STRICT,
                onClick = { viewModel.requestModeChange(BlockingMode.STRICT) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Hardcore Mode Card ──
            ModeCard(
                title = "Hardcore Mode",
                description = "Once the timer hits, you can't scroll anymore.",
                icon = Icons.Default.Lock,
                accentColor = ModeHardcoreAccent,
                bgAlpha = ModeHardcoreBg,
                isActive = uiState.currentMode == BlockingMode.HARDCORE,
                onClick = { viewModel.requestModeChange(BlockingMode.HARDCORE) }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── Dialogs ──
    pendingChange?.let { change ->
        when (change.type) {
            PendingChangeType.UPGRADE_CONFIRMATION -> {
                UpgradeConfirmationDialog(
                    targetMode = change.targetMode,
                    onNavigateToSettings = {
                        onNavigateToDisciplineModeSettings()
                    },
                    onConfirm = { viewModel.confirmModeChange() },
                    onDismiss = { viewModel.dismissModeChange() }
                )
            }
            PendingChangeType.DOWNGRADE_CONFIRMATION -> {
                DowngradeConfirmationDialog(
                    targetMode = change.targetMode,
                    downgradeWaitRemaining = downgradeWaitRemaining,
                    autoDowngradeAtMidnight = uiState.autoDowngradeAtMidnight,
                    onNavigateToSettings = {
                        onNavigateToDisciplineModeSettings()
                    },
                    onAutoDowngradeChange = { viewModel.setAutoDowngradeAtMidnight(it) },
                    onConfirm = { viewModel.confirmModeChange() },
                    onDismiss = { viewModel.dismissModeChange() }
                )
            }
        }
    }
}

private fun buildStrictDescription(challengeType: String): String {
    return when (challengeType) {
        "MATH" -> "Solve maths to earn scroll time."
        "ADVANCED_MATH" -> "Solve advanced maths to earn scroll time."
        "SQUATS" -> "Perform squats to earn scroll time."
        "CHARGE_PHONE" -> "Charge your phone to earn scroll time."
        else -> "Perform pushups to earn scroll time."
    }
}

// ── Mode Card ──
@Composable
private fun ModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    bgAlpha: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null
) {
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isActive) accentColor else accentColor.copy(alpha = 0.3f),
        animationSpec = tween(300),
        label = "borderColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bgAlpha)
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = animatedBorderColor,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Active badge
            if (isActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ACTIVE",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = description,
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        extraContent?.invoke()
    }
}


// ── Upgrade Confirmation Dialog ──
@Composable
private fun UpgradeConfirmationDialog(
    targetMode: BlockingMode,
    onNavigateToSettings: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(24.dp),
        icon = {
            val color = when (targetMode) {
                BlockingMode.EASY -> ModeEasyAccent
                BlockingMode.STRICT -> ModeStrictAccent
                BlockingMode.HARDCORE -> ModeHardcoreAccent
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (targetMode) {
                        BlockingMode.EASY -> Icons.Default.Timer
                        BlockingMode.STRICT -> Icons.Default.Psychology
                        BlockingMode.HARDCORE -> Icons.Default.Lock
                    },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                text = "Switch to ${targetMode.displayName} Mode?",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column {
                Text(
                    text = getModeDescription(targetMode),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (targetMode == BlockingMode.STRICT) {
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ModeStrictAccent
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ModeStrictAccent.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Configure your challenge",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                // Warning about downgrade cooldown
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Amber700.copy(alpha = 0.15f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Amber500,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Downgrading back will require a 6-hour cooldown period.",
                        color = Amber500,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (targetMode) {
                        BlockingMode.EASY -> ModeEasyAccent
                        BlockingMode.STRICT -> ModeStrictAccent
                        BlockingMode.HARDCORE -> ModeHardcoreAccent
                    }
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "Confirm",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

// ── Downgrade Confirmation Dialog ──
@Composable
private fun DowngradeConfirmationDialog(
    targetMode: BlockingMode,
    downgradeWaitRemaining: Int,
    autoDowngradeAtMidnight: Boolean,
    onNavigateToSettings: () -> Unit,
    onAutoDowngradeChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Downgrade to ${targetMode.displayName} Mode?",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column {
                Text(
                    text = "Are you sure you want to make it easier? You can always switch back to a stricter mode at any time.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                
                if (targetMode == BlockingMode.STRICT) {
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ModeStrictAccent
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ModeStrictAccent.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Configure your challenge",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAutoDowngradeChange(!autoDowngradeAtMidnight) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = autoDowngradeAtMidnight,
                        onCheckedChange = onAutoDowngradeChange,
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Amber500)
                    )
                    Text(
                        text = "Auto downgrade next day",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }

                if (downgradeWaitRemaining > 0) {
                    Spacer(modifier = Modifier.height(20.dp))
                    val hours = downgradeWaitRemaining / 3600
                    val minutes = (downgradeWaitRemaining % 3600) / 60
                    val seconds = downgradeWaitRemaining % 60
                    val timeString = if (hours > 0) {
                        String.format("%dh %02dm %02ds", hours, minutes, seconds)
                    } else if (minutes > 0) {
                        String.format("%dm %02ds", minutes, seconds)
                    } else {
                        String.format("%ds", seconds)
                    }
                    Text(
                        text = "You can downgrade in $timeString...",
                        color = Amber500,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = downgradeWaitRemaining == 0 || autoDowngradeAtMidnight,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (targetMode) {
                        BlockingMode.EASY -> ModeEasyAccent
                        BlockingMode.STRICT -> ModeStrictAccent
                        BlockingMode.HARDCORE -> ModeHardcoreAccent
                    }
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (autoDowngradeAtMidnight) "Downgrade next day" else "Downgrade",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

private fun getModeDescription(mode: BlockingMode): String {
    return when (mode) {
        BlockingMode.EASY -> "After the set screen time, hold for 90 seconds before you can proceed scrolling again."
        BlockingMode.STRICT -> "Perform pushups or solve advanced maths to earn scroll time."
        BlockingMode.HARDCORE -> "Once the timer hits, you can't scroll anymore."
    }
}

