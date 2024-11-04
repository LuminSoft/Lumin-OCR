package com.luminsoft.ocr.core.models


interface OCRCallback {
    fun success(ocrSuccessModel: OCRSuccessModel)
    fun error(ocrFailedModel: OCRFailedModel)
}

