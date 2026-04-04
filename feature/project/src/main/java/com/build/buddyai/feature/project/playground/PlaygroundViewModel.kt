package com.build.buddyai.feature.project.playground

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class PlaygroundTab(val title: String) {
    OVERVIEW("Overview"),
    AGENT("Agent"),
    EDITOR("Editor"),
    FILES("Files"),
    BUILD("Build"),
    ARTIFACTS("Artifacts")
}

data class PlaygroundState(
    val project: Project? = null,
    val currentTab: PlaygroundTab = PlaygroundTab.OVERVIEW,
    val isLoading: Boolean = true
)

@HiltViewModel
class PlaygroundViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository
) : ViewModel() {
    private val projectId: String = savedStateHandle.get<String>("projectId") ?: ""
    private val _currentTab = MutableStateFlow(PlaygroundTab.OVERVIEW)

    val state: StateFlow<PlaygroundState> = combine(
        projectRepository.observeById(projectId),
        _currentTab
    ) { project, tab ->
        PlaygroundState(
            project = project,
            currentTab = tab,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaygroundState())

    fun setTab(tab: PlaygroundTab) { _currentTab.value = tab }
}