package com.luminsoft.ocr.head_rotation_liveness

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.luminsoft.ocr.R
import com.luminsoft.ocr.core.graphic.CircularOverlayView
import com.luminsoft.ocr.core.graphic.GraphicOverlay
import com.luminsoft.ocr.core.models.OCRFailedModel
import com.luminsoft.ocr.core.models.OCRSuccessModel
import com.luminsoft.ocr.core.sdk.OcrSDK
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HeadRotationCameraManager(
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

    // images
    private var neutralExpressionImage: Bitmap? = null
    private var finalRotationImage: Bitmap? = null

    // video
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null
    private var lastSavedVideoUri: Uri? = null

    private var discardNextFinalize = false

    // flag to wait for video before sending success
    private var pendingSuccessUntilVideo = false

    fun cameraStart() {
        val cameraProcessProvider = ProcessCameraProvider.getInstance(context)

        cameraProcessProvider.addListener(
            {
                cameraProvider = cameraProcessProvider.get()
                preview = Preview.Builder()
                    .setTargetRotation(Surface.ROTATION_0)
                    .build()

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(Surface.ROTATION_0)
                    .build()

                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(Surface.ROTATION_0)
                    .build()
                    .also {
                        it.setAnalyzer(
                            cameraExecutor,
                            HeadRotationCameraAnalyzer(
                                context,
                                graphicOverlay,
                                circularOverlayView,
                                ::captureImage,
                                (context as HeadRotationLivenessActivity)::updateInstructions,
                                { startRecording() },
                                { stopRecording() }
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

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.SD,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.LOWEST)
                    )
                )
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis,
                videoCapture
            )
            preview.setSurfaceProvider(previewView.surfaceProvider)
        } catch (e: Exception) {
            Log.e(TAG, "setCameraConfig : $e")
        }
    }

    private fun captureImage(isFinalCapture: Boolean) {
        if (!this::imageCapture.isInitialized || isCallbackExecuted && finalRotationImage != null) return

        val photoFile = File(
            context.filesDir,
            "${if (isFinalCapture) "rotation_final" else "rotation_neutral"}_face_${System.currentTimeMillis()}.jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (isCallbackExecuted && finalRotationImage != null) return
                    Log.i(TAG, "Image captured: ${photoFile.absolutePath}")

                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val correctedBitmap = adjustBitmapIfNeeded(photoFile.absolutePath, bitmap)

                    if (isFinalCapture) {
                        finalRotationImage = correctedBitmap
                    } else {
                        neutralExpressionImage = correctedBitmap
                    }

                    // Check if we have both photos for final success
                    if (neutralExpressionImage != null && finalRotationImage != null) {
                        // If the video URI is already ready, send immediately
                        if (lastSavedVideoUri != null) {
                            sendSuccessAndFinish()
                        } else {
                            // otherwise, wait for video to finalize
                            pendingSuccessUntilVideo = true
                            stopRecording()
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

                    (context as HeadRotationLivenessActivity).run {
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
            matrix.postScale(-1f, 1f)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun cameraStop() {
        currentRecording?.stop()
        currentRecording = null
        cameraProvider.unbindAll()
    }

    private fun startRecording() {
        Log.d("startRecording", "startRecording - HeadRotation")
        val vc = videoCapture ?: return

        if (currentRecording != null) {
            Log.w(TAG, "Recording already in progress, stopping previous recording")
            discardNextFinalize = true
            currentRecording?.stop()
            currentRecording = null
        }

        val tempVideoFile = File(context.cacheDir, "head_rotation_liveness_${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(tempVideoFile).build()

        val recording = vc.output.prepareRecording(context, outputOptions)
        currentRecording = recording.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    if (event.hasError()) {
                        Log.e(TAG, "Video finalize error: ${event.error}")
                    } else {
                        val uri = event.outputResults.outputUri

                        if (discardNextFinalize) {
                            discardNextFinalize = false
                            try {
                                val discardFile = File(Uri.parse(uri.toString()).path ?: "")
                                if (discardFile.exists()) discardFile.delete()
                                Log.i(TAG, "Discarded old recording")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to delete discarded recording", e)
                            }
                            return@start
                        }

                        lastSavedVideoUri = Uri.fromFile(tempVideoFile)
                        Log.i(TAG, "Video recorded to cache: ${tempVideoFile.absolutePath}")

                        // if we were waiting for the video to send success, do it now
                        if (pendingSuccessUntilVideo &&
                            neutralExpressionImage != null &&
                            finalRotationImage != null &&
                            !isCallbackExecuted
                        ) {
                            sendSuccessAndFinish()
                        }
                    }
                }
            }
        }
    }

    private fun stopRecording() {
        currentRecording?.stop()
        currentRecording = null
    }

    private fun sendSuccessAndFinish() {
        if (isCallbackExecuted) return
        isCallbackExecuted = true

        OcrSDK.ocrCallback?.success(
            OCRSuccessModel(
                naturalExpressionImage = neutralExpressionImage,
                livenessSmileExpressionImage = finalRotationImage,
                livenessVideoUri = lastSavedVideoUri,
                ocrMessage = context.getString(R.string.head_rotation_success),
            )
        )

        (context as HeadRotationLivenessActivity).run {
            ContextCompat.getMainExecutor(this).execute {
                cameraStop()
                finish()
            }
        }
    }

    companion object {
        private const val TAG: String = "HeadRotationCameraManager"
        var cameraOption: Int = CameraSelector.LENS_FACING_FRONT
    }
}
