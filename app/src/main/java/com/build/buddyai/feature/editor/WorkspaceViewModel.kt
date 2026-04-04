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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class WorkspaceUiState(
    val fileTree: FileNode? = null,
    val isLoading: Boolean = true,
    val expandedPaths: Set<String> = setOf(""),
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

private data class WorkspaceFileHistory(
    val undo: List<String> = emptyList(),
    val redo: List<String> = emptyList()
)

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
    private val historyByPath = linkedMapOf<String, WorkspaceFileHistory>()

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

    private fun loadFiles(projectId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val tree = withContext(Dispatchers.IO) {
                val project = projectRepository.getProjectById(projectId) ?: return@withContext null
                val projectDir = File(project.projectPath)
                if (projectDir.exists()) FileUtils.buildFileTree(projectDir) else null
            }
            _uiState.update { it.copy(fileTree = tree, isLoading = false) }
        }
    }

    fun refreshFiles() {
        currentProjectId?.let(::loadFiles)
    }

    fun toggleExpand(path: String) {
        _uiState.update {
            val updated = it.expandedPaths.toMutableSet()
            if (!updated.add(path)) updated.remove(path)
            it.copy(expandedPaths = updated)
        }
    }

    fun createFile(name: String) {
        val normalized = try { FileUtils.normalizeRelativePath(name) } catch (_: IllegalArgumentException) { return }
        viewModelScope.launch(Dispatchers.IO) {
            val project = currentProjectId?.let { projectRepository.getProjectById(it) } ?: return@launch
            FileUtils.createFile(File(project.projectPath), normalized)
            refreshFiles()
        }
    }

    fun createFolder(name: String) {
        val normalized = try { FileUtils.normalizeRelativePath(name) } catch (_: IllegalArgumentException) { return }
        viewModelScope.launch(Dispatchers.IO) {
            val project = currentProjectId?.let { projectRepository.getProjectById(it) } ?: return@launch
            FileUtils.createDirectory(File(project.projectPath), normalized)
            refreshFiles()
        }
    }

    fun deleteFile(path: String) {
        val normalized = try { FileUtils.normalizeRelativePath(path) } catch (_: IllegalArgumentException) { return }
        viewModelScope.launch(Dispatchers.IO) {
            val project = currentProjectId?.let { projectRepository.getProjectById(it) } ?: return@launch
            FileUtils.deleteFileOrDir(File(project.projectPath), normalized)
            historyByPath.remove(normalized)
            refreshFiles()
        }
    }

    fun openFile(relativePath: String) {
        val normalized = try { FileUtils.normalizeRelativePath(relativePath) } catch (_: IllegalArgumentException) { return }
        val existing = _uiState.value.openFiles.indexOfFirst { it.path == normalized }
        if (existing >= 0) {
            _uiState.update { it.copy(activeFileIndex = existing) }
            syncHistoryUi(normalized)
            return
        }

        viewModelScope.launch {
            val projectId = currentProjectId ?: return@launch
            val project = projectRepository.getProjectById(projectId) ?: return@launch
            val content = FileUtils.readFileContent(File(project.projectPath), normalized) ?: return@launch
            val name = normalized.substringAfterLast('/')
            val fileType = FileType.fromExtension(name.substringAfterLast('.', ""))
            val openFile = OpenFile(path = normalized, name = name, content = content, fileType = fileType)
            historyByPath.putIfAbsent(normalized, WorkspaceFileHistory())
            _uiState.update {
                val files = it.openFiles + openFile
                it.copy(openFiles = files, activeFileIndex = files.size - 1)
            }
            syncHistoryUi(normalized)
        }
    }

    fun closeFile(index: Int) {
        val state = _uiState.value
        if (index !in state.openFiles.indices) return
        if (state.openFiles[index].isModified) saveFile(index)
        val removedPath = state.openFiles[index].path
        _uiState.update {
            val files = it.openFiles.toMutableList().apply { removeAt(index) }
            val newIndex = when {
                files.isEmpty() -> -1
                index >= files.size -> files.size - 1
                else -> index
            }
            it.copy(openFiles = files, activeFileIndex = newIndex)
        }
        historyByPath.remove(removedPath)
        _uiState.value.activeFile?.path?.let(::syncHistoryUi)
    }

    fun setActiveFile(index: Int) {
        _uiState.update { it.copy(activeFileIndex = index) }
        _uiState.value.activeFile?.path?.let(::syncHistoryUi)
    }

    fun updateFileContent(content: String) {
        val state = _uiState.value
        val index = state.activeFileIndex
        if (index !in state.openFiles.indices) return
        val current = state.openFiles[index]
        val history = historyByPath[current.path] ?: WorkspaceFileHistory()
        historyByPath[current.path] = history.copy(undo = history.undo + current.content, redo = emptyList())

        _uiState.update {
            val files = it.openFiles.toMutableList()
            files[index] = files[index].copy(content = content, isModified = true)
            it.copy(openFiles = files)
        }
        syncHistoryUi(current.path)

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
        if (index !in state.openFiles.indices) return
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
        val active = _uiState.value.activeFile ?: return
        val history = historyByPath[active.path] ?: return
        if (history.undo.isEmpty()) return
        val previous = history.undo.last()
        historyByPath[active.path] = history.copy(undo = history.undo.dropLast(1), redo = history.redo + active.content)
        replaceActiveFileContent(previous)
        syncHistoryUi(active.path)
    }

    fun redo() {
        val active = _uiState.value.activeFile ?: return
        val history = historyByPath[active.path] ?: return
        if (history.redo.isEmpty()) return
        val next = history.redo.last()
        historyByPath[active.path] = history.copy(undo = history.undo + active.content, redo = history.redo.dropLast(1))
        replaceActiveFileContent(next)
        syncHistoryUi(active.path)
    }

    private fun replaceActiveFileContent(content: String) {
        val index = _uiState.value.activeFileIndex
        if (index !in _uiState.value.openFiles.indices) return
        _uiState.update {
            val files = it.openFiles.toMutableList()
            files[index] = files[index].copy(content = content, isModified = true)
            it.copy(openFiles = files)
        }
    }

    private fun syncHistoryUi(path: String) {
        val history = historyByPath[path] ?: WorkspaceFileHistory()
        _uiState.update { it.copy(undoStack = history.undo, redoStack = history.redo) }
    }
}
