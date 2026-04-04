package com.build.buddyai.domain.usecase

import android.content.Context
import com.build.buddyai.core.common.BuildCancellationRegistry
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.model.BuildLogEntry
import com.build.buddyai.core.model.LogLevel
import com.build.buddyai.core.model.Project
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val buildCancellationRegistry: BuildCancellationRegistry
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
            onEvent(BuildEvent.Progress(0.05f, "Validating real build environment…"))
            onEvent(BuildEvent.Log(logEntry(LogLevel.INFO, "Starting real Gradle build for ${project.name}")))
            onEvent(BuildEvent.Log(logEntry(LogLevel.INFO, "Project path: ${project.projectPath}")))

            val wrapperScript = resolveGradleWrapper(projectDir)
                ?: run {
                    onEvent(BuildEvent.Failure("Gradle wrapper not found. BuildBuddy now performs real Gradle builds only and will not fabricate APKs. Add gradlew/gradlew.bat and the wrapper files to the project root."))
                    return@withContext
                }
            val wrapperJar = findGradleWrapperJar(projectDir)
                ?: run {
                    onEvent(BuildEvent.Failure("Gradle wrapper JAR is missing. Real builds require gradle/wrapper/gradle-wrapper.jar."))
                    return@withContext
                }

            onEvent(BuildEvent.Log(logEntry(LogLevel.INFO, "Using wrapper script: ${wrapperScript.name}")))
            onEvent(BuildEvent.Log(logEntry(LogLevel.INFO, "Using wrapper runtime: ${wrapperJar.relativeTo(projectDir).invariantSeparatorsPath}")))
            onEvent(BuildEvent.Progress(0.15f, "Launching Gradle assembleDebug…"))

            val process = ProcessBuilder(buildCommand(wrapperScript))
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            buildCancellationRegistry.register(buildId, process)

            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    if (buildCancellationRegistry.isCancelled(buildId) || !coroutineContext.isActive) {
                        process.destroyForcibly()
                        break
                    }
                    val line = reader.readLine() ?: break
                    onEvent(BuildEvent.Log(logEntry(classifyLogLevel(line), line)))
                    emitProgressFromLog(line, onEvent)
                }
            }

            val exitCode = process.waitFor()
            val wasCancelled = buildCancellationRegistry.isCancelled(buildId) || !coroutineContext.isActive
            buildCancellationRegistry.unregister(buildId)

            if (wasCancelled) {
                onEvent(BuildEvent.Cancelled())
                return@withContext
            }

            if (exitCode != 0) {
                onEvent(BuildEvent.Failure("Gradle build failed with exit code $exitCode"))
                return@withContext
            }

            onEvent(BuildEvent.Progress(0.92f, "Collecting generated APK artifact…"))
            val apk = findLatestApk(projectDir) ?: run {
                onEvent(BuildEvent.Failure("Gradle reported success but no APK was found under app/build/outputs/apk."))
                return@withContext
            }

            val sanitizedProjectName = project.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val destination = File(FileUtils.getArtifactsDir(context), "${sanitizedProjectName}_${buildId}.apk")
            apk.copyTo(destination, overwrite = true)
            onEvent(BuildEvent.Log(logEntry(LogLevel.INFO, "APK captured: ${destination.absolutePath}")))
            onEvent(BuildEvent.Progress(1f, "Build completed"))
            onEvent(BuildEvent.Success(destination.absolutePath, destination.length()))
        } catch (e: Exception) {
            val wasCancelled = buildCancellationRegistry.isCancelled(buildId) || e is InterruptedException
            buildCancellationRegistry.unregister(buildId)
            if (wasCancelled) {
                onEvent(BuildEvent.Cancelled())
            } else {
                onEvent(BuildEvent.Failure(e.message ?: "Build failed"))
            }
        }
    }

    private suspend fun emitProgressFromLog(line: String, onEvent: suspend (BuildEvent) -> Unit) {
        val normalized = line.lowercase(Locale.US)
        when {
            normalized.contains("task :app:prebuild") -> onEvent(BuildEvent.Progress(0.22f, "Preparing project…"))
            normalized.contains("compile") -> onEvent(BuildEvent.Progress(0.45f, "Compiling sources…"))
            normalized.contains("merge") && normalized.contains("resources") -> onEvent(BuildEvent.Progress(0.60f, "Merging resources…"))
            normalized.contains("dex") -> onEvent(BuildEvent.Progress(0.75f, "Dexing bytecode…"))
            normalized.contains("package") || normalized.contains("assemble") -> onEvent(BuildEvent.Progress(0.88f, "Packaging APK…"))
            normalized.contains("build successful") -> {
                onEvent(BuildEvent.Progress(0.95f, "Finalizing build artifact…"))
                delay(50)
            }
        }
    }

    private fun resolveGradleWrapper(projectDir: File): File? {
        val unix = File(projectDir, "gradlew")
        if (unix.exists()) {
            unix.setExecutable(true)
            return unix
        }
        return File(projectDir, "gradlew.bat").takeIf { it.exists() }
    }

    private fun findGradleWrapperJar(projectDir: File): File? =
        File(projectDir, "gradle/wrapper/gradle-wrapper.jar").takeIf { it.exists() && it.isFile }

    private fun buildCommand(wrapperScript: File): List<String> = when {
        wrapperScript.name.endsWith(".bat", ignoreCase = true) -> listOf("cmd", "/c", wrapperScript.absolutePath, ":app:assembleDebug", "--stacktrace", "--console=plain")
        else -> listOf("sh", wrapperScript.absolutePath, ":app:assembleDebug", "--stacktrace", "--console=plain")
    }

    private fun findLatestApk(projectDir: File): File? {
        val outputsDir = File(projectDir, "app/build/outputs/apk")
        return outputsDir.takeIf { it.exists() }
            ?.walkTopDown()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
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
        BuildLogEntry(timestamp = System.currentTimeMillis(), level = level, message = message, source = "gradle")
}
