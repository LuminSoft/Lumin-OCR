package com.luminsoft.ocr.license

import android.content.Context
import android.util.Base64
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object LicenseVerifier {

    private const val SECRET_KEY = "YourSecureKey16!" // Change to a secure key
    private const val LICENSE_PATH = "/storage/emulated/0/Download/iengine.lic"

    fun verifyLicense(): Boolean {
        val licenseFile = File(LICENSE_PATH)
        if (!licenseFile.exists()) return false

        val encryptedLicense = licenseFile.readBytes()
        val decryptedLicense = decryptLicense(encryptedLicense) ?: return false

        // Parse decryptedLicense as per your license structure
        return isLicenseValid(decryptedLicense)
    }

    private fun decryptLicense(data: ByteArray): String? {
        return try {
            val secretKey = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            String(cipher.doFinal(Base64.decode(data, Base64.DEFAULT)))
        } catch (e: Exception) {
            null
        }
    }

    private fun isLicenseValid(decryptedData: String): Boolean {
        // Implement checks, e.g., expiry date, matching ID
        return true // Replace with actual validation logic
    }
}
