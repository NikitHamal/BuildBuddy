package com.build.buddyai.feature.dependencies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.dependency.ProjectDependencyManager
import com.build.buddyai.core.model.BuildProblem
import com.build.buddyai.core.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DependenciesUiState(
    val isLoading: Boolean = true,
    val buildFiles: List<String> = emptyList(),
    val dependencies: List<ProjectDependencyManager.DependencyEntry> = emptyList(),
    val repositories: List<ProjectDependencyManager.RepositoryEntry> = emptyList(),
    val problems: List<BuildProblem> = emptyList(),
    val dependencyNotation: String = "",
    val dependencyConfiguration: String = "implementation",
    val repositoryUrl: String = "",
    val message: String? = null
)

@HiltViewModel
class DependenciesViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val dependencyManager: ProjectDependencyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DependenciesUiState())
    val uiState: StateFlow<DependenciesUiState> = _uiState.asStateFlow()

    private var currentProjectId: String? = null
    private var projectDir: File? = null

    fun initialize(projectId: String) {
        if (currentProjectId == projectId && !_uiState.value.isLoading) return
        currentProjectId = projectId
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            projectDir = project?.projectPath?.let(::File)
            refresh("Dependency manager ready")
        }
    }

    fun updateNotation(value: String) = _uiState.update { it.copy(dependencyNotation = value) }
    fun updateConfiguration(value: String) = _uiState.update { it.copy(dependencyConfiguration = value) }
    fun updateRepositoryUrl(value: String) = _uiState.update { it.copy(repositoryUrl = value) }

    fun toggleDependency(entry: ProjectDependencyManager.DependencyEntry) = mutate {
        dependencyManager.toggleDependency(requireProjectDir(), entry, enabled = !entry.enabled)
    }

    fun deleteDependency(entry: ProjectDependencyManager.DependencyEntry) = mutate {
        dependencyManager.deleteDependency(requireProjectDir(), entry)
    }

    fun toggleRepository(entry: ProjectDependencyManager.RepositoryEntry) = mutate {
        dependencyManager.toggleRepository(requireProjectDir(), entry, enabled = !entry.enabled)
    }

    fun deleteRepository(entry: ProjectDependencyManager.RepositoryEntry) = mutate {
        dependencyManager.deleteRepository(requireProjectDir(), entry)
    }

    fun addDependency() = mutate {
        val state = _uiState.value
        val notation = state.dependencyNotation.trim()
        if (notation.isBlank()) error("Dependency notation cannot be blank")
        dependencyManager.addDependency(
            projectDir = requireProjectDir(),
            configuration = state.dependencyConfiguration.trim().ifBlank { "implementation" },
            notation = notation,
            repositoryUrl = state.repositoryUrl.trim().ifBlank { null }
        )
        _uiState.update { it.copy(dependencyNotation = "", repositoryUrl = "") }
    }

    fun addRepository() = mutate {
        val url = _uiState.value.repositoryUrl.trim()
        if (url.isBlank()) error("Repository URL cannot be blank")
        dependencyManager.addRepository(requireProjectDir(), url)
        _uiState.update { it.copy(repositoryUrl = "") }
    }

    fun refresh(message: String? = null) {
        viewModelScope.launch {
            val dir = projectDir
            if (dir == null || !dir.exists()) {
                _uiState.update { it.copy(isLoading = false, message = "Project directory not found") }
                return@launch
            }
            val snapshot = dependencyManager.snapshot(dir)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    buildFiles = snapshot.buildFiles,
                    dependencies = snapshot.dependencies.sortedWith(compareBy<ProjectDependencyManager.DependencyEntry>({ !it.enabled }, { it.sourceFile }, { it.lineNumber })),
                    repositories = snapshot.repositories.sortedWith(compareBy<ProjectDependencyManager.RepositoryEntry>({ !it.enabled }, { it.sourceFile }, { it.lineNumber })),
                    problems = snapshot.issues,
                    message = message
                )
            }
        }
    }

    private fun mutate(block: () -> Unit) {
        viewModelScope.launch {
            runCatching(block)
                .onSuccess { refresh("Dependency graph updated") }
                .onFailure { error -> _uiState.update { state -> state.copy(message = error.message ?: "Dependency update failed") } }
        }
    }

    private fun requireProjectDir(): File = projectDir ?: error("Project not loaded")
}
