package com.build.buddyai.feature.project.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OverviewUiState(
    val project: Project? = null
)

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.observeProject(projectId).collect { project ->
                _uiState.update { it.copy(project = project) }
            }
        }
    }
}
