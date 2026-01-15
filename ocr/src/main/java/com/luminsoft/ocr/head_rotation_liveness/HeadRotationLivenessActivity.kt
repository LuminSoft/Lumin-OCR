package com.luminsoft.ocr.head_rotation_liveness

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.luminsoft.ocr.databinding.ActivityHeadRotationLivenessBinding

class HeadRotationLivenessActivity : AppCompatActivity() {

    private lateinit var cameraManager: HeadRotationCameraManager
    private val binding by lazy { ActivityHeadRotationLivenessBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        cameraManager = HeadRotationCameraManager(
            this,
            binding.viewCameraPreview,
            binding.viewGraphicOverlay,
            binding.circularOverlayView,
            this,
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
}
