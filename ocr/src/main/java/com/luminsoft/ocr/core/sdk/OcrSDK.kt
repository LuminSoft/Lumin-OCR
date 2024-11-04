package com.luminsoft.ocr.core.sdk

import com.luminsoft.ocr.core.models.LocalizationCode
import com.luminsoft.ocr.core.models.OCRCallback
import com.luminsoft.ocr.core.models.OCREnvironment


object OcrSDK {


    // this info related to sdk initiation
    var environment = OCREnvironment.STAGING
    var localizationCode = LocalizationCode.AR


    var ocrCallback: OCRCallback? = null

    private fun getBaseUrl(): String {
        return when (environment) {
            OCREnvironment.STAGING -> "http://197.168.1.39"
            OCREnvironment.PRODUCTION -> "https://ocr.nasps.org.eg"
        }
    }

    fun getApisUrl(): String {
        return if (environment == OCREnvironment.STAGING)
            getBaseUrl() + ":4800"
        else getBaseUrl() + ":7400/OnBoarding/"
    }

}