package com.luminsoft.ocrandroid

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.luminsoft.ocr.OCR
import com.luminsoft.ocr.core.models.LocalizationCode
import com.luminsoft.ocr.core.models.OCRCallback
import com.luminsoft.ocr.core.models.OCREnvironment.STAGING
import com.luminsoft.ocr.core.models.OCRFailedModel
import com.luminsoft.ocr.core.models.OCRSuccessModel
import com.luminsoft.ocrandroid.ui.theme.OCRAndroidTheme

class MainActivity : ComponentActivity() {

    var text = mutableStateOf("")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val activity = LocalContext.current as Activity

            OCRAndroidTheme {
                Column {

                    Button(
                        modifier = Modifier
                            .padding(top = 200.dp)
                            .fillMaxWidth(0.9f),
                        onClick = {
                            initOCR(
                                activity
                            )
                        },
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "Launch OCR",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }

    private fun initOCR(
        activity: Activity,
    ) {

        try {
            OCR.init(
                environment = STAGING,
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
