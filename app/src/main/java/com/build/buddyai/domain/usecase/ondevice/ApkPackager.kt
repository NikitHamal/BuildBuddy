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

        ZipOutputStream(output.outputStream().buffered()).use { zos ->
            val resourcesApk = File(resourcesApkPath)
            log("[APK] Looking for resources.ap_ at: $resourcesApkPath")
            log("[APK] resources.ap_ exists: ${resourcesApk.exists()}, size: ${if (resourcesApk.exists()) resourcesApk.length() else 0}")
            
            if (resourcesApk.exists()) {
                log("[APK] Copying resources from: ${resourcesApk.name} (${resourcesApk.length()} bytes)")
                ZipInputStream(resourcesApk.inputStream().buffered()).use { zis ->
                    copyZipEntries(zis, zos, excludeSignatureFiles = true)
                }
            } else {
                log("[APK] ERROR: resources.ap_ NOT FOUND at: $resourcesApkPath")
                log("[APK] WARNING: APK will be missing resources and manifest!")
            }

            dexOutputDir.listFiles()
                ?.filter { it.isFile && it.extension == "dex" }
                ?.sortedBy { it.name }
                ?.forEach { dexFile ->
                    log("[APK] Adding ${dexFile.name} (${dexFile.length()} bytes)")
                    zos.putNextEntry(ZipEntry(dexFile.name))
                    dexFile.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
        }
        
        // Verify the APK was created properly
        if (output.exists()) {
            val zipEntries = mutableListOf<String>()
            java.util.zip.ZipFile(output).use { zip ->
                zip.entries().asSequence().forEach { entry -> zipEntries.add(entry.name) }
            }
            log("[APK] APK contains ${zipEntries.size} entries: ${zipEntries.joinToString(", ")}")
            
            // Verify critical entries
            val hasManifest = zipEntries.contains("AndroidManifest.xml")
            val hasDex = zipEntries.any { it.endsWith(".dex") }
            val hasResources = zipEntries.contains("resources.arsc")
            
            if (!hasManifest) log("[APK] ERROR: Missing AndroidManifest.xml - APK WILL NOT INSTALL!")
            if (!hasDex) log("[APK] WARNING: No classes.dex found!")
            if (!hasResources) log("[APK] ERROR: Missing resources.arsc - APK WILL NOT INSTALL!")
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
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .build()

            log("[APK] Starting APK signing with apksig library...")
            ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsignedApk)
                .setOutputApk(signedApk)
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

    private fun copyZipEntries(zis: ZipInputStream, zos: ZipOutputStream, excludeSignatureFiles: Boolean) {
        var entry = zis.nextEntry
        while (entry != null) {
            val name = entry.name
            if (excludeSignatureFiles && (name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA")))) {
                zis.closeEntry()
                entry = zis.nextEntry
                continue
            }
            try {
                zos.putNextEntry(ZipEntry(name))
                if (!entry.isDirectory) {
                    zis.copyTo(zos)
                }
                zos.closeEntry()
            } catch (_: java.util.zip.ZipException) {
                // skip duplicates
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
}
