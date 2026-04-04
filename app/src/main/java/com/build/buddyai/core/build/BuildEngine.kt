package com.build.buddyai.core.build

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.build.buddyai.R
import com.build.buddyai.core.data.repository.BuildRepository
import com.build.buddyai.core.data.repository.PreferencesRepository
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.Artifact
import com.build.buddyai.core.model.ArtifactType
import com.build.buddyai.core.model.BuildCompatibilityReport
import com.build.buddyai.core.model.BuildDiagnostic
import com.build.buddyai.core.model.BuildMode
import com.build.buddyai.core.model.BuildRecord
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.BuildSupportLevel
import com.build.buddyai.core.model.Project
import com.build.buddyai.core.model.ToolchainManifest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json

interface BuildEngine {
    suspend fun assess(project: Project): BuildCompatibilityReport
    suspend fun enqueue(projectId: String, mode: BuildMode): String
}

@Singleton
class ToolchainManager @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val json: Json,
) {
    fun toolchainDirectory(overridePath: String?): File =
        overridePath?.let(::File) ?: File(context.filesDir, "toolchains/android")

    fun probe(overridePath: String?): ToolchainManifest? {
        val root = toolchainDirectory(overridePath)
        val manifestFile = File(root, "manifest.json")
        if (!manifestFile.exists()) return null
        return runCatching {
            json.decodeFromString(ToolchainManifest.serializer(), manifestFile.readText())
        }.getOrNull()
    }

    fun executablePath(manifest: ToolchainManifest, overridePath: String?): File =
        File(toolchainDirectory(overridePath), manifest.buildExecutable)
}

@Singleton
class ToolchainBackedBuildEngine @Inject constructor(
    private val context: Context,
    private val workManager: WorkManager,
    private val preferencesRepository: PreferencesRepository,
    private val toolchainManager: ToolchainManager,
) : BuildEngine {
    override suspend fun assess(project: Project): BuildCompatibilityReport {
        val manifestFile = File(project.workspacePath, "buildbuddy.json")
        if (!manifestFile.exists()) {
            return BuildCompatibilityReport(
                level = BuildSupportLevel.UNSUPPORTED,
                summary = "Workspace is missing buildbuddy.json.",
                diagnostics = listOf(
                    BuildDiagnostic(
                        title = "Missing workspace manifest",
                        detail = "Only BuildBuddy-compatible projects with a buildbuddy.json manifest are supported for on-device builds.",
                    ),
                ),
            )
        }
        val preferences = preferencesRepository.preferences.firstOrNull()
        val toolchainManifest = toolchainManager.probe(preferences?.toolchainRootOverride)
        if (toolchainManifest != null) {
            return BuildCompatibilityReport(
                level = BuildSupportLevel.SUPPORTED,
                summary = "Ready to build with toolchain ${toolchainManifest.version}.",
                diagnostics = listOf(
                    BuildDiagnostic(
                        title = "BuildBuddy-compatible workspace detected",
                        detail = "This project can be handed to the installed BuildBuddy Android toolchain.",
                    ),
                ),
            )
        }
        return BuildCompatibilityReport(
            level = BuildSupportLevel.PARTIAL,
            summary = context.getString(R.string.build_toolchain_missing),
            diagnostics = listOf(
                BuildDiagnostic(
                    title = "Toolchain bundle required",
                    detail = "Install or configure a BuildBuddy Android toolchain bundle to compile this project on-device.",
                ),
            ),
        )
    }

    override suspend fun enqueue(projectId: String, mode: BuildMode): String {
        val workName = "buildbuddy-build-$projectId"
        val request = OneTimeWorkRequestBuilder<BuildWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .setInputData(
                workDataOf(
                    BuildWorker.KEY_PROJECT_ID to projectId,
                    BuildWorker.KEY_BUILD_MODE to mode.name,
                ),
            )
            .build()
        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
        return request.id.toString()
    }
}

