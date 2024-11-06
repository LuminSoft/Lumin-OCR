package com.luminsoft.ocr.camera

import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.luminsoft.ocr.graphic.CircularOverlayView
import com.luminsoft.ocr.graphic.GraphicOverlay

class CameraAnalyzer(
    private val overlay: GraphicOverlay<*>,
    private val circularOverlayView: CircularOverlayView,
    private val captureCallback: () -> Unit,
    private val updateInstructionsCallback: (String) -> Unit
) : BaseCameraAnalyzer<List<Face>>() {

    // Variables to store image dimensions
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    // Initialize the FaceDetector
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f) // Adjust based on your needs
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

    override fun onSuccess(
        results: List<Face>,
        graphicOverlay: GraphicOverlay<*>,
        rect: Rect
    ) {
        graphicOverlay.clear()

        when {
            results.isEmpty() -> {
                updateInstructionsCallback("No face detected")
            }
            results.size > 1 -> {
                updateInstructionsCallback("Only one face required")
            }
            results.size == 1 -> {
                val face = results[0]

                // Check if the face is within the circular overlay
                if (isFaceWithinCircle(face.boundingBox)) {
                    val faceWidth = face.boundingBox.width()

                    // Check if the user is looking straight based on head Euler angles
                    val yaw = face.headEulerAngleY // Left-right angle
                    val pitch = face.headEulerAngleX // Up-down angle

                    // Set thresholds for yaw and pitch to determine if the user is looking straight
                    if (yaw < -10 || yaw > 10) {
                        updateInstructionsCallback("Please look straight")
                    } else if (pitch < -15 || pitch > 15) {
                        updateInstructionsCallback("Please look straight")
                    } else {
                        // Check other face conditions
                        when {
                            faceWidth < MIN_FACE_SIZE_THRESHOLD -> {
                                updateInstructionsCallback("Move closer")
                            }
                            faceWidth > MAX_FACE_SIZE_THRESHOLD -> {
                                updateInstructionsCallback("Move back a bit")
                            }
                            face.smilingProbability != null && face.smilingProbability!! > 0.8 -> {
                                updateInstructionsCallback("Good, Hold still")
                                circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#00ff00"))
                                captureCallback() // Trigger capture
                            }
                            else -> {
                                updateInstructionsCallback("Please smile")
                                circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#FFFFFF"))
                            }
                        }
                    }
                } else {
                    updateInstructionsCallback("Center your face")
                    circularOverlayView.updateCircleColor(android.graphics.Color.parseColor("#FFFFFF"))
                }
            }
        }

        graphicOverlay.postInvalidate()
    }

    // Helper function to check if the face is within the circular overlay
    private fun isFaceWithinCircle(boundingBox: Rect): Boolean {
        // Get the face bounding box center coordinates (in image space)
        val faceCenterX = boundingBox.centerX()
        val faceCenterY = boundingBox.centerY()

        // Map the face center coordinates to the overlay view space
        val mappedCenterX = mapX(faceCenterX)
        val mappedCenterY = mapY(faceCenterY)

        // Get the center of the circular overlay (assumed to be in the middle of the view)
        val overlayCenterX = overlay.width / 2f
        val overlayCenterY = overlay.height / 2f

        // Calculate the distance between the mapped face center and the overlay center
        val distance = Math.sqrt(
            Math.pow((mappedCenterX - overlayCenterX).toDouble(), 2.0) +
                    Math.pow((mappedCenterY - overlayCenterY).toDouble(), 2.0)
        )

        // Check if the distance is within the circle's radius
        return distance <= circleRadius
    }

    // Helper function to map X-coordinate from image space to overlay view space
    private fun mapX(imageX: Int): Float {
        return imageX * overlay.width / imageWidth.toFloat()
    }

    // Helper function to map Y-coordinate from image space to overlay view space
    private fun mapY(imageY: Int): Float {
        return imageY * overlay.height / imageHeight.toFloat()
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


    companion object {
        private const val TAG = "com.luminsoft.ocr.camera.CameraAnalyzer"
        private const val MIN_FACE_SIZE_THRESHOLD = 150  // Adjust as needed
        private const val MAX_FACE_SIZE_THRESHOLD = 400  // Adjust as needed
        private const val circleRadius = 450f  // Radius of the circular overlay
    }
}
