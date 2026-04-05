package com.build.buddyai.domain.usecase.ondevice

import com.android.apksig.ApkSigner
import com.iyxan23.zipalignjava.ZipAlign
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Stage 4 + 5: Package DEX + resources into an APK, then sign it using the test key.
 * Includes zipalign optimization before signing.
 */
class ApkPackager(
    private val resourcesApkPath: String,
    private val dexOutputDir: File,
    private val testkeyDir: File,
    private val outputApkPath: String,
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
        log("[APK] Signing APK...")

        try {
            val signerClass = Class.forName("mod.alucard.tn.apksigner.ApkSigner")
            val signer = signerClass.getDeclaredConstructor().newInstance()
            val signMethod = signerClass.getMethod("signWithTestKey", String::class.java, String::class.java, String::class.java)
            val result = signMethod.invoke(signer, unsignedApk.absolutePath, signedApk.absolutePath, null) as Boolean
            if (!result) throw RuntimeException("ApkSigner.signWithTestKey returned false")
            log("[APK] Signed via ApkSigner")
            return
        } catch (_: Exception) {
            log("[APK] ApkSigner not found, using apksig library...")
        }

        signWithApkSig(unsignedApk, signedApk)
    }

    private fun signWithApkSig(unsignedApk: File, signedApk: File) {
        val pk8 = File(testkeyDir, "testkey.pk8")
        val x509 = File(testkeyDir, "testkey.x509.pem")

        if (!pk8.exists() || !x509.exists()) {
            log("[APK] ERROR: No signing keys found!")
            unsignedApk.copyTo(signedApk, overwrite = true)
            return
        }

        try {
            val pkBytes = pk8.readBytes()
            val pkcs8Key = stripPkcs8Headers(pkBytes)
            val keySpec = PKCS8EncodedKeySpec(pkcs8Key)
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)

            val cert = x509.inputStream().use {
                CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
            }

            log("[APK] Signing with: ${cert.subjectDN}")

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
            e.printStackTrace()
            unsignedApk.copyTo(signedApk, overwrite = true)
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
