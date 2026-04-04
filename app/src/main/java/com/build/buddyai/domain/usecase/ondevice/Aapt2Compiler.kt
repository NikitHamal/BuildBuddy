package com.build.buddyai.domain.usecase.ondevice

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Stage 1: Compile resources using the bundled AAPT2 native binary.
 *
 * Produces:
 *  - `bin/res/project.zip`   (compiled flat resources)
 *  - `bin/resources.ap_`     (linked resource APK shell)
 *  - `gen/R.java`            (generated R class)
 */
class Aapt2Compiler(
    private val aapt2: File,
    private val projectDir: File,
    private val packageName: String,
    private val androidJar: File,
    private val minSdkVersion: Int = 21,
    private val targetSdkVersion: Int = 35,
    private val versionCode: String = "1",
    private val versionName: String = "1.0.0",
    private val log: (String) -> Unit = {}
) {
    private val resDir = File(projectDir, "app/src/main/res")
    private val manifestFile = File(projectDir, "app/src/main/AndroidManifest.xml")
    private val binDir = File(projectDir, ".build/bin").also { it.mkdirs() }
    private val genDir = File(projectDir, ".build/gen").also { it.mkdirs() }
    private val compiledResDir = File(binDir, "res").also { it.mkdirs() }

    val resourcesApkPath: String get() = File(binDir, "resources.ap_").absolutePath
    val rJavaDir: String get() = genDir.absolutePath

    fun compile() {
        if (resDir.exists()) {
            compileResources()
        }
        linkResources()
    }

    // ────────────────────────────────────────────────────────
    // Step 1a: aapt2 compile --dir res/ -o bin/res/project.zip
    // ────────────────────────────────────────────────────────
    private fun compileResources() {
        val compiledZip = File(compiledResDir, "project.zip")
        val args = listOf(
            aapt2.absolutePath,
            "compile",
            "--dir", resDir.absolutePath,
            "-o", compiledZip.absolutePath
        )
        execute(args, "AAPT2 compile")
    }

    // ────────────────────────────────────────────────────────
    // Step 1b: aapt2 link ... -o resources.ap_
    // ────────────────────────────────────────────────────────
    private fun linkResources() {
        val args = mutableListOf(
            aapt2.absolutePath,
            "link",
            "--allow-reserved-package-id",
            "--auto-add-overlay",
            "--no-version-vectors",
            "--min-sdk-version", minSdkVersion.toString(),
            "--target-sdk-version", targetSdkVersion.toString(),
            "--version-code", versionCode,
            "--version-name", versionName,
            "-I", androidJar.absolutePath,
            "--manifest", manifestFile.absolutePath,
            "--java", genDir.absolutePath,
            "-o", resourcesApkPath
        )

        // Add compiled resource ZIPs if they exist
        compiledResDir.listFiles()?.filter { it.isFile && it.extension == "zip" }?.forEach {
            args += listOf("-R", it.absolutePath)
        }

        execute(args, "AAPT2 link")
    }

    private fun execute(args: List<String>, tag: String) {
        log("[$tag] ${args.joinToString(" ")}")
        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (output.isNotBlank()) log("[$tag] $output")
        if (exitCode != 0) {
            throw RuntimeException("$tag failed (exit $exitCode):\n$output")
        }
    }
}
