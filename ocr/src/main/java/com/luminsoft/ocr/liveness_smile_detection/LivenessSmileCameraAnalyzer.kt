package com.luminsoft.ocr.liveness_smile_detection

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.luminsoft.ocr.R
import com.luminsoft.ocr.core.camera.BaseCameraAnalyzer
import com.luminsoft.ocr.core.graphic.CircularOverlayView
import com.luminsoft.ocr.core.graphic.GraphicOverlay
import kotlin.math.pow
import kotlin.math.sqrt

class LivenessSmileCameraAnalyzer(
    private val context: Context,
    private val overlay: GraphicOverlay<*>,
    private val circularOverlayView: CircularOverlayView,
    private val captureCallback: (Boolean) -> Unit, // false = NATURAL, true = SMILE (wink not captured in this signature)
    private val updateInstructionsCallback: (String) -> Unit
) : BaseCameraAnalyzer<List<Face>>() {

    // Image dims for mapping
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    // Timing + state
    private val naturalExpressionHandler = Handler(Looper.getMainLooper())
    private var isNaturalExpressionDetected = false
    private var naturalExpressionStartTime: Long = 0

    // Flow flags
    private var capturingNaturalExpression = true
    private var awaitingWink = false
    private var awaitingSmile = false

    // ML Kit detector
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
    )

    override val graphicOverlay: GraphicOverlay<*>
        get() = overlay

    override fun detectInImage(image: InputImage): Task<List<Face>> {
        imageWidth = image.width
        imageHeight = image.height
        return detector.process(image)
    }

    override fun stop() {
        try {
            detector.close()
        } catch (e: Exception) {
            Log.e(TAG, "stop: ${e.message}", e)
        }
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "onFailure: ${e.message}", e)
    }

    override fun onSuccess(results: List<Face>, graphicOverlay: GraphicOverlay<*>, rect: Rect) {
        graphicOverlay.clear()

        when {
            results.isEmpty() -> {
                updateInstructionsCallback(context.getString(R.string.instruction_no_face))
                circularOverlayView.updateCircleColor(0xFFFFFFFF.toInt())
                resetNaturalExpressionState()
            }

            results.size > 1 -> {
                updateInstructionsCallback(context.getString(R.string.instruction_one_face))
                circularOverlayView.updateCircleColor(0xFFFFFFFF.toInt())
                resetNaturalExpressionState()
            }

            results.size == 1 -> {
                val face = results[0]
                if (isFaceWithinCircle(face.boundingBox)) {
                    if (checkFaceOrientation(face) && checkFaceSize(face)) {
                        handleExpressions(face)
                    }
                } else {
                    updateInstructionsCallback(context.getString(R.string.instruction_move_center))
                    circularOverlayView.updateCircleColor(0xFFFFFFFF.toInt())
                    resetNaturalExpressionState()
                }
            }
        }

        graphicOverlay.postInvalidate()
    }

    // Orientation (yaw/pitch) gates
    private fun checkFaceOrientation(face: Face): Boolean {
        val yaw = face.headEulerAngleY
        val pitch = face.headEulerAngleX

        return if (yaw < -10 || yaw > 10 || pitch < -15 || pitch > 15) {
            updateInstructionsCallback(context.getString(R.string.instruction_look_straight))
            circularOverlayView.updateCircleColor(0xFFFFFFFF.toInt())
            resetNaturalExpressionState()
            false
        } else true
    }

    // Size gates
    private fun checkFaceSize(face: Face): Boolean {
        val faceWidth = face.boundingBox.width()
        return when {
            faceWidth < MIN_FACE_SIZE_THRESHOLD -> {
                updateInstructionsCallback(context.getString(R.string.instruction_move_closer))
                circularOverlayView.updateCircleColor(0xFFFFFFFF.toInt())
                resetNaturalExpressionState()
                false
            }

            faceWidth > MAX_FACE_SIZE_THRESHOLD -> {
                updateInstructionsCallback(context.getString(R.string.instruction_move_back))
                circularOverlayView.updateCircleColor(0xFFFFFFFF.toInt())
                resetNaturalExpressionState()
                false
            }

            else -> true
        }
    }

    // Main flow controller
    private fun handleExpressions(face: Face) {
        val smileProb = face.smilingProbability
        val leftOpen = face.leftEyeOpenProbability
        val rightOpen = face.rightEyeOpenProbability

        when {
            // Step 1: NATURAL (neutral) — smile must be low
            capturingNaturalExpression && smileProb != null && smileProb < NATURAL_THRESHOLD -> {
                handleNaturalExpression()
            }

            // Step 2: WINK — one of eyes should be "closed"
            awaitingWink && leftOpen != null && rightOpen != null &&
                    (leftOpen < WINK_THRESHOLD || rightOpen < WINK_THRESHOLD) -> {
                handleWinkExpression()
            }

            // Step 3: SMILE — smile high
            awaitingSmile && smileProb != null && smileProb > SMILE_THRESHOLD -> {
                handleSmilingExpression()
            }

            // Otherwise, keep guiding user for current expected step
            else -> {
                when {
                    capturingNaturalExpression -> {
                        updateInstructionsCallback(context.getString(R.string.instruction_please_keep_natural_expression))
                        circularOverlayView.updateCircleColor(0xFFFFFFFF.toInt())
                    }

                    awaitingWink -> {
                        updateInstructionsCallback(context.getString(R.string.instruction_wink_now))
                        // amber
                        circularOverlayView.updateCircleColor(0xFFFFD600.toInt())
                    }

                    awaitingSmile -> {
                        updateInstructionsCallback(context.getString(R.string.instruction_smile_now))
                        // green
                        circularOverlayView.updateCircleColor(0xFF00FF00.toInt())
                    }
                }
            }
        }
    }

    // NATURAL step
    private fun handleNaturalExpression() {
        if (!isNaturalExpressionDetected) {
            isNaturalExpressionDetected = true
            naturalExpressionStartTime = System.currentTimeMillis()
            updateInstructionsCallback(context.getString(R.string.instruction_keep_natural))
            circularOverlayView.updateCircleColor(0xFFFFFFFF.toInt())
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - naturalExpressionStartTime > 1000) {
                // Capture natural image
                captureCallback(false)

                // Move to WINK step
                capturingNaturalExpression = false
                awaitingWink = true
                awaitingSmile = false

                updateInstructionsCallback(context.getString(R.string.instruction_wink_now))
                // amber
                circularOverlayView.updateCircleColor(0xFFFFD600.toInt())
            }
        }
    }

    // WINK step: proceed to SMILE when wink detected
    private fun handleWinkExpression() {
        // If you decide to capture wink too, change callback signature to an enum and capture here.
        // capture(ExpressionType.WINK)

        awaitingWink = false
        awaitingSmile = true

        updateInstructionsCallback(context.getString(R.string.instruction_smile_now))
        // green
        circularOverlayView.updateCircleColor(0xFF00FF00.toInt())
    }

    // SMILE step
    private fun handleSmilingExpression() {
        updateInstructionsCallback(context.getString(R.string.message_hold_still))
        captureCallback(true) // Capture smiling image

        naturalExpressionHandler.postDelayed({
            resetNaturalExpressionState()
        }, 500)
    }

    // Reset flow
    private fun resetNaturalExpressionState() {
        isNaturalExpressionDetected = false
        naturalExpressionStartTime = 0
        capturingNaturalExpression = true
        awaitingWink = false
        awaitingSmile = false
    }

    // Geometry helpers
    private fun isFaceWithinCircle(boundingBox: Rect): Boolean {
        val mappedCenterX = mapX(boundingBox.centerX())
        val mappedCenterY = mapY(boundingBox.centerY())

        val overlayCenterX = overlay.width / 2f
        val overlayCenterY = overlay.height / 2f

        val dx = (mappedCenterX - overlayCenterX).toDouble()
        val dy = (mappedCenterY - overlayCenterY).toDouble()
        val distance = sqrt(dx.pow(2.0) + dy.pow(2.0))

        return distance <= DistanceThreshold
    }

    private fun mapX(imageX: Int): Float =
        imageX * overlay.width / imageHeight.toFloat() // rotated input

    private fun mapY(imageY: Int): Float =
        imageY * overlay.height / imageWidth.toFloat() // rotated input

    companion object {
        private const val TAG = "CameraAnalyzer"

        private const val MIN_FACE_SIZE_THRESHOLD = 150
        private const val MAX_FACE_SIZE_THRESHOLD = 400
        private const val DistanceThreshold = 65f

        // Thresholds
        private const val NATURAL_THRESHOLD = 0.1f   // smilingProbability < 0.1
        private const val WINK_THRESHOLD = 0.1f      // eyeOpenProbability < 0.1 (either eye)
        private const val SMILE_THRESHOLD = 0.8f     // smilingProbability > 0.8
    }
}
