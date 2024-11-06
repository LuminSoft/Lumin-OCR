package com.luminsoft.ocr

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.luminsoft.ocr.camera.CameraManager
import com.luminsoft.ocr.databinding.ActivityFaceDetectionBinding

class FaceDetectionActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private val binding by lazy { ActivityFaceDetectionBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        cameraManager = CameraManager(
            this,
            binding.viewCameraPreview,
            binding.viewGraphicOverlay,
            binding.circularOverlayView,
            this,
            ::showCapturedImage // Pass callback to show captured image
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
     * Display the captured image in the ImageView and stop the camera.
     */
    fun showCapturedImage(bitmap: Bitmap) {
        runOnUiThread {
            binding.viewCameraPreview.visibility = View.GONE
            binding.viewGraphicOverlay.visibility = View.GONE
            binding.imageCaptured.setImageBitmap(bitmap)
            binding.imageCaptured.visibility = View.VISIBLE

            cameraManager.cameraStop()
        }
    }
}