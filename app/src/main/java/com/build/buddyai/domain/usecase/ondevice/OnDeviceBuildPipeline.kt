package com.build.buddyai.domain.usecase.ondevice

import android.content.Context
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.model.Project
import java.io.File

/**
 * Orchestrates the complete on-device build pipeline:
 *
 *   1. Initialize build tools (AAPT2, android.jar, testkey)
 *   2. AAPT2: Compile resources → R.java + resources.ap_
 *   3. ECJ:   Compile Java sources → .class files
 *   4. D8:    Convert .class → classes.dex
 *   5. APK:   Package + sign → final .apk
 *
 * All stages run in-process on Android's ART runtime.
 * No external JDK or Gradle installation required.
 */
class OnDeviceBuildPipeline(
    private val context: Context
) {
    data class BuildResult(val apkPath: String, val apkSize: Long)

    fun build(
        project: Project,
        buildId: String,
        onProgress: (Float, String) -> Unit,
        onLog: (String) -> Unit
    ): BuildResult {
        val projectDir = File(project.projectPath)

        // ── Step 0: Prepare build tools ────────────────────────────────────────
        onProgress(0.05f, "Initializing build tools…")
        onLog("Initializing on-device build environment")
        OnDeviceBuildEnvironment.initialize(context)

        val env = OnDeviceBuildEnvironment
        val buildDir = File(projectDir, ".build").also { it.mkdirs() }
        val classOutputDir = File(buildDir, "classes").also { it.mkdirs() }
        val dexOutputDir = File(buildDir, "dex").also { it.mkdirs() }
        val genDir = File(buildDir, "gen").also { it.mkdirs() }

        // Parse project metadata
        val packageName = resolvePackageName(projectDir)
        val minSdk = 21
        val targetSdk = 35

        // ── Step 1: AAPT2 Resource Compilation ────────────────────────────────
        onProgress(0.15f, "Compiling resources with AAPT2…")
        onLog("Stage 1/4: AAPT2 resource compilation")

        val aapt2 = Aapt2Compiler(
            aapt2 = env.aapt2Binary,
            projectDir = projectDir,
            packageName = packageName,
            androidJar = env.androidJar,
            minSdkVersion = minSdk,
            targetSdkVersion = targetSdk,
            log = onLog
        )
        aapt2.compile()
        onLog("Resources compiled successfully")

        // ── Step 2: ECJ Java Compilation ──────────────────────────────────────
        onProgress(0.40f, "Compiling Java sources…")
        onLog("Stage 2/4: ECJ Java compilation")

        val ecj = EcjCompiler(
            projectDir = projectDir,
            androidJar = env.androidJar,
            coreLambdaStubsJar = env.coreLambdaStubsJar,
            classOutputDir = classOutputDir,
            rJavaDir = File(aapt2.rJavaDir),
            log = onLog
        )
        ecj.compile()
        onLog("Java compilation complete")

        // ── Step 3: D8 DEX Compilation ─────────────────────────────────────────
        onProgress(0.65f, "Converting to DEX with D8…")
        onLog("Stage 3/4: D8 DEX compilation")

        val d8 = D8Dexer(
            classOutputDir = classOutputDir,
            androidJar = env.androidJar,
            dexOutputDir = dexOutputDir,
            minApiLevel = minSdk,
            log = onLog
        )
        d8.dex()
        onLog("DEX compilation complete")

        // ── Step 4: APK Packaging + Signing ───────────────────────────────────
        onProgress(0.85f, "Packaging and signing APK…")
        onLog("Stage 4/4: APK packaging and signing")

        val sanitizedName = project.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val outputApkPath = File(FileUtils.getArtifactsDir(context), "${sanitizedName}_${buildId}.apk").absolutePath

        val packager = ApkPackager(
            resourcesApkPath = aapt2.resourcesApkPath,
            dexOutputDir = dexOutputDir,
            testkeyDir = env.testkeyDir,
            outputApkPath = outputApkPath,
            log = onLog
        )
        packager.packageAndSign()

        // ── Done ───────────────────────────────────────────────────────────────
        onProgress(1.0f, "Build complete")
        val apkFile = File(outputApkPath)
        onLog("APK ready: $outputApkPath (${apkFile.length()} bytes)")

        return BuildResult(outputApkPath, apkFile.length())
    }

    /** Extract package name from the project's AndroidManifest.xml. */
    private fun resolvePackageName(projectDir: File): String {
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        if (!manifest.exists()) return "com.example.app"
        return try {
            val content = manifest.readText()
            val match = Regex("""package\s*=\s*["']([^"']+)["']""").find(content)
            match?.groupValues?.get(1) ?: "com.example.app"
        } catch (_: Exception) {
            "com.example.app"
        }
    }
}
