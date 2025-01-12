package com.luminsoft.ocrandroid


import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.luminsoft.ocr.LocalizationCode
import com.luminsoft.ocr.OCR
import com.luminsoft.ocr.core.models.OCRCallback
import com.luminsoft.ocr.core.models.OCREnvironment
import com.luminsoft.ocr.core.models.OCRFailedModel
import com.luminsoft.ocr.core.models.OCRMode
import com.luminsoft.ocr.core.models.OCRSuccessModel
import com.luminsoft.ocrandroid.ui.theme.OCRAndroidTheme


var isArabic = mutableStateOf(false)


class MainActivity : ComponentActivity() {

    var text = mutableStateOf("")

    @Composable
    fun ArabicCheckbox() {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isArabic.value,
                onCheckedChange = { isChecked -> isArabic.value = isChecked }
            )
            Text("is Arabic")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val activity = LocalContext.current as Activity

            OCRAndroidTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {


                    Button(
                        modifier = Modifier.width(260.dp),
                        onClick = {
                            initOCR(activity, OCRMode.NATIONAL_ID_DETECTION)
                        },
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "National ID Detection",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        modifier = Modifier.width(260.dp),
                        onClick = {
                            initOCR(activity, OCRMode.PASSPORT_DETECTION)
                        },
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "Passport Detection",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        modifier = Modifier.width(260.dp),
                        onClick = {
                            initOCR(activity, OCRMode.SMILE_LIVENESS)
                        },
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "Liveness Smile Detection",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }



                    Spacer(modifier = Modifier.height(16.dp))


                    Button(
                        modifier = Modifier.width(260.dp),

                        onClick = {
                            initOCR(activity, OCRMode.NaturalExpressionDetection)
                        },
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "Natural Expression Detection",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }


                    Spacer(modifier = Modifier.height(24.dp))
                    ArabicCheckbox()
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = text.value,
                        style = MaterialTheme.typography.titleMedium,
                    )
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
                licenseResource = R.raw.iengine,
                localizationCode = if (isArabic.value) LocalizationCode.AR else LocalizationCode.EN,
                ocrMode = ocrMode,
                ocrCallback = object :
                    OCRCallback {
                    override fun success(ocrSuccessModel: OCRSuccessModel) {
                        Log.d("OCRCallback", "Nature image :${ocrSuccessModel.naturalExpressionImage}")
                        Log.d("OCRCallback", "Smile image :${ocrSuccessModel.livenessSmileExpressionImage}")
                        Log.d("OCRCallback", "National Id image :${ocrSuccessModel.nationalIdImage}")

                        text.value = "OCR Message: ${ocrSuccessModel.ocrMessage}"

                    }

                    override fun error(ocrFailedModel: OCRFailedModel) {
                        text.value =  "OCR Error: ${ocrFailedModel.failureMessage}"

                    }
                },
            )
        } catch (e: Exception) {
            Log.e("error", e.toString())
        }
        try {
            OCR.launch(activity)
        } catch (e: Exception) {
            Toast.makeText(this, e.message.toString(), Toast.LENGTH_SHORT).show()
            Log.e("error", e.toString())
        }
    }
}
