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
    private val captureCallback: (Boolean) -> Unit,
    private val updateInstructionsCallback: (String) -> Unit,
    private val onSessionVideoStart: () -> Unit,
    private val onSessionVideoStop: () -> Unit
) : BaseCameraAnalyzer<List<Face>>() {

    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    private val naturalExpressionHandler = Handler(Looper.getMainLooper())
    private var isNaturalExpressionDetected = false
    private var naturalExpressionStartTime: Long = 0

    private var capturingNaturalExpression = true
    private var awaitingWink = false
    private var awaitingSmile = false

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

    private fun handleExpressions(face: Face) {
        val smileProb = face.smilingProbability
        val leftOpen = face.leftEyeOpenProbability
        val rightOpen = face.rightEyeOpenProbability

        when {
            capturingNaturalExpression && smileProb != null && smileProb < NATURAL_THRESHOLD -> {
                handleNaturalExpression()
            }
            awaitingWink && leftOpen != null && rightOpen != null &&
                    (leftOpen < WINK_THRESHOLD || rightOpen < WINK_THRESHOLD) -> {
                handleWinkExpression()
            }
            awaitingSmile && smileProb != null && smileProb > SMILE_THRESHOLD -> {
                handleSmilingExpression()
            }
            else -> {
                when {
                    capturingNaturalExpression -> {
                        updateInstructionsCallback(context.getString(R.string.instruction_please_keep_natural_expression))
                        circularOverlayView.updateCircleColor(0xFFFFFFFF.toInt())
                    }
                    awaitingWink -> {
                        updateInstructionsCallback(context.getString(R.string.instruction_wink_now))
                        circularOverlayView.updateCircleColor(0xFFFFD600.toInt())
                    }
                    awaitingSmile -> {
                        updateInstructionsCallback(context.getString(R.string.instruction_smile_now))
                        circularOverlayView.updateCircleColor(0xFF00FF00.toInt())
                    }
                }
            }
        }
    }

    private fun handleNaturalExpression() {
        if (!isNaturalExpressionDetected) {
            isNaturalExpressionDetected = true
            naturalExpressionStartTime = System.currentTimeMillis()
            updateInstructionsCallback(context.getString(R.string.instruction_keep_natural))
            circularOverlayView.updateCircleColor(0xFFFFFFFF.toInt())
//            onSessionVideoStart() // Start recording only once
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - naturalExpressionStartTime > 1000) {
                captureCallback(false)
                capturingNaturalExpression = false
                awaitingWink = true
                awaitingSmile = false
                updateInstructionsCallback(context.getString(R.string.instruction_wink_now))
                circularOverlayView.updateCircleColor(0xFFFFD600.toInt())
            }
        }
    }

    private fun handleWinkExpression() {
        awaitingWink = false
        awaitingSmile = true
        updateInstructionsCallback(context.getString(R.string.instruction_smile_now))
        circularOverlayView.updateCircleColor(0xFF00FF00.toInt())
    }

    private fun handleSmilingExpression() {
        updateInstructionsCallback(context.getString(R.string.message_hold_still))
        captureCallback(true)
        naturalExpressionHandler.postDelayed({
            resetNaturalExpressionState()
            onSessionVideoStop()
        }, 500)
    }

    private fun resetNaturalExpressionState() {
        isNaturalExpressionDetected = false
        naturalExpressionStartTime = 0
        capturingNaturalExpression = true
        awaitingWink = false
        awaitingSmile = false
    }

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

    private fun mapX(imageX: Int): Float = imageX * overlay.width / imageHeight.toFloat()
    private fun mapY(imageY: Int): Float = imageY * overlay.height / imageWidth.toFloat()

    companion object {
        private const val TAG = "CameraAnalyzer"
        private const val MIN_FACE_SIZE_THRESHOLD = 150
        private const val MAX_FACE_SIZE_THRESHOLD = 400
        private const val DistanceThreshold = 65f
        private const val NATURAL_THRESHOLD = 0.1f
        private const val WINK_THRESHOLD = 0.1f
        private const val SMILE_THRESHOLD = 0.8f
    }
}