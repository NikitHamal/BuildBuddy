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
 *   6. Parse validation: ensure Android can open the APK we just produced
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
        require(projectDir.exists() && projectDir.isDirectory) { "Project directory not found: ${project.projectPath}" }

        onLog("Initializing on-device build environment")
        OnDeviceBuildEnvironment.initialize(context)

        val env = OnDeviceBuildEnvironment
        val buildDir = File(projectDir, ".build").also { it.mkdirs() }
        val classOutputDir = File(buildDir, "classes").also { it.mkdirs() }
        val dexOutputDir = File(buildDir, "dex").also { it.mkdirs() }

        val projectBuildTools = File(projectDir, "build_tools").also { it.mkdirs() }
        val projectStubsJar = File(projectBuildTools, "javax-lang-model-stubs.jar")
        if (!projectStubsJar.exists() && env.javaxLangModelStubsJar.exists()) {
            env.javaxLangModelStubsJar.copyTo(projectStubsJar)
        }

        validateProjectSources(projectDir)

        val packageName = resolvePackageName(projectDir)
        val minSdk = 21
        val targetSdk = 35

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

        val apkFile = File(outputApkPath)
        BuiltApkInspector.verifyParseable(
            context = context,
            apkFile = apkFile,
            expectedPackageName = packageName,
            log = onLog
        )

        onProgress(1.0f, "Build complete")
        onLog("APK ready: $outputApkPath (${apkFile.length()} bytes)")

        return BuildResult(outputApkPath, apkFile.length())
    }

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

    private fun validateProjectSources(projectDir: File) {
        val kotlinFiles = sequenceOf(
            File(projectDir, "app/src/main/java"),
            File(projectDir, "app/src/main/kotlin")
        ).filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" } }
            .map { it.relativeTo(projectDir).invariantSeparatorsPath }
            .toList()

        if (kotlinFiles.isNotEmpty()) {
            throw RuntimeException(
                "On-device validation currently supports Java source compilation only. This project contains Kotlin sources that cannot be compiled by the ECJ-based pipeline yet: ${kotlinFiles.take(10).joinToString()}."
            )
        }
    }
}
