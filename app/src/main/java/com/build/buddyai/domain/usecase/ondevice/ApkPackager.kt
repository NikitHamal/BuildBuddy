package com.build.buddyai.domain.usecase.ondevice

import com.android.apksig.ApkSigner
import com.iyxan23.zipalignjava.ZipAlign
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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

        // Step 1: Package resources + DEX into unsigned APK
        packageApk(unsignedApk)

        // Step 2: Zipalign the APK (optimizes memory usage at runtime)
        zipalignApk(unsignedApk, alignedApk)

        // Step 3: Sign the aligned APK
        signApk(alignedApk, File(outputApkPath))

        // Clean up intermediate files
        unsignedApk.delete()
        alignedApk.delete()

        log("[APK] Final signed APK: ${outputApkPath} (${File(outputApkPath).length()} bytes)")
    }

    // Step 1: Merge resources.ap_ + classes.dex -> unsigned APK
    private fun packageApk(output: File) {
        output.parentFile?.mkdirs()
        log("[APK] Packaging resources + DEX -> ${output.name}")

        val resourcesApk = File(resourcesApkPath)
        log("[APK] Checking resources.ap_: $resourcesApkPath")
        
        if (!resourcesApk.exists()) {
            log("[APK] ERROR: resources.ap_ NOT FOUND at: $resourcesApkPath")
            throw RuntimeException("AAPT2 did not create resources.ap_ at:\n$resourcesApkPath\n\n" +
                "Possible causes:\n" +
                "1. AAPT2 link failed (check AAPT2 logs above)\n" +
                "2. AndroidManifest.xml has syntax errors\n" +
                "3. Resource files have invalid names/structure")
        }
        
        log("[APK] resources.ap_ found (${resourcesApk.length()} bytes), copying contents...")

        // List what's in resources.ap_
        val resEntries = mutableListOf<String>()
        try {
            java.util.zip.ZipFile(resourcesApk).use { zip ->
                zip.entries().asSequence().forEach { resEntries.add(it.name) }
            }
            log("[APK] resources.ap_ contains: ${resEntries.joinToString(", ")}")
        } catch (e: Exception) {
            log("[APK] WARNING: Could not list resources.ap_ contents: ${e.message}")
        }

        ZipOutputStream(output.outputStream().buffered()).use { zos ->
            // Copy entries from resources.ap_
            ZipInputStream(resourcesApk.inputStream().buffered()).use { zis ->
                var entryCount = 0
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val isSignatureFile = name.startsWith("META-INF/") &&
                        (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"))

                    if (!isSignatureFile) {
                        try {
                            zos.putNextEntry(ZipEntry(name))
                            if (!entry.isDirectory) {
                                zis.copyTo(zos)
                            }
                            zos.closeEntry()
                            entryCount++
                            log("[APK]   Copied: $name")
                        } catch (e: Exception) {
                            log("[APK]   FAILED to copy $name: ${e.message}")
                        }
                    } else {
                        log("[APK]   Skipped signature: $name")
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                log("[APK] Copied $entryCount entries from resources.ap_")
            }

            // Add DEX files
            val dexFiles = dexOutputDir.listFiles()
                ?.filter { it.isFile && it.extension == "dex" }
                ?.sortedBy { it.name }
                ?: emptyList()

            if (dexFiles.isEmpty()) {
                log("[APK] WARNING: No DEX files found in: ${dexOutputDir.absolutePath}")
            }

            dexFiles.forEach { dexFile ->
                log("[APK] Adding ${dexFile.name} (${dexFile.length()} bytes)")
                zos.putNextEntry(ZipEntry(dexFile.name))
                dexFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }

        // Final APK summary
        if (output.exists()) {
            val entries = mutableListOf<String>()
            try {
                java.util.zip.ZipFile(output).use { zip ->
                    zip.entries().asSequence().forEach { entries.add("${it.name} (${it.compressedSize}b)") }
                }
            } catch (e: Exception) {}
            log("[APK] Final unsigned APK: ${entries.size} entries, ${output.length()} bytes")
            if (entries.size <= 20) {
                entries.forEach { log("[APK]   $it") }
            }
        }
    }

    // Step 2: Zipalign the APK using zipalign-java library
    private fun zipalignApk(input: File, output: File) {
        log("[APK] Aligning APK with zipalign...")
        try {
            RandomAccessFile(input, "r").use { inputFile ->
                FileOutputStream(output).use { outputFile ->
                    ZipAlign.alignZip(inputFile, outputFile)
                }
            }
            log("[APK] APK aligned successfully (${input.length()} -> ${output.length()} bytes)")
        } catch (e: Exception) {
            log("[APK] WARNING: Zipalign failed: ${e.message}. Using unaligned APK.")
            input.copyTo(output, overwrite = true)
        }
    }

    // Step 3: Sign the APK
    private fun signApk(unsignedApk: File, signedApk: File) {
        log("[APK] Signing with test key...")

        // Try reflection-based external signer first
        try {
            val signerClass = Class.forName("mod.alucard.tn.apksigner.ApkSigner")
            val signer = signerClass.getDeclaredConstructor().newInstance()
            val signMethod = signerClass.getMethod("signWithTestKey", String::class.java, String::class.java, String::class.java)
            val result = signMethod.invoke(signer, unsignedApk.absolutePath, signedApk.absolutePath, null) as Boolean
            if (!result) throw RuntimeException("ApkSigner.signWithTestKey returned false")
            log("[APK] APK signed successfully via ApkSigner")
            return
        } catch (e: Exception) {
            log("[APK] ApkSigner not available (Reason: ${e.message}), using apksig library...")
        }

        // Fallback: Use official apksig library
        signWithApkSig(unsignedApk, signedApk)
    }

    /**
     * Sign APK using official Google apksig library (v2 + v3 signature schemes)
     */
    private fun signWithApkSig(unsignedApk: File, signedApk: File) {
        val pk8 = File(testkeyDir, "testkey.pk8")
        val x509 = File(testkeyDir, "testkey.x509.pem")

        log("[APK] Looking for signing keys in: ${testkeyDir.absolutePath}")
        log("[APK] testkey.pk8 exists: ${pk8.exists()}, size: ${if (pk8.exists()) pk8.length() else 0}")
        log("[APK] testkey.x509.pem exists: ${x509.exists()}, size: ${if (x509.exists()) x509.length() else 0}")

        if (!pk8.exists() || !x509.exists()) {
            log("[APK] WARNING: No signing key found - APK will be unsigned (will fail to install)")
            unsignedApk.copyTo(signedApk, overwrite = true)
            return
        }

        try {
            // Load private key
            val pkBytes = pk8.readBytes()
            log("[APK] Key file size: ${pkBytes.size} bytes")
            val pkcs8Key = stripPkcs8Headers(pkBytes)
            log("[APK] Decoded key size: ${pkcs8Key.size} bytes")

            val keySpec = PKCS8EncodedKeySpec(pkcs8Key)
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)
            log("[APK] Private key loaded: ${privateKey.algorithm}, format: ${privateKey.format}")

            // Load certificate
            val cert = x509.inputStream().use {
                CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
            }
            log("[APK] Certificate loaded: ${cert.subjectDN}, valid until: ${cert.notAfter}")

            // Use apksig library to sign the APK
            val signerConfig = ApkSigner.SignerConfig.Builder("testkey", privateKey, listOf(cert))
                .build()

            log("[APK] Starting APK signing with apksig library...")
            ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsignedApk)
                .setOutputApk(signedApk)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign()

            log("[APK] APK signed with apksig library (v2/v3 signature)")
            log("[APK] Signed APK size: ${signedApk.length()} bytes")
        } catch (e: Exception) {
            log("[APK] ERROR: Signing failed: ${e.message}")
            log("[APK] Exception type: ${e.javaClass.simpleName}")
            e.printStackTrace()
            log("[APK] WARNING: Copying unsigned APK (will likely fail to install)")
            unsignedApk.copyTo(signedApk, overwrite = true)
        }
    }

    /**
     * Strip PKCS#8 PEM headers/footers and decode base64 if needed.
     * Handles both raw DER and PEM-encoded keys.
     */
    private fun stripPkcs8Headers(keyBytes: ByteArray): ByteArray {
        val content = String(keyBytes)

        // Check if it's PEM encoded
        if (content.contains("-----BEGIN")) {
            val base64Content = content
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN ENCRYPTED PRIVATE KEY-----", "")
                .replace("-----END ENCRYPTED PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")

            return android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
        }

        // Already DER encoded
        return keyBytes
    }
}
