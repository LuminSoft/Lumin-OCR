package com.luminsoft.ocr.liveness_smile_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.luminsoft.ocr.R
import com.luminsoft.ocr.core.graphic.CircularOverlayView
import com.luminsoft.ocr.core.graphic.GraphicOverlay
import com.luminsoft.ocr.core.models.OCRFailedModel
import com.luminsoft.ocr.core.models.OCRSuccessModel
import com.luminsoft.ocr.core.sdk.OcrSDK
import com.luminsoft.ocr.natural_expression_detection.NaturalExpressionDetectionActivity
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class LivenessSmileCameraManager(
    private val context: Context,
    private val previewView: PreviewView,
    private val graphicOverlay: GraphicOverlay<*>,
    private val circularOverlayView: CircularOverlayView,
    private val lifecycleOwner: LifecycleOwner,
) {

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var preview: Preview
    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var camera: Camera
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var imageCapture: ImageCapture

    private var isCallbackExecuted = false

    // Bitmaps to store the natural and smiling expression images
    private var naturalExpressionImage: Bitmap? = null
    private var smilingImage: Bitmap? = null

    fun cameraStart() {
        val cameraProcessProvider = ProcessCameraProvider.getInstance(context)

        cameraProcessProvider.addListener(
            {
                cameraProvider = cameraProcessProvider.get()
                preview = Preview.Builder().build()

                // Initialize ImageCapture
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            cameraExecutor,
                            LivenessSmileCameraAnalyzer(
                                context,
                                graphicOverlay,
                                circularOverlayView,
                                ::captureImage, // Pass captureImage function to capture based on expression type
                                (context as LivenessSmileDetectionActivity)::updateInstructions
                            )
                        )
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraOption)
                    .build()

                setCameraConfig(cameraProvider, cameraSelector)
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun setCameraConfig(
        cameraProvider: ProcessCameraProvider,
        cameraSelector: CameraSelector
    ) {
        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis
            )
            preview.setSurfaceProvider(previewView.surfaceProvider)
        } catch (e: Exception) {
            Log.e(TAG, "setCameraConfig : $e")
        }
    }

    // Updated captureImage function to handle two images based on isSmiling flag
    private fun captureImage(isSmiling: Boolean) {
        if (!this::imageCapture.isInitialized || isCallbackExecuted && smilingImage != null) return

        val photoFile = File(
            context.filesDir,
            "${if (isSmiling) "smiling" else "natural"}_face_${System.currentTimeMillis()}.jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (isCallbackExecuted && smilingImage != null) return
                    isCallbackExecuted = true
                    Log.i(TAG, "Image captured: ${photoFile.absolutePath}")

                    // Decode the saved image into a Bitmap
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val correctedBitmap = adjustBitmapIfNeeded(photoFile.absolutePath, bitmap)

                    // Store the image based on the expression type
                    if (isSmiling) {
                        smilingImage = correctedBitmap
                    } else {
                        naturalExpressionImage = correctedBitmap
                    }

                    // If both images are captured, call the callback to display them
                    if (naturalExpressionImage != null && smilingImage != null) {
                        OcrSDK.ocrCallback?.success(
                            OCRSuccessModel(
                                naturalExpressionImage = naturalExpressionImage,
                                livenessSmileExpressionImage = smilingImage,
                                ocrMessage = context.getString(R.string.captured_successfully),
                            )
                        )

                        (context as LivenessSmileDetectionActivity).run {
                            ContextCompat.getMainExecutor(this).execute {
                                cameraStop()
                                finish()
                            }
                        }

                    }
                }

                override fun onError(exception: ImageCaptureException) {

                    if (isCallbackExecuted) return
                    isCallbackExecuted = true

                    OcrSDK.ocrCallback?.error(
                        OCRFailedModel(
                            exception.message.toString(),
                            exception.message
                        )
                    )

                    (context as LivenessSmileDetectionActivity).run {
                        ContextCompat.getMainExecutor(this).execute {
                            cameraStop()
                            finish()
                        }
                    }
                    Log.e(TAG, "Image capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    private fun adjustBitmapIfNeeded(imagePath: String, bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(imagePath)
        val rotationDegrees = when (exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        val isFrontCamera = cameraOption == CameraSelector.LENS_FACING_FRONT
        val matrix = Matrix()

        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }

        if (isFrontCamera) {
            matrix.postScale(-1f, 1f) // Mirror horizontally for front camera
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun cameraStop() {
        cameraProvider.unbindAll()
    }

    companion object {
        private const val TAG: String = "CameraManager"
        var cameraOption: Int = CameraSelector.LENS_FACING_FRONT
    }
}



