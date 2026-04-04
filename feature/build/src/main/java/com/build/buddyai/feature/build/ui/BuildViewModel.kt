package com.build.buddyai.feature.build.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.BuildRepository
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.*
import com.build.buddyai.feature.build.engine.BuildConfig
import com.build.buddyai.feature.build.engine.BuildEngine
import com.build.buddyai.feature.build.engine.BuildEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BuildScreenState(
    val project: Project? = null,
    val currentStatus: BuildStatus = BuildStatus.IDLE,
    val progress: Float = 0f,
    val progressPhase: String = "",
    val logEntries: List<BuildLogEntry> = emptyList(),
    val diagnostics: List<BuildDiagnostic> = emptyList(),
    val buildHistory: List<BuildRecord> = emptyList(),
    val artifacts: List<BuildArtifact> = emptyList(),
    val lastRecord: BuildRecord? = null,
    val isBuilding: Boolean = false
)

@HiltViewModel
class BuildViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val buildEngine: BuildEngine,
    private val buildRepository: BuildRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {
    private val projectId: String = savedStateHandle.get<String>("projectId") ?: ""
    private val _state = MutableStateFlow(BuildScreenState())
    val state: StateFlow<BuildScreenState> = _state.asStateFlow()
    private var buildJob: Job? = null

    init {
        viewModelScope.launch {
            projectRepository.observeById(projectId).collect { project ->
                _state.update { it.copy(project = project) }
            }
        }
        viewModelScope.launch {
            buildRepository.observeRecordsByProject(projectId).collect { records ->
                _state.update { it.copy(buildHistory = records) }
            }
        }
        viewModelScope.launch {
            buildRepository.observeArtifactsByProject(projectId).collect { artifacts ->
                _state.update { it.copy(artifacts = artifacts) }
            }
        }
        viewModelScope.launch {
            buildEngine.events.collect { event ->
                when (event) {
                    is BuildEvent.StatusChanged -> _state.update { it.copy(currentStatus = event.status, isBuilding = event.status != BuildStatus.SUCCESS && event.status != BuildStatus.FAILED && event.status != BuildStatus.CANCELLED) }
                    is BuildEvent.LogEntry -> _state.update { it.copy(logEntries = it.logEntries + event.entry) }
                    is BuildEvent.DiagnosticFound -> _state.update { it.copy(diagnostics = it.diagnostics + event.diagnostic) }
                    is BuildEvent.Progress -> _state.update { it.copy(progress = event.percent, progressPhase = event.phase) }
                    is BuildEvent.Completed -> {
                        val record = event.record.copy(projectId = projectId)
                        buildRepository.saveRecord(record)
                        projectRepository.updateBuildStatus(projectId, record.completedAt ?: System.currentTimeMillis(), BuildStatus.SUCCESS.name)
                        if (record.artifactPath != null) {
                            val file = java.io.File(record.artifactPath)
                            if (file.exists()) {
                                val artifact = BuildArtifact(
                                    projectId = projectId,
                                    buildId = record.id,
                                    fileName = file.name,
                                    filePath = file.absolutePath,
                                    sizeBytes = file.length(),
                                    packageName = _state.value.project?.packageName ?: "",
                                    versionName = "1.0",
                                    versionCode = 1
                                )
                                buildRepository.saveArtifact(artifact)
                            }
                        }
                        _state.update { it.copy(lastRecord = record, isBuilding = false) }
                    }
                    is BuildEvent.Failed -> {
                        val record = event.record.copy(projectId = projectId)
                        buildRepository.saveRecord(record)
                        projectRepository.updateBuildStatus(projectId, record.completedAt ?: System.currentTimeMillis(), BuildStatus.FAILED.name)
                        _state.update { it.copy(lastRecord = record, isBuilding = false) }
                    }
                }
            }
        }
    }

    fun startBuild() {
        val project = _state.value.project ?: return
        _state.update { it.copy(logEntries = emptyList(), diagnostics = emptyList(), progress = 0f, progressPhase = "") }

        buildJob = viewModelScope.launch {
            val config = BuildConfig(
                projectPath = project.projectPath,
                projectName = project.name,
                packageName = project.packageName,
                language = project.language,
                uiFramework = project.uiFramework,
                minSdk = project.minSdk,
                targetSdk = project.targetSdk
            )
            buildEngine.build(config)
        }
    }

    fun cancelBuild() {
        buildJob?.cancel()
        buildEngine.cancelBuild()
        _state.update { it.copy(currentStatus = BuildStatus.CANCELLED, isBuilding = false) }
    }

    fun cleanBuild() {
        val project = _state.value.project ?: return
        val outputDir = java.io.File(project.projectPath, "build/output")
        if (outputDir.exists()) outputDir.deleteRecursively()
        _state.update { it.copy(logEntries = emptyList(), diagnostics = emptyList()) }
    }
}