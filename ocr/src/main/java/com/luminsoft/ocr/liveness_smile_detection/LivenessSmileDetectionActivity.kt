package com.luminsoft.ocr.liveness_smile_detection

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.luminsoft.ocr.databinding.ActivityLivenessSmileDetectionBinding

class LivenessSmileDetectionActivity : AppCompatActivity() {

    private lateinit var cameraManager: LivenessSmileCameraManager
    private val binding by lazy { ActivityLivenessSmileDetectionBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Initialize CameraManager with a callback to show both captured images
        cameraManager = LivenessSmileCameraManager(
            this,
            binding.viewCameraPreview,
            binding.viewGraphicOverlay,
            binding.circularOverlayView,
            this,
//            ::showCapturedImages // Pass callback to show both captured images
        )

        askCameraPermission()
    }

    fun updateInstructions(message: String) {
        runOnUiThread {
            binding.textInstructions.text = message
        }
    }

    private fun askCameraPermission() {
        if (arrayOf(android.Manifest.permission.CAMERA).all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            cameraManager.cameraStart()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0 && ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            cameraManager.cameraStart()
        } else {
            Toast.makeText(this, "Camera Permission Denied!", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Display the captured images (natural and smiling) in the ImageViews and stop the camera.
     */
    private fun showCapturedImages(naturalImage: Bitmap, smilingImage: Bitmap) {
        runOnUiThread {
            // Hide the camera preview and overlays
            binding.viewCameraPreview.visibility = View.GONE
            binding.viewGraphicOverlay.visibility = View.GONE
            binding.textInstructions.visibility = View.GONE

            // Display the captured images layout and set the images
            binding.capturedImagesLayout.visibility = View.VISIBLE
            binding.imageCapturedNatural.setImageBitmap(naturalImage)
            binding.imageCapturedSmiling.setImageBitmap(smilingImage)

            // Stop the camera as both images have been captured
            cameraManager.cameraStop()
        }
    }
}
