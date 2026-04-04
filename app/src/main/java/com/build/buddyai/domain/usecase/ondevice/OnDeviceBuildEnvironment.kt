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

    @Volatile
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
        synchronized(this) {
            if (initialized) return

            val toolsDir = File(context.filesDir, "build_tools").also { it.mkdirs() }

            aapt2Binary = extractAapt2(context, toolsDir)

            val androidJarZip = File(toolsDir, "android.jar.zip")
            androidJar = File(toolsDir, "android.jar")
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
    }

    fun reset() {
        initialized = false
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun extractAapt2(context: Context, toolsDir: File): File {
        val abi = selectAbi()
        val target = File(toolsDir, "aapt2")

        // Always re-extract + re-chmod on every cold start so permissions are fresh.
        // extractAsset skips if file is already identical in size.
        extractAsset(context, "$ASSETS_PREFIX/aapt2-$abi", target)
        makeExecutable(target)

        // Verify executable - throw early with a clear message if all methods fail
        if (!target.canExecute()) {
            throw RuntimeException(
                "aapt2 binary at ${target.absolutePath} is not executable after chmod. " +
                "Device ABI: $abi, File size: ${target.length()}"
            )
        }

        return target
    }

    private fun selectAbi(): String {
        val supported = Build.SUPPORTED_ABIS.toList()
        // Pick the best ABI we have a binary for
        for (candidate in listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")) {
            if (supported.any { it == candidate }) return candidate
        }
        return supported.firstOrNull() ?: "arm64-v8a"
    }

    /**
     * Makes a file executable using three escalating strategies:
     *  1. android.system.Os.chmod (kernel-level, most reliable)
     *  2. java.io.File.setExecutable (JVM wrapper)
     *  3. Shell `chmod 755` via ProcessBuilder
     */
    private fun makeExecutable(file: File) {
        // Strategy 1: Os.chmod with rwxr-xr-x (493 decimal = 0755 octal)
        try {
            Os.chmod(file.absolutePath, 493)
        } catch (_: Exception) { }

        if (file.canExecute()) return

        // Strategy 2: Java File API (belt-and-suspenders)
        file.setExecutable(true, false)

        if (file.canExecute()) return

        // Strategy 3: Shell chmod as last resort
        try {
            ProcessBuilder("chmod", "755", file.absolutePath)
                .redirectErrorStream(true)
                .start()
                .waitFor()
        } catch (_: Exception) { }
    }

    /**
     * Extracts an asset file to [target].
     *
     * - If the asset is uncompressed in the APK (noCompress flag): uses openFd() size comparison
     *   to skip re-extraction when already up-to-date.
     * - If the asset is compressed: falls back to streaming via open(), skipping only if
     *   the target already has content (assumes it's up-to-date across reinstalls).
     */
    private fun extractAsset(context: Context, assetPath: String, target: File) {
        try {
            // Fast path: uncompressed asset in APK - compare sizes
            val fd = context.assets.openFd(assetPath)
            val assetLength = fd.length
            fd.close()
            if (target.exists() && target.length() == assetLength) return
        } catch (_: Exception) {
            // Compressed asset: can't use openFd(). Skip only if target has content.
            if (target.exists() && target.length() > 0) return
        }

        target.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
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
