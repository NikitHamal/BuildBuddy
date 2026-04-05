package com.build.buddyai.feature.build

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.common.BuildCancellationRegistry
import com.build.buddyai.core.common.ChangeSetInfo
import com.build.buddyai.core.common.ChangeSetManager
import com.build.buddyai.core.common.SnapshotManager
import com.build.buddyai.core.common.BuildForegroundService
import com.build.buddyai.core.data.repository.ArtifactRepository
import com.build.buddyai.core.data.repository.BuildRepository
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.BuildArtifact
import com.build.buddyai.core.model.BuildLogEntry
import com.build.buddyai.core.model.BuildRecord
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.LogLevel
import com.build.buddyai.domain.usecase.BuildProjectUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class BuildUiState(
    val isBuilding: Boolean = false,
    val buildProgress: Float = 0f,
    val buildStatus: BuildStatus = BuildStatus.NONE,
    val statusMessage: String = "",
    val logEntries: List<BuildLogEntry> = emptyList(),
    val errorSummary: String? = null,
    val buildHistory: List<BuildRecord> = emptyList(),
    val currentBuildId: String? = null,
    val lastSuccessfulBuild: BuildRecord? = null,
    val compatibilityWarnings: List<String> = emptyList(),
    val latestArtifact: BuildArtifact? = null,
    val snapshots: List<SnapshotManager.SnapshotInfo> = emptyList(),
    val changeSets: List<ChangeSetInfo> = emptyList()
) {
    val problems: List<String>
        get() = buildList {
            compatibilityWarnings.forEach { add(it) }
            errorSummary?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
}

@HiltViewModel
class BuildViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val buildRepository: BuildRepository,
    private val artifactRepository: ArtifactRepository,
    private val buildProjectUseCase: BuildProjectUseCase,
    private val buildCancellationRegistry: BuildCancellationRegistry,
    private val snapshotManager: SnapshotManager,
    private val changeSetManager: ChangeSetManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    private var currentProjectId: String? = null
    private var buildJob: Job? = null
    private var buildHistoryJob: Job? = null
    private var artifactsJob: Job? = null

    fun initialize(projectId: String) {
        currentProjectId = projectId
        refreshRestorePoints()

        buildHistoryJob?.cancel()
        buildHistoryJob = viewModelScope.launch {
            buildRepository.getBuildRecordsByProject(projectId).collectLatest { records ->
                _uiState.update {
                    it.copy(
                        buildHistory = records,
                        lastSuccessfulBuild = records.firstOrNull { record -> record.status == BuildStatus.SUCCESS }
                    )
                }
            }
        }

        artifactsJob?.cancel()
        artifactsJob = viewModelScope.launch {
            artifactRepository.getArtifactsByProject(projectId).collectLatest { artifacts ->
                _uiState.update { it.copy(latestArtifact = artifacts.firstOrNull()) }
            }
        }
    }

    fun startBuild() {
        val projectId = currentProjectId ?: return
        if (_uiState.value.isBuilding) return

        buildJob = viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId) ?: return@launch
            val buildId = UUID.randomUUID().toString()
            runCatching {
                snapshotManager.createSnapshot(project.id, File(project.projectPath), "pre_build")
            }

            _uiState.update {
                it.copy(
                    isBuilding = true,
                    buildProgress = 0f,
                    buildStatus = BuildStatus.BUILDING,
                    statusMessage = "Preparing build…",
                    logEntries = emptyList(),
                    errorSummary = null,
                    currentBuildId = buildId,
                    compatibilityWarnings = emptyList()
                )
            }

            context.startForegroundService(
                Intent(context, BuildForegroundService::class.java).apply {
                    putExtra(BuildForegroundService.EXTRA_BUILD_ID, buildId)
                    putExtra(BuildForegroundService.EXTRA_PROJECT_NAME, project.name)
                }
            )

            val buildRecord = BuildRecord(
                id = buildId,
                projectId = projectId,
                status = BuildStatus.BUILDING,
                startedAt = System.currentTimeMillis()
            )
            buildRepository.insertBuildRecord(buildRecord)
            projectRepository.updateProject(project.copy(lastBuildStatus = BuildStatus.BUILDING))

            buildProjectUseCase(project, buildId) { event ->
                when (event) {
                    is BuildProjectUseCase.BuildEvent.Progress -> {
                        _uiState.update { it.copy(buildProgress = event.progress, statusMessage = event.message) }
                    }
                    is BuildProjectUseCase.BuildEvent.Log -> {
                        _uiState.update { it.copy(logEntries = it.logEntries + event.entry) }
                    }
                    is BuildProjectUseCase.BuildEvent.Warning -> {
                        _uiState.update { it.copy(compatibilityWarnings = (it.compatibilityWarnings + event.message).distinct()) }
                    }
                    is BuildProjectUseCase.BuildEvent.Success -> {
                        val completedAt = System.currentTimeMillis()
                        val completedRecord = buildRecord.copy(
                            status = BuildStatus.SUCCESS,
                            completedAt = completedAt,
                            durationMs = completedAt - buildRecord.startedAt,
                            artifactPath = event.artifactPath,
                            artifactSizeBytes = event.artifactSize,
                            logEntries = _uiState.value.logEntries
                        )
                        buildRepository.updateBuildRecord(completedRecord)
                        projectRepository.updateProject(project.copy(lastBuildStatus = BuildStatus.SUCCESS, lastBuildAt = completedAt))
                        artifactRepository.insertArtifact(
                            BuildArtifact(
                                id = UUID.randomUUID().toString(),
                                projectId = projectId,
                                projectName = project.name,
                                buildRecordId = buildId,
                                filePath = event.artifactPath,
                                fileName = File(event.artifactPath).name,
                                sizeBytes = event.artifactSize,
                                packageName = project.packageName,
                                versionName = "1.0.0",
                                versionCode = 1,
                                createdAt = completedAt,
                                minSdk = project.minSdk,
                                targetSdk = project.targetSdk
                            )
                        )
                        _uiState.update {
                            it.copy(isBuilding = false, buildProgress = 1f, buildStatus = BuildStatus.SUCCESS, statusMessage = "Build successful")
                        }
                    }
                    is BuildProjectUseCase.BuildEvent.Failure -> {
                        val completedAt = System.currentTimeMillis()
                        val failedRecord = buildRecord.copy(
                            status = BuildStatus.FAILED,
                            completedAt = completedAt,
                            durationMs = completedAt - buildRecord.startedAt,
                            errorSummary = event.error,
                            logEntries = _uiState.value.logEntries
                        )
                        buildRepository.updateBuildRecord(failedRecord)
                        projectRepository.updateProject(project.copy(lastBuildStatus = BuildStatus.FAILED, lastBuildAt = completedAt))
                        _uiState.update {
                            it.copy(isBuilding = false, buildStatus = BuildStatus.FAILED, statusMessage = "Build failed", errorSummary = event.error)
                        }
                    }
                    is BuildProjectUseCase.BuildEvent.Cancelled -> {
                        val completedAt = System.currentTimeMillis()
                        val cancelledRecord = buildRecord.copy(
                            status = BuildStatus.CANCELLED,
                            completedAt = completedAt,
                            durationMs = completedAt - buildRecord.startedAt,
                            errorSummary = event.message,
                            logEntries = _uiState.value.logEntries
                        )
                        buildRepository.updateBuildRecord(cancelledRecord)
                        projectRepository.updateProject(project.copy(lastBuildStatus = BuildStatus.CANCELLED, lastBuildAt = completedAt))
                        _uiState.update {
                            it.copy(isBuilding = false, buildStatus = BuildStatus.CANCELLED, statusMessage = event.message, errorSummary = null)
                        }
                    }
                }
            }

            refreshRestorePoints()
            context.stopService(Intent(context, BuildForegroundService::class.java))
        }
    }

    fun cancelBuild() {
        val buildId = _uiState.value.currentBuildId ?: return
        buildCancellationRegistry.cancelBuild(buildId)
        buildJob?.cancel()
        _uiState.update {
            it.copy(isBuilding = false, buildStatus = BuildStatus.CANCELLED, statusMessage = "Build cancelled")
        }
        context.stopService(Intent(context, BuildForegroundService::class.java))
    }

    fun cleanBuild() {
        viewModelScope.launch {
            val project = currentProjectId?.let { projectRepository.getProjectById(it) } ?: return@launch
            File(project.projectPath, "build").deleteRecursively()
            File(project.projectPath, "app/build").deleteRecursively()
            File(project.projectPath, ".build").deleteRecursively()
            addLogEntry(LogLevel.INFO, "Build outputs cleaned")
            refreshRestorePoints()
        }
    }

    fun restoreSnapshot(snapshotPath: String) {
        viewModelScope.launch {
            val project = currentProjectId?.let { projectRepository.getProjectById(it) } ?: return@launch
            snapshotManager.restoreSnapshot(project.id, snapshotPath, File(project.projectPath))
            addLogEntry(LogLevel.INFO, "Restored project from snapshot")
            refreshRestorePoints()
        }
    }

    fun restoreChangeSet(changeSetPath: String) {
        viewModelScope.launch {
            val project = currentProjectId?.let { projectRepository.getProjectById(it) } ?: return@launch
            val reverted = changeSetManager.rollbackChangeSet(File(project.projectPath), changeSetPath)
            addLogEntry(LogLevel.INFO, "Rolled back ${reverted.size} file change(s) from change set")
            refreshRestorePoints()
        }
    }

    private fun refreshRestorePoints() {
        val projectId = currentProjectId ?: return
        _uiState.update {
            it.copy(
                snapshots = snapshotManager.listSnapshots(projectId),
                changeSets = changeSetManager.listChangeSets(projectId)
            )
        }
    }

    private fun addLogEntry(level: LogLevel, message: String) {
        _uiState.update {
            it.copy(logEntries = it.logEntries + BuildLogEntry(System.currentTimeMillis(), level, message))
        }
    }
}
