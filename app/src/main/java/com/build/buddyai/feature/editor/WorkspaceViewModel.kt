package com.build.buddyai.feature.editor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.data.datastore.SettingsDataStore
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.FileNode
import com.build.buddyai.core.model.FileType
import com.build.buddyai.core.model.OpenFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class WorkspaceUiState(
    // File tree
    val fileTree: FileNode? = null,
    val isLoading: Boolean = true,
    val expandedPaths: Set<String> = setOf(""),
    // Editor
    val openFiles: List<OpenFile> = emptyList(),
    val activeFileIndex: Int = -1,
    val fontSize: Int = 14,
    val tabWidth: Int = 4,
    val softWrap: Boolean = true,
    val showLineNumbers: Boolean = true,
    val autosave: Boolean = true,
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList()
) {
    val activeFile: OpenFile? get() = openFiles.getOrNull(activeFileIndex)
}

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var currentProjectId: String? = null
    private var autosaveJob: Job? = null

    init {
        viewModelScope.launch {
            settingsDataStore.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        fontSize = settings.editorFontSize,
                        tabWidth = settings.editorTabWidth,
                        softWrap = settings.editorSoftWrap,
                        showLineNumbers = settings.editorLineNumbers,
                        autosave = settings.editorAutosave
                    )
                }
            }
        }
    }

    fun initialize(projectId: String) {
        currentProjectId = projectId
        loadFiles(projectId)
    }

    // File Tree Functions
    private fun loadFiles(projectId: String) {
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

    // Editor Functions
    fun openFile(relativePath: String) {
        val existing = _uiState.value.openFiles.indexOfFirst { it.path == relativePath }
        if (existing >= 0) {
            _uiState.update { it.copy(activeFileIndex = existing) }
            return
        }

        viewModelScope.launch {
            val projectId = currentProjectId ?: return@launch
            val project = projectRepository.getProjectById(projectId) ?: return@launch
            val content = FileUtils.readFileContent(File(project.projectPath), relativePath) ?: return@launch
            val name = relativePath.substringAfterLast("/")
            val fileType = FileType.fromExtension(name.substringAfterLast(".", ""))
            val openFile = OpenFile(path = relativePath, name = name, content = content, fileType = fileType)
            _uiState.update {
                val files = it.openFiles + openFile
                it.copy(openFiles = files, activeFileIndex = files.size - 1, undoStack = emptyList(), redoStack = emptyList())
            }
        }
    }

    fun closeFile(index: Int) {
        val state = _uiState.value
        if (state.openFiles[index].isModified) {
            saveFile(index)
        }
        _uiState.update {
            val files = it.openFiles.toMutableList().apply { removeAt(index) }
            val newIndex = when {
                files.isEmpty() -> -1
                index >= files.size -> files.size - 1
                else -> index
            }
            it.copy(openFiles = files, activeFileIndex = newIndex)
        }
    }

    fun setActiveFile(index: Int) = _uiState.update { it.copy(activeFileIndex = index) }

    fun updateFileContent(content: String) {
        val state = _uiState.value
        val index = state.activeFileIndex
        if (index < 0 || index >= state.openFiles.size) return

        val currentContent = state.openFiles[index].content
        _uiState.update {
            val files = it.openFiles.toMutableList()
            files[index] = files[index].copy(content = content, isModified = true)
            it.copy(
                openFiles = files,
                undoStack = it.undoStack + currentContent,
                redoStack = emptyList()
            )
        }

        if (_uiState.value.autosave) {
            autosaveJob?.cancel()
            autosaveJob = viewModelScope.launch {
                delay(2000)
                saveFile(index)
            }
        }
    }

    fun saveFile(index: Int = _uiState.value.activeFileIndex) {
        val state = _uiState.value
        if (index < 0 || index >= state.openFiles.size) return
        val file = state.openFiles[index]
        if (!file.isModified) return

        viewModelScope.launch {
            val projectId = currentProjectId ?: return@launch
            val project = projectRepository.getProjectById(projectId) ?: return@launch
            FileUtils.writeFileContent(File(project.projectPath), file.path, file.content)
            _uiState.update {
                val files = it.openFiles.toMutableList()
                files[index] = files[index].copy(isModified = false)
                it.copy(openFiles = files)
            }
        }
    }

    fun undo() {
        val state = _uiState.value
        if (state.undoStack.isEmpty() || state.activeFileIndex < 0) return
        val previousContent = state.undoStack.last()
        val currentContent = state.openFiles[state.activeFileIndex].content
        _uiState.update {
            val files = it.openFiles.toMutableList()
            files[state.activeFileIndex] = files[state.activeFileIndex].copy(content = previousContent, isModified = true)
            it.copy(
                openFiles = files,
                undoStack = it.undoStack.dropLast(1),
                redoStack = it.redoStack + currentContent
            )
        }
    }

    fun redo() {
        val state = _uiState.value
        if (state.redoStack.isEmpty() || state.activeFileIndex < 0) return
        val nextContent = state.redoStack.last()
        val currentContent = state.openFiles[state.activeFileIndex].content
        _uiState.update {
            val files = it.openFiles.toMutableList()
            files[state.activeFileIndex] = files[state.activeFileIndex].copy(content = nextContent, isModified = true)
            it.copy(
                openFiles = files,
                undoStack = it.undoStack + currentContent,
                redoStack = it.redoStack.dropLast(1)
            )
        }
    }
}
