package com.luminsoft.ocr.core.models

import android.graphics.Bitmap

data class OCRSuccessModel(
    val ocrMessage: String,
    val naturalExpressionImage: Bitmap? = null,
    val livenessSmileExpressionImage: Bitmap? = null,
    val nationalIdImage: Bitmap? = null,
    val passportImage: Bitmap? = null,
)