@HiltWorker
class BuildWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val projectRepository: ProjectRepository,
    private val buildRepository: BuildRepository,
    private val toolchainManager: ToolchainManager,
    private val preferencesRepository: PreferencesRepository,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val projectId = inputData.getString(KEY_PROJECT_ID) ?: return Result.failure()
        val mode = inputData.getString(KEY_BUILD_MODE)?.let(BuildMode::valueOf) ?: BuildMode.DEBUG
        val project = projectRepository.getProject(projectId) ?: return Result.failure()
        val queued = buildRepository.createQueuedBuild(projectId, mode)
        setForeground(createForegroundInfo())

        val log = StringBuilder()
        val preferences = preferencesRepository.preferences.firstOrNull() ?: com.build.buddyai.core.model.AppPreferences()
        val manifest = toolchainManager.probe(preferences.toolchainRootOverride)
        if (manifest == null) {
            buildRepository.upsertBuild(
                queued.copy(
                    status = BuildStatus.FAILED,
                    supportLevel = BuildSupportLevel.PARTIAL,
                    finishedAt = System.currentTimeMillis(),
                    summary = applicationContext.getString(R.string.build_toolchain_missing),
                    rawLog = "Toolchain root: ${preferences.toolchainRootOverride ?: "<app files>/toolchains/android"}\nMissing manifest.json",
                    diagnostics = listOf(
                        BuildDiagnostic(
                            title = "Toolchain missing",
                            detail = "Expected manifest.json inside the configured toolchain directory.",
                        ),
                    ),
                ),
            )
            return Result.success()
        }

        val executable = toolchainManager.executablePath(manifest, preferences.toolchainRootOverride)
        if (!executable.exists()) {
            buildRepository.upsertBuild(
                queued.copy(
                    status = BuildStatus.FAILED,
                    supportLevel = BuildSupportLevel.PARTIAL,
                    finishedAt = System.currentTimeMillis(),
                    summary = "Toolchain manifest found but executable is missing.",
                    rawLog = "Expected executable at ${executable.absolutePath}",
                    diagnostics = listOf(
                        BuildDiagnostic(
                            title = "Executable missing",
                            detail = "The toolchain bundle is incomplete or misconfigured.",
                        ),
                    ),
                ),
            )
            return Result.success()
        }

        val outputDir = File(applicationContext.filesDir, "artifacts/${project.id}/${queued.id}").apply { mkdirs() }
        val process = ProcessBuilder(
            executable.absolutePath,
            "build",
            "--project",
            project.workspacePath,
            "--mode",
            mode.name.lowercase(),
            "--out",
            outputDir.absolutePath,
        ).redirectErrorStream(true).start()

        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                log.appendLine(line)
            }
        }

        val exitCode = process.waitFor()
        val artifact = outputDir.listFiles()
            ?.firstOrNull { it.extension.equals("apk", ignoreCase = true) }
            ?.let { file ->
                Artifact(
                    id = UUID.randomUUID().toString(),
                    projectId = project.id,
                    buildId = queued.id,
                    type = ArtifactType.APK,
                    filePath = file.absolutePath,
                    packageName = project.packageName,
                    versionName = "1.0.0",
                    versionCode = 1,
                    createdAt = System.currentTimeMillis(),
                    fileSizeBytes = file.length(),
                    installable = true,
                )
            }

        if (artifact != null) {
            buildRepository.upsertArtifact(artifact)
        }

        buildRepository.upsertBuild(
            queued.copy(
                status = if (exitCode == 0) BuildStatus.SUCCESS else BuildStatus.FAILED,
                supportLevel = BuildSupportLevel.SUPPORTED,
                finishedAt = System.currentTimeMillis(),
                summary = if (exitCode == 0) "Build finished successfully." else "Build failed with exit code $exitCode.",
                rawLog = log.toString(),
                diagnostics = if (exitCode == 0) emptyList() else listOf(
                    BuildDiagnostic(
                        title = "Toolchain build failed",
                        detail = "Inspect the raw logs and use Ask AI to fix with relevant file context.",
                    ),
                ),
                artifactId = artifact?.id,
            ),
        )
        return Result.success()
    }

    private fun createForegroundInfo(): ForegroundInfo = ForegroundInfo(
        88,
        androidx.core.app.NotificationCompat.Builder(applicationContext, "buildbuddy-builds")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.build_notification_title))
            .setOngoing(true)
            .build(),
    )

    companion object {
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_BUILD_MODE = "build_mode"
    }
}
