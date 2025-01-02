package com.luminsoft.ocr.natural_expression_detection

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

class NaturalExpressionCameraAnalyzer(
    private val context: Context,
    private val overlay: GraphicOverlay<*>,
    private val circularOverlayView: CircularOverlayView,
    private val captureCallback: (Boolean) -> Unit, // Callback for capturing image
    private val updateInstructionsCallback: (String) -> Unit
) : BaseCameraAnalyzer<List<Face>>() {

    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    private var isNaturalExpressionDetected = false
    private var naturalExpressionStartTime: Long = 0
    private var capturingNaturalExpression = true
    private var expressionHoldTime: Long = 1000

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
                        handleNaturalExpression(face)
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

    private fun handleNaturalExpression(face: Face) {
        if (capturingNaturalExpression) {
            // Check if the user is smiling
            if (face.smilingProbability != null && face.smilingProbability!! > 0.5) {
                // If the user is smiling, ask them to keep a natural expression and reset the timer
                updateInstructionsCallback(context.getString(R.string.instruction_please_keep_natural_expression))
                circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#FFFFFF"))
                resetNaturalExpressionState()
            } else {
                // User is keeping a natural expression
                if (!isNaturalExpressionDetected) {
                    isNaturalExpressionDetected = true
                    naturalExpressionStartTime = System.currentTimeMillis()
                    updateInstructionsCallback(context.getString(R.string.instruction_keep_natural))
                    circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#00ff00"))
                } else {
                    // Check if the user has held a natural expression for the required time
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - naturalExpressionStartTime >= expressionHoldTime) {
                        // Capture image after the hold time
                        captureCallback(false) // False indicates a natural expression image
                        resetNaturalExpressionState()
//                        updateInstructionsCallback("Captured natural expression")
                    }
                }
            }
        }
    }

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

    private fun resetNaturalExpressionState() {
        isNaturalExpressionDetected = false
        naturalExpressionStartTime = 0
        capturingNaturalExpression = true
    }

    private fun isFaceWithinCircle(boundingBox: Rect): Boolean {
        val mappedCenterX = mapX(boundingBox.centerX())
        val mappedCenterY = mapY(boundingBox.centerY())

        val overlayCenterX = overlay.width / 2f
        val overlayCenterY = overlay.height / 2f
        val distance = Math.sqrt(
            Math.pow((mappedCenterX - overlayCenterX).toDouble(), 2.0) +
                    Math.pow((mappedCenterY - overlayCenterY).toDouble(), 2.0)
        )

        return distance <= DistanceThreshold
    }

    private fun mapX(imageX: Int): Float {
        return imageX * overlay.width / imageHeight.toFloat()
    }

    private fun mapY(imageY: Int): Float {
        return imageY * overlay.height / imageWidth.toFloat()
    }

    companion object {
        private const val TAG = "CameraAnalyzer"
        private const val MIN_FACE_SIZE_THRESHOLD = 150
        private const val MAX_FACE_SIZE_THRESHOLD = 400
        private const val DistanceThreshold = 65f
    }
}






