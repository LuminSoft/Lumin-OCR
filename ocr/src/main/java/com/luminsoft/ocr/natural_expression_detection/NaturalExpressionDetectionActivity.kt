package com.luminsoft.ocr.natural_expression_detection

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.luminsoft.ocr.core.models.OCRFailedModel
import com.luminsoft.ocr.core.sdk.OcrSDK
import com.luminsoft.ocr.databinding.ActivityNaturalExpressionDetectionBinding

class NaturalExpressionDetectionActivity : AppCompatActivity() {

    private lateinit var cameraManager: NaturalExpressionCameraManager
    private val binding by lazy { ActivityNaturalExpressionDetectionBinding.inflate(layoutInflater) }
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var callbackSent = false

    private val timeoutRunnable = Runnable {
        if (!callbackSent && !isFinishing) {
            callbackSent = true
            cameraManager.cameraStop()
            OcrSDK.ocrCallback?.error(
                OCRFailedModel("Session timed out")
            )
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        cameraManager = NaturalExpressionCameraManager(
            this,
            binding.viewCameraPreview,
            binding.viewGraphicOverlay,
            binding.circularOverlayView,
            this,
        )

        askCameraPermission()
        timeoutHandler.postDelayed(timeoutRunnable, SESSION_TIMEOUT_MS)
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!callbackSent) {
            callbackSent = true
            cameraManager.cameraStop()
            OcrSDK.ocrCallback?.error(
                OCRFailedModel("Capture cancelled")
            )
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        super.onDestroy()
    }

    companion object {
        private const val SESSION_TIMEOUT_MS = 30_000L
    }
}