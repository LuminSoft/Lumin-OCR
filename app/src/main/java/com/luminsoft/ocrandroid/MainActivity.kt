package com.luminsoft.ocrandroid

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.luminsoft.ocr.OCR
import com.luminsoft.ocr.core.models.LocalizationCode
import com.luminsoft.ocr.core.models.OCRCallback
import com.luminsoft.ocr.core.models.OCREnvironment
import com.luminsoft.ocr.core.models.OCRFailedModel
import com.luminsoft.ocr.core.models.OCRMode
import com.luminsoft.ocr.core.models.OCRSuccessModel

import com.luminsoft.ocrandroid.ui.theme.OCRAndroidTheme

class MainActivity : ComponentActivity() {

    var text = mutableStateOf("")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            println("onCreate")
            val activity = LocalContext.current as Activity

            OCRAndroidTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {

                    Button(
                        modifier = Modifier.width(240.dp),
                        onClick = {
                            initOCR(activity, OCRMode.SMILE_LIVENESS)
                        },
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "Smile Liveness Check",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Button(
                        modifier = Modifier.width(240.dp),

                        onClick = {
                            initOCR(activity, OCRMode.NATIONAL_ID_DETECTION)
                        },
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "Detect National ID",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }

    private fun initOCR(
        activity: Activity,
        ocrMode: OCRMode
    ) {

        try {

            OCR.init(
                environment = OCREnvironment.STAGING,
                ocrMode = ocrMode,
                ocrCallback = object :
                    OCRCallback {
                    override fun success(ocrSuccessModel: OCRSuccessModel) {
                        text.value =
                            "OCR Message: ${ocrSuccessModel.ocrMessage}"
                    }

                    override fun error(ocrFailedModel: OCRFailedModel) {
                        text.value = ocrFailedModel.failureMessage

                    }
                },
                localizationCode = LocalizationCode.EN,
            )
        } catch (e: Exception) {
            Log.e("error", e.toString())
        }
        try {
            OCR.launch(activity)
        } catch (e: Exception) {
            Log.e("error", e.toString())
        }
    }
}
