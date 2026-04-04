package com.build.buddyai.feature.build

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.common.BuildForegroundService
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.data.repository.ArtifactRepository
import com.build.buddyai.core.data.repository.BuildRepository
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.*
import com.build.buddyai.domain.usecase.BuildProjectUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val compatibilityWarnings: List<String> = emptyList()
)

@HiltViewModel
class BuildViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val buildRepository: BuildRepository,
    private val artifactRepository: ArtifactRepository,
    private val buildProjectUseCase: BuildProjectUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    private var currentProjectId: String? = null
    private var buildJob: Job? = null

    fun initialize(projectId: String) {
        currentProjectId = projectId
        viewModelScope.launch {
            buildRepository.getBuildRecordsByProject(projectId).collect { records ->
                _uiState.update {
                    it.copy(
                        buildHistory = records,
                        lastSuccessfulBuild = records.firstOrNull { r -> r.status == BuildStatus.SUCCESS }
                    )
                }
            }
        }
    }

    fun startBuild() {
        val projectId = currentProjectId ?: return
        if (_uiState.value.isBuilding) return

        buildJob = viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId) ?: return@launch
            val buildId = UUID.randomUUID().toString()

            _uiState.update {
                it.copy(
                    isBuilding = true,
                    buildProgress = 0f,
                    buildStatus = BuildStatus.BUILDING,
                    statusMessage = "Validating project…",
                    logEntries = emptyList(),
                    errorSummary = null,
                    currentBuildId = buildId,
                    compatibilityWarnings = emptyList()
                )
            }

            // Start foreground service
            val serviceIntent = Intent(context, BuildForegroundService::class.java).apply {
                putExtra("project_name", project.name)
            }
            context.startForegroundService(serviceIntent)

            val buildRecord = BuildRecord(
                id = buildId,
                projectId = projectId,
                status = BuildStatus.BUILDING,
                startedAt = System.currentTimeMillis()
            )
            buildRepository.insertBuildRecord(buildRecord)

            // Update project status
            projectRepository.updateProject(project.copy(lastBuildStatus = BuildStatus.BUILDING))

            buildProjectUseCase(project, buildId) { event ->
                when (event) {
                    is BuildProjectUseCase.BuildEvent.Progress -> {
                        _uiState.update {
                            it.copy(
                                buildProgress = event.progress,
                                statusMessage = event.message
                            )
                        }
                    }
                    is BuildProjectUseCase.BuildEvent.Log -> {
                        _uiState.update {
                            it.copy(logEntries = it.logEntries + event.entry)
                        }
                    }
                    is BuildProjectUseCase.BuildEvent.Warning -> {
                        _uiState.update {
                            it.copy(compatibilityWarnings = it.compatibilityWarnings + event.message)
                        }
                    }
                    is BuildProjectUseCase.BuildEvent.Success -> {
                        val completedRecord = buildRecord.copy(
                            status = BuildStatus.SUCCESS,
                            completedAt = System.currentTimeMillis(),
                            durationMs = System.currentTimeMillis() - buildRecord.startedAt,
                            artifactPath = event.artifactPath,
                            artifactSizeBytes = event.artifactSize,
                            logEntries = _uiState.value.logEntries
                        )
                        buildRepository.updateBuildRecord(completedRecord)
                        projectRepository.updateProject(
                            project.copy(lastBuildStatus = BuildStatus.SUCCESS, lastBuildAt = System.currentTimeMillis())
                        )

                        // Create artifact record
                        val artifact = BuildArtifact(
                            id = UUID.randomUUID().toString(),
                            projectId = projectId,
                            projectName = project.name,
                            buildRecordId = buildId,
                            filePath = event.artifactPath,
                            fileName = event.artifactPath.substringAfterLast("/"),
                            sizeBytes = event.artifactSize,
                            packageName = project.packageName,
                            versionName = "1.0.0",
                            versionCode = 1,
                            createdAt = System.currentTimeMillis(),
                            minSdk = project.minSdk,
                            targetSdk = project.targetSdk
                        )
                        artifactRepository.insertArtifact(artifact)

                        _uiState.update {
                            it.copy(
                                isBuilding = false,
                                buildProgress = 1f,
                                buildStatus = BuildStatus.SUCCESS,
                                statusMessage = "Build successful"
                            )
                        }
                    }
                    is BuildProjectUseCase.BuildEvent.Failure -> {
                        val failedRecord = buildRecord.copy(
                            status = BuildStatus.FAILED,
                            completedAt = System.currentTimeMillis(),
                            durationMs = System.currentTimeMillis() - buildRecord.startedAt,
                            errorSummary = event.error,
                            logEntries = _uiState.value.logEntries
                        )
                        buildRepository.updateBuildRecord(failedRecord)
                        projectRepository.updateProject(
                            project.copy(lastBuildStatus = BuildStatus.FAILED, lastBuildAt = System.currentTimeMillis())
                        )
                        _uiState.update {
                            it.copy(
                                isBuilding = false,
                                buildStatus = BuildStatus.FAILED,
                                statusMessage = "Build failed",
                                errorSummary = event.error
                            )
                        }
                    }
                }
            }

            // Stop foreground service
            context.stopService(Intent(context, BuildForegroundService::class.java))
        }
    }

    fun cancelBuild() {
        buildJob?.cancel()
        _uiState.update {
            it.copy(
                isBuilding = false,
                buildStatus = BuildStatus.CANCELLED,
                statusMessage = "Build cancelled"
            )
        }
        context.stopService(Intent(context, BuildForegroundService::class.java))
    }

    fun cleanBuild() {
        viewModelScope.launch {
            val project = currentProjectId?.let { projectRepository.getProjectById(it) } ?: return@launch
            val buildDir = java.io.File(project.projectPath, "build")
            buildDir.deleteRecursively()
            addLogEntry(LogLevel.INFO, "Build directory cleaned")
        }
    }

    private fun addLogEntry(level: LogLevel, message: String) {
        _uiState.update {
            it.copy(logEntries = it.logEntries + BuildLogEntry(System.currentTimeMillis(), level, message))
        }
    }
}
