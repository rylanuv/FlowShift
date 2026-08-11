package com.flow.shift.feature.exerciseblocker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.shift.core.progress.ProgressRepository
import com.flow.shift.theme.FlowShiftTheme
import com.flow.shift.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class WaitActivity : ComponentActivity() {

    @Inject
    lateinit var progressRepository: ProgressRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val targetPackage = intent.getStringExtra("TARGET_PACKAGE") ?: "ALL_APPS"
        val waitSeconds = intent.getIntExtra("WAIT_SECONDS", 90)
        val breakDurationMinutes = intent.getIntExtra("BREAK_DURATION_MINUTES", 5)

        setContent {
            FlowShiftTheme {
                MaterialTheme(
                    typography = Typography,
                    colorScheme = MaterialTheme.colorScheme.copy(
                        background = SurfaceBlack
                    )
                ) {
                    WaitScreen(
                        targetApp = targetPackage,
                        waitSeconds = waitSeconds,
                        breakDurationMinutes = breakDurationMinutes,
                        onFinish = {
                            lifecycleScope.launch {
                                progressRepository.recordUnlock(
                                    targetPackage = targetPackage,
                                    repsCompleted = 0,
                                    durationUnlockedMillis = TimeUnit.MINUTES.toMillis(breakDurationMinutes.toLong())
                                )
                                finish()
                            }
                        },
                        onCancel = {
                            finishAffinity()
                        }
                    )
                }
            }
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intercept back
    }
}

@Composable
fun WaitScreen(
    targetApp: String,
    waitSeconds: Int,
    breakDurationMinutes: Int,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    var timeLeft by remember { mutableIntStateOf(waitSeconds) }
    var isHolding by remember { mutableStateOf(false) }
    var finishCalled by remember { mutableStateOf(false) }

    LaunchedEffect(isHolding) {
        while (isHolding && timeLeft > 0) {
            delay(1000)
            timeLeft -= 1
        }
        if (timeLeft <= 0 && !finishCalled) {
            finishCalled = true
            onFinish()
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (waitSeconds > 0) timeLeft.toFloat() / waitSeconds.toFloat() else 0f,
        animationSpec = tween(1000),
        label = "wait_progress"
    )

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
                text = "Taking a Break",
                color = TextSecondary,
                fontSize = 16.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Hold steady.",
                color = TextPrimary,
                fontSize = 40.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Timer display
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(if (isHolding) ModeEasyAccent.copy(alpha = 0.12f) else SurfaceCard)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isHolding = true
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    isHolding = false
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = ModeEasyAccent,
                    strokeWidth = 8.dp,
                    trackColor = SurfaceCardLight
                )
                Text(
                    text = "${timeLeft}s",
                    color = ModeEasyAccent,
                    fontSize = 36.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = if (isHolding) {
                    "Keep holding to earn $breakDurationMinutes minutes of scrolling time."
                } else {
                    "Hold the timer for $waitSeconds seconds to earn $breakDurationMinutes minutes of scrolling time."
                },
                color = TextSecondary,
                fontSize = 16.sp,
                fontFamily = AppFontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            TextButton(onClick = onCancel) {
                Text(
                    text = "Cancel & Close App",
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
