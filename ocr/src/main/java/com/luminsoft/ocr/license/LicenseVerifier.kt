package com.luminsoft.ocr.license

import android.content.Context
import android.util.Log
import com.luminsoft.ocr.core.sdk.OcrSDK
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Calendar
import java.util.TimeZone

object LicenseVerifier {

    fun readRawFile(context: Context, rawResourceId: Int): Boolean {
        val inputStream = context.resources.openRawResource(rawResourceId)
        val valid = verifyLicense(inputStream.bufferedReader().use { it.readText() })
        Log.e("verifyLicense", valid.toString())
        return valid
    }

    private fun loadJsonFromRaw(context: Context, rawResourceId: Int): String {
        return context.resources.openRawResource(rawResourceId).bufferedReader().use { it.readText() }
    }

    fun isFaceEnabled(context: Context, rawResourceId: Int): Boolean {
        return try {
            val jsonString = loadJsonFromRaw(context, rawResourceId)
            val jsonObject = JSONObject(jsonString)
            jsonObject.getJSONObject("contract")
                .getJSONObject("eNROLL")
                .getJSONObject("mobile")
                .getJSONObject("face")
                .getBoolean("enabled")
        } catch (e: Exception) {
            false
        }
    }


    fun isDocumentEnabled(context: Context, rawResourceId: Int): Boolean {
        return try {
            val jsonString = loadJsonFromRaw(context, rawResourceId)
            val jsonObject = JSONObject(jsonString)
            jsonObject.getJSONObject("contract")
                .getJSONObject("eNROLL")
                .getJSONObject("mobile")
                .getJSONObject("document")
                .getBoolean("enabled")
        } catch (e: Exception) {
            false
        }
    }


    private fun verifyLicense(licenseData: String): Boolean {
        val contractModel = parseJsonToModel(licenseData)
        if (!checkExpiry(contractModel.contract.expiration)) {
            Log.e("verifyLicense", "expiration")
            return false
        }
        if (!checkId(contractModel.contract.id)) {
            Log.e("verifyLicense", "id")
            return false
        }
        val createdHash = createDataToEncrypt(contractModel)

        return checkHash(createdHash, contractModel.contractSignature)
    }

    private fun parseJsonToModel(jsonString: String): ContractModel {
        val jsonObject = JSONObject(jsonString)

        val contractObject = jsonObject.getJSONObject("contract")
        val expirationObject = contractObject.getJSONObject("expiration")
        val eNROLLObject = contractObject.getJSONObject("eNROLL")
        val mobileObject = eNROLLObject.getJSONObject("mobile")
        val faceObject = mobileObject.getJSONObject("face")
        val documentObject = mobileObject.getJSONObject("document")

        val contract = Contract(
            customer = contractObject.getString("customer"),
            expiration = Expiration(
                day = expirationObject.getInt("day"),
                month = expirationObject.getInt("month"),
                year = expirationObject.getInt("year")
            ),
            id = contractObject.getString("id"),
            eNROLL = ENROLL(
                mobile = Mobile(
                    face = Feature(enabled = faceObject.getBoolean("enabled")),
                    document = Feature(enabled = documentObject.getBoolean("enabled"))
                )
            )
        )

        val contractSignature =
            jsonObject.getString("contractSignature")

        return ContractModel(
            contract = contract,
            contractSignature = contractSignature
        )

    }

    private fun createDataToEncrypt(contractModel: ContractModel): String {

        var dataToEncrypt = "0xeN"

        // customer
        dataToEncrypt += contractModel.contract.customer.reversed()

        //expiration
        val expiration = contractModel.contract.expiration
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(expiration.year, expiration.month - 1, expiration.day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val expirationTimeStamp = calendar.timeInMillis
        dataToEncrypt += (expirationTimeStamp * 21).toString().substring(2, 9)

        dataToEncrypt += "LU0x12"

        //id
        dataToEncrypt += contractModel.contract.id.replace(
            ".",
            contractModel.contract.customer.substring(0, 1)
        )

        //eNROLL
        dataToEncrypt += contractModel.contract.eNROLL.mobile.face.enabled.toString().reversed()
            .substring(0, 3)
        dataToEncrypt += contractModel.contract.eNROLL.mobile.document.enabled.toString().reversed()
            .substring(0, 2)

        dataToEncrypt += dataToEncrypt.reversed()
        return encryptWithSHA512(dataToEncrypt)
    }

    private fun encryptWithSHA512(input: String): String {
        val digest = MessageDigest.getInstance("SHA-512")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        val hashOutput = hashBytes.joinToString("") { "%02x".format(it) }
        Log.d("hashOutput", hashOutput)
        return hashOutput
    }

    private fun checkExpiry(expiration: Expiration): Boolean {
        val expiry = getExpirationTimeStamp(expiration)
        val currentTimestamp = System.currentTimeMillis()
        return expiry >= currentTimestamp
    }

    private fun getExpirationTimeStamp(expiration: Expiration): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(expiration.year, expiration.month - 1, expiration.day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun checkId(id: String): Boolean {
        return OcrSDK.packageId == id
    }

    private fun checkHash(createdHash: String, contractSignature: String): Boolean {
        return createdHash == contractSignature
    }
}
