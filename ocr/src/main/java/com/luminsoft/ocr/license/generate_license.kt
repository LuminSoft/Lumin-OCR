package com.luminsoft.ocr.license

import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import android.util.Log

fun generateLicenseFile(content: String, secretKey: String, outputFilePath: String) {
    // Encrypt the content
    val encryptedData = encrypt(content, secretKey)

    // Write encrypted data to file
    try {
        File(outputFilePath).writeText(encryptedData)
    } catch (e: Exception) {
        Log.d("TAFG", e.message.toString())
    }
}

private fun encrypt(data: String, secretKey: String): String {
    val keySpec = SecretKeySpec(secretKey.toByteArray(), "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec)
    return Base64.encodeToString(cipher.doFinal(data.toByteArray()), Base64.DEFAULT)
}
