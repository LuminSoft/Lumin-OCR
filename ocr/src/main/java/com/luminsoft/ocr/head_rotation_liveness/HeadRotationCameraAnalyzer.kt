package com.luminsoft.ocr.head_rotation_liveness

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

enum class HeadRotationDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN
}

enum class HeadRotationState {
    DETECTING_FACE,
    WAITING_NEUTRAL,
    INSTRUCTION_ROTATION_1,
    VALIDATING_ROTATION_1,
    INSTRUCTION_ROTATION_2,
    VALIDATING_ROTATION_2,
    SUCCESS,
    FAILED
}

class HeadRotationCameraAnalyzer(
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

    private val handler = Handler(Looper.getMainLooper())
    
    private var currentState = HeadRotationState.DETECTING_FACE
    private var neutralStartTime: Long = 0
    private var isNeutralDetected = false
    
    private var rotation1Direction: HeadRotationDirection = HeadRotationDirection.LEFT
    private var rotation2Direction: HeadRotationDirection = HeadRotationDirection.RIGHT
    
    private var rotation1Validated = false
    private var rotation2Validated = false
    
    private var rotationHoldStartTime: Long = 0
    private var isHoldingRotation = false

    init {
        selectRandomRotations()
    }

    private fun selectRandomRotations() {
        val directions = HeadRotationDirection.values().toMutableList()
        directions.shuffle()
        rotation1Direction = directions[0]
        
        val oppositeDirection = getOppositeDirection(rotation1Direction)
        directions.remove(rotation1Direction)
        directions.remove(oppositeDirection)
        if (directions.isEmpty()) {
            directions.addAll(HeadRotationDirection.values().filter { it != rotation1Direction })
        }
        directions.shuffle()
        rotation2Direction = directions[0]
        
        Log.d(TAG, "Selected rotations: $rotation1Direction -> $rotation2Direction")
    }
    
    private fun getOppositeDirection(direction: HeadRotationDirection): HeadRotationDirection {
        return when (direction) {
            HeadRotationDirection.LEFT -> HeadRotationDirection.RIGHT
            HeadRotationDirection.RIGHT -> HeadRotationDirection.LEFT
            HeadRotationDirection.UP -> HeadRotationDirection.DOWN
            HeadRotationDirection.DOWN -> HeadRotationDirection.UP
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

        when {
            results.isEmpty() -> {
                updateInstructionsCallback(context.getString(R.string.instruction_no_face))
                circularOverlayView.updateCircleColor(COLOR_WHITE)
                resetState()
            }

            results.size > 1 -> {
                updateInstructionsCallback(context.getString(R.string.instruction_one_face))
                circularOverlayView.updateCircleColor(COLOR_WHITE)
                resetState()
            }

            results.size == 1 -> {
                val face = results[0]
                val isInRotationPhase = currentState == HeadRotationState.VALIDATING_ROTATION_1 || 
                    currentState == HeadRotationState.VALIDATING_ROTATION_2 ||
                    currentState == HeadRotationState.INSTRUCTION_ROTATION_1 ||
                    currentState == HeadRotationState.INSTRUCTION_ROTATION_2
                
                val facePositionOk = if (isInRotationPhase) {
                    isFaceWithinCircleLenient(face.boundingBox)
                } else {
                    isFaceWithinCircle(face.boundingBox)
                }
                
                if (facePositionOk) {
                    if (checkFaceSize(face)) {
                        handleHeadRotationStateMachine(face)
                    }
                } else {
                    updateInstructionsCallback(context.getString(R.string.instruction_move_center))
                    circularOverlayView.updateCircleColor(COLOR_WHITE)
                    if (!isInRotationPhase) {
                        resetState()
                    }
                }
            }
        }

        graphicOverlay.postInvalidate()
    }

    private fun checkFaceSize(face: Face): Boolean {
        val faceWidth = face.boundingBox.width()
        return when {
            faceWidth < MIN_FACE_SIZE_THRESHOLD -> {
                updateInstructionsCallback(context.getString(R.string.instruction_move_closer))
                circularOverlayView.updateCircleColor(COLOR_WHITE)
                resetState()
                false
            }

            faceWidth > MAX_FACE_SIZE_THRESHOLD -> {
                updateInstructionsCallback(context.getString(R.string.instruction_move_back))
                circularOverlayView.updateCircleColor(COLOR_WHITE)
                resetState()
                false
            }

            else -> true
        }
    }

    private fun handleHeadRotationStateMachine(face: Face) {
        val yaw = face.headEulerAngleY   // Left/Right rotation
        val pitch = face.headEulerAngleX // Up/Down rotation

        when (currentState) {
            HeadRotationState.DETECTING_FACE -> {
                if (isNeutralPosition(yaw, pitch)) {
                    currentState = HeadRotationState.WAITING_NEUTRAL
                    handleNeutralState(yaw, pitch)
                } else {
                    updateInstructionsCallback(context.getString(R.string.instruction_look_straight))
                    circularOverlayView.updateCircleColor(COLOR_WHITE)
                }
            }

            HeadRotationState.WAITING_NEUTRAL -> {
                handleNeutralState(yaw, pitch)
            }

            HeadRotationState.INSTRUCTION_ROTATION_1 -> {
                updateInstructionsCallback(getRotationInstruction(rotation1Direction))
                circularOverlayView.updateCircleColor(COLOR_YELLOW)
                currentState = HeadRotationState.VALIDATING_ROTATION_1
            }

            HeadRotationState.VALIDATING_ROTATION_1 -> {
                handleRotationValidation(yaw, pitch, rotation1Direction, isFirstRotation = true)
            }

            HeadRotationState.INSTRUCTION_ROTATION_2 -> {
                updateInstructionsCallback(getRotationInstruction(rotation2Direction))
                circularOverlayView.updateCircleColor(COLOR_BLUE)
                currentState = HeadRotationState.VALIDATING_ROTATION_2
            }

            HeadRotationState.VALIDATING_ROTATION_2 -> {
                handleRotationValidation(yaw, pitch, rotation2Direction, isFirstRotation = false)
            }

            HeadRotationState.SUCCESS -> {
                // Already handled
            }

            HeadRotationState.FAILED -> {
                // Reset and retry
                resetState()
            }
        }
    }

    private fun handleNeutralState(yaw: Float, pitch: Float) {
        if (isNeutralPosition(yaw, pitch)) {
            if (!isNeutralDetected) {
                isNeutralDetected = true
                neutralStartTime = System.currentTimeMillis()
                onSessionVideoStart()
                updateInstructionsCallback(context.getString(R.string.instruction_keep_natural))
                circularOverlayView.updateCircleColor(COLOR_WHITE)
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - neutralStartTime > NEUTRAL_HOLD_DURATION_MS) {
                    captureCallback(false)
                    currentState = HeadRotationState.INSTRUCTION_ROTATION_1
                }
            }
        } else {
            updateInstructionsCallback(context.getString(R.string.instruction_look_straight))
            circularOverlayView.updateCircleColor(COLOR_WHITE)
            isNeutralDetected = false
            neutralStartTime = 0
        }
    }

    private fun handleRotationValidation(yaw: Float, pitch: Float, direction: HeadRotationDirection, isFirstRotation: Boolean) {
        val isCorrectRotation = when (direction) {
            HeadRotationDirection.LEFT -> yaw > LEFT_ROTATION_THRESHOLD
            HeadRotationDirection.RIGHT -> yaw < RIGHT_ROTATION_THRESHOLD
            HeadRotationDirection.UP -> pitch < UP_ROTATION_THRESHOLD
            HeadRotationDirection.DOWN -> pitch > DOWN_ROTATION_THRESHOLD
        }

        if (isCorrectRotation) {
            if (!isHoldingRotation) {
                isHoldingRotation = true
                rotationHoldStartTime = System.currentTimeMillis()
                updateInstructionsCallback(context.getString(R.string.instruction_hold_position))
                circularOverlayView.updateCircleColor(COLOR_GREEN)
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - rotationHoldStartTime > ROTATION_HOLD_DURATION_MS) {
                    isHoldingRotation = false
                    rotationHoldStartTime = 0
                    
                    if (isFirstRotation) {
                        rotation1Validated = true
                        currentState = HeadRotationState.INSTRUCTION_ROTATION_2
                    } else {
                        rotation2Validated = true
                        handleSuccess()
                    }
                }
            }
        } else {
            isHoldingRotation = false
            rotationHoldStartTime = 0
            updateInstructionsCallback(getRotationInstruction(direction))
            circularOverlayView.updateCircleColor(if (isFirstRotation) COLOR_YELLOW else COLOR_BLUE)
        }
    }

    private fun handleSuccess() {
        currentState = HeadRotationState.SUCCESS
        updateInstructionsCallback(context.getString(R.string.message_hold_still))
        circularOverlayView.updateCircleColor(COLOR_GREEN)
        captureCallback(true)
        handler.postDelayed({
            onSessionVideoStop()
        }, 500)
    }

    private fun isNeutralPosition(yaw: Float, pitch: Float): Boolean {
        return yaw > NEUTRAL_MIN_YAW && yaw < NEUTRAL_MAX_YAW &&
                pitch > NEUTRAL_MIN_PITCH && pitch < NEUTRAL_MAX_PITCH
    }

    private fun getRotationInstruction(direction: HeadRotationDirection): String {
        return when (direction) {
            HeadRotationDirection.LEFT -> context.getString(R.string.instruction_turn_head_left)
            HeadRotationDirection.RIGHT -> context.getString(R.string.instruction_turn_head_right)
            HeadRotationDirection.UP -> context.getString(R.string.instruction_look_up)
            HeadRotationDirection.DOWN -> context.getString(R.string.instruction_look_down)
        }
    }

    private fun resetState() {
        currentState = HeadRotationState.DETECTING_FACE
        isNeutralDetected = false
        neutralStartTime = 0
        rotation1Validated = false
        rotation2Validated = false
        isHoldingRotation = false
        rotationHoldStartTime = 0
    }

    private fun isFaceWithinCircle(boundingBox: Rect): Boolean {
        val mappedCenterX = mapX(boundingBox.centerX())
        val mappedCenterY = mapY(boundingBox.centerY())
        val overlayCenterX = overlay.width / 2f
        val overlayCenterY = overlay.height / 2f
        val dx = (mappedCenterX - overlayCenterX).toDouble()
        val dy = (mappedCenterY - overlayCenterY).toDouble()
        val distance = sqrt(dx.pow(2.0) + dy.pow(2.0))
        return distance <= DISTANCE_THRESHOLD
    }
    
    private fun isFaceWithinCircleLenient(boundingBox: Rect): Boolean {
        val mappedCenterX = mapX(boundingBox.centerX())
        val mappedCenterY = mapY(boundingBox.centerY())
        val overlayCenterX = overlay.width / 2f
        val overlayCenterY = overlay.height / 2f
        val dx = (mappedCenterX - overlayCenterX).toDouble()
        val dy = (mappedCenterY - overlayCenterY).toDouble()
        val distance = sqrt(dx.pow(2.0) + dy.pow(2.0))
        return distance <= DISTANCE_THRESHOLD_LENIENT
    }

    private fun mapX(imageX: Int): Float = imageX * overlay.width / imageHeight.toFloat()
    private fun mapY(imageY: Int): Float = imageY * overlay.height / imageWidth.toFloat()

    companion object {
        private const val TAG = "HeadRotationAnalyzer"
        private const val MIN_FACE_SIZE_THRESHOLD = 100
        private const val MAX_FACE_SIZE_THRESHOLD = 500
        private const val DISTANCE_THRESHOLD = 150f
        private const val DISTANCE_THRESHOLD_LENIENT = 280f
        
        // Neutral position thresholds - wider tolerance
        private const val NEUTRAL_MIN_YAW = -15f
        private const val NEUTRAL_MAX_YAW = 15f
        private const val NEUTRAL_MIN_PITCH = -15f
        private const val NEUTRAL_MAX_PITCH = 15f
        
        // Rotation detection thresholds - easier for users
        private const val LEFT_ROTATION_THRESHOLD = 15f    // eulerY > 15 means head turned left
        private const val RIGHT_ROTATION_THRESHOLD = -15f  // eulerY < -15 means head turned right
        private const val UP_ROTATION_THRESHOLD = -10f     // eulerX < -10 means looking up
        private const val DOWN_ROTATION_THRESHOLD = 10f    // eulerX > 10 means looking down
        
        // Timing
        private const val NEUTRAL_HOLD_DURATION_MS = 1000L
        private const val ROTATION_HOLD_DURATION_MS = 300L
        
        // Colors
        private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        private const val COLOR_YELLOW = 0xFFFFD600.toInt()
        private const val COLOR_GREEN = 0xFF00FF00.toInt()
        private const val COLOR_BLUE = 0xFF2196F3.toInt()
    }
}
