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
)
