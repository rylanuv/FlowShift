package com.flow.shift.core.cameravision

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import android.util.Size
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.flow.shift.theme.Amber500
import com.flow.shift.theme.AppFontFamily
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    requiredReps: Int,
    currentReps: Int,
    onRepCounted: (Int) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    
    val previewView = remember { androidx.camera.view.PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var currentPose by remember { mutableStateOf<Pose?>(null) }
    var currentImageWidth by remember { mutableStateOf(1) }
    var currentImageHeight by remember { mutableStateOf(1) }
    
    var feedbackText by remember { mutableStateOf("Position yourself in front of the camera") }

    val analyzer = remember { 
        PushupAnalyzer(
            onRepCounted = onRepCounted,
            onFeedbackChanged = { feedbackText = it }
        ) 
    }
    
    val mlKitPoseHelper = remember {
        MlKitPoseHelper(
            onResult = { pose, imageWidth, imageHeight ->
                analyzer.processResult(pose)
                currentPose = pose
                currentImageWidth = imageWidth
                currentImageHeight = imageHeight
            },
            onError = onError
        )
    }

    LaunchedEffect(currentReps) {
        if (currentReps > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        mlKitPoseHelper.detectAsync(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                onError("Camera bind failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            mlKitPoseHelper.close()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        
        val connections = remember {
            listOf(
                PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST, // left arm
                PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST, // right arm
                PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER, // shoulders
                PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP, // torso
                PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP, // hips
                PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE, // left leg
                PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE  // right leg
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            currentPose?.let { pose ->
                val w = size.width
                val h = size.height
                
                val scaleX = w / currentImageWidth.toFloat()
                val scaleY = h / currentImageHeight.toFloat()
                
                val toPixel = { landmark: PoseLandmark ->
                    Offset(
                        x = w - (landmark.position.x * scaleX), // flip X for front camera mirror
                        y = landmark.position.y * scaleY
                    )
                }

                connections.forEach { (startType, endType) ->
                    val startLandmark = pose.getPoseLandmark(startType)
                    val endLandmark = pose.getPoseLandmark(endType)
                    if (startLandmark != null && endLandmark != null &&
                        startLandmark.inFrameLikelihood > 0.5f &&
                        endLandmark.inFrameLikelihood > 0.5f
                    ) {
                        drawLine(
                            color = Color.Green,
                            start = toPixel(startLandmark),
                            end = toPixel(endLandmark),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                }

                pose.allPoseLandmarks
                    .filter { it.inFrameLikelihood > 0.5f }
                    .forEach { landmark ->
                        drawCircle(
                            color = Color.Red,
                            radius = 4.dp.toPx(),
                            center = toPixel(landmark)
                        )
                    }
            }
        }
        
        // HUD Overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(24.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = currentReps.toString(),
                        color = Amber500,
                        fontSize = 56.sp,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = " / $requiredReps",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 24.sp,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (feedbackText.isNotEmpty()) {
                    Text(
                        text = feedbackText,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

