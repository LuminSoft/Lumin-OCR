package com.luminsoft.ocr.core.models

import android.graphics.Bitmap
import android.net.Uri

data class OCRSuccessModel(
    val ocrMessage: String,
    val naturalExpressionImage: Bitmap? = null,
    val livenessSmileExpressionImage: Bitmap? = null,
    val nationalIdImage: Bitmap? = null,
    val passportImage: Bitmap? = null,
    val livenessVideoUri: Uri? = null,
    val livenessMovementScores: LivenessMovementScores? = null,
)

data class LivenessMovementScores(
    val movement1Score: Double = 0.0,
    val movement2Score: Double = 0.0,
    val movement3Score: Double = 0.0,
    val movement1Type: String = "",
    val movement2Type: String = "",
    val movement3Type: String = "",
) {
    fun getAverageScore(): Double {
        return (movement1Score + movement2Score + movement3Score) / 3.0
    }
}
