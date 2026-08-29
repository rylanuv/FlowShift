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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.shift.core.cameravision.CameraScreen
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
                                        challengeType = challengeType,
                                        requiredAmount = requiredReps,
                                        breakDurationMinutes = breakDurationMinutes,
                                        onStartExercise = { viewModel.startExercise() },
                                        onCancel = {
                                            finishAffinity()
                                        }
                                    )
                                }
                                is BlockerUiState.Exercising -> {
                                    if (challengeType == "MATH") {
                                        MathChallengeScreen(
                                            requiredProblems = requiredReps,
                                            completedProblems = currentReps,
                                            onProblemSolved = { solved ->
                                                if (solved >= requiredReps) {
                                                    viewModel.completeManualChallenge(targetPackage, requiredReps, breakDurationMinutes)
                                                } else {
                                                    viewModel.updateReps(solved, requiredReps, targetPackage, breakDurationMinutes)
                                                }
                                            }
                                        )
                                    } else {
                                        CameraScreen(
                                            requiredReps = requiredReps,
                                            currentReps = currentReps,
                                            onRepCounted = { reps ->
                                                viewModel.updateReps(reps, requiredReps, targetPackage, breakDurationMinutes)
                                            },
                                            onError = {
                                                // Camera errors leave the user on the challenge screen.
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
            Text(
                text = if (challengeType == "CHARGE_PHONE") "Earn $breakDurationMinutes minutes of scrolling in $appDisplayName by charging your phone." else "Earn $breakDurationMinutes minutes of scrolling in $appDisplayName by completing $requiredAmount $challengeName.",
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
                        "CHARGE_PHONE" -> "Start Charging Phone"
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
                text = if (challengeType == "MATH") "$requiredAmount Problems Completed!" else "$requiredAmount Push-ups Completed!",
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
    onProblemSolved: (Int) -> Unit
) {
    val first = (completedProblems * 7 + 13) % 41 + 9
    val second = (completedProblems * 5 + 17) % 37 + 8
    var answer by remember(completedProblems) { mutableStateOf("") }
    var error by remember(completedProblems) { mutableStateOf(false) }

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
                text = "${completedProblems.coerceAtMost(requiredProblems)} / $requiredProblems",
                color = Amber500,
                fontSize = 16.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$first + $second",
                color = TextPrimary,
                fontSize = 44.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = answer,
                onValueChange = {
                    answer = it.filter(Char::isDigit)
                    error = false
                },
                isError = error,
                singleLine = true,
                label = { Text("Answer") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Amber500,
                    focusedLabelColor = Amber500
                )
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (answer.toIntOrNull() == first + second) {
                        onProblemSolved(completedProblems + 1)
                    } else {
                        error = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber500)
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
