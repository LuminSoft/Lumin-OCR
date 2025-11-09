package com.luminsoft.ocr.national_id_detection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.luminsoft.ocr.R
import com.luminsoft.ocr.core.models.OCRSuccessModel
import com.luminsoft.ocr.core.sdk.OcrSDK

class NationalIdDetection : ComponentActivity() {

    // 1) ask for camera permission
    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openSystemCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    // 2) open system camera (simple preview → returns Bitmap)
    private val takePicturePreview =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            if (bitmap != null) {
                // 3) send back to SDK
                OcrSDK.ocrCallback?.success(
                    OCRSuccessModel(
                        nationalIdImage = bitmap,
                        ocrMessage = getString(R.string.captured_successfully)
                    )
                )
            } else {
                // user cancelled or camera failed
                Toast.makeText(this, "No image captured", Toast.LENGTH_SHORT).show()
            }
            // 4) close screen
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // check permission first
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openSystemCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openSystemCamera() {
        // this opens the built-in camera UI and returns a Bitmap
        takePicturePreview.launch(null)
    }
}
