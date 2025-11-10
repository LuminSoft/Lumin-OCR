package com.luminsoft.ocr.liveness_smile_detection

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import androidx.camera.video.MediaStoreOutputOptions
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

    // images
    private var naturalExpressionImage: Bitmap? = null
    private var smilingImage: Bitmap? = null

    // video
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null
    private var lastSavedVideoUri: Uri? = null

    // NEW: flag to say “I have the photos, wait for video then send success”
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
                            LivenessSmileCameraAnalyzer(
                                context,
                                graphicOverlay,
                                circularOverlayView,
                                ::captureImage,
                                (context as LivenessSmileDetectionActivity)::updateInstructions,
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
                    Log.i(TAG, "Image captured: ${photoFile.absolutePath}")

                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val correctedBitmap = adjustBitmapIfNeeded(photoFile.absolutePath, bitmap)

                    if (isSmiling) {
                        smilingImage = correctedBitmap
                    } else {
                        naturalExpressionImage = correctedBitmap
                    }

                    // Now check if we have both photos
                    if (naturalExpressionImage != null && smilingImage != null) {
                        // If the video URI is already ready, send immediately
                        if (lastSavedVideoUri != null) {
                            sendSuccessAndFinish()
                        } else {
                            // otherwise, tell the recorder: “when you finalize, send success”
                            pendingSuccessUntilVideo = true
                            // stop recording to trigger Finalize
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
        Log.d("startRecording", "startRecording")
        val vc = videoCapture ?: return

        if (currentRecording != null) {
            Log.w(TAG, "Recording already in progress, stopping previous recording")
            currentRecording?.stop()
            currentRecording = null
        }

        val name = "liveness_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/LuminOCR")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val recording = vc.output.prepareRecording(context, outputOptions)
        currentRecording = recording.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    if (event.hasError()) {
                        Log.e(TAG, "Video finalize error: ${event.error}")
                    } else {
                        lastSavedVideoUri = event.outputResults.outputUri
                        Log.i(TAG, "Video saved: $lastSavedVideoUri")

                        // if we were waiting for the video to send success, do it now
                        if (pendingSuccessUntilVideo &&
                            naturalExpressionImage != null &&
                            smilingImage != null &&
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

    // NEW: single place to build the model & finish the activity
    private fun sendSuccessAndFinish() {
        if (isCallbackExecuted) return
        isCallbackExecuted = true

        OcrSDK.ocrCallback?.success(
            OCRSuccessModel(
                naturalExpressionImage = naturalExpressionImage,
                livenessSmileExpressionImage = smilingImage,
                livenessVideoUri = lastSavedVideoUri,
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

    companion object {
        private const val TAG: String = "CameraManager"
        var cameraOption: Int = CameraSelector.LENS_FACING_FRONT
    }
}
