package com.luminsoft.ocr.license

import android.util.Log
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Calendar
import java.util.TimeZone


private fun encryptWithSHA512(input: String) {
    val digest = MessageDigest.getInstance("SHA-512")
    val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    val hashOutput = hashBytes.joinToString("") { "%02x".format(it) }
    Log.d("hashOutput", hashOutput)
}

fun parseJsonToModel(jsonString: String) {
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
        id = contractObject.getString("id"),  // changed from hwids to id
        eNROLL = ENROLL(
            mobile = Mobile(
                face = Feature(enabled = faceObject.getBoolean("enabled")),
                document = Feature(enabled = documentObject.getBoolean("enabled"))
            )
        )
    )

    val contractSignature =
        jsonObject.getString("contractSignature") // changed from contract_signature to contractSignature

    createDataToEncrypt(
        ContractModel(
            contract = contract,
            contractSignature = contractSignature
        )
    )
}

private fun createDataToEncrypt(contractModel: ContractModel) {

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
    encryptWithSHA512(dataToEncrypt)
}


