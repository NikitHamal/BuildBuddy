package com.build.buddyai.feature.editor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.data.datastore.SettingsDataStore
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.FileType
import com.build.buddyai.core.model.OpenFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class EditorUiState(
    val openFiles: List<OpenFile> = emptyList(),
    val activeFileIndex: Int = -1,
    val fontSize: Int = 14,
    val tabWidth: Int = 4,
    val softWrap: Boolean = true,
    val showLineNumbers: Boolean = true,
    val autosave: Boolean = true,
    val searchQuery: String = "",
    val replaceQuery: String = "",
    val showSearch: Boolean = false,
    val searchResults: List<Int> = emptyList(),
    val currentSearchIndex: Int = -1,
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList()
) {
    val activeFile: OpenFile? get() = openFiles.getOrNull(activeFileIndex)
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

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
    }

    fun openFile(projectId: String, relativePath: String) {
        currentProjectId = projectId
        val existing = _uiState.value.openFiles.indexOfFirst { it.path == relativePath }
        if (existing >= 0) {
            _uiState.update { it.copy(activeFileIndex = existing) }
            return
        }

        viewModelScope.launch {
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
            val project = currentProjectId?.let { projectRepository.getProjectById(it) } ?: return@launch
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

    fun toggleSearch() = _uiState.update { it.copy(showSearch = !it.showSearch) }
    fun updateSearchQuery(query: String) {
        _uiState.update {
            val results = if (query.isNotEmpty()) {
                val content = it.activeFile?.content ?: ""
                val indices = mutableListOf<Int>()
                var index = content.indexOf(query, ignoreCase = true)
                while (index >= 0) {
                    indices.add(index)
                    index = content.indexOf(query, index + 1, ignoreCase = true)
                }
                indices
            } else emptyList()
            it.copy(searchQuery = query, searchResults = results, currentSearchIndex = if (results.isNotEmpty()) 0 else -1)
        }
    }

    fun updateReplaceQuery(query: String) = _uiState.update { it.copy(replaceQuery = query) }

    fun replaceAll() {
        val state = _uiState.value
        if (state.searchQuery.isEmpty() || state.activeFile == null) return
        val newContent = state.activeFile!!.content.replace(state.searchQuery, state.replaceQuery, ignoreCase = true)
        updateFileContent(newContent)
    }
}
