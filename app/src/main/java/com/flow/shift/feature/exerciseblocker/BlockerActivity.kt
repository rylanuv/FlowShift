package com.flow.shift.feature.exerciseblocker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.shift.theme.FlowShiftTheme
import android.content.Context
import android.content.pm.PackageManager
import com.flow.shift.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

fun getAppLabel(context: Context, packageName: String): String {
    if (packageName == "ALL_APPS") return "all apps"
    return try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}

@AndroidEntryPoint
class BlockerActivity : ComponentActivity() {

    private val viewModel: BlockerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val targetPackage = intent.getStringExtra("TARGET_PACKAGE") ?: "Unknown App"
        val requiredReps = intent.getIntExtra("REQUIRED_REPS", 15)
        val breakDurationMinutes = intent.getIntExtra("BREAK_DURATION_MINUTES", 5)
        val challengeType = intent.getStringExtra("CHALLENGE_TYPE") ?: "PUSHUPS"
        val isHardcoreLock = intent.getBooleanExtra("IS_HARDCORE_LOCK", false)
        val intentDifficulty = intent.getStringExtra("CHALLENGE_DIFFICULTY")

        setContent {
            FlowShiftTheme {
                MaterialTheme(
                    typography = Typography,
                    colorScheme = MaterialTheme.colorScheme.copy(
                        background = SurfaceBlack
                    )
                ) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val currentReps by viewModel.currentReps.collectAsStateWithLifecycle()
                    val savedDifficulty by viewModel.challengeDifficulty.collectAsStateWithLifecycle()
                    val currentDifficulty = intentDifficulty ?: savedDifficulty
                    val advancedMathTopics by viewModel.advancedMathTopics.collectAsStateWithLifecycle()

                    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
                    val effectiveChallengeType = remember(challengeType, isPremium) {
                        if (!isPremium && viewModel.isPremiumChallenge(challengeType)) {
                            viewModel.fallbackChallenge(challengeType)
                        } else {
                            challengeType
                        }
                    }

                    if (isHardcoreLock) {
                        HardcoreLockedScreen(
                            targetApp = targetPackage,
                            onCloseApp = { finishAffinity() }
                        )
                    } else {
                        AnimatedContent(
                            targetState = uiState,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                            },
                            label = "blocker_transitions"
                        ) { state ->
                            when (state) {
                                is BlockerUiState.Intro -> {
                                    BlockerIntroScreen(
                                        targetApp = targetPackage,
                                        challengeType = effectiveChallengeType,
                                        requiredAmount = requiredReps,
                                        breakDurationMinutes = breakDurationMinutes,
                                        onStartExercise = { viewModel.startExercise() },
                                        onCancel = {
                                            finishAffinity()
                                        }
                                    )
                                }
                                is BlockerUiState.Exercising -> {
                                    if (effectiveChallengeType == "ADVANCED_MATH") {
                                        AdvancedMathChallengeScreen(
                                            requiredProblems = requiredReps,
                                            completedProblems = currentReps,
                                            difficulty = currentDifficulty,
                                            topics = advancedMathTopics,
                                            onProblemSolved = { solved ->
                                                if (solved >= requiredReps) {
                                                    viewModel.completeManualChallenge(targetPackage, requiredReps, breakDurationMinutes)
                                                } else {
                                                    viewModel.updateReps(solved, requiredReps, targetPackage, breakDurationMinutes)
                                                }
                                            }
                                        )
                                    } else if (effectiveChallengeType == "MATH") {
                                        MathChallengeScreen(
                                            requiredProblems = requiredReps,
                                            completedProblems = currentReps,
                                            difficulty = currentDifficulty,
                                            onProblemSolved = { solved ->
                                                if (solved >= requiredReps) {
                                                    viewModel.completeManualChallenge(targetPackage, requiredReps, breakDurationMinutes)
                                                } else {
                                                    viewModel.updateReps(solved, requiredReps, targetPackage, breakDurationMinutes)
                                                }
                                            }
                                        )
                                    } else if (effectiveChallengeType == "SQUATS") {
                                        SquatChallengeScreen(
                                            targetSquats = requiredReps,
                                            currentSquatsInitial = currentReps,
                                            onSquatDetected = { reps ->
                                                viewModel.updateReps(reps, requiredReps, targetPackage, breakDurationMinutes) 
                                            },
                                            onSuccess = {
                                            }
                                        )
                                    } else if (effectiveChallengeType == "CHARGE_PHONE") {
                                        ChargePhoneChallengeScreen(
                                            requiredMinutes = requiredReps,
                                            onComplete = {
                                                viewModel.completeManualChallenge(targetPackage, requiredReps, breakDurationMinutes)
                                            },
                                            onCancel = {
                                                finishAffinity()
                                            }
                                        )
                                    } else {
                                        PushUpChallengeScreen(
                                            targetPushUps = requiredReps,
                                            currentPushUpsInitial = currentReps,
                                            onPushUpDetected = { reps ->
                                                viewModel.updateReps(reps, requiredReps, targetPackage, breakDurationMinutes) 
                                            },
                                            onSuccess = {
                                            }
                                        )
                                    }
                                }
                                is BlockerUiState.Success -> {
                                    BlockerSuccessScreen(
                                        targetApp = targetPackage,
                                        requiredAmount = requiredReps,
                                        challengeType = challengeType,
                                        onFinish = {
                                            finish()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intercept back to prevent easy bypass, 
        // they should use the explicitly designed Bailout button
    }
}

@Composable
fun BlockerIntroScreen(
    targetApp: String,
    challengeType: String,
    requiredAmount: Int,
    breakDurationMinutes: Int,
    onStartExercise: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val challengeName = when (challengeType) {
        "MATH" -> "math problems"
        "ADVANCED_MATH" -> "advanced math problems"
        "SQUATS" -> "squats"
        "CHARGE_PHONE" -> "phone charge"
        else -> "push-ups"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCardDarker), // Deep moody background
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Scroll Limit Reached",
                color = TextSecondary,
                fontSize = 16.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Time to move.",
                color = TextPrimary,
                fontSize = 40.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            val appDisplayName = getAppLabel(context, targetApp)
            val durationText = if (requiredAmount == 1) "1 minute" else "$requiredAmount minutes"
            Text(
                text = if (challengeType == "CHARGE_PHONE") {
                    "Earn $breakDurationMinutes minutes of scrolling in $appDisplayName by charging your phone for $durationText."
                } else {
                    "Earn $breakDurationMinutes minutes of scrolling in $appDisplayName by completing $requiredAmount $challengeName."
                },
                color = TextSecondary,
                fontSize = 18.sp,
                fontFamily = AppFontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            Button(
                onClick = onStartExercise,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber500)
            ) {
                Text(
                    text = when (challengeType) {
                        "MATH", "ADVANCED_MATH" -> "Start $requiredAmount Problems"
                        "SQUATS" -> "Start $requiredAmount Squats"
                        "CHARGE_PHONE" -> if (requiredAmount == 1) "Start 1 Min Charge" else "Start $requiredAmount Min Charge"
                        else -> "Start $requiredAmount Push-ups"
                    },
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            TextButton(onClick = onCancel) {
                Text(
                    text = "I'm in public / Cancel & Close App",
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun BlockerSuccessScreen(
    targetApp: String,
    requiredAmount: Int,
    challengeType: String,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        delay(2000) // Show celebration for 2 seconds
        onFinish()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StatusGreen.copy(alpha = 0.15f)), // Subtle green success glow
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(StatusGreen, shape = RoundedCornerShape(40.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = when (challengeType) {
                    "MATH", "ADVANCED_MATH" -> "$requiredAmount Problems Completed!"
                    "SQUATS" -> "$requiredAmount Squats Completed!"
                    "CHARGE_PHONE" -> if (requiredAmount == 1) "Charged for 1 Minute!" else "Charged for $requiredAmount Minutes!"
                    else -> "$requiredAmount Push-ups Completed!"
                },
                color = TextPrimary,
                fontSize = 24.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val appDisplayName = getAppLabel(context, targetApp)
            Text(
                text = "Unlocking $appDisplayName...",
                color = TextSecondary,
                fontSize = 16.sp,
                fontFamily = AppFontFamily
            )
        }
    }
}

@Composable
fun MathChallengeScreen(
    requiredProblems: Int,
    completedProblems: Int,
    difficulty: String = "Medium",
    onProblemSolved: (Int) -> Unit
) {
    val sessionSeed = remember { System.currentTimeMillis() }
    val problemIndex = completedProblems.coerceAtMost((requiredProblems - 1).coerceAtLeast(0))
    val problem = remember(problemIndex, difficulty) {
        MathProblemGenerator.generate(difficulty, problemIndex, sessionSeed)
    }
    var answer by remember(problemIndex) { mutableStateOf("") }
    var error by remember(problemIndex) { mutableStateOf(false) }
    var isSubmitting by remember(problemIndex) { mutableStateOf(false) }

    val submitAnswer = {
        if (completedProblems < requiredProblems && !isSubmitting) {
            if (answer.toIntOrNull() == problem.answer) {
                isSubmitting = true
                onProblemSolved(completedProblems + 1)
            } else {
                error = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCardDarker),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${completedProblems.coerceAtMost(requiredProblems)} / $requiredProblems",
                    color = Amber500,
                    fontSize = 16.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "•",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
                Text(
                    text = difficulty.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    color = ModeStrictAccent,
                    fontSize = 14.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = problem.expression,
                color = TextPrimary,
                fontSize = if (problem.expression.length > 20) 24.sp else if (problem.expression.length > 12) 32.sp else 42.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = answer,
                onValueChange = {
                    if (!isSubmitting && completedProblems < requiredProblems) {
                        answer = it.filter(Char::isDigit)
                        error = false
                    }
                },
                enabled = completedProblems < requiredProblems && !isSubmitting,
                isError = error,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { submitAnswer() }
                ),
                label = { Text(if (error) "Incorrect, try again" else "Answer") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = if (error) DangerRed else Amber500,
                    unfocusedBorderColor = if (error) DangerRed else Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = if (error) DangerRed else Amber500,
                    errorBorderColor = DangerRed,
                    errorLabelColor = DangerRed
                )
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { submitAnswer() },
                enabled = completedProblems < requiredProblems && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber500,
                    disabledContainerColor = Amber500.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "Submit",
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AdvancedMathChallengeScreen(
    requiredProblems: Int,
    completedProblems: Int,
    difficulty: String = "Medium",
    topics: Set<String>,
    onProblemSolved: (Int) -> Unit
) {
    val sessionSeed = remember { System.currentTimeMillis() }
    val problemIndex = completedProblems.coerceAtMost((requiredProblems - 1).coerceAtLeast(0))
    val problem = remember(problemIndex, difficulty, topics) {
        val mathDifficulty = try {
            MathDifficulty.valueOf(difficulty.trim().uppercase())
        } catch (e: Exception) {
            MathDifficulty.MEDIUM
        }
        val advancedMathTopics = if (topics.contains("RANDOM") || topics.isEmpty()) {
            AdvancedMathTopic.values().toSet()
        } else {
            topics.mapNotNull {
                try { AdvancedMathTopic.valueOf(it) } catch (e: Exception) { null }
            }.toSet().ifEmpty { AdvancedMathTopic.values().toSet() }
        }
        
        // Ensure random uses seed combined with problem index so it's consistent if recomposed
        // AdvancedMathProblemGenerator uses kotlin.random.Random
        AdvancedMathProblemGenerator.generateProblem(advancedMathTopics, mathDifficulty)
    }
    var answer by remember(problemIndex) { mutableStateOf("") }
    var error by remember(problemIndex) { mutableStateOf(false) }
    var isSubmitting by remember(problemIndex) { mutableStateOf(false) }

    val submitAnswer = {
        if (completedProblems < requiredProblems && !isSubmitting) {
            if (answer.trim() == problem.answer.trim()) {
                isSubmitting = true
                onProblemSolved(completedProblems + 1)
            } else {
                error = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCardDarker),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${completedProblems.coerceAtMost(requiredProblems)} / $requiredProblems",
                    color = Amber500,
                    fontSize = 16.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "•",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
                Text(
                    text = difficulty.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + " Advanced",
                    color = ModeStrictAccent,
                    fontSize = 14.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = problem.question,
                color = TextPrimary,
                fontSize = if (problem.question.length > 20) 24.sp else 32.sp,
                fontFamily = if (problem.topic == AdvancedMathTopic.MATRIX) FontFamily.Monospace else AppFontFamily,
                fontWeight = if (problem.topic == AdvancedMathTopic.MATRIX) FontWeight.Bold else FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = answer,
                onValueChange = {
                    if (!isSubmitting && completedProblems < requiredProblems) {
                        // allow negative sign and digits for advanced math
                        answer = it.filter { char -> char.isDigit() || char == '-' || char == '.' }
                        error = false
                    }
                },
                enabled = completedProblems < requiredProblems && !isSubmitting,
                isError = error,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { submitAnswer() }
                ),
                label = { Text(if (error) problem.hint.ifEmpty { "Incorrect, try again" } else "Answer") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = if (error) DangerRed else Amber500,
                    unfocusedBorderColor = if (error) DangerRed else Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = if (error) DangerRed else Amber500,
                    errorBorderColor = DangerRed,
                    errorLabelColor = DangerRed
                )
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { submitAnswer() },
                enabled = completedProblems < requiredProblems && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber500,
                    disabledContainerColor = Amber500.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "Submit",
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun HardcoreLockedScreen(
    targetApp: String,
    onCloseApp: () -> Unit
) {
    val context = LocalContext.current
    val appDisplayName = getAppLabel(context, targetApp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCardDarker),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Hardcore Mode",
                color = ModeHardcoreAccent,
                fontSize = 16.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No breaks available.",
                color = TextPrimary,
                fontSize = 36.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (targetApp == "ALL_APPS") "All apps are blocked for the rest of today." else "$appDisplayName is blocked for the rest of today.",
                color = TextSecondary,
                fontSize = 17.sp,
                fontFamily = AppFontFamily,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
            Spacer(modifier = Modifier.height(36.dp))
            Button(
                onClick = onCloseApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ModeHardcoreAccent)
            ) {
                Text(
                    text = "Close App",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
