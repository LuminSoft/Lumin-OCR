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

enum class LivenessMovement {
    WINK,
    SMILE,
    HEAD_UP,
    HEAD_DOWN,
    HEAD_LEFT,
    HEAD_RIGHT
}

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
    private var awaitingMovement1 = false
    private var awaitingMovement2 = false
    private var awaitingMovement3 = false
    
    private var movement1: LivenessMovement = LivenessMovement.WINK
    private var movement2: LivenessMovement = LivenessMovement.SMILE
    private var movement3: LivenessMovement = LivenessMovement.HEAD_UP
    private var movementHoldStartTime: Long = 0
    private var isHoldingMovement = false
    private var movementStartTime: Long = 0
    private var wrongMovementCount = 0
    private var completedMovements = 0
    private var livenessCompleted = false
    
    private var movement1Score: Double = 0.0
    private var movement2Score: Double = 0.0
    private var movement3Score: Double = 0.0
    private var currentMovementScore: Double = 0.0
    
    init {
        selectRandomMovements()
    }
    
    private fun selectRandomMovements() {
        val movements = LivenessMovement.values().toMutableList()
        movements.shuffle()
        
        movement1 = movements[0]
        movement2 = movements[1]
        movement3 = movements[2]
        
        Log.d(TAG, "Selected movements: $movement1 -> $movement2 -> $movement3")
    }
    
    private fun getOppositeMovement(movement: LivenessMovement): LivenessMovement? {
        return when (movement) {
            LivenessMovement.HEAD_LEFT -> LivenessMovement.HEAD_RIGHT
            LivenessMovement.HEAD_RIGHT -> LivenessMovement.HEAD_LEFT
            LivenessMovement.HEAD_UP -> LivenessMovement.HEAD_DOWN
            LivenessMovement.HEAD_DOWN -> LivenessMovement.HEAD_UP
            else -> null
        }
    }

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
        
        if (livenessCompleted) {
            return
        }

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
                val isInLivenessPhase = awaitingMovement1 || awaitingMovement2 || awaitingMovement3
                
                // During liveness phases, be more lenient
                val facePositionOk = if (isInLivenessPhase) {
                    isFaceWithinCircleLenient(face.boundingBox)
                } else {
                    isFaceWithinCircle(face.boundingBox)
                }
                
                if (facePositionOk) {
                    // Only check face orientation during natural expression capture
                    val orientationOk = if (capturingNaturalExpression) {
                        checkFaceOrientation(face)
                    } else {
                        true
                    }
                    
                    val sizeOk = if (isInLivenessPhase) {
                        checkFaceSizeWithoutReset(face)
                    } else {
                        checkFaceSize(face)
                    }
                    if (orientationOk && sizeOk) {
                        handleExpressions(face)
                    }
                } else {
                    updateInstructionsCallback(context.getString(R.string.instruction_move_center))
                    circularOverlayView.updateCircleColor(0xFFFFFFFF.toInt())
                    if (!isInLivenessPhase) {
                        resetNaturalExpressionState()
                    }
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

        when {
            capturingNaturalExpression && smileProb != null && smileProb < NATURAL_THRESHOLD -> {
                handleNaturalExpression()
            }
            
            awaitingMovement1 -> {
                handleMovement(face, movement1, movementNumber = 1)
            }
            
            awaitingMovement2 -> {
                handleMovement(face, movement2, movementNumber = 2)
            }
            
            awaitingMovement3 -> {
                handleMovement(face, movement3, movementNumber = 3)
            }

            else -> {
                when {
                    capturingNaturalExpression -> {
                        updateInstructionsCallback(context.getString(R.string.instruction_please_keep_natural_expression))
                        circularOverlayView.updateCircleColor(0xFFFFFFFF.toInt())
                    }
                    
                    awaitingMovement1 -> {
                        updateInstructionsCallback(getMovementInstruction(movement1))
                        circularOverlayView.updateCircleColor(0xFF2196F3.toInt())
                    }
                    
                    awaitingMovement2 -> {
                        updateInstructionsCallback(getMovementInstruction(movement2))
                        circularOverlayView.updateCircleColor(0xFFFF9800.toInt())
                    }
                    
                    awaitingMovement3 -> {
                        updateInstructionsCallback(getMovementInstruction(movement3))
                        circularOverlayView.updateCircleColor(0xFF9C27B0.toInt())
                    }
                }
            }
        }
    }

    private fun handleNaturalExpression() {
        if (!isNaturalExpressionDetected) {
            isNaturalExpressionDetected = true
            naturalExpressionStartTime = System.currentTimeMillis()
            onSessionVideoStart()
            updateInstructionsCallback(context.getString(R.string.instruction_keep_natural))
            circularOverlayView.updateCircleColor(0xFFFFFFFF.toInt())
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - naturalExpressionStartTime > 1000) {
                captureCallback(false)
                capturingNaturalExpression = false
                awaitingMovement1 = true
                awaitingMovement2 = false
                awaitingMovement3 = false
                movementStartTime = System.currentTimeMillis()
                wrongMovementCount = 0
                completedMovements = 0
                updateInstructionsCallback(getMovementInstruction(movement1))
                circularOverlayView.updateCircleColor(0xFF2196F3.toInt())
            }
        }
    }
    
    private fun handleMovement(face: Face, movement: LivenessMovement, movementNumber: Int) {
        val smileProb = face.smilingProbability
        val leftOpen = face.leftEyeOpenProbability
        val rightOpen = face.rightEyeOpenProbability
        val yaw = face.headEulerAngleY
        val pitch = face.headEulerAngleX
        
        Log.d(TAG, "handleMovement - Movement: $movement, Yaw: $yaw, Pitch: $pitch, SmileProb: $smileProb, Number: $movementNumber")
        
        // Check timeout
        val currentTime = System.currentTimeMillis()
        if (currentTime - movementStartTime > MOVEMENT_TIMEOUT_MS) {
            Log.e(TAG, "Movement timeout! Failed to complete $movement")
            handleLivenessFailure()
            return
        }
        
        val isCorrectMovement = when (movement) {
            LivenessMovement.HEAD_LEFT -> yaw > HEAD_ROTATION_THRESHOLD_HORIZONTAL
            LivenessMovement.HEAD_RIGHT -> yaw < -HEAD_ROTATION_THRESHOLD_HORIZONTAL
            LivenessMovement.HEAD_UP -> pitch > HEAD_ROTATION_THRESHOLD_VERTICAL
            LivenessMovement.HEAD_DOWN -> pitch < -HEAD_ROTATION_THRESHOLD_DOWN
            LivenessMovement.WINK -> leftOpen != null && rightOpen != null && 
                    (leftOpen < WINK_THRESHOLD || rightOpen < WINK_THRESHOLD)
            LivenessMovement.SMILE -> smileProb != null && smileProb > SMILE_THRESHOLD
        }
        
        if (isCorrectMovement) {
            currentMovementScore = calculateMovementScore(movement, smileProb, leftOpen, rightOpen, yaw, pitch)
        }
        
        // Detect wrong movement for head rotations only
        if (movement in listOf(LivenessMovement.HEAD_LEFT, LivenessMovement.HEAD_RIGHT, 
                               LivenessMovement.HEAD_UP, LivenessMovement.HEAD_DOWN)) {
            val isWrongMovement = checkWrongMovement(yaw, pitch, movement)
            if (isWrongMovement) {
                wrongMovementCount++
                Log.w(TAG, "Wrong movement detected! Count: $wrongMovementCount")
                if (wrongMovementCount >= MAX_WRONG_MOVEMENTS) {
                    Log.e(TAG, "Too many wrong movements! Retrying current movement")
                    retryCurrentMovement(movementNumber)
                    return
                }
            }
        }

        Log.d(TAG, "isCorrectMovement: $isCorrectMovement")

        if (isCorrectMovement) {
            wrongMovementCount = 0
            
            // For WINK and SMILE, no hold duration needed
            val needsHold = movement in listOf(LivenessMovement.HEAD_LEFT, LivenessMovement.HEAD_RIGHT,
                                                LivenessMovement.HEAD_UP, LivenessMovement.HEAD_DOWN)
            
            if (needsHold) {
                if (!isHoldingMovement) {
                    isHoldingMovement = true
                    movementHoldStartTime = System.currentTimeMillis()
                    updateInstructionsCallback(context.getString(R.string.instruction_hold_position))
                    circularOverlayView.updateCircleColor(0xFF00FF00.toInt())
                    Log.d(TAG, "Started holding movement for $movement")
                } else {
                    val holdDuration = System.currentTimeMillis() - movementHoldStartTime
                    if (holdDuration > MOVEMENT_HOLD_DURATION_MS) {
                        Log.d(TAG, "Movement held for ${holdDuration}ms - completing!")
                        completeMovement(movementNumber)
                    }
                }
            } else {
                // WINK and SMILE complete immediately
                completeMovement(movementNumber)
            }
        } else {
            isHoldingMovement = false
            movementHoldStartTime = 0
            updateInstructionsCallback(getMovementInstruction(movement))
            val color = when (movementNumber) {
                1 -> 0xFF2196F3.toInt()
                2 -> 0xFFFF9800.toInt()
                else -> 0xFF9C27B0.toInt()
            }
            circularOverlayView.updateCircleColor(color)
        }
    }
    
    private fun completeMovement(movementNumber: Int) {
        isHoldingMovement = false
        movementHoldStartTime = 0
        completedMovements++
        
        when (movementNumber) {
            1 -> {
                movement1Score = currentMovementScore
                Log.d(TAG, "Movement 1 complete! Score: $movement1Score Moving to movement 2: $movement2")
                awaitingMovement1 = false
                awaitingMovement2 = true
                movementStartTime = System.currentTimeMillis()
                wrongMovementCount = 0
                currentMovementScore = 0.0
                updateInstructionsCallback(getMovementInstruction(movement2))
                circularOverlayView.updateCircleColor(0xFFFF9800.toInt())
            }
            2 -> {
                movement2Score = currentMovementScore
                Log.d(TAG, "Movement 2 complete! Score: $movement2Score Moving to movement 3: $movement3")
                awaitingMovement2 = false
                awaitingMovement3 = true
                movementStartTime = System.currentTimeMillis()
                wrongMovementCount = 0
                currentMovementScore = 0.0
                updateInstructionsCallback(getMovementInstruction(movement3))
                circularOverlayView.updateCircleColor(0xFF9C27B0.toInt())
            }
            3 -> {
                movement3Score = currentMovementScore
                Log.d(TAG, "All 3 movements complete! Score: $movement3Score Liveness successful")
                Log.d(TAG, "Final scores - M1: $movement1Score, M2: $movement2Score, M3: $movement3Score, Avg: ${getAverageScore()}")
                awaitingMovement3 = false
                handleLivenessSuccess()
            }
        }
    }
    
    private fun calculateMovementScore(
        movement: LivenessMovement,
        smileProb: Float?,
        leftOpen: Float?,
        rightOpen: Float?,
        yaw: Float,
        pitch: Float
    ): Double {
        return when (movement) {
            LivenessMovement.SMILE -> {
                ((smileProb ?: 0f) * 100).toDouble().coerceIn(0.0, 100.0)
            }
            LivenessMovement.WINK -> {
                val closedEyeScore = minOf(leftOpen ?: 1f, rightOpen ?: 1f)
                ((1f - closedEyeScore) * 100).toDouble().coerceIn(0.0, 100.0)
            }
            LivenessMovement.HEAD_LEFT -> {
                (yaw / 45f * 100).toDouble().coerceIn(0.0, 100.0)
            }
            LivenessMovement.HEAD_RIGHT -> {
                (-yaw / 45f * 100).toDouble().coerceIn(0.0, 100.0)
            }
            LivenessMovement.HEAD_UP -> {
                (pitch / 30f * 100).toDouble().coerceIn(0.0, 100.0)
            }
            LivenessMovement.HEAD_DOWN -> {
                (-pitch / 30f * 100).toDouble().coerceIn(0.0, 100.0)
            }
        }
    }
    
    fun getMovementScores(): Triple<Double, Double, Double> {
        return Triple(movement1Score, movement2Score, movement3Score)
    }
    
    fun getMovementTypes(): Triple<String, String, String> {
        return Triple(movement1.name, movement2.name, movement3.name)
    }
    
    fun getAverageScore(): Double {
        return (movement1Score + movement2Score + movement3Score) / 3.0
    }
    
    private fun checkWrongMovement(yaw: Float, pitch: Float, movement: LivenessMovement): Boolean {
        val threshold = 25f
        return when (movement) {
            LivenessMovement.HEAD_LEFT -> yaw < -threshold
            LivenessMovement.HEAD_RIGHT -> yaw > threshold
            LivenessMovement.HEAD_UP -> pitch < -threshold
            LivenessMovement.HEAD_DOWN -> pitch > threshold
            else -> false
        }
    }
    
    private fun retryCurrentMovement(movementNumber: Int) {
        updateInstructionsCallback(context.getString(R.string.instruction_wrong_movement))
        circularOverlayView.updateCircleColor(0xFFFF0000.toInt())
        
        naturalExpressionHandler.postDelayed({
            wrongMovementCount = 0
            isHoldingMovement = false
            movementHoldStartTime = 0
            movementStartTime = System.currentTimeMillis()
            
            val currentMovement = when (movementNumber) {
                1 -> movement1
                2 -> movement2
                3 -> movement3
                else -> movement1
            }
            
            updateInstructionsCallback(getMovementInstruction(currentMovement))
            val color = when (movementNumber) {
                1 -> 0xFF2196F3.toInt()
                2 -> 0xFFFF9800.toInt()
                else -> 0xFF9C27B0.toInt()
            }
            circularOverlayView.updateCircleColor(color)
            Log.i(TAG, "Retrying movement $movementNumber: $currentMovement")
        }, 1000)
    }
    
    private fun handleLivenessFailure() {
        updateInstructionsCallback(context.getString(R.string.instruction_liveness_failed))
        circularOverlayView.updateCircleColor(0xFFFF0000.toInt())
        naturalExpressionHandler.postDelayed({
            resetNaturalExpressionState()
            onSessionVideoStop()
        }, 1000)
    }
    
    private fun handleLivenessSuccess() {
        livenessCompleted = true
        updateInstructionsCallback(context.getString(R.string.message_hold_still))
        Log.i(TAG, "✅ Liveness success - capturing smile image")
        captureCallback(true)
        naturalExpressionHandler.postDelayed({
            Log.i(TAG, "Stopping video recording after smile capture")
            onSessionVideoStop()
        }, 1500)
    }
    
    private fun getMovementInstruction(movement: LivenessMovement): String {
        return when (movement) {
            LivenessMovement.HEAD_LEFT -> context.getString(R.string.instruction_turn_head_left)
            LivenessMovement.HEAD_RIGHT -> context.getString(R.string.instruction_turn_head_right)
            LivenessMovement.HEAD_UP -> context.getString(R.string.instruction_look_up)
            LivenessMovement.HEAD_DOWN -> context.getString(R.string.instruction_look_down)
            LivenessMovement.WINK -> context.getString(R.string.instruction_wink_now)
            LivenessMovement.SMILE -> context.getString(R.string.instruction_smile_now)
        }
    }

    private fun resetNaturalExpressionState() {
        isNaturalExpressionDetected = false
        naturalExpressionStartTime = 0
        capturingNaturalExpression = true
        awaitingMovement1 = false
        awaitingMovement2 = false
        awaitingMovement3 = false
        isHoldingMovement = false
        movementHoldStartTime = 0
        movementStartTime = 0
        wrongMovementCount = 0
        completedMovements = 0
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
    
    private fun isFaceWithinCircleLenient(boundingBox: Rect): Boolean {
        val mappedCenterX = mapX(boundingBox.centerX())
        val mappedCenterY = mapY(boundingBox.centerY())
        val overlayCenterX = overlay.width / 2f
        val overlayCenterY = overlay.height / 2f
        val dx = (mappedCenterX - overlayCenterX).toDouble()
        val dy = (mappedCenterY - overlayCenterY).toDouble()
        val distance = sqrt(dx.pow(2.0) + dy.pow(2.0))
        return distance <= DistanceThresholdLenient
    }
    
    private fun checkFaceSizeWithoutReset(face: Face): Boolean {
        val faceWidth = face.boundingBox.width()
        return faceWidth in MIN_FACE_SIZE_THRESHOLD_LENIENT..MAX_FACE_SIZE_THRESHOLD_LENIENT
    }

    private fun mapX(imageX: Int): Float = imageX * overlay.width / imageHeight.toFloat()
    private fun mapY(imageY: Int): Float = imageY * overlay.height / imageWidth.toFloat()

    companion object {
        private const val TAG = "CameraAnalyzer"
        // Face size thresholds - more lenient to allow users at different distances
        private const val MIN_FACE_SIZE_THRESHOLD = 120          // Was 150 - allows user to be slightly farther
        private const val MAX_FACE_SIZE_THRESHOLD = 450          // Was 400 - allows user to be slightly closer
        private const val MIN_FACE_SIZE_THRESHOLD_LENIENT = 80   // Was 100 - very lenient during movements
        private const val MAX_FACE_SIZE_THRESHOLD_LENIENT = 550  // Was 500 - very lenient during movements
        
        // Distance from center thresholds - bigger circle tolerance
        private const val DistanceThreshold = 100f               // Was 65f - easier to center face
        private const val DistanceThresholdLenient = 200f        // Was 150f - more room during movements (especially look down)
        
        // Natural expression threshold - more lenient
        private const val NATURAL_THRESHOLD = 0.35f              // Was 0.1f - allows slight smile/expression
        
        // Movement thresholds
        private const val WINK_THRESHOLD = 0.15f                 // Was 0.1f - slightly easier wink detection
        private const val SMILE_THRESHOLD = 0.7f                 // Was 0.8f - slightly easier smile detection
        private const val HEAD_ROTATION_THRESHOLD_HORIZONTAL = 18f // Was 20f - slightly easier left/right
        private const val HEAD_ROTATION_THRESHOLD_VERTICAL = 12f   // Was 15f - easier look up
        private const val HEAD_ROTATION_THRESHOLD_DOWN = 7f        // Was 10f - much easier look down
        
        private const val MOVEMENT_HOLD_DURATION_MS = 500L
        private const val MOVEMENT_TIMEOUT_MS = 10000L
        private const val MAX_WRONG_MOVEMENTS = 5
    }
}