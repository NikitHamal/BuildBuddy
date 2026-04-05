package com.build.buddyai.feature.dependencies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.dependency.ProjectDependencyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class DependenciesViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val dependencyManager: ProjectDependencyManager
) : ViewModel() {

    data class UiState(
        val projectId: String? = null,
        val projectName: String = "Dependencies",
        val packageName: String = "",
        val isLoading: Boolean = true,
        val dependencyNotation: String = "",
        val configuration: String = "implementation",
        val repositoryUrl: String = "",
        val dependencies: List<ProjectDependencyManager.DependencyEntry> = emptyList(),
        val repositories: List<ProjectDependencyManager.RepositoryEntry> = emptyList(),
        val warnings: List<ProjectDependencyManager.DependencyWarning> = emptyList(),
        val message: String? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun initialize(projectId: String) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            _uiState.update {
                it.copy(
                    projectId = projectId,
                    projectName = project?.name ?: "Dependencies",
                    packageName = project?.packageName.orEmpty(),
                    isLoading = false
                )
            }
            refresh()
        }
    }

    fun updateDependencyNotation(value: String) = _uiState.update { it.copy(dependencyNotation = value, error = null, message = null) }
    fun updateConfiguration(value: String) = _uiState.update { it.copy(configuration = value, error = null, message = null) }
    fun updateRepositoryUrl(value: String) = _uiState.update { it.copy(repositoryUrl = value, error = null, message = null) }

    fun refresh() {
        viewModelScope.launch {
            val projectDir = currentProjectDir() ?: return@launch
            val snapshot = dependencyManager.scan(projectDir)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    dependencies = snapshot.dependencies,
                    repositories = snapshot.repositories,
                    warnings = snapshot.warnings,
                    error = null
                )
            }
        }
    }

    fun addDependency() {
        viewModelScope.launch {
            val projectDir = currentProjectDir() ?: return@launch
            val state = _uiState.value
            runCatching {
                dependencyManager.addDependency(projectDir, state.dependencyNotation, state.configuration, state.repositoryUrl)
            }.onSuccess { snapshot ->
                _uiState.update {
                    it.copy(
                        dependencies = snapshot.dependencies,
                        repositories = snapshot.repositories,
                        warnings = snapshot.warnings,
                        dependencyNotation = "",
                        repositoryUrl = "",
                        message = "Dependency added.",
                        error = null
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "Unable to add dependency", message = null) }
            }
        }
    }

    fun toggleDependency(entry: ProjectDependencyManager.DependencyEntry, enabled: Boolean) {
        viewModelScope.launch {
            val projectDir = currentProjectDir() ?: return@launch
            runCatching { dependencyManager.toggleDependency(projectDir, entry.id, enabled) }
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            dependencies = snapshot.dependencies,
                            repositories = snapshot.repositories,
                            warnings = snapshot.warnings,
                            error = null,
                            message = if (enabled) "Dependency enabled." else "Dependency disabled."
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "Unable to update dependency") } }
        }
    }

    fun deleteDependency(entry: ProjectDependencyManager.DependencyEntry) {
        viewModelScope.launch {
            val projectDir = currentProjectDir() ?: return@launch
            runCatching { dependencyManager.deleteDependency(projectDir, entry.id) }
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            dependencies = snapshot.dependencies,
                            repositories = snapshot.repositories,
                            warnings = snapshot.warnings,
                            error = null,
                            message = "Dependency removed."
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "Unable to delete dependency") } }
        }
    }

    fun addRepository() {
        viewModelScope.launch {
            val projectDir = currentProjectDir() ?: return@launch
            val url = _uiState.value.repositoryUrl
            runCatching { dependencyManager.addRepository(projectDir, url) }
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            repositories = snapshot.repositories,
                            warnings = snapshot.warnings,
                            error = null,
                            repositoryUrl = "",
                            message = "Repository added."
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "Unable to add repository", message = null) } }
        }
    }

    fun toggleRepository(entry: ProjectDependencyManager.RepositoryEntry, enabled: Boolean) {
        viewModelScope.launch {
            val projectDir = currentProjectDir() ?: return@launch
            runCatching { dependencyManager.toggleRepository(projectDir, entry.id, enabled) }
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            repositories = snapshot.repositories,
                            warnings = snapshot.warnings,
                            error = null,
                            message = if (enabled) "Repository enabled." else "Repository disabled."
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "Unable to update repository") } }
        }
    }

    fun deleteRepository(entry: ProjectDependencyManager.RepositoryEntry) {
        viewModelScope.launch {
            val projectDir = currentProjectDir() ?: return@launch
            runCatching { dependencyManager.deleteRepository(projectDir, entry.id) }
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            repositories = snapshot.repositories,
                            warnings = snapshot.warnings,
                            error = null,
                            message = "Repository removed."
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "Unable to delete repository") } }
        }
    }

    private suspend fun currentProjectDir(): File? {
        val projectId = _uiState.value.projectId ?: return null
        return projectRepository.getProjectById(projectId)?.projectPath?.let(::File)
    }
}
