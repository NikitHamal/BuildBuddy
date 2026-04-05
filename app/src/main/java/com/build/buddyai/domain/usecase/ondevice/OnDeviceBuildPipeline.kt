package com.build.buddyai.domain.usecase.ondevice

import android.content.Context
import com.build.buddyai.core.agent.ManifestPlaceholderResolver
import com.build.buddyai.core.common.BuildProfileManager
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.model.BuildProfile
import com.build.buddyai.core.model.Project
import java.io.File

class OnDeviceBuildPipeline(
    private val context: Context,
    private val manifestPlaceholderResolver: ManifestPlaceholderResolver = ManifestPlaceholderResolver()
) {
    data class BuildResult(val apkPath: String, val apkSize: Long)

    fun build(
        project: Project,
        buildId: String,
        buildProfile: BuildProfile,
        signingSecrets: BuildProfileManager.SigningSecrets?,
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
        val variantDir = File(buildDir, "variant/${buildProfile.variant.name.lowercase()}_${buildProfile.flavorName.ifBlank { "main" }}").also { it.mkdirs() }

        val projectBuildTools = File(projectDir, "build_tools").also { it.mkdirs() }
        val projectStubsJar = File(projectBuildTools, "javax-lang-model-stubs.jar")
        if (!projectStubsJar.exists() && env.javaxLangModelStubsJar.exists()) env.javaxLangModelStubsJar.copyTo(projectStubsJar)

        validateProjectSources(projectDir)

        val stagedManifest = File(variantDir, "AndroidManifest.xml")
        val placeholderResult = manifestPlaceholderResolver.stage(project, projectDir, buildProfile, variantDir)
        if (!placeholderResult.isValid) throw RuntimeException("Manifest placeholder resolution failed: ${placeholderResult.unresolvedKeys.joinToString()}")
        onLog("Resolved manifest placeholders: ${placeholderResult.resolvedValues.keys.joinToString()}")
        placeholderResult.warnings.forEach(onLog)

        val packageName = resolvePackageName(stagedManifest, project.packageName)
        val minSdk = 21
        val targetSdk = 35
        val versionCode = (buildProfile.versionCodeOverride ?: 1).toString()
        val versionName = buildProfile.versionNameOverride ?: "1.0.0${buildProfile.versionNameSuffix}"

        onProgress(0.15f, "Compiling resources with AAPT2…")
        onLog("Stage 1/4: AAPT2 resource compilation")

        val aapt2 = Aapt2Compiler(
            aapt2 = env.aapt2Binary,
            projectDir = projectDir,
            packageName = packageName,
            androidJar = env.androidJar,
            manifestFileOverride = stagedManifest,
            minSdkVersion = minSdk,
            targetSdkVersion = targetSdk,
            versionCode = versionCode,
            versionName = versionName,
            log = onLog
        )
        aapt2.compile()
        onLog("Resources compiled successfully")

        onProgress(0.40f, "Compiling Java sources…")
        onLog("Stage 2/4: ECJ Java compilation")
        EcjCompiler(
            projectDir = projectDir,
            androidJar = env.androidJar,
            coreLambdaStubsJar = env.coreLambdaStubsJar,
            classOutputDir = classOutputDir,
            rJavaDir = File(aapt2.rJavaDir),
            log = onLog
        ).compile()
        onLog("Java compilation complete")

        onProgress(0.65f, "Converting to DEX with D8…")
        onLog("Stage 3/4: D8 DEX compilation")
        D8Dexer(
            classOutputDir = classOutputDir,
            androidJar = env.androidJar,
            dexOutputDir = dexOutputDir,
            minApiLevel = minSdk,
            log = onLog
        ).dex()
        onLog("DEX compilation complete")

        onProgress(0.85f, "Packaging and signing APK…")
        onLog("Stage 4/4: APK packaging and signing")

        val sanitizedName = project.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val variantName = buildProfile.variant.name.lowercase()
        val flavorName = buildProfile.flavorName.ifBlank { "main" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val outputApkPath = File(FileUtils.getArtifactsDir(context), "${sanitizedName}_${variantName}_${flavorName}_$buildId.apk").absolutePath

        ApkPackager(
            resourcesApkPath = aapt2.resourcesApkPath,
            dexOutputDir = dexOutputDir,
            testkeyDir = env.testkeyDir,
            outputApkPath = outputApkPath,
            buildVariant = buildProfile.variant,
            signingConfig = buildProfile.signing,
            signingSecrets = signingSecrets,
            log = onLog
        ).packageAndSign()

        val apkFile = File(outputApkPath)
        BuiltApkInspector.verifyParseable(context = context, apkFile = apkFile, expectedPackageName = packageName, log = onLog)
        onProgress(1.0f, "Build complete")
        onLog("APK ready: $outputApkPath (${apkFile.length()} bytes)")
        return BuildResult(outputApkPath, apkFile.length())
    }

    private fun resolvePackageName(manifestFile: File, fallback: String): String = try {
        Regex("""package\s*=\s*["']([^"']+)["']""").find(manifestFile.readText())?.groupValues?.get(1) ?: fallback
    } catch (_: Exception) { fallback }

    private fun validateProjectSources(projectDir: File) {
        val kotlinFiles = sequenceOf(File(projectDir, "app/src/main/java"), File(projectDir, "app/src/main/kotlin"))
            .filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" } }
            .map { it.relativeTo(projectDir).invariantSeparatorsPath }
            .toList()
        if (kotlinFiles.isNotEmpty()) {
            throw RuntimeException("On-device validation currently supports Java source compilation only. This project contains Kotlin sources that cannot be compiled by the ECJ-based pipeline yet: ${kotlinFiles.take(10).joinToString()}.")
        }
    }
}
