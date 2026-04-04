package com.build.buddyai.feature.editor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.datastore.SettingsDataStore
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.*
import com.build.buddyai.core.ui.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class OpenTab(
    val filePath: String,
    val fileName: String,
    val content: String,
    val originalContent: String,
    val isModified: Boolean = false,
    val fileType: FileType = FileType.UNKNOWN,
    val cursorLine: Int = 0,
    val cursorColumn: Int = 0
)

data class EditorState(
    val tabs: List<OpenTab> = emptyList(),
    val activeTabIndex: Int = -1,
    val searchQuery: String = "",
    val replaceText: String = "",
    val showSearch: Boolean = false,
    val searchResults: List<IntRange> = emptyList(),
    val currentSearchIndex: Int = -1,
    val settings: EditorSettings = EditorSettings(),
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList()
) {
    val activeTab: OpenTab? get() = tabs.getOrNull(activeTabIndex)
    val hasUnsavedChanges: Boolean get() = tabs.any { it.isModified }
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var autoSaveJob: Job? = null

    init {
        viewModelScope.launch {
            settingsDataStore.appSettings.collect { settings ->
                _state.update { it.copy(settings = settings.editorSettings) }
            }
        }
    }

    fun openFile(projectPath: String, relativePath: String) {
        val existingIndex = _state.value.tabs.indexOfFirst { it.filePath == relativePath }
        if (existingIndex >= 0) {
            _state.update { it.copy(activeTabIndex = existingIndex) }
            return
        }

        val file = File(projectPath, relativePath)
        if (!file.exists() || !file.isFile) return

        val content = FileUtils.readFileContent(file) ?: return
        val fileName = file.name
        val ext = fileName.substringAfterLast('.', "")
        val tab = OpenTab(
            filePath = relativePath,
            fileName = fileName,
            content = content,
            originalContent = content,
            fileType = FileType.fromExtension(ext)
        )

        _state.update {
            it.copy(
                tabs = it.tabs + tab,
                activeTabIndex = it.tabs.size
            )
        }
    }

    fun closeTab(index: Int) {
        _state.update {
            val newTabs = it.tabs.toMutableList().apply { removeAt(index) }
            val newActive = when {
                newTabs.isEmpty() -> -1
                index >= newTabs.size -> newTabs.size - 1
                else -> index
            }
            it.copy(tabs = newTabs, activeTabIndex = newActive)
        }
    }

    fun setActiveTab(index: Int) {
        _state.update { it.copy(activeTabIndex = index) }
    }

    fun updateContent(content: String) {
        val active = _state.value.activeTabIndex
        if (active < 0) return
        _state.update {
            val tab = it.tabs[active]
            val newUndoStack = it.undoStack + tab.content
            val updatedTab = tab.copy(
                content = content,
                isModified = content != tab.originalContent
            )
            it.copy(
                tabs = it.tabs.toMutableList().apply { set(active, updatedTab) },
                undoStack = newUndoStack.takeLast(50),
                redoStack = emptyList()
            )
        }
        scheduleAutoSave()
    }

    fun save(projectPath: String) {
        val active = _state.value.activeTabIndex
        if (active < 0) return
        val tab = _state.value.tabs[active]
        if (!tab.isModified) return

        val file = File(projectPath, tab.filePath)
        if (FileUtils.writeFileContent(file, tab.content)) {
            _state.update {
                val updatedTab = tab.copy(originalContent = tab.content, isModified = false)
                it.copy(tabs = it.tabs.toMutableList().apply { set(active, updatedTab) })
            }
        }
    }

    fun saveAll(projectPath: String) {
        _state.value.tabs.forEachIndexed { index, tab ->
            if (tab.isModified) {
                val file = File(projectPath, tab.filePath)
                if (FileUtils.writeFileContent(file, tab.content)) {
                    _state.update {
                        val updatedTab = tab.copy(originalContent = tab.content, isModified = false)
                        it.copy(tabs = it.tabs.toMutableList().apply { set(index, updatedTab) })
                    }
                }
            }
        }
    }

    fun undo() {
        val stack = _state.value.undoStack
        if (stack.isEmpty()) return
        val previous = stack.last()
        val active = _state.value.activeTabIndex
        if (active < 0) return
        val tab = _state.value.tabs[active]
        _state.update {
            val updatedTab = tab.copy(content = previous, isModified = previous != tab.originalContent)
            it.copy(
                tabs = it.tabs.toMutableList().apply { set(active, updatedTab) },
                undoStack = stack.dropLast(1),
                redoStack = it.redoStack + tab.content
            )
        }
    }

    fun redo() {
        val stack = _state.value.redoStack
        if (stack.isEmpty()) return
        val next = stack.last()
        val active = _state.value.activeTabIndex
        if (active < 0) return
        val tab = _state.value.tabs[active]
        _state.update {
            val updatedTab = tab.copy(content = next, isModified = next != tab.originalContent)
            it.copy(
                tabs = it.tabs.toMutableList().apply { set(active, updatedTab) },
                undoStack = it.undoStack + tab.content,
                redoStack = stack.dropLast(1)
            )
        }
    }

    fun toggleSearch() {
        _state.update { it.copy(showSearch = !it.showSearch) }
    }

    fun setSearchQuery(query: String) {
        _state.update {
            val results = if (query.isNotEmpty()) {
                val tab = it.activeTab ?: return@update it
                val pattern = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
                pattern.findAll(tab.content).map { m -> m.range }.toList()
            } else emptyList()
            it.copy(searchQuery = query, searchResults = results, currentSearchIndex = if (results.isNotEmpty()) 0 else -1)
        }
    }

    fun setReplaceText(text: String) { _state.update { it.copy(replaceText = text) } }

    fun replaceNext(projectPath: String) {
        val s = _state.value
        val tab = s.activeTab ?: return
        val idx = s.currentSearchIndex
        if (idx < 0 || idx >= s.searchResults.size) return
        val range = s.searchResults[idx]
        val newContent = tab.content.replaceRange(range, s.replaceText)
        updateContent(newContent)
        setSearchQuery(s.searchQuery)
    }

    fun replaceAll(projectPath: String) {
        val s = _state.value
        val tab = s.activeTab ?: return
        if (s.searchQuery.isEmpty()) return
        val newContent = tab.content.replace(s.searchQuery, s.replaceText, ignoreCase = true)
        updateContent(newContent)
        setSearchQuery(s.searchQuery)
    }

    private fun scheduleAutoSave() {
        if (!_state.value.settings.autoSave) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(_state.value.settings.autoSaveIntervalSeconds * 1000L)
            // auto-save would be triggered here if project path available
        }
    }
}
