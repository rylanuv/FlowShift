package com.flow.shift.core.cameravision

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.atan2

class PushupAnalyzer(
    private val onRepCounted: (Int) -> Unit,
    private val onFeedbackChanged: (String) -> Unit = {}
) {
    private var repCount = 0
    private var currentState = PushupState.UP
    private var currentFeedback = ""

    // --- Smoothing & stability ---
    // Exponential moving average for the elbow angle to filter ML Kit jitter.
    // A lower alpha reacts more slowly but is far more stable.
    private var smoothedAngle: Float? = null
    private val smoothingAlpha = 0.35f // Blend factor: 0 = ignore new, 1 = no smoothing

    // Temporal debounce: require N consecutive frames in agreement before
    // committing a state transition. Prevents single-frame noise spikes
    // from flipping states.
    private var pendingState: PushupState? = null
    private var pendingStateFrames = 0
    private val requiredConfirmationFrames = 3

    // Minimum confidence for a landmark to be considered usable.
    private val minLandmarkConfidence = 0.6f

    enum class PushupState { UP, GOING_DOWN, DOWN, GOING_UP }

    // ---- Angle thresholds (degrees) ----
    // Wider hysteresis bands prevent oscillation at boundaries.
    //
    //  UP ──(< 120)──▸ GOING_DOWN ──(< 90)──▸ DOWN
    //  UP ◂──(> 155)── GOING_UP   ◂──(> 120)── DOWN
    //
    // False-start reset from GOING_DOWN back to UP requires > 160.
    // Fall-back from GOING_UP to DOWN requires < 90.

    companion object {
        // Transition INTO the down position
        private const val THRESHOLD_START_GOING_DOWN = 120f
        private const val THRESHOLD_REACHED_DOWN = 90f

        // Transition OUT of the down position
        private const val THRESHOLD_START_GOING_UP = 120f
        private const val THRESHOLD_REACHED_UP = 155f

        // Resets / corrections
        private const val THRESHOLD_FALSE_START_RESET = 160f
        private const val THRESHOLD_WENT_BACK_DOWN = 90f
    }

    private fun updateFeedback(newFeedback: String) {
        if (currentFeedback != newFeedback) {
            currentFeedback = newFeedback
            onFeedbackChanged(newFeedback)
        }
    }

    fun processResult(pose: Pose) {
        val landmarks = pose.allPoseLandmarks
        if (landmarks.isEmpty()) {
            updateFeedback("Body not detected — try moving into frame")
            return
        }

        // --- Gather landmarks with confidence filtering ---
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        if (leftShoulder == null || rightShoulder == null ||
            leftElbow == null || rightElbow == null ||
            leftWrist == null || rightWrist == null
        ) {
            updateFeedback("Move fully into the camera")
            return
        }

        val leftConfidence = minOf(
            leftShoulder.inFrameLikelihood,
            leftElbow.inFrameLikelihood,
            leftWrist.inFrameLikelihood
        )
        val rightConfidence = minOf(
            rightShoulder.inFrameLikelihood,
            rightElbow.inFrameLikelihood,
            rightWrist.inFrameLikelihood
        )

        val leftVisible = leftConfidence > minLandmarkConfidence
        val rightVisible = rightConfidence > minLandmarkConfidence

        val rawAngle = when {
            leftVisible && rightVisible -> {
                val leftAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
                val rightAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)
                // Confidence-weighted average: trust the side ML Kit is more sure about.
                val totalConf = leftConfidence + rightConfidence
                (leftAngle * leftConfidence + rightAngle * rightConfidence) / totalConf
            }
            leftVisible -> calculateAngle(leftShoulder, leftElbow, leftWrist)
            rightVisible -> calculateAngle(rightShoulder, rightElbow, rightWrist)
            else -> {
                updateFeedback("Move into the camera")
                return
            }
        }

        // --- Exponential moving average ---
        val ema = smoothedAngle
        val filteredAngle = if (ema == null) {
            rawAngle
        } else {
            ema + smoothingAlpha * (rawAngle - ema)
        }
        smoothedAngle = filteredAngle

        // --- State machine with temporal debounce ---
        val proposedState = computeNextState(filteredAngle)
        if (proposedState != currentState) {
            if (proposedState == pendingState) {
                pendingStateFrames++
                if (pendingStateFrames >= requiredConfirmationFrames) {
                    val previousState = currentState
                    currentState = proposedState
                    pendingState = null
                    pendingStateFrames = 0

                    // Count a rep only on a confirmed GOING_UP → UP transition
                    if (previousState == PushupState.GOING_UP && currentState == PushupState.UP) {
                        repCount++
                        onRepCounted(repCount)
                    }
                }
            } else {
                // New candidate — start counting confirmation frames
                pendingState = proposedState
                pendingStateFrames = 1
            }
        } else {
            // Angle agrees with current state — reset any pending transition
            pendingState = null
            pendingStateFrames = 0
        }

        // --- Feedback ---
        when (currentState) {
            PushupState.UP, PushupState.GOING_DOWN -> updateFeedback("Lower your chest")
            PushupState.DOWN, PushupState.GOING_UP -> updateFeedback("Push back up")
        }
    }

    /**
     * Pure function: given the current [state] and a new [angle], returns the
     * state the machine *wants* to move to (before debounce confirmation).
     */
    private fun computeNextState(angle: Float): PushupState {
        return when (currentState) {
            PushupState.UP -> {
                if (angle < THRESHOLD_START_GOING_DOWN) PushupState.GOING_DOWN else PushupState.UP
            }
            PushupState.GOING_DOWN -> {
                when {
                    angle < THRESHOLD_REACHED_DOWN -> PushupState.DOWN
                    angle > THRESHOLD_FALSE_START_RESET -> PushupState.UP
                    else -> PushupState.GOING_DOWN
                }
            }
            PushupState.DOWN -> {
                if (angle > THRESHOLD_START_GOING_UP) PushupState.GOING_UP else PushupState.DOWN
            }
            PushupState.GOING_UP -> {
                when {
                    angle > THRESHOLD_REACHED_UP -> PushupState.UP
                    angle < THRESHOLD_WENT_BACK_DOWN -> PushupState.DOWN
                    else -> PushupState.GOING_UP
                }
            }
        }
    }

    private fun calculateAngle(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Float {
        val radians = atan2(
            (c.position.y - b.position.y).toDouble(),
            (c.position.x - b.position.x).toDouble()
        ) - atan2(
            (a.position.y - b.position.y).toDouble(),
            (a.position.x - b.position.x).toDouble()
        )
        var degrees = Math.toDegrees(radians).toFloat()
        if (degrees < 0) degrees += 360f
        if (degrees > 180) degrees = 360f - degrees
        return degrees
    }
}
