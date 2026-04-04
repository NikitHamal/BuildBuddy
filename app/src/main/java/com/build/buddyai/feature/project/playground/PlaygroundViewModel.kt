package com.build.buddyai.feature.project.playground

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaygroundUiState(
    val project: Project? = null,
    val isLoading: Boolean = true,
    val currentOpenFilePath: String? = null
)

@HiltViewModel
class PlaygroundViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaygroundUiState())
    val uiState: StateFlow<PlaygroundUiState> = _uiState.asStateFlow()

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.observeProject(projectId).collect { project ->
                _uiState.update { it.copy(project = project, isLoading = false) }
            }
        }
    }

    fun openFile(path: String) {
        _uiState.update { it.copy(currentOpenFilePath = path) }
    }
}
