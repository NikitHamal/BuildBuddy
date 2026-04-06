package com.build.buddyai.feature.project.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
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

    private var observeJob: Job? = null

    fun loadProject(projectId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            projectRepository.observeProject(projectId).collect { project ->
                _uiState.update { it.copy(project = project) }
            }
        }
    }
}
