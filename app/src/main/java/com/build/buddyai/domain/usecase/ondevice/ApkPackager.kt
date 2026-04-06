package com.build.buddyai.domain.usecase.ondevice

import com.android.apksig.ApkSigner
import com.build.buddyai.core.common.BuildProfileManager
import com.build.buddyai.core.model.BuildVariant
import com.build.buddyai.core.model.SigningConfig
import com.iyxan23.zipalignjava.ZipAlign
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Stage 4 + 5: Package DEX + resources into an APK, align it, then sign it.
 * Debug builds fall back to the bundled test key. Release builds require a user keystore.
 */
class ApkPackager(
    private val resourcesApkPath: String,
    private val dexOutputDir: File,
    private val testkeyDir: File,
    private val outputApkPath: String,
    private val buildVariant: BuildVariant = BuildVariant.DEBUG,
    private val signingConfig: SigningConfig? = null,
    private val signingSecrets: BuildProfileManager.SigningSecrets? = null,
    private val log: (String) -> Unit = {}
) {
    fun packageAndSign() {
        val unsignedApk = File("${outputApkPath}.unsigned.apk")
        val alignedApk = File("${outputApkPath}.aligned.apk")

        packageApk(unsignedApk)
        zipalignApk(unsignedApk, alignedApk)
        signApk(alignedApk, File(outputApkPath))

        unsignedApk.delete()
        alignedApk.delete()

        log("[APK] Final signed APK: $outputApkPath (${File(outputApkPath).length()} bytes)")
    }

    private fun packageApk(output: File) {
        output.parentFile?.mkdirs()
        val resourcesApk = File(resourcesApkPath)

        val reportFile = File(output.parentFile ?: output, "${output.nameWithoutExtension}_report.txt")
        val report = StringBuilder()
        report.appendLine("=== APK Packaging Report ===")
        report.appendLine("resources.ap_ path: $resourcesApkPath")
        report.appendLine("resources.ap_ exists: ${resourcesApk.exists()}")
        report.appendLine("resources.ap_ size: ${if (resourcesApk.exists()) resourcesApk.length() else 0}")

        if (!resourcesApk.exists()) {
            report.appendLine("ERROR: resources.ap_ NOT FOUND!")
            reportFile.writeText(report.toString())
            throw RuntimeException("AAPT2 did not create resources.ap_ at:\n$resourcesApkPath")
        }

        log("[APK] Packaging resources + DEX")

        ZipOutputStream(output.outputStream().buffered()).use { zos ->
            ZipFile(resourcesApk).use { zip ->
                var entryCount = 0
                var skippedCount = 0
                zip.entries().asSequence().forEach { entry ->
                    val name = entry.name
                    val isSignatureFile = name.startsWith("META-INF/") &&
                        (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"))

                    if (isSignatureFile) {
                        skippedCount++
                        report.appendLine("Skip: $name")
                    } else {
                        val copiedEntry = cloneEntry(entry)
                        zos.putNextEntry(copiedEntry)
                        if (!entry.isDirectory) {
                            zip.getInputStream(entry).use { input -> input.copyTo(zos) }
                        }
                        zos.closeEntry()
                        entryCount++
                        report.appendLine("Copy: $name (${entry.size}b, method=${entry.method})")
                    }
                }
                report.appendLine("Summary: Copied $entryCount, Skipped $skippedCount")
                log("[APK] resources: $entryCount entries copied from resources.ap_")
            }

            val dexFiles = dexOutputDir.listFiles()
                ?.filter { it.isFile && it.extension == "dex" }
                ?.sortedBy { it.name }
                ?: emptyList()

            dexFiles.forEach { dexFile ->
                log("[APK] DEX: ${dexFile.name} (${dexFile.length()}b)")
                report.appendLine("DEX: ${dexFile.name} (${dexFile.length()}b)")
                zos.putNextEntry(ZipEntry(dexFile.name))
                dexFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }

        if (output.exists()) {
            ZipFile(output).use { zip ->
                val entries = zip.entries().asSequence().toList()
                report.appendLine("=== Final APK: ${entries.size} entries, ${output.length()} bytes ===")
                entries.forEach { report.appendLine("  ${it.name} (${it.size}b, method=${it.method})") }
                log("[APK] APK ready: ${entries.size} files, ${output.length()} bytes")
            }
        }

        reportFile.writeText(report.toString())
    }

    private fun cloneEntry(entry: ZipEntry): ZipEntry {
        val copy = ZipEntry(entry.name)
        copy.comment = entry.comment
        copy.extra = entry.extra
        copy.time = entry.time
        copy.method = entry.method
        if (entry.method == ZipEntry.STORED) {
            copy.size = entry.size
            copy.compressedSize = entry.size
            copy.crc = entry.crc
        }
        return copy
    }

    private fun zipalignApk(input: File, output: File) {
        log("[APK] Aligning APK with zipalign...")
        try {
            RandomAccessFile(input, "r").use { inputFile ->
                FileOutputStream(output).use { outputFile ->
                    ZipAlign.alignZip(inputFile, outputFile)
                }
            }
            log("[APK] APK aligned (${input.length()} -> ${output.length()} bytes)")
        } catch (e: Exception) {
            log("[APK] Zipalign failed: ${e.message}. Using unaligned APK.")
            input.copyTo(output, overwrite = true)
        }
    }

    private fun signApk(unsignedApk: File, signedApk: File) {
        log("[APK] Signing APK for ${buildVariant.name.lowercase(Locale.US)} build...")

        if (buildVariant == BuildVariant.RELEASE) {
            signWithUserKeystore(unsignedApk, signedApk)
            return
        }

        try {
            val signerClass = Class.forName("mod.alucard.tn.apksigner.ApkSigner")
            val signer = signerClass.getDeclaredConstructor().newInstance()
            val signMethod = signerClass.getMethod("signWithTestKey", String::class.java, String::class.java, String::class.java)
            val result = signMethod.invoke(signer, unsignedApk.absolutePath, signedApk.absolutePath, null) as Boolean
            if (!result) throw RuntimeException("ApkSigner.signWithTestKey returned false")
            log("[APK] Signed via ApkSigner test key")
            return
        } catch (_: Exception) {
            log("[APK] ApkSigner test-key helper not found, using bundled apksig test key...")
        }

        signWithBundledTestKey(unsignedApk, signedApk)
    }

    private fun signWithUserKeystore(unsignedApk: File, signedApk: File) {
        val config = requireNotNull(signingConfig) {
            "Release build requested but no signing config was provided"
        }
        val secrets = requireNotNull(signingSecrets) {
            "Release build requested but signing passwords are missing"
        }
        val keystoreFile = File(config.keystorePath)
        require(keystoreFile.exists()) { "Configured keystore file does not exist: ${keystoreFile.absolutePath}" }

        val alias = config.keyAlias.ifBlank { error("Signing key alias is required for release builds") }
        val keyStore = loadKeyStore(keystoreFile, secrets.storePassword)
        require(keyStore.containsAlias(alias)) {
            "Keystore does not contain alias '$alias'. Update the signing alias in the build workspace."
        }

        val privateKey = keyStore.getKey(alias, secrets.keyPassword.toCharArray()) as? PrivateKey
            ?: error("Unable to load private key for alias '$alias'")
        val certificateChain = keyStore.getCertificateChain(alias)?.toList().orEmpty().map { it as X509Certificate }
            .ifEmpty {
                listOf(keyStore.getCertificate(alias) as? X509Certificate ?: error("No certificate found for alias '$alias'"))
            }

        val signerConfig = ApkSigner.SignerConfig.Builder(alias, privateKey, certificateChain).build()
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsignedApk)
            .setOutputApk(signedApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()

        log("[APK] Release APK signed with user keystore alias '$alias'")
    }

    private fun loadKeyStore(keystoreFile: File, password: String): KeyStore {
        val candidates = when (keystoreFile.extension.lowercase(Locale.US)) {
            "p12", "pfx" -> listOf("PKCS12", KeyStore.getDefaultType())
            else -> listOf(KeyStore.getDefaultType(), "JKS", "PKCS12")
        }.distinct()

        var lastError: Throwable? = null
        candidates.forEach { type ->
            try {
                return KeyStore.getInstance(type).apply {
                    keystoreFile.inputStream().use { load(it, password.toCharArray()) }
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw RuntimeException("Unable to open keystore '${keystoreFile.name}': ${lastError?.message}", lastError)
    }

    private fun signWithBundledTestKey(unsignedApk: File, signedApk: File) {
        val pk8 = File(testkeyDir, "testkey.pk8")
        val x509 = File(testkeyDir, "testkey.x509.pem")

        if (!pk8.exists() || !x509.exists()) {
            log("[APK] ERROR: No signing keys found!")
            throw IllegalStateException(
                "APK signing keys are missing. Expected ${pk8.absolutePath} and ${x509.absolutePath}"
            )
        }

        try {
            val pkBytes = pk8.readBytes()
            val pkcs8Key = stripPkcs8Headers(pkBytes)
            val keySpec = PKCS8EncodedKeySpec(pkcs8Key)
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)

            val cert = x509.inputStream().use {
                CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
            }

            log("[APK] Signing with bundled test key: ${cert.subjectDN}")

            val signerConfig = ApkSigner.SignerConfig.Builder("testkey", privateKey, listOf(cert)).build()

            ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsignedApk)
                .setOutputApk(signedApk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign()

            log("[APK] Signed with apksig (v1/v2/v3)")
        } catch (e: Exception) {
            log("[APK] Signing failed: ${e.message}")
            throw RuntimeException("APK signing failed with bundled test key: ${e.message}", e)
        }
    }

    private fun stripPkcs8Headers(keyBytes: ByteArray): ByteArray {
        val content = String(keyBytes)
        if (content.contains("-----BEGIN")) {
            val base64Content = content
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN ENCRYPTED PRIVATE KEY-----", "")
                .replace("-----END ENCRYPTED PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
            return android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
        }
        return keyBytes
    }
}
