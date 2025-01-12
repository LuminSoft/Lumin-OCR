package com.luminsoft.ocr.passport_detection

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_BASE
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.luminsoft.ocr.R
import com.luminsoft.ocr.core.models.OCRSuccessModel
import com.luminsoft.ocr.core.sdk.OcrSDK

class PassportDetectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(RESULT_FORMAT_JPEG)
            .setScannerMode(SCANNER_MODE_BASE)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)

        val scannerLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val imageUris = scanningResult?.pages?.map { it.imageUri } ?: emptyList()

                // If the imageUris is not empty, process the first image
                if (imageUris.isNotEmpty()) {
                    processScannedImage(imageUris.first())
                } else {
                    handleScanFailure()
                }
            } else {
                // Handle cancellation or failure
                handleScanFailure()
            }
        }

        // Automatically trigger the scanner when the activity starts
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                // Handle failure case here
                handleScanFailure()
            }
    }

    private fun processScannedImage(imageUri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            OcrSDK.ocrCallback?.success(
                OCRSuccessModel(
                    passportImage = bitmap,
                    ocrMessage = getString(R.string.captured_successfully),
                )
            )
            finish() // Close the activity
        } catch (e: Exception) {
            e.printStackTrace()
            handleScanFailure()
        }
    }

    private fun handleScanFailure() {
        // Handle the failure (e.g., show a log or error message)
        Log.e("PassportDetection", "Scanning failed or was cancelled.")
    }
}
