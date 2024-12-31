package com.luminsoft.ocr.liveness_smile_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.ui.graphics.Color
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
    private val captureCallback: (Boolean) -> Unit, // Callback that accepts a Boolean for the type of expression
    private val updateInstructionsCallback: (String) -> Unit
) : BaseCameraAnalyzer<List<Face>>() {

    // Variables for tracking face detection and expression timing
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private val naturalExpressionHandler = Handler(Looper.getMainLooper())

    private var isNaturalExpressionDetected = false
    private var naturalExpressionStartTime: Long = 0
    private var awaitingSmile = false
    private var capturingNaturalExpression = true

    // Initialize the FaceDetector
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
        // Set the image dimensions from the InputImage
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


    override fun onSuccess(
        results: List<Face>,
        graphicOverlay: GraphicOverlay<*>,
        rect: Rect
    ) {

        graphicOverlay.clear()

        when {
            results.isEmpty() -> {
                updateInstructionsCallback(context.getString(R.string.instruction_no_face))
                circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#FFFFFF"))

                resetNaturalExpressionState()
            }

            results.size > 1 -> {
                updateInstructionsCallback(context.getString(R.string.instruction_one_face))
                circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#FFFFFF"))

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
                    circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#FFFFFF"))
                    resetNaturalExpressionState()
                }
            }
        }

        graphicOverlay.postInvalidate()
    }


    // Check if the face orientation is within limits
    private fun checkFaceOrientation(face: Face): Boolean {

        val yaw = face.headEulerAngleY
        val pitch = face.headEulerAngleX

        return if (yaw < -10 || yaw > 10) {

            updateInstructionsCallback(context.getString(R.string.instruction_look_straight))
            circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#FFFFFF"))

            resetNaturalExpressionState()
            false
        } else if (pitch < -15 || pitch > 15) {
            updateInstructionsCallback(context.getString(R.string.instruction_look_straight))
            circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#FFFFFF"))

            resetNaturalExpressionState()
            false
        } else {
            true
        }
    }

    // Check if the face size is within the acceptable range
    private fun checkFaceSize(face: Face): Boolean {
        val faceWidth = face.boundingBox.width()

        return when {
            faceWidth < MIN_FACE_SIZE_THRESHOLD -> {
                updateInstructionsCallback(context.getString(R.string.instruction_move_closer))
                circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#FFFFFF"))
                resetNaturalExpressionState()
                false
            }

            faceWidth > MAX_FACE_SIZE_THRESHOLD -> {
                updateInstructionsCallback(context.getString(R.string.instruction_move_back))
                circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#FFFFFF"))

                resetNaturalExpressionState()
                false
            }

            else -> true
        }
    }

    // Handle capturing natural and smiling expressions based on probability
    private fun handleExpressions(face: Face) {
        if (capturingNaturalExpression && face.smilingProbability != null && face.smilingProbability!! < 0.1) {
            handleNaturalExpression()
        } else if (awaitingSmile && face.smilingProbability != null && face.smilingProbability!! > 0.8) {
            handleSmilingExpression()
        } else if (!awaitingSmile) {
            resetNaturalExpressionState()
            updateInstructionsCallback(context.getString(R.string.instruction_please_keep_natural_expression))

            circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#FFFFFF"))

        }
    }

    // Handle natural expression detection and timing
    private fun handleNaturalExpression() {
        if (!isNaturalExpressionDetected) {
            isNaturalExpressionDetected = true
            naturalExpressionStartTime = System.currentTimeMillis()
            updateInstructionsCallback(context.getString(R.string.instruction_keep_natural))
            circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#FFFFFF"))
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - naturalExpressionStartTime > 1000) {
                captureCallback(false) // Capture natural expression image
                awaitingSmile = true
                capturingNaturalExpression = false
                updateInstructionsCallback(context.getString(R.string.instruction_smile_now))
                circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#00ff00"))
            }
        }
    }

    // Handle smiling expression detection and capture
    private fun handleSmilingExpression() {
        updateInstructionsCallback(context.getString(R.string.message_hold_still))
        captureCallback(true) // Capture smiling image
        naturalExpressionHandler.postDelayed({
            resetNaturalExpressionState()
        }, 500)
    }

    // Reset the state to prepare for the next capture
    private fun resetNaturalExpressionState() {
        isNaturalExpressionDetected = false
        naturalExpressionStartTime = 0
        awaitingSmile = false
        capturingNaturalExpression = true
    }

    // Helper function to check if the face is within the circular overlay
    private fun isFaceWithinCircle(boundingBox: Rect): Boolean {
        // Map the face center coordinates to the overlay view space
        val mappedCenterX = mapX(boundingBox.centerX())
        val mappedCenterY = mapY(boundingBox.centerY())

        // Calculate the center of the overlay
        val overlayCenterX = overlay.width / 2f
        val overlayCenterY = overlay.height / 2f

        // Calculate the distance from the face center to the overlay center
        val distance = Math.sqrt(
            Math.pow((mappedCenterX - overlayCenterX).toDouble(), 2.0) +
                    Math.pow((mappedCenterY - overlayCenterY).toDouble(), 2.0)
        )

        // Check if the distance is within the circle's radius
        return distance <= DistanceThreshold
    }

        // Helper function to map X-coordinate from image space to overlay view space
        private fun mapX(imageX: Int): Float {
            // here we used imageHeight instead of imageWidth because the captured image comes rotated
            return imageX * overlay.width / imageHeight.toFloat()
        }

        // Helper function to map Y-coordinate from image space to overlay view space
        private fun mapY(imageY: Int): Float {
            // here we used imageHeight instead of imageWidth because the captured image comes rotated
            return imageY * overlay.height / imageWidth.toFloat()
        }


    companion object {
        private const val TAG = "CameraAnalyzer"
        private const val MIN_FACE_SIZE_THRESHOLD = 150
        private const val MAX_FACE_SIZE_THRESHOLD = 400
        private const val DistanceThreshold = 65f
    }
}




