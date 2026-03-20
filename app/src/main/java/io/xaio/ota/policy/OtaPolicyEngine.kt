package io.xaio.ota.policy

import android.content.Context
import android.util.Base64
import io.xaio.ota.model.DeviceVersion
import io.xaio.ota.model.PolicyResult
import io.xaio.ota.model.ReleaseRecord
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class OtaPolicyEngine(private val context: Context) {

    fun evaluate(
        device: DeviceVersion,
        release: ReleaseRecord,
        zipFile: File,
        signatureFile: File,
    ): PolicyResult {
        if (release.dfuPackageFormat != "legacy-crc") {
            return PolicyResult.HardBlock(
                "This release uses an unsupported DFU package format for the current XIAO bootloader.",
            )
        }

        if (!release.hwAllow.contains(device.hardwareRev)) {
            return PolicyResult.HardBlock(
                "This package is not compatible with hardware ${device.hardwareRev}. Allowed hardware: ${release.hwAllow.joinToString()}",
            )
        }

        if (release.securityEpoch < device.securityEpoch) {
            return PolicyResult.HardBlock(
                "Package security epoch ${release.securityEpoch} is lower than the device floor ${device.securityEpoch}.",
            )
        }

        val actualSha = computeSha256(zipFile)
        if (!actualSha.equals(release.sha256, ignoreCase = true)) {
            return PolicyResult.HardBlock(
                "ZIP checksum mismatch. Expected ${release.sha256}, got $actualSha.",
            )
        }

        if (!signatureFile.exists()) {
            return PolicyResult.HardBlock("Signature file was not downloaded.")
        }

        val signatureVerification = runCatching {
            verifySignature(zipFile, signatureFile)
        }.getOrElse { error ->
            return PolicyResult.HardBlock("Signature verification failed: ${error.message ?: "unknown error"}")
        }

        if (!signatureVerification) {
            return PolicyResult.HardBlock("The ZIP signature is not trusted by this app.")
        }

        if (release.forcedUpdate && release.versionCode >= device.versionCode) {
            return PolicyResult.ForcedUpdate(release.version)
        }

        return when {
            release.versionCode > device.versionCode -> PolicyResult.Allow
            release.versionCode == device.versionCode -> {
                if (release.channel == "dev") PolicyResult.Allow else PolicyResult.AlreadyInstalled
            }
            else -> {
                val requiresCheckbox = release.channel == "stable"
                if (release.channel == "dev") {
                    PolicyResult.Allow
                } else {
                    PolicyResult.DowngradeWarning(
                        fromVersion = device.firmwareRev,
                        toVersion = release.version,
                        requiresCheckbox = requiresCheckbox,
                    )
                }
            }
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun verifySignature(zipFile: File, signatureFile: File): Boolean {
        val pem = context.assets.open("ota_app_signature_public.pem")
            .bufferedReader()
            .use { it.readText() }
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s+"), "")

        check(pem.isNotBlank() && !pem.contains("REPLACE_WITH")) {
            "Replace app/src/main/assets/ota_app_signature_public.pem with the real public key."
        }

        val publicKeyBytes = Base64.decode(pem, Base64.DEFAULT)
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(publicKeyBytes))

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(publicKey)
        zipFile.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                signature.update(buffer, 0, read)
            }
        }
        return signature.verify(signatureFile.readBytes())
    }
}
