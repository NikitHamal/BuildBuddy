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
 * All tools run on the device's ART runtime — no JDK required.
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

        // Extract AAPT2 native binary for this device's ABI
        aapt2Binary = extractAapt2(context, toolsDir)

        // Extract android.jar (compressed as android.jar.zip inside assets)
        androidJar = File(toolsDir, "android.jar")
        extractIfChanged(context, "$ASSETS_PREFIX/android.jar.zip", File(toolsDir, "android.jar.zip"))
        if (!androidJar.exists() || androidJar.length() == 0L) {
            unzip(File(toolsDir, "android.jar.zip"), toolsDir)
        }

        // Extract core-lambda-stubs.jar directly
        coreLambdaStubsJar = File(toolsDir, "core-lambda-stubs.jar")
        extractIfChanged(context, "$ASSETS_PREFIX/core-lambda-stubs.jar", coreLambdaStubsJar)

        // Extract testkey.zip → testkey/
        val testkeyZip = File(toolsDir, "testkey.zip")
        testkeyDir = File(toolsDir, "testkey")
        extractIfChanged(context, "$ASSETS_PREFIX/testkey.zip", testkeyZip)
        if (!testkeyDir.exists() || testkeyDir.list().isNullOrEmpty()) {
            testkeyDir.mkdirs()
            unzip(testkeyZip, testkeyDir)
        }

        initialized = true
    }

    /** Re-initialize on next call (e.g. after app update). */
    fun reset() { initialized = false }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun extractAapt2(context: Context, toolsDir: File): File {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val assetName = "aapt2-$abi"
        val target = File(toolsDir, "aapt2")
        extractIfChanged(context, "$ASSETS_PREFIX/$assetName", target)
        try {
            Os.chmod(target.absolutePath, 0b111_101_101) // rwxr-xr-x
        } catch (_: Exception) {
            target.setExecutable(true, false)
        }
        return target
    }

    /**
     * Copies an asset file to [target] only if the sizes differ (i.e. app updated).
     */
    private fun extractIfChanged(context: Context, assetPath: String, target: File) {
        val assetLength = try { context.assets.openFd(assetPath).length } catch (_: Exception) { return }
        if (target.exists() && target.length() == assetLength) return
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
