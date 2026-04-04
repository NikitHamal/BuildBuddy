package com.build.buddyai.domain.usecase.ondevice

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Manages setup of build tools needed by the on-device build pipeline.
 *
 * AAPT2 is shipped as a JNI library (libaapt2.so) so Android's package manager
 * installs it with the correct SELinux context that allows execution via execve().
 * This sidesteps the Android 10+ restriction that blocks executing files from
 * app_data_file labeled directories (i.e. filesDir).
 *
 * android.jar, core-lambda-stubs.jar, and testkey are extracted from assets
 * on first run.
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

            // AAPT2: shipped as libaapt2.so in jniLibs → installed by package manager
            // with proper SELinux context (app_executable_file), already executable.
            aapt2Binary = resolveAapt2(context)

            // android.jar: large, compressed in assets → extract to filesDir on first run
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

    /**
     * Resolves the aapt2 binary from the app's native library directory.
     * The package manager installs libaapt2.so there with execute permission and
     * the correct SELinux label, so no chmod is needed.
     */
    private fun resolveAapt2(context: Context): File {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val aapt2 = File(nativeLibDir, "libaapt2.so")
        if (!aapt2.exists()) {
            throw RuntimeException(
                "libaapt2.so not found in nativeLibraryDir ($nativeLibDir). " +
                "Ensure the app was installed from a valid APK (not run directly from IDE)."
            )
        }
        if (!aapt2.canExecute()) {
            // This should not happen when installed via package manager, but try anyway
            aapt2.setExecutable(true, false)
        }
        return aapt2
    }

    /**
     * Extracts an asset file to [target].
     *
     * - Uncompressed assets (noCompress flag set): uses openFd() size comparison to skip
     *   re-extraction when already up-to-date.
     * - Compressed assets: falls back to streaming via open(), skipping if target has content.
     */
    private fun extractAsset(context: Context, assetPath: String, target: File) {
        try {
            // Fast path: uncompressed asset - compare sizes to detect updates
            val fd = context.assets.openFd(assetPath)
            val assetLength = fd.length
            fd.close()
            if (target.exists() && target.length() == assetLength) return
        } catch (_: Exception) {
            // Compressed asset: openFd() not available. Skip if target already has content.
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
