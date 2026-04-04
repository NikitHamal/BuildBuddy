package com.build.buddyai.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.BuildRepository
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.BuildRecord
import com.build.buddyai.core.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortMode(val displayName: String) {
    RECENT("Recently Updated"),
    NAME("Name"),
    CREATED("Date Created")
}

enum class ViewMode { LIST, GRID }

data class HomeState(
    val projects: List<Project> = emptyList(),
    val recentBuilds: List<BuildRecord> = emptyList(),
    val searchQuery: String = "",
    val sortMode: SortMode = SortMode.RECENT,
    val viewMode: ViewMode = ViewMode.LIST,
    val isLoading: Boolean = true,
    val projectCount: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val buildRepository: BuildRepository
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    private val _sortMode = MutableStateFlow(SortMode.RECENT)
    private val _viewMode = MutableStateFlow(ViewMode.LIST)

    val state: StateFlow<HomeState> = combine(
        _searchQuery,
        _sortMode,
        _viewMode,
        projectRepository.observeAll(),
        buildRepository.observeRecentRecords(5)
    ) { query, sort, view, allProjects, recentBuilds ->
        val filtered = if (query.isBlank()) allProjects else {
            allProjects.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
            }
        }
        val sorted = when (sort) {
            SortMode.RECENT -> filtered.sortedByDescending { it.updatedAt }
            SortMode.NAME -> filtered.sortedBy { it.name.lowercase() }
            SortMode.CREATED -> filtered.sortedByDescending { it.createdAt }
        }
        HomeState(
            projects = sorted,
            recentBuilds = recentBuilds,
            searchQuery = query,
            sortMode = sort,
            viewMode = view,
            isLoading = false,
            projectCount = allProjects.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSortMode(mode: SortMode) { _sortMode.value = mode }
    fun toggleViewMode() { _viewMode.update { if (it == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST } }

    fun deleteProject(id: String) {
        viewModelScope.launch { projectRepository.delete(id) }
    }

    fun archiveProject(id: String) {
        viewModelScope.launch { projectRepository.archive(id) }
    }
}
