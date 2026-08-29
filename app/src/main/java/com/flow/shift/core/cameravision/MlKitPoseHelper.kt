package com.flow.shift.core.cameravision

import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

class MlKitPoseHelper(
    private val onResult: (Pose, Int, Int) -> Unit,
    private val onError: (String) -> Unit
) {
    private val poseDetector: PoseDetector
    @Volatile
    private var isProcessingFrame = false
    private var lastFrameStartedAtMillis = 0L

    init {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun detectAsync(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (isProcessingFrame || now - lastFrameStartedAtMillis < MIN_FRAME_INTERVAL_MS) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            isProcessingFrame = true
            lastFrameStartedAtMillis = now
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            try {
                poseDetector.process(image)
                    .addOnSuccessListener { pose ->
                        onResult(pose, image.width, image.height)
                    }
                    .addOnFailureListener { e ->
                        onError(e.message ?: "Unknown error")
                    }
                    .addOnCompleteListener {
                        isProcessingFrame = false
                        imageProxy.close()
                    }
            } catch (e: Exception) {
                isProcessingFrame = false
                imageProxy.close()
                onError(e.message ?: "Unknown error")
            }
        } else {
            imageProxy.close()
        }
    }

    fun close() {
        poseDetector.close()
    }

    private companion object {
        const val MIN_FRAME_INTERVAL_MS = 120L
    }
}
