package com.build.buddyai.domain.usecase

import android.content.Context
import com.build.buddyai.core.common.BuildCancellationRegistry
import com.build.buddyai.core.common.BuildProfileManager
import com.build.buddyai.core.model.BuildLogEntry
import com.build.buddyai.core.model.ArtifactFormat
import com.build.buddyai.core.model.BuildProfile
import com.build.buddyai.core.model.LogLevel
import com.build.buddyai.core.model.Project
import com.build.buddyai.domain.usecase.ondevice.OnDeviceBuildPipeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class BuildProjectUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val buildCancellationRegistry: BuildCancellationRegistry,
    private val buildProfileManager: BuildProfileManager
) {
    sealed class BuildEvent {
        data class Progress(val progress: Float, val message: String) : BuildEvent()
        data class Log(val entry: BuildLogEntry) : BuildEvent()
        data class Warning(val message: String) : BuildEvent()
        data class Success(val artifactPath: String, val artifactSize: Long) : BuildEvent()
        data class Failure(val error: String) : BuildEvent()
        data class Cancelled(val message: String = "Build cancelled") : BuildEvent()
    }

    suspend operator fun invoke(
        project: Project,
        buildId: String,
        buildProfile: BuildProfile = BuildProfile(),
        onEvent: suspend (BuildEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        val projectDir = File(project.projectPath)
        if (!projectDir.exists()) {
            onEvent(BuildEvent.Failure("Project directory not found"))
            return@withContext
        }

        try {
            detectCompatibilityWarnings(projectDir).forEach { warning ->
                onEvent(BuildEvent.Warning(warning))
            }

            if (buildProfile.artifactFormat == ArtifactFormat.AAB) {
                onEvent(
                    BuildEvent.Failure(
                        "AAB export is configured in the build profile, but the local bundle toolchain is not bundled in this on-device engine yet. Switch the artifact format to APK for now."
                    )
                )
                return@withContext
            }

            if (buildProfile.variant.name == "RELEASE") {
                val signing = buildProfile.signing
                val secrets = buildProfileManager.getSigningSecrets(project.id)
                if (signing == null || signing.keystorePath.isBlank() || signing.keyAlias.isBlank() || secrets == null) {
                    onEvent(
                        BuildEvent.Failure(
                            "Release build requires a configured keystore, key alias, store password, and key password. Open the build workspace, import a keystore, and save signing details first."
                        )
                    )
                    return@withContext
                }
            }

            onEvent(BuildEvent.Progress(0.02f, "Preparing on-device build environment…"))
            onEvent(BuildEvent.Log(logEntry(LogLevel.INFO, "Starting on-device build for ${project.name}")))
            onEvent(BuildEvent.Log(logEntry(LogLevel.INFO, "Project path: ${project.projectPath}")))
            onEvent(
                BuildEvent.Log(
                    logEntry(
                        LogLevel.INFO,
                        "Build engine: AAPT2 + ECJ + D8 (no JDK required) • variant=${buildProfile.variant.name.lowercase(Locale.US)} • artifact=${buildProfile.artifactFormat.extension} • flavor=${buildProfile.flavorName.ifBlank { "main" }}"
                    )
                )
            )

            val pipeline = OnDeviceBuildPipeline(context)
            val signingSecrets = buildProfileManager.getSigningSecrets(project.id)

            val result = pipeline.build(
                project = project,
                buildId = buildId,
                buildProfile = buildProfile,
                signingSecrets = signingSecrets,
                onProgress = { fraction, message ->
                    kotlinx.coroutines.runBlocking {
                        if (coroutineContext.isActive && !buildCancellationRegistry.isCancelled(buildId)) {
                            onEvent(BuildEvent.Progress(fraction, message))
                        }
                    }
                },
                onLog = { message ->
                    kotlinx.coroutines.runBlocking {
                        if (coroutineContext.isActive && !buildCancellationRegistry.isCancelled(buildId)) {
                            val level = classifyLogLevel(message)
                            onEvent(BuildEvent.Log(logEntry(level, message)))
                        }
                    }
                }
            )

            if (buildCancellationRegistry.isCancelled(buildId)) {
                onEvent(BuildEvent.Cancelled())
                return@withContext
            }

            onEvent(BuildEvent.Progress(1f, "Build complete"))
            onEvent(BuildEvent.Success(result.apkPath, result.apkSize))
        } catch (e: Exception) {
            val wasCancelled = buildCancellationRegistry.isCancelled(buildId) || e is InterruptedException
            buildCancellationRegistry.unregister(buildId)
            if (wasCancelled) {
                onEvent(BuildEvent.Cancelled())
            } else {
                onEvent(BuildEvent.Failure(e.message ?: "Build failed"))
                onEvent(BuildEvent.Log(logEntry(LogLevel.ERROR, e.stackTraceToString())))
            }
        } finally {
            buildCancellationRegistry.unregister(buildId)
        }
    }

    private fun detectCompatibilityWarnings(projectDir: File): List<String> {
        val kotlinFiles = sequenceOf(
            File(projectDir, "app/src/main/java"),
            File(projectDir, "app/src/main/kotlin")
        )
            .filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" } }
            .map { it.relativeTo(projectDir).invariantSeparatorsPath }
            .take(10)
            .toList()

        return buildList {
            if (kotlinFiles.isNotEmpty()) {
                add(
                    "On-device validation currently compiles Java sources only. Kotlin sources were detected and this build will fail until the Kotlin pipeline lands: ${kotlinFiles.joinToString()}"
                )
            }
        }
    }

    private fun classifyLogLevel(line: String): LogLevel {
        val normalized = line.lowercase(Locale.US)
        return when {
            normalized.contains("error") || normalized.contains("exception") || normalized.contains("failed") -> LogLevel.ERROR
            normalized.contains("warning") || normalized.contains("deprecated") -> LogLevel.WARNING
            normalized.contains("debug") -> LogLevel.DEBUG
            else -> LogLevel.INFO
        }
    }

    private fun logEntry(level: LogLevel, message: String): BuildLogEntry =
        BuildLogEntry(timestamp = System.currentTimeMillis(), level = level, message = message, source = "ondevice")
}
