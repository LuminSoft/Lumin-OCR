package com.luminsoft.ocr.liveness_smile_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import com.luminsoft.ocr.R
import com.luminsoft.ocr.core.graphic.CircularOverlayView
import com.luminsoft.ocr.core.graphic.GraphicOverlay
import com.luminsoft.ocr.core.models.LivenessMovementScores
import com.luminsoft.ocr.core.models.OCRFailedModel
import com.luminsoft.ocr.core.models.OCRSuccessModel
import com.luminsoft.ocr.core.sdk.OcrSDK
import android.os.Handler
import android.os.Looper
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
    private var currentVideoFile: File? = null

    private var discardNextFinalize = false

    // flag to say "I have the photos, wait for video then send success"
    private var pendingSuccessUntilVideo = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val videoTimeoutRunnable = Runnable {
        Log.w(TAG, "Video finalize timeout - forcing completion")
        if (!isCallbackExecuted) {
            currentVideoFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    lastSavedVideoUri = Uri.fromFile(file)
                }
            }
            sendSuccessAndFinish()
        }
    }
    
    private var livenessAnalyzer: LivenessSmileCameraAnalyzer? = null

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
                        livenessAnalyzer = LivenessSmileCameraAnalyzer(
                            context,
                            graphicOverlay,
                            circularOverlayView,
                            ::captureImage,
                            (context as LivenessSmileDetectionActivity)::updateInstructions,
                            { startRecording() },
                            { stopRecording() }
                        )
                        it.setAnalyzer(cameraExecutor, livenessAnalyzer!!)
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
                        Quality.LOWEST,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.LOWEST)
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
                            mainHandler.removeCallbacks(videoTimeoutRunnable)
                            sendSuccessAndFinish()
                        } else {
                            // otherwise, tell the recorder: “when you finalize, send success”
                            pendingSuccessUntilVideo = true
                            stopRecording()
                            // Safety timeout: if video doesn't finalize in 5s, force completion
                            mainHandler.postDelayed(videoTimeoutRunnable, 5000)
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
            discardNextFinalize = true
            currentRecording?.stop()
            currentRecording = null
        }

        currentVideoFile = File(context.cacheDir, "liveness_${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(currentVideoFile!!).build()

        val recording = vc.output.prepareRecording(context, outputOptions)
        currentRecording = recording.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    if (discardNextFinalize) {
                        discardNextFinalize = false
                        try {
                            val uri = event.outputResults.outputUri
                            val discardFile = File(Uri.parse(uri.toString()).path ?: "")
                            if (discardFile.exists()) discardFile.delete()
                            Log.i(TAG, "Discarded old recording")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to delete discarded recording", e)
                        }
                        return@start
                    }

                    if (event.hasError()) {
                        Log.e(TAG, "Video finalize error: ${event.error}")
                    }

                    // Set video URI regardless of error - file may have usable data
                    currentVideoFile?.let { file ->
                        if (file.exists() && file.length() > 0) {
                            lastSavedVideoUri = Uri.fromFile(file)
                            Log.i(TAG, "Video recorded to cache: ${file.absolutePath}")
                        } else {
                            Log.w(TAG, "Video file missing or empty")
                        }
                    }

                    // if we were waiting for the video to send success, do it now
                    if (pendingSuccessUntilVideo &&
                        naturalExpressionImage != null &&
                        smilingImage != null &&
                        !isCallbackExecuted
                    ) {
                        mainHandler.removeCallbacks(videoTimeoutRunnable)
                        sendSuccessAndFinish()
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
        
        // Safety check: ensure both images are captured before finishing
        if (naturalExpressionImage == null || smilingImage == null) {
            Log.w(TAG, "⚠️ Cannot finish - missing images. Natural: ${naturalExpressionImage != null}, Smiling: ${smilingImage != null}")
            return
        }
        
        if (lastSavedVideoUri == null) {
            Log.w(TAG, "⚠️ Missing video URI - proceeding without video")
        }
        
        isCallbackExecuted = true
        mainHandler.removeCallbacks(videoTimeoutRunnable)
        
        // Get movement scores from analyzer
        val movementScores = livenessAnalyzer?.let { analyzer ->
            val scores = analyzer.getMovementScores()
            val types = analyzer.getMovementTypes()
            LivenessMovementScores(
                movement1Score = scores.first,
                movement2Score = scores.second,
                movement3Score = scores.third,
                movement1Type = types.first,
                movement2Type = types.second,
                movement3Type = types.third
            )
        }
        
        Log.i(TAG, "✅ All captures complete - sending success")
        Log.i(TAG, "=== SCORE DEBUG OCR === Movement scores: ${movementScores?.movement1Score}, ${movementScores?.movement2Score}, ${movementScores?.movement3Score}")
        Log.i(TAG, "=== SCORE DEBUG OCR === Average score: ${movementScores?.getAverageScore()}")
        Log.i(TAG, "=== SCORE DEBUG OCR === movementScores is null: ${movementScores == null}")

        OcrSDK.ocrCallback?.success(
            OCRSuccessModel(
                naturalExpressionImage = naturalExpressionImage,
                livenessSmileExpressionImage = smilingImage,
                livenessVideoUri = lastSavedVideoUri,
                ocrMessage = context.getString(R.string.captured_successfully),
                livenessMovementScores = movementScores
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
