package com.luminsoft.ocr

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import com.luminsoft.ocr.core.models.LocalizationCode
import com.luminsoft.ocr.core.models.OCRCallback
import com.luminsoft.ocr.core.models.OCREnvironment
import com.luminsoft.ocr.core.models.OCRMode
import com.luminsoft.ocr.core.sdk.OcrSDK
import com.luminsoft.ocr.liveness_smile_detection.LivenessSmileDetectionActivity
import com.luminsoft.ocr.national_id_detection.NationalIdDetection
import com.luminsoft.ocr.natural_expression_detection.NaturalExpressionDetectionActivity
import java.util.Locale

object OCR {
    @Throws(Exception::class)
    fun init(
        environment: OCREnvironment = OCREnvironment.STAGING,
        localizationCode: LocalizationCode = LocalizationCode.EN,
        ocrCallback: OCRCallback? = null,
        ocrMode: OCRMode = OCRMode.SMILE_LIVENESS,
    ) {

        OcrSDK.environment = environment
        OcrSDK.localizationCode = localizationCode
        OcrSDK.ocrCallback = ocrCallback
        OcrSDK.ocrMode = ocrMode
    }

    fun launch(
        activity: Activity,
    ) {
        Log.d("LaunchOCR", "OCR Launched Successfully for ${getModeActivity(OcrSDK.ocrMode)}")
        setLocale(OcrSDK.localizationCode, activity)
        val targetActivity = getModeActivity(OcrSDK.ocrMode)
        val intent = Intent(activity, targetActivity)
        Log.d("LaunchOCR", "Intent created for: ${targetActivity.name}")
        try {
            activity.startActivity(intent)
            Log.d("LaunchOCR", "startActivity called successfully")
        } catch (e: Exception) {
            Log.e("LaunchOCR", "Error starting activity: ${e.message}", e)
        }
    }


    private fun getModeActivity(ocrMode: OCRMode): Class<out Activity> {
        return when (ocrMode) {
            OCRMode.SMILE_LIVENESS -> LivenessSmileDetectionActivity::class.java
            OCRMode.NaturalExpressionDetection -> NaturalExpressionDetectionActivity::class.java
            OCRMode.PASSPORT_DETECTION -> NaturalExpressionDetectionActivity::class.java
            OCRMode.NATIONAL_ID_DETECTION -> NationalIdDetection::class.java
        }
    }

    private fun setLocale(lang: LocalizationCode, activity: Activity) {
        val locale = if (lang.name.lowercase() != LocalizationCode.AR.name.lowercase()) {
            Locale("en")
        } else {
            Locale("ar")
        }

        val config: Configuration = activity.baseContext.resources.configuration
        config.setLocale(locale)

        activity.baseContext.resources.updateConfiguration(
            config,
            activity.baseContext.resources.displayMetrics
        )
    }

}