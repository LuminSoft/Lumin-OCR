package com.luminsoft.ocr.national_id_detection
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.sqrt
import kotlin.math.max
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberImagePainter
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCamera2View
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.lang.Math.pow
import kotlin.math.abs
import kotlin.math.acos
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.luminsoft.ocr.R
import com.luminsoft.ocr.core.models.OCRSuccessModel
import com.luminsoft.ocr.core.sdk.OcrSDK
import com.luminsoft.ocr.natural_expression_detection.NaturalExpressionDetectionActivity
import org.opencv.android.Utils
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt

import java.lang.Math.pow
import kotlin.math.max
import kotlin.math.sqrt

class NationalIdDetection : ComponentActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var rgbaMat: Mat
    private lateinit var rgbaMatFinal: Mat


    // Add these properties to your MainActivity class
    private val BLUR_THRESHOLD = 220.0  // Adjust this value based on testing
    private var isBlurry = false

    // Compose state for the captured image path
    private var _capturedImagePath = mutableStateOf<String?>(null)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check and request camera permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("Permission", "Camera permission granted")
            setContentViewWithCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun setContentViewWithCamera() {
        setContent {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (OpenCVLoader.initDebug()) {
                        Log.d("OpenCV", "OpenCV loaded")



                        CameraPreviewWithCapturedImageView(
                            cameraViewListener = this@NationalIdDetection,
                            capturedImagePath = _capturedImagePath.value
                        )
                        Log.d("OpenCV", "OpenCV finished loading")
                    } else {

                        Toast.makeText(this@NationalIdDetection, "Failed to load OpenCV", Toast.LENGTH_SHORT).show()

                    }
                }
            }
        }
    }

    private fun requestCameraPermission() {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d("Permission", "Camera permission granted")
                setContentViewWithCamera()
            } else {
                Log.e("Permission", "Camera permission denied")
            }
        }.launch(Manifest.permission.CAMERA)
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        Log.d("CameraView", "onCameraViewStarted called")
        rgbaMat = Mat(height, width, CvType.CV_8UC4)
        Log.d("CameraView", "Camera view started with resolution: ${width}x${height}")
    }

    override fun onCameraViewStopped() {
        rgbaMat.release()
        Log.d("CameraView", "Camera view stopped")
    }

    // Add these utility functions at the class level
    private fun orderPoints(pts: Array<Point>): Array<Point> {
        val rect = Array(4) { Point() }

        // Calculate sum and difference of points
        val sums = pts.map { it.x + it.y }
        val diffs = pts.map { it.x - it.y }

        // Top-left point will have smallest sum
        rect[0] = pts[sums.indexOf(sums.minOrNull()!!)]
        // Bottom-right point will have largest sum
        rect[2] = pts[sums.indexOf(sums.maxOrNull()!!)]

        // Top-right point will have largest difference
        rect[1] = pts[diffs.indexOf(diffs.maxOrNull()!!)]
        // Bottom-left point will have smallest difference
        rect[3] = pts[diffs.indexOf(diffs.minOrNull()!!)]

        return rect
    }

    private fun fourPointTransform(original: Mat, pts: Array<Point>): Mat {
        // Order the points in the order: top-left, top-right, bottom-right, bottom-left
        val rect = orderPoints(pts)

        // Calculate width of new image
        val widthA = sqrt(pow(rect[2].x - rect[3].x, 2.0) + pow(rect[2].y - rect[3].y, 2.0))
        val widthB = sqrt(pow(rect[1].x - rect[0].x, 2.0) + pow(rect[1].y - rect[0].y, 2.0))
        val maxWidth = max(widthA, widthB).toInt()

        // Calculate height of new image
        val heightA = sqrt(pow(rect[1].x - rect[2].x, 2.0) + pow(rect[1].y - rect[2].y, 2.0))
        val heightB = sqrt(pow(rect[0].x - rect[3].x, 2.0) + pow(rect[0].y - rect[3].y, 2.0))
        val maxHeight = max(heightA, heightB).toInt()

        // Create destination points
        val dst = arrayOf(
            Point(0.0, 0.0),
            Point(maxWidth - 1.0, 0.0),
            Point(maxWidth - 1.0, maxHeight - 1.0),
            Point(0.0, maxHeight - 1.0)
        )

        // Calculate perspective transform
        val srcMat = MatOfPoint2f(*rect)
        val dstMat = MatOfPoint2f(*dst)
        val transformMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat)

        // Apply perspective transformation
        val warped = Mat()
        Imgproc.warpPerspective(
            original,
            warped,
            transformMatrix,
            Size(maxWidth.toDouble(), maxHeight.toDouble()),
            Imgproc.INTER_LANCZOS4
        )

        return warped
    }

    // Helper function to detect flash
    private fun detectFlash(roiMat: Mat): Boolean {
        // Calculate the brightness histogram
        val hist = Mat()
        Imgproc.calcHist(
            listOf(roiMat),
            MatOfInt(0),
            Mat(),
            hist,
            MatOfInt(256),
            MatOfFloat(0f, 256f)
        )

        // Sum of bright pixels near maximum intensity
        val brightPixels = Core.sumElems(hist.rowRange(240, 256)).`val`[0]

        // Threshold for flash detection (e.g., 10% of pixels are bright)
        val brightnessThreshold = 15
        return brightPixels > brightnessThreshold
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {

        rgbaMat = inputFrame.rgba()
        rgbaMatFinal = inputFrame.rgba()

        // Convert frame to RGB and Grayscale
        val rgbMat = Mat()
        Imgproc.cvtColor(rgbaMatFinal, rgbMat, Imgproc.COLOR_BGR2RGB)
        val grayMat = Mat()
        Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

        // Calculate the middle third region
        val height = grayMat.rows()
        val startY = height / 3
        val endY = 2 * height / 3

        // Create ROI (Region of Interest) for middle third
        val roi = Rect(0, startY, grayMat.cols(), endY - startY)
        val roiMat = Mat(grayMat, roi)

        // Detect if lighting is poor in ROI
        val lightingMessage = if (isPoorLighting(roiMat)) getString(R.string.message_more_light) else null

        // Apply blur to reduce noise
        Imgproc.GaussianBlur(roiMat, roiMat, Size(3.0, 3.0), 0.0)
        Imgproc.medianBlur(roiMat, roiMat, 5)

        // Edge detection and enhancement
        val edges = Mat()
        Imgproc.Canny(roiMat, edges, 20.0, 30.0)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.dilate(edges, edges, kernel, Point(-1.0, -1.0), 1)

        // Find contours
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edges,
            contours,
            hierarchy,
            Imgproc.RETR_LIST,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        // Initialize detection variables
        var cardDetected = false
        var detectedRect: Rect? = null
        var detectedCorners: Array<Point>? = null
        var flashDetected = false

        // Get guide corners from center rectangle
        val guideRect = getCenterRect()
        val guideCorners = arrayOf(
            guideRect.tl(),
            Point(guideRect.br().x, guideRect.tl().y),
            guideRect.br(),
            Point(guideRect.tl().x, guideRect.br().y)
        )

        // Process contours to find card
        for (contour in contours) {
            val approx = MatOfPoint2f()
            val contour2f = MatOfPoint2f(*contour.toArray())
            val perimeter = Imgproc.arcLength(contour2f, true)
            Imgproc.approxPolyDP(contour2f, approx, 0.06 * perimeter, true)

            if (approx.total() == 4L) {
                val points = approx.toArray()

                // Apply ROI offset and adjust corners
                val adjustedPoints = points.map { point ->
                    Point(point.x, point.y + startY)
                }.toTypedArray()

                // Calculate center for corner identification
                val centerX = adjustedPoints.map { it.x }.average()
                val centerY = adjustedPoints.map { it.y }.average()

                // Adjust corner positions
                for (i in adjustedPoints.indices) {
                    val point = adjustedPoints[i]
                    adjustedPoints[i] = when {
                        point.x > centerX && point.y > centerY ->
                            Point(point.x + 5, point.y + 10) // Bottom Right corner
                        point.x < centerX && point.y > centerY ->
                            Point(point.x - 5, point.y + 5) // Bottom Left corner
                        point.x > centerX && point.y < centerY ->
                            Point(point.x + 5, point.y - 5) // Top Right corner
                        else -> Point(point.x - 5, point.y - 10) // Top Left corner
                    }
                }

                // Check if detected rectangle matches expected card dimensions
                val rect = Imgproc.boundingRect(MatOfPoint(*approx.toArray()))
                val aspectRatio = rect.width.toDouble() / rect.height.toDouble()
                val idealAspectRatio = 85.60 / 53.98
                val tolerance = 0.1
                if (aspectRatio in (idealAspectRatio - tolerance)..(idealAspectRatio + tolerance)) {
                    val cornerDistances =
                        adjustedPoints.zip(guideCorners).map { (detected, guide) ->
                            getDistance(detected, guide)
                        }
                    if (cornerDistances.all { it < 35.0 }) {

                        detectedRect = Rect(rect.x, rect.y + startY, rect.width, rect.height)
                        val cardRoi = Mat(grayMat, detectedRect)
                        flashDetected = detectFlash(cardRoi)
                        if(flashDetected) {
                            break
                        }
                        cardRoi.release()
                        detectedCorners = adjustedPoints
                        cardDetected = true
                        break
                    }
                }
            }
        }

        // Visual feedback
        val color =
            if (cardDetected) Scalar(0.0, 255.0, 0.0, 255.0) else Scalar(255.0, 255.0, 255.0, 255.0)
        drawRectangleCorners(rgbaMat, guideRect, color)

        // Handle auto capture if card is detected
        if (cardDetected && detectedCorners != null) {
            handleAutoCapture(cardDetected, detectedCorners, rgbMat)
        }

        // Display appropriate message
        val message = when {
            flashDetected -> getString(R.string.message_avoid_reflection)
            lightingMessage != null -> lightingMessage
            cardDetected && isBlurry -> getString(R.string.message_hold_still_stabilize)
            cardDetected -> getString(R.string.message_hold_still)
            else -> getString(R.string.message_center_document)
        }
        displayCenteredText(rgbaMat, message, isBlurry)

        // Clean up resources
        roiMat.release()
        edges.release()
        hierarchy.release()

        return rgbaMat
    }



    // Updated distance calculation function for better accuracy
    private fun getDistance(p1: Point, p2: Point): Double {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return Math.sqrt(dx * dx + dy * dy)
    }


    // Helper to detect poor lighting by checking average brightness
    private fun isPoorLighting(grayMat: Mat): Boolean {
        val meanIntensity = Core.mean(grayMat).`val`[0] // Get the average pixel intensity
        val brightnessThreshold = 20.0 // Adjust this threshold based on testing
        return meanIntensity < brightnessThreshold
    }

    // Helper to draw only four corners of the rectangle
    private fun drawRectangleCorners(mat: Mat, rect: Rect, color: Scalar) {
        val cornerLength = 30 // Length of the corner lines

        // Top-left corner
        Imgproc.line(mat, rect.tl(), Point(rect.tl().x + cornerLength, rect.tl().y), color, 5)
        Imgproc.line(mat, rect.tl(), Point(rect.tl().x, rect.tl().y + cornerLength), color, 5)

        // Top-right corner
        Imgproc.line(
            mat,
            Point(rect.br().x, rect.tl().y),
            Point(rect.br().x - cornerLength, rect.tl().y),
            color,
            5
        )
        Imgproc.line(
            mat,
            Point(rect.br().x, rect.tl().y),
            Point(rect.br().x, rect.tl().y + cornerLength),
            color,
            5
        )

        // Bottom-left corner
        Imgproc.line(
            mat,
            Point(rect.tl().x, rect.br().y),
            Point(rect.tl().x + cornerLength, rect.br().y),
            color,
            5
        )
        Imgproc.line(
            mat,
            Point(rect.tl().x, rect.br().y),
            Point(rect.tl().x, rect.br().y - cornerLength),
            color,
            5
        )

        // Bottom-right corner
        Imgproc.line(mat, rect.br(), Point(rect.br().x - cornerLength, rect.br().y), color, 5)
        Imgproc.line(mat, rect.br(), Point(rect.br().x, rect.br().y - cornerLength), color, 5)
    }

    // Helper to get the center rectangle area
    private fun getCenterRect(): Rect {
        val centerX = rgbaMat.cols() / 2
        val centerY = rgbaMat.rows() / 2
        val rectWidth = (rgbaMat.cols() * 0.65).toInt()
        val rectHeight = (rgbaMat.rows() * 0.3).toInt()

        return Rect(
            centerX - rectWidth / 2,
            centerY - rectHeight / 2,
            rectWidth,
            rectHeight
        )
    }
    var stableFrameCount = 0
    private fun handleAutoCapture(cardDetected: Boolean, corners: Array<Point>, rgbMat: Mat) {
        // Counter for stable frames

        val stableFrameThreshold = 10 // Number of stable frames required

        if (cardDetected) {
            // Extract the card region
            val cardRegion = extractCardRegion(rgbMat, corners)

            // Check if the card region is blurry
            isBlurry = detectBlur(cardRegion)

            if (!isBlurry) {
                // Increment stable frame count
                stableFrameCount++
            } else {
                // Reset if blurry
                stableFrameCount = 0
            }

            // If stable frames exceed threshold, capture the image
            if (stableFrameCount >= stableFrameThreshold) {
                // Perform perspective transform
                val correctedImage = fourPointTransform(rgbMat, corners)
                val filename =
                    "${externalCacheDir?.absolutePath}/captured_card_${System.currentTimeMillis()}.png"

                // Save with high quality
                val params =
                    MatOfInt(Imgcodecs.IMWRITE_PNG_COMPRESSION, 0) // 0 = no compression
                Imgcodecs.imwrite(filename, correctedImage, params)

                Log.d("Capture", "Sharp image captured and saved as $filename")
                _capturedImagePath.value = filename

                // Reset stable frame counter
                stableFrameCount = 0
            }

            cardRegion.release()
        } else {
            // Reset counter if no card detected
            stableFrameCount = 0
        }
    }


    // Helper function to extract card region for blur detection
    private fun extractCardRegion(mat: Mat, corners: Array<Point>): Mat {
        val rect = Imgproc.boundingRect(MatOfPoint(*corners))
        return Mat(mat, rect)
    }

    // Add this function to detect image blur using Laplacian variance
    private fun detectBlur(mat: Mat): Boolean {
        val laplacian = Mat()
        val gray = Mat()

        // Convert to grayscale if needed
        if (mat.channels() > 1) {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        } else {
            mat.copyTo(gray)
        }

        // Calculate Laplacian variance
        Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
        val stdDev = MatOfDouble()
        Core.meanStdDev(laplacian, MatOfDouble(), stdDev)

        // Clean up
        laplacian.release()
        gray.release()

        // Calculate variance
        val variance = stdDev.get(0, 0)[0] * stdDev.get(0, 0)[0]
        Log.d("BlurDetection", "Image variance: $variance")

        return variance < BLUR_THRESHOLD
    }

    private fun displayCenteredText(mat: Mat, text: String, isBlurry: Boolean = false) {
        // Create a bitmap with the same size as the Mat
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap) // Convert Mat to Bitmap to retain original content

        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 32f // Adjust as needed
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL) // Ensure font supports Arabic
        }

        // Handle bidirectional text for Arabic
        val directionAdjustedText = "\u202B$text"

        // Calculate text position
        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f - (paint.descent() + paint.ascent()) / 2)

        // Draw the text over the original image
        canvas.drawText(directionAdjustedText, xPos, yPos, paint)

        // Convert the updated bitmap back to Mat
        Utils.bitmapToMat(bitmap, mat)
    }

}


@Composable
fun CameraPreviewWithCapturedImageView(
    cameraViewListener: CameraBridgeViewBase.CvCameraViewListener2,
    capturedImagePath: String?
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Transparent)
    ) {
        if (capturedImagePath == null) {
            AndroidView(
                factory = { context ->
                    val cameraView = JavaCamera2View(context, 0).apply {
                        visibility = SurfaceView.VISIBLE
                        setCameraIndex(0)  // Use rear camera or front camera
                        setCvCameraViewListener(cameraViewListener)
                        setCameraPermissionGranted()

                        // Get the highest available resolution
                        val maxResolution = getHighestResolution(context, cameraIndex = 0)
                        setMaxFrameSize(maxResolution.first, maxResolution.second)

                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                Log.d("CameraPreviewView", "Surface created")
                                enableView()
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) {
                                Log.d("CameraPreviewView", "Surface changed")
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                Log.d("CameraPreviewView", "Surface destroyed")
                                disableView()
                            }
                        })
                    }
                    cameraView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val scaledBitmap = decodeSampledBitmapFromFile(capturedImagePath, 800, 600)
            val bitmap = BitmapFactory.decodeFile(capturedImagePath)

            OcrSDK.ocrCallback?.success(
                OCRSuccessModel(
                    nationalIdImage = bitmap,
                    ocrMessage = context.getString(R.string.captured_successfully),
                )
            )

            (context as NationalIdDetection).finish()

        }
    }
}

fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    BitmapFactory.decodeFile(path, options)

    // Calculate the sample size
    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
    options.inJustDecodeBounds = false

    return BitmapFactory.decodeFile(path, options)
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}



// Function to get the highest available resolution for the camera
fun getHighestResolution(context: Context, cameraIndex: Int): Pair<Int, Int> {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = cameraManager.cameraIdList[cameraIndex]
    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
    val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val outputSizes = map?.getOutputSizes(SurfaceHolder::class.java)

    // Find the highest resolution available
    val highestResolution = outputSizes?.maxByOrNull { it.width * it.height }
    return if (highestResolution != null) {
        Pair(highestResolution.width, highestResolution.height)
    } else {
        Pair(1920, 1080) // Default to Full HD if no resolutions are found
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
