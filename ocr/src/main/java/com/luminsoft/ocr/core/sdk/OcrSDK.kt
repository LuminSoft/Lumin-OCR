package com.luminsoft.ocr.core.sdk

import com.luminsoft.ocr.core.models.LocalizationCode
import com.luminsoft.ocr.core.models.OCRCallback
import com.luminsoft.ocr.core.models.OCREnvironment
import com.luminsoft.ocr.core.models.OCRMode


object OcrSDK {

    // this info related to sdk initiation
    var environment = OCREnvironment.STAGING
    var localizationCode = LocalizationCode.EN
    var ocrMode = OCRMode.SMILE_LIVENESS

    var ocrCallback: OCRCallback? = null
    var licenseResource = 0
    var packageId = ""

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