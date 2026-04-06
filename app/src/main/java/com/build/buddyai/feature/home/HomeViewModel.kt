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

private data class HomeBaseData(
    val allProjects: List<Project>,
    val recentProjects: List<Project>,
    val recentBuilds: List<BuildRecord>,
    val projectCount: Int
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
            val baseDataFlow = combine(
                projectRepository.getAllProjects(),
                projectRepository.getRecentProjects(5),
                buildRepository.getRecentBuildRecords(5),
                projectRepository.getProjectCount()
            ) { allProjects, recent, builds, count ->
                HomeBaseData(
                    allProjects = allProjects,
                    recentProjects = recent,
                    recentBuilds = builds,
                    projectCount = count
                )
            }

            combine(baseDataFlow, _searchQuery, _sortMode) { base, query, sort ->
                val filtered = if (query.isBlank()) base.allProjects
                else base.allProjects.filter {
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
                    recentProjects = base.recentProjects,
                    recentBuilds = base.recentBuilds,
                    searchQuery = query,
                    sortMode = sort,
                    isLoading = false,
                    projectCount = base.projectCount
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
