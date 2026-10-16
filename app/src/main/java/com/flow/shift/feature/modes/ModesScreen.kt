package com.flow.shift.feature.modes

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.shift.theme.*
import kotlin.math.roundToInt

private val GlassBorderBrush = Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.10f)))

data class ChallengeTypeInfo(
    val type: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isPremium: Boolean,
    val requiresCamera: Boolean = false
)

@Composable
fun ModesScreen(
    viewModel: ModesViewModel = hiltViewModel(),
    showBackground: Boolean = false,
    onNavigateToEasyModeSettings: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingChange by viewModel.pendingModeChange.collectAsStateWithLifecycle()
    val downgradeWaitRemaining by viewModel.downgradeWaitRemainingSeconds.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()

    val strictChallengeType by viewModel.strictChallengeType.collectAsStateWithLifecycle()
    val strictChallengeAmount by viewModel.strictChallengeAmount.collectAsStateWithLifecycle()
    val strictChallengeDifficulty by viewModel.strictChallengeDifficulty.collectAsStateWithLifecycle()
    val advancedMathTopics by viewModel.advancedMathTopics.collectAsStateWithLifecycle()
    val breakDurationMinutes by viewModel.breakDurationMinutes.collectAsStateWithLifecycle()
    val easyWaitSeconds by viewModel.easyModeWaitSeconds.collectAsStateWithLifecycle()
    val activeBreakUntilMillis by viewModel.activeBreakUntilMillis.collectAsStateWithLifecycle()

    var localChallengeAmount by remember(strictChallengeType, strictChallengeAmount) { mutableIntStateOf(strictChallengeAmount) }
    var localBreakDuration by remember(breakDurationMinutes) { mutableIntStateOf(breakDurationMinutes) }

    val context = LocalContext.current
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeBreakUntilMillis) {
        while (activeBreakUntilMillis > System.currentTimeMillis()) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
        currentTime = System.currentTimeMillis()
    }
    val isBreakActive = activeBreakUntilMillis > currentTime
    val remainingSeconds = if (isBreakActive) (activeBreakUntilMillis - currentTime) / 1000 else 0L
    val remainingTimeFormatted = if (remainingSeconds >= 60) {
        "${remainingSeconds / 60}m ${remainingSeconds % 60}s"
    } else {
        "${remainingSeconds}s"
    }
    var pendingCameraChallenge by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                pendingCameraChallenge?.let { viewModel.setStrictChallengeType(it) }
            } else {
                Toast.makeText(context, "Camera permission is required to track reps using AI.", Toast.LENGTH_LONG).show()
            }
            pendingCameraChallenge = null
        }
    )

    val challengeOptions = remember {
        listOf(
            ChallengeTypeInfo(
                type = "NONE",
                title = "No Challenge",
                description = "Instant unlock",
                icon = Icons.Default.CheckCircle,
                isPremium = false
            ),
            ChallengeTypeInfo(
                type = "HOLD_SCREEN",
                title = "Hold Screen",
                description = "Hold to unlock",
                icon = Icons.Default.TouchApp,
                isPremium = false
            ),
            ChallengeTypeInfo(
                type = "MATH",
                title = "Maths",
                description = "Solve sums",
                icon = Icons.Default.Calculate,
                isPremium = false
            ),
            ChallengeTypeInfo(
                type = "ADVANCED_MATH",
                title = "Advanced Maths",
                description = "For Maths Lovers",
                icon = Icons.Default.Psychology,
                isPremium = true
            ),
            ChallengeTypeInfo(
                type = "PUSHUPS",
                title = "Push-ups",
                description = "Do Push-ups",
                icon = Icons.Default.FitnessCenter,
                isPremium = true,
                requiresCamera = true
            ),
            ChallengeTypeInfo(
                type = "SQUATS",
                title = "Squats",
                description = "Do Squats",
                icon = Icons.Default.Accessibility,
                isPremium = true,
                requiresCamera = true
            ),
            ChallengeTypeInfo(
                type = "CHARGE_PHONE",
                title = "Charge Phone",
                description = "Plug in",
                icon = Icons.Default.BatteryChargingFull,
                isPremium = true
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (showBackground) {
                    Modifier
                        .paint(
                            painter = painterResource(id = uiState.currentMode.backgroundImageRes),
                            contentScale = ContentScale.Crop,
                            alpha = uiState.currentMode.backgroundAlpha
                        )
                        .background(SurfaceBlack.copy(alpha = 0.65f))
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
                text = "Block type",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose your mode and configure your challenges",
                color = TextSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section 1: Modes ──
            Text(
                text = "BLOCKING MODE",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))



            // Challenge Mode Card
            ModeCard(
                title = "Challenge Mode",
                description = "Complete challenges to earn scroll time",
                icon = Icons.Default.Psychology,
                accentColor = ModeStrictAccent,
                bgAlpha = ModeStrictBg,
                isActive = uiState.currentMode == BlockingMode.STRICT,
                onClick = { viewModel.requestModeChange(BlockingMode.STRICT) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Hardcore Mode Card
            ModeCard(
                title = "Hardcore Mode",
                description = "Once limits are reached, strictly no breaks are allowed.",
                icon = Icons.Default.Lock,
                accentColor = ModeHardcoreAccent,
                bgAlpha = ModeHardcoreBg,
                isActive = uiState.currentMode == BlockingMode.HARDCORE,
                onClick = { viewModel.requestModeChange(BlockingMode.HARDCORE) }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Section 2: Challenge Configuration ──
            if (uiState.currentMode != BlockingMode.HARDCORE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Text(
                    text = "CHALLENGE CONFIGURATION",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )

            }

            Spacer(modifier = Modifier.height(12.dp))

            // Challenge Setup
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Challenge Type",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select what you must complete to earn break time",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                // Challenge Selection List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    challengeOptions.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { item ->
                                val isSelected = strictChallengeType == item.type
                                
                                Box(modifier = Modifier.weight(1f)) {
                                    ChallengeOptionCard(
                                        item = item,
                                        isSelected = isSelected,
                                        isUserPremium = isPremium,
                                        onClick = {
                                            if (item.isPremium && !isPremium) {
                                                onNavigateToSubscription()
                                            } else if (item.requiresCamera) {
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                    viewModel.setStrictChallengeType(item.type)
                                                } else {
                                                    pendingCameraChallenge = item.type
                                                    cameraLauncher.launch(Manifest.permission.CAMERA)
                                                }
                                            } else {
                                                viewModel.setStrictChallengeType(item.type)
                                            }
                                        }
                                    )
                                }
                            }
                            
                            if (rowItems.size < 3) {
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(16.dp))

                // ── Parameters for Current Challenge ──

                // Difficulty (for Math & Advanced Math)
                if (strictChallengeType == "MATH" || strictChallengeType == "ADVANCED_MATH") {
                    Text(
                        text = "Difficulty Level",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val difficulties = listOf("Easy", "Medium", "Hard", "Extreme")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        difficulties.forEach { diff ->
                            val isDiffSelected = strictChallengeDifficulty.equals(diff, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(if (isDiffSelected) ModeStrictAccent else Color.Transparent)
                                    .clickable { viewModel.setStrictChallengeDifficulty(diff) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = diff,
                                    color = if (isDiffSelected) Color.Black else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isDiffSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Topics (for Advanced Math)
                if (strictChallengeType == "ADVANCED_MATH") {
                    Text(
                        text = "Math Topics",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Choose topics included in problem generation",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    val topics = listOf(
                        "RANDOM" to "Random",
                        "POLYNOMIAL" to "Polynomial",
                        "GEOMETRY" to "Geometry",
                        "TRIGONOMETRY" to "Trigonometry",
                        "CALCULUS" to "Calculus",
                        "MATRIX" to "Matrix",
                        "LOGARITHM" to "Logarithm",
                        "PROBABILITY" to "Probability",
                        "SEQUENCE" to "Sequence",
                        "VECTOR" to "Vector"
                    )

                    topics.chunked(2).forEach { rowTopics ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowTopics.forEach { (topicId, topicName) ->
                                val isTopicSelected = advancedMathTopics.contains(topicId)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(CircleShape)
                                        .background(if (isTopicSelected) ModeStrictAccent else Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, if (isTopicSelected) ModeStrictAccent else Color.White.copy(alpha = 0.15f), CircleShape)
                                        .clickable { viewModel.toggleAdvancedMathTopic(topicId) }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = topicName,
                                        color = if (isTopicSelected) Color.Black else TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isTopicSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                            if (rowTopics.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (strictChallengeType != "NONE") {
                    // Challenge Target / Amount
                    val amountUnit = when (strictChallengeType) {
                        "HOLD_SCREEN" -> if (strictChallengeAmount == 1) "second" else "seconds"
                        "CHARGE_PHONE" -> if (strictChallengeAmount == 1) "minute" else "minutes"
                        "MATH", "ADVANCED_MATH" -> if (strictChallengeAmount == 1) "problem" else "problems"
                        "PUSHUPS", "SQUATS" -> if (strictChallengeAmount == 1) "rep" else "reps"
                        else -> if (strictChallengeAmount == 1) "rep" else "reps"
                    }

                    val maxRange = when (strictChallengeType) {
                        "HOLD_SCREEN" -> 90f
                        "CHARGE_PHONE" -> 60f
                        "MATH", "ADVANCED_MATH" -> 30f
                        "PUSHUPS", "SQUATS" -> 100f
                        else -> 50f
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (strictChallengeType) {
                                "CHARGE_PHONE" -> "Charging Duration"
                                "HOLD_SCREEN" -> "Hold Duration"
                                else -> "Challenge Amount"
                            },
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$localChallengeAmount $amountUnit",
                            color = ModeStrictAccent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val minRange = if (strictChallengeType == "MATH" || strictChallengeType == "ADVANCED_MATH") 1f else 5f
                    val stepSize = if (strictChallengeType == "MATH" || strictChallengeType == "ADVANCED_MATH") 1f else 5f
                    val sliderSteps = (((maxRange - minRange) / stepSize).roundToInt() - 1).coerceAtLeast(0)

                    Slider(
                        value = localChallengeAmount.toFloat().coerceIn(minRange, maxRange),
                        onValueChange = { rawVal ->
                            val snapped = (((rawVal - minRange) / stepSize).roundToInt() * stepSize + minRange).roundToInt().coerceIn(minRange.toInt(), maxRange.toInt())
                            if (localChallengeAmount != snapped) {
                                localChallengeAmount = snapped
                            }
                        },
                        onValueChangeFinished = {
                            viewModel.setStrictChallengeAmount(localChallengeAmount)
                        },
                        valueRange = minRange..maxRange,
                        steps = sliderSteps,
                        colors = SliderDefaults.colors(
                            thumbColor = ModeStrictAccent,
                            activeTrackColor = ModeStrictAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )

                    // Quick presets for amount
                    val presets = when (strictChallengeType) {
                        "HOLD_SCREEN" -> listOf(15, 30, 60, 90)
                        "CHARGE_PHONE" -> listOf(5, 10, 15, 30)
                        "MATH", "ADVANCED_MATH" -> listOf(3, 5, 10, 15)
                        "PUSHUPS", "SQUATS" -> listOf(10, 25, 50, 100)
                        else -> listOf(5, 10, 15, 20)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { presetVal ->
                            val isPresetActive = localChallengeAmount == presetVal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(if (isPresetActive) ModeStrictAccent else Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, if (isPresetActive) ModeStrictAccent else Color.White.copy(alpha = 0.15f), CircleShape)
                                    .clickable {
                                        localChallengeAmount = presetVal
                                        viewModel.setStrictChallengeAmount(presetVal)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$presetVal",
                                    color = if (isPresetActive) Color.Black else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isPresetActive) FontWeight.Bold else FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Break Duration Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Break Duration Earned",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$localBreakDuration min",
                        color = ModeStrictAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Time apps remain unblocked after completing challenge",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                val breakMin = 5f
                val breakMax = 60f
                val breakStep = 5f
                val breakSteps = (((breakMax - breakMin) / breakStep).roundToInt() - 1).coerceAtLeast(0)

                Slider(
                    value = localBreakDuration.toFloat().coerceIn(breakMin, breakMax),
                    onValueChange = { rawVal ->
                        val snapped = (((rawVal - breakMin) / breakStep).roundToInt() * breakStep + breakMin).roundToInt().coerceIn(breakMin.toInt(), breakMax.toInt())
                        if (localBreakDuration != snapped) {
                            localBreakDuration = snapped
                        }
                    },
                    onValueChangeFinished = {
                        viewModel.setBreakDurationMinutes(localBreakDuration)
                    },
                    valueRange = breakMin..breakMax,
                    steps = breakSteps,
                    colors = SliderDefaults.colors(
                        thumbColor = ModeStrictAccent,
                        activeTrackColor = ModeStrictAccent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )

                val breakPresets = listOf(5, 15, 30, 60)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    breakPresets.forEach { minutes ->
                        val isPresetActive = localBreakDuration == minutes
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(if (isPresetActive) ModeStrictAccent else Color.White.copy(alpha = 0.08f))
                                .border(1.dp, if (isPresetActive) ModeStrictAccent else Color.White.copy(alpha = 0.15f), CircleShape)
                                .clickable {
                                    localBreakDuration = minutes
                                    viewModel.setBreakDurationMinutes(minutes)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${minutes}min",
                                color = if (isPresetActive) Color.Black else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isPresetActive) FontWeight.Bold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            } // End of Challenge Configuration visibility check

            Spacer(modifier = Modifier.height(36.dp))
        }
    }

    // ── Dialogs ──
    pendingChange?.let { change ->
        when (change.type) {
            PendingChangeType.UPGRADE_CONFIRMATION -> {
                UpgradeConfirmationDialog(
                    targetMode = change.targetMode,
                    onConfirm = { viewModel.confirmModeChange() },
                    onDismiss = { viewModel.dismissModeChange() }
                )
            }
            PendingChangeType.DOWNGRADE_CONFIRMATION -> {
                DowngradeConfirmationDialog(
                    targetMode = change.targetMode,
                    downgradeWaitRemaining = downgradeWaitRemaining,
                    autoDowngradeAtMidnight = uiState.autoDowngradeAtMidnight,
                    onAutoDowngradeChange = { viewModel.setAutoDowngradeAtMidnight(it) },
                    onConfirm = { viewModel.confirmModeChange() },
                    onDismiss = { viewModel.dismissModeChange() }
                )
            }
        }
    }
}

// ── Challenge Option Card Component ──
@Composable
private fun ChallengeOptionCard(
    item: ChallengeTypeInfo,
    isSelected: Boolean,
    isUserPremium: Boolean,
    onClick: () -> Unit
) {
    val itemColor = when(item.type) {
        "MATH" -> Color(0xFF42A5F5)
        "ADVANCED_MATH" -> Color(0xFFEC407A)
        "PUSHUPS" -> Color(0xFFFFA726)
        "SQUATS" -> Color(0xFF66BB6A)
        "CHARGE_PHONE" -> Color(0xFF4CAF50)
        "HOLD_SCREEN" -> Color(0xFFAB47BC)
        "NONE" -> Color(0xFF81C784)
        else -> Color(0xFFFFCA28)
    }

    val bgColor = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.08f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .then(
                if (isSelected) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            // Circle with colored outline and icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, itemColor.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = itemColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Lock or Selected indicator on bottom right
            if (item.isPremium && !isUserPremium) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(SurfaceCard),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(10.dp)
                    )
                }
            } else if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(SurfaceCard),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(ModeStrictAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.Black,
                            modifier = Modifier.size(9.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = item.description,
            color = TextSecondary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

private fun buildStrictDescription(challengeType: String): String {
    return when (challengeType) {
        "MATH" -> "Solve maths to earn scroll time."
        "ADVANCED_MATH" -> "Solve advanced maths to earn scroll time."
        "SQUATS" -> "Perform squats to earn scroll time."
        "CHARGE_PHONE" -> "Charge your phone to earn scroll time."
        "HOLD_SCREEN" -> "Hold the screen to earn scroll time."
        "NONE" -> "Earn scroll time without completing a challenge."
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
    onClick: () -> Unit
) {
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isActive) accentColor else accentColor.copy(alpha = 0.45f),
        animationSpec = tween(300),
        label = "borderColor"
    )
    val animatedBgColor by animateColorAsState(
        targetValue = if (isActive) bgAlpha else accentColor.copy(alpha = 0.08f),
        animationSpec = tween(300),
        label = "bgColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(animatedBgColor)
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = animatedBorderColor,
                shape = RoundedCornerShape(24.dp)
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
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) accentColor.copy(alpha = 0.15f) else accentColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) accentColor else accentColor.copy(alpha = 0.75f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isActive) TextPrimary else TextSecondary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Active badge
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = description,
            color = if (isActive) TextSecondary else TextSecondary.copy(alpha = 0.85f),
            fontSize = 13.5.sp,
            lineHeight = 19.sp
        )
    }
}

// ── Upgrade Confirmation Dialog ──
@Composable
private fun UpgradeConfirmationDialog(
    targetMode: BlockingMode,
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

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAutoDowngradeChange(!autoDowngradeAtMidnight) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = autoDowngradeAtMidnight,
                        onCheckedChange = onAutoDowngradeChange,
                        colors = CheckboxDefaults.colors(checkedColor = ModeStrictAccent)
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
                        color = ModeStrictAccent,
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
        BlockingMode.EASY -> "A lighter blocking mode without mandatory challenges."
        BlockingMode.STRICT -> "Perform pushups or solve advanced maths to earn scroll time."
        BlockingMode.HARDCORE -> "Once the timer hits, you can't scroll anymore."
    }
}


