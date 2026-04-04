package com.build.buddyai.feature.files

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.FileNode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class FilesUiState(
    val fileTree: FileNode? = null,
    val isLoading: Boolean = true,
    val expandedPaths: Set<String> = setOf("")
)

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    private var currentProjectId: String? = null

    fun loadFiles(projectId: String) {
        currentProjectId = projectId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            withContext(Dispatchers.IO) {
                val project = projectRepository.getProjectById(projectId)
                if (project != null) {
                    val projectDir = File(project.projectPath)
                    if (projectDir.exists()) {
                        val tree = FileUtils.buildFileTree(projectDir)
                        _uiState.update { it.copy(fileTree = tree, isLoading = false) }
                    } else {
                        _uiState.update { it.copy(fileTree = null, isLoading = false) }
                    }
                }
            }
        }
    }

    fun refreshFiles() {
        currentProjectId?.let { loadFiles(it) }
    }

    fun toggleExpand(path: String) {
        _uiState.update {
            val paths = it.expandedPaths.toMutableSet()
            if (paths.contains(path)) paths.remove(path) else paths.add(path)
            it.copy(expandedPaths = paths)
        }
    }

    fun createFile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val project = currentProjectId?.let { projectRepository.getProjectById(it) } ?: return@launch
            FileUtils.createFile(File(project.projectPath), "app/src/main/java/$name")
            refreshFiles()
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val project = currentProjectId?.let { projectRepository.getProjectById(it) } ?: return@launch
            FileUtils.createDirectory(File(project.projectPath), name)
            refreshFiles()
        }
    }

    fun deleteFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val project = currentProjectId?.let { projectRepository.getProjectById(it) } ?: return@launch
            FileUtils.deleteFileOrDir(File(project.projectPath), path)
            refreshFiles()
        }
    }
}
