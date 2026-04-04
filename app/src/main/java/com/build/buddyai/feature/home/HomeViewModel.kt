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

enum class SortMode { NAME, RECENT, CREATED }

data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val recentProjects: List<Project> = emptyList(),
    val recentBuilds: List<BuildRecord> = emptyList(),
    val searchQuery: String = "",
    val sortMode: SortMode = SortMode.RECENT,
    val isLoading: Boolean = true,
    val projectCount: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val buildRepository: BuildRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _sortMode = MutableStateFlow(SortMode.RECENT)

    init {
        viewModelScope.launch {
            combine(
                projectRepository.getAllProjects(),
                projectRepository.getRecentProjects(5),
                buildRepository.getRecentBuildRecords(5),
                projectRepository.getProjectCount(),
                _searchQuery,
                _sortMode
            ) { values: Array<Any> ->
                val allProjects = values[0] as List<Project>
                val recent = values[1] as List<Project>
                val builds = values[2] as List<BuildRecord>
                val count = values[3] as Int
                val query = values[4] as String
                val sort = values[5] as SortMode

                val filtered = if (query.isBlank()) allProjects
                else allProjects.filter {
                    it.name.contains(query, ignoreCase = true) ||
                            it.packageName.contains(query, ignoreCase = true)
                }
                val sorted = when (sort) {
                    SortMode.NAME -> filtered.sortedBy { it.name.lowercase() }
                    SortMode.RECENT -> filtered.sortedByDescending { it.updatedAt }
                    SortMode.CREATED -> filtered.sortedByDescending { it.createdAt }
                }
                HomeUiState(
                    projects = sorted,
                    recentProjects = recent,
                    recentBuilds = builds,
                    searchQuery = query,
                    sortMode = sort,
                    isLoading = false,
                    projectCount = count
                )
            }.collect { _uiState.value = it }
        }
    }

    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun updateSortMode(mode: SortMode) { _sortMode.value = mode }

    fun deleteProject(projectId: String) {
        viewModelScope.launch { projectRepository.deleteProject(projectId) }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch { projectRepository.duplicateProject(projectId) }
    }
}
