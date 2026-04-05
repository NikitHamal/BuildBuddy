package com.build.buddyai.domain.usecase

import android.content.Context
import com.build.buddyai.core.model.BuildLogEntry
import com.build.buddyai.core.model.LogLevel
import com.build.buddyai.core.model.PreferredBuildEngine
import com.build.buddyai.core.model.Project
import com.build.buddyai.domain.usecase.ondevice.GradleOnDeviceBuilder
import com.build.buddyai.domain.usecase.ondevice.IntegrityLevel
import com.build.buddyai.domain.usecase.ondevice.OnDeviceBuildPipeline
import com.build.buddyai.domain.usecase.ondevice.ProjectIntegrityVerifier
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
    private val buildCancellationRegistry: com.build.buddyai.core.common.BuildCancellationRegistry
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
        onEvent: suspend (BuildEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        val projectDir = File(project.projectPath)
        if (!projectDir.exists()) {
            onEvent(BuildEvent.Failure("Project directory not found"))
            return@withContext
        }

        try {
            val integrity = ProjectIntegrityVerifier.verify(projectDir)
            integrity.warnings.forEach { onEvent(BuildEvent.Warning(it.message + (it.filePath?.let { path -> " [$path]" } ?: ""))) }
            if (integrity.errors.isNotEmpty()) {
                val errorMessage = buildString {
                    appendLine("Project integrity checks failed:")
                    integrity.errors.forEach { issue ->
                        append("• ")
                        append(issue.message)
                        issue.filePath?.let { append(" [").append(it).append("]") }
                        appendLine()
                    }
                }.trim()
                onEvent(BuildEvent.Failure(errorMessage))
                return@withContext
            }

            val engine = selectEngine(project, integrity)

            onEvent(BuildEvent.Progress(0.02f, "Preparing build environment…"))
            onEvent(BuildEvent.Log(logEntry(LogLevel.INFO, "Starting build for ${project.name}")))
            onEvent(BuildEvent.Log(logEntry(LogLevel.INFO, "Project path: ${project.projectPath}")))
            onEvent(BuildEvent.Log(logEntry(LogLevel.INFO, "Selected build engine: ${engine.displayName}")))

            val result = when (engine) {
                BuildEngine.LEGACY -> {
                    onEvent(BuildEvent.Log(logEntry(LogLevel.INFO, "Build engine: AAPT2 + ECJ + D8 (legacy on-device pipeline)")))
                    val pipeline = OnDeviceBuildPipeline(context)
                    val buildResult = pipeline.build(
                        project = project,
                        buildId = buildId,
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
                                    onEvent(BuildEvent.Log(logEntry(classifyLogLevel(message), message)))
                                }
                            }
                        }
                    )
                    BuildResult(buildResult.apkPath, buildResult.apkSize)
                }
                BuildEngine.GRADLE -> {
                    onEvent(BuildEvent.Progress(0.08f, "Running Gradle validation build…"))
                    val builder = GradleOnDeviceBuilder(context = context, projectDir = projectDir) { message ->
                        kotlinx.coroutines.runBlocking {
                            if (coroutineContext.isActive && !buildCancellationRegistry.isCancelled(buildId)) {
                                val level = classifyLogLevel(message)
                                onEvent(BuildEvent.Log(logEntry(level, message)))
                            }
                        }
                    }
                    val buildResult = builder.build()
                    BuildResult(buildResult.apkFile.absolutePath, buildResult.apkFile.length())
                }
            }

            if (buildCancellationRegistry.isCancelled(buildId)) {
                onEvent(BuildEvent.Cancelled())
                return@withContext
            }

            onEvent(BuildEvent.Progress(1f, "Build complete"))
            onEvent(BuildEvent.Success(result.artifactPath, result.artifactSize))
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

    private fun selectEngine(
        project: Project,
        integrity: com.build.buddyai.domain.usecase.ondevice.IntegrityReport
    ): BuildEngine {
        val preferred = integrity.preferredBuildEngine
            ?.let { raw -> PreferredBuildEngine.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
            ?: project.template.preferredBuildEngine
        val index = integrity.symbolIndex
        val legacyBlocked = integrity.warnings.any { warning ->
            warning.message.contains("ConstraintLayout", ignoreCase = true) ||
                warning.message.contains("AndroidX/Material XML widgets", ignoreCase = true)
        }
        return when (preferred) {
            PreferredBuildEngine.LEGACY -> {
                if (index.hasKotlin) throw RuntimeException("Legacy validation was requested but Kotlin sources are present. Switch this project to the Gradle build path.")
                if (legacyBlocked) throw RuntimeException("Legacy validation is blocked by dependency-backed XML resources. Use the Gradle build path for this project.")
                BuildEngine.LEGACY
            }
            PreferredBuildEngine.GRADLE -> BuildEngine.GRADLE
            PreferredBuildEngine.AUTO -> when {
                index.hasKotlin || index.hasCompose || legacyBlocked -> BuildEngine.GRADLE
                else -> BuildEngine.LEGACY
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
        BuildLogEntry(timestamp = System.currentTimeMillis(), level = level, message = message, source = "build")

    private enum class BuildEngine(val displayName: String) {
        LEGACY("AAPT2 + ECJ + D8"),
        GRADLE("Gradle wrapper + AGP")
    }

    private data class BuildResult(
        val artifactPath: String,
        val artifactSize: Long
    )
}
