package com.luminsoft.ocr

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import com.luminsoft.ocr.core.models.LocalizationCode
import com.luminsoft.ocr.core.models.OCRCallback
import com.luminsoft.ocr.core.models.OCREnvironment
import com.luminsoft.ocr.core.sdk.OcrSDK
import java.util.Locale

object OCR {
    @Throws(Exception::class)
    fun init(
        environment: OCREnvironment = OCREnvironment.STAGING,
        localizationCode: LocalizationCode = LocalizationCode.EN,
        ocrCallback: OCRCallback? = null,
    ) {

        OcrSDK.environment = environment
        OcrSDK.localizationCode = localizationCode
        OcrSDK.ocrCallback = ocrCallback
    }

    fun launch(
        activity: Activity,
    ) {
        Log.d("LaunchOCR", "OCR Launched Successfully")
        setLocale(OcrSDK.localizationCode, activity)

        activity.startActivity(Intent(activity, FaceDetectionActivity::class.java))


    }

    private fun setLocale(lang: LocalizationCode, activity: Activity) {
        val locale = if (lang != LocalizationCode.AR) {
            Locale("en")
        } else {
            Locale("ar")
        }
        Locale.setDefault(locale)

        val config = Configuration(activity.resources.configuration)
        config.setLocale(locale)

        activity.createConfigurationContext(config)

        // Apply for app-wide locale change if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            activity.applicationContext.createConfigurationContext(config)
        }
    }
}