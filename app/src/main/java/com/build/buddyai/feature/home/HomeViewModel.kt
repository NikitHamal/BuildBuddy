package com.build.buddyai.feature.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.ProjectFilter
import com.build.buddyai.core.model.SortMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
) : ViewModel() {
    private val search = MutableStateFlow("")
    private val sortMode = MutableStateFlow(SortMode.RECENT)
    private val filter = MutableStateFlow(ProjectFilter.ALL)
    private val openProject = MutableSharedFlow<String>()

    val uiState = projectRepository.dashboard(search, sortMode, filter)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.build.buddyai.core.model.DashboardState())

    val openProjectEvents = openProject.asSharedFlow()

    fun updateSearch(value: String) {
        search.value = value
    }

    fun updateSort(value: SortMode) {
        sortMode.value = value
    }

    fun updateFilter(value: ProjectFilter) {
        filter.value = value
    }

    fun openProject(projectId: String) {
        viewModelScope.launch {
            openProject.emit(projectId)
        }
    }

    fun importProject(uri: Uri) {
        viewModelScope.launch {
            projectRepository.importProject(uri)?.let { openProject.emit(it.id) }
        }
    }
}

