package com.build.buddyai.domain.usecase.ondevice

import android.content.Context
import android.os.Build
import android.system.Os
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Manages extraction and caching of build tools (aapt2, android.jar, testkey)
 * from the app's assets to the device's internal storage.
 *
 * All tools run on the device's ART runtime - no JDK required.
 */
object OnDeviceBuildEnvironment {

    private const val ASSETS_PREFIX = "build_tools"
    private var initialized = false

    lateinit var aapt2Binary: File
        private set
    lateinit var androidJar: File
        private set
    lateinit var coreLambdaStubsJar: File
        private set
    lateinit var testkeyDir: File
        private set

    fun initialize(context: Context) {
        if (initialized) return
        val toolsDir = File(context.filesDir, "build_tools").also { it.mkdirs() }

        aapt2Binary = extractAapt2(context, toolsDir)

        androidJar = File(toolsDir, "android.jar")
        val androidJarZip = File(toolsDir, "android.jar.zip")
        extractAsset(context, "$ASSETS_PREFIX/android.jar.zip", androidJarZip)
        if (!androidJar.exists() || androidJar.length() == 0L) {
            unzip(androidJarZip, toolsDir)
        }

        coreLambdaStubsJar = File(toolsDir, "core-lambda-stubs.jar")
        extractAsset(context, "$ASSETS_PREFIX/core-lambda-stubs.jar", coreLambdaStubsJar)

        val testkeyZip = File(toolsDir, "testkey.zip")
        testkeyDir = File(toolsDir, "testkey")
        extractAsset(context, "$ASSETS_PREFIX/testkey.zip", testkeyZip)
        if (!testkeyDir.exists() || testkeyDir.list().isNullOrEmpty()) {
            testkeyDir.mkdirs()
            unzip(testkeyZip, testkeyDir)
        }

        initialized = true
    }

    fun reset() {
        initialized = false
    }

    private fun extractAapt2(context: Context, toolsDir: File): File {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val assetName = "aapt2-$abi"
        val target = File(toolsDir, "aapt2")
        extractAsset(context, "$ASSETS_PREFIX/$assetName", target)
        try {
            Os.chmod(target.absolutePath, 0b111_101_101) // rwxr-xr-x
        } catch (_: Exception) {
            target.setExecutable(true, false)
        }
        return target
    }

    /**
     * Extracts an asset file to [target].
     *
     * Strategy:
     *  1. If the asset is UNCOMPRESSED in the APK (noCompress flag set), openFd() works and
     *     we can compare sizes cheaply to skip re-extraction.
     *  2. If the asset is compressed (openFd() throws), we fall back to streaming via open()
     *     and only skip if the target file already has content (first-run guard).
     */
    private fun extractAsset(context: Context, assetPath: String, target: File) {
        // Try fast-path: uncompressed asset - compare sizes
        try {
            val fd = context.assets.openFd(assetPath)
            val assetLength = fd.length
            fd.close()
            if (target.exists() && target.length() == assetLength) return
        } catch (_: Exception) {
            // Asset is compressed in APK; openFd() not available.
            // Only skip if target already exists and is non-empty (assumed up-to-date).
            if (target.exists() && target.length() > 0) return
        }

        // Extract via streaming (works for both compressed and uncompressed assets)
        target.parentFile?.mkdirs()
        try {
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to extract asset '$assetPath': ${e.message}", e)
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        if (!zipFile.exists()) return
        destDir.mkdirs()
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
