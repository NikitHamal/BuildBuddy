package com.build.buddyai.feature.playground

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.build.BuildEngine
import com.build.buddyai.core.data.repository.BuildRepository
import com.build.buddyai.core.data.repository.ConversationRepository
import com.build.buddyai.core.data.repository.PreferencesRepository
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.install.ApkInstaller
import com.build.buddyai.core.model.AgentMode
import com.build.buddyai.core.model.AppPreferences
import com.build.buddyai.core.model.Artifact
import com.build.buddyai.core.model.BuildCompatibilityReport
import com.build.buddyai.core.model.BuildMode
import com.build.buddyai.core.model.BuildRecord
import com.build.buddyai.core.model.ChatMessage
import com.build.buddyai.core.model.EditorSession
import com.build.buddyai.core.model.Project
import com.build.buddyai.core.model.Snapshot
import com.build.buddyai.core.model.WorkspaceFile
import com.build.buddyai.feature.agent.AgentOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlaygroundTab {
    OVERVIEW,
    AGENT,
    EDITOR,
    FILES,
    BUILD,
    ARTIFACTS,
}

data class PlaygroundUiState(
    val project: Project? = null,
    val files: List<WorkspaceFile> = emptyList(),
    val builds: List<BuildRecord> = emptyList(),
    val artifacts: List<Artifact> = emptyList(),
    val snapshots: List<Snapshot> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val editorSession: EditorSession? = null,
    val openTabs: List<String> = emptyList(),
    val selectedTab: PlaygroundTab = PlaygroundTab.OVERVIEW,
    val compatibility: BuildCompatibilityReport? = null,
    val preferences: AppPreferences = AppPreferences(),
)

@HiltViewModel
class PlaygroundViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val buildRepository: BuildRepository,
    private val conversationRepository: ConversationRepository,
    private val preferencesRepository: PreferencesRepository,
    private val buildEngine: BuildEngine,
    private val agentOrchestrator: AgentOrchestrator,
    private val apkInstaller: ApkInstaller,
) : ViewModel() {
    private val projectId = checkNotNull(savedStateHandle.get<String>("projectId"))
    private val selectedTab = MutableStateFlow(PlaygroundTab.OVERVIEW)
    private val editorSession = MutableStateFlow<EditorSession?>(null)
    private val openTabs = MutableStateFlow<List<String>>(emptyList())
    private val compatibility = MutableStateFlow<BuildCompatibilityReport?>(null)
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var activeAgentJob: Job? = null

    private val projectFlow = projectRepository.observeProject(projectId)
    private val filesFlow = projectFlow.map { project -> project?.let(projectRepository::listFiles).orEmpty() }
    private val buildsFlow = buildRepository.observeBuilds(projectId)
    private val artifactsFlow = projectRepository.observeArtifacts(projectId)
    private val snapshotsFlow = projectRepository.observeSnapshots(projectId)
    private val messagesFlow = conversationRepository.observeConversation(projectId).flatMapLatest { conversation ->
        conversation?.let { conversationRepository.observeMessages(it.id) } ?: emptyFlow()
    }
    private val preferencesFlow = preferencesRepository.preferences

    val uiState = combine(
        projectFlow,
        filesFlow,
        buildsFlow,
        artifactsFlow,
        snapshotsFlow,
        messagesFlow,
        editorSession,
        openTabs,
        selectedTab,
        compatibility,
        preferencesFlow,
    ) { project, files, builds, artifacts, snapshots, messages, editor, tabs, tab, compatibility, preferences ->
        PlaygroundUiState(
            project = project,
            files = files,
            builds = builds,
            artifacts = artifacts,
            snapshots = snapshots,
            messages = messages,
            editorSession = editor,
            openTabs = tabs,
            selectedTab = tab,
            compatibility = compatibility,
            preferences = preferences,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaygroundUiState())

    init {
        viewModelScope.launch {
            projectFlow.firstOrNull()?.let { project ->
                compatibility.value = buildEngine.assess(project)
            }
        }
    }

    fun selectTab(tab: PlaygroundTab) {
        selectedTab.value = tab
    }

    fun openFile(path: String) {
        viewModelScope.launch {
            val project = projectFlow.firstOrNull() ?: return@launch
            val content = projectRepository.readFile(project, path)
            editorSession.value = EditorSession(
                path = path,
                content = content,
                languageHint = path.substringAfterLast('.', "txt"),
            )
            openTabs.update { (it + path).distinct() }
            selectedTab.value = PlaygroundTab.EDITOR
            undoStack.clear()
            redoStack.clear()
        }
    }

    fun updateEditorContent(value: String) {
        val session = editorSession.value ?: return
        if (value == session.content) return
        undoStack.addLast(session.content)
        redoStack.clear()
        editorSession.value = session.copy(content = value, isDirty = true)
        viewModelScope.launch {
            if (preferencesFlow.firstOrNull()?.autosave == true) {
                saveFile()
            }
        }
    }

    fun updateSearchReplace(search: String, replace: String) {
        editorSession.update { it?.copy(searchQuery = search, replaceQuery = replace) }
    }

    fun replaceAll() {
        val session = editorSession.value ?: return
        if (session.searchQuery.isBlank()) return
        updateEditorContent(session.content.replace(session.searchQuery, session.replaceQuery))
    }

    fun undo() {
        val session = editorSession.value ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(session.content)
        editorSession.value = session.copy(content = previous, isDirty = true)
    }

    fun redo() {
        val session = editorSession.value ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(session.content)
        editorSession.value = session.copy(content = next, isDirty = true)
    }

    fun saveFile() {
        viewModelScope.launch {
            val project = projectFlow.firstOrNull() ?: return@launch
            val session = editorSession.value ?: return@launch
            projectRepository.writeFile(project, session.path, session.content)
            editorSession.value = session.copy(isDirty = false)
        }
    }

    fun createFile(path: String) {
        viewModelScope.launch {
            val project = projectFlow.firstOrNull() ?: return@launch
            projectRepository.createFile(project, path)
        }
    }

    fun createFolder(path: String) {
        viewModelScope.launch {
            val project = projectFlow.firstOrNull() ?: return@launch
            projectRepository.createFolder(project, path)
        }
    }

    fun renamePath(from: String, to: String) {
        viewModelScope.launch {
            val project = projectFlow.firstOrNull() ?: return@launch
            projectRepository.rename(project, from, to)
        }
    }

    fun deletePath(path: String) {
        viewModelScope.launch {
            val project = projectFlow.firstOrNull() ?: return@launch
            projectRepository.deletePath(project, path)
            if (editorSession.value?.path == path) {
                editorSession.value = null
            }
            openTabs.update { it - path }
        }
    }

    fun restoreSnapshot(snapshot: Snapshot) {
        viewModelScope.launch {
            val project = projectFlow.firstOrNull() ?: return@launch
            projectRepository.restoreSnapshot(project, snapshot)
            compatibility.value = buildEngine.assess(project)
        }
    }

    fun startBuild(mode: BuildMode) {
        viewModelScope.launch {
            buildEngine.enqueue(projectId, mode)
        }
    }

    fun askAi(prompt: String, mode: AgentMode, selectedFiles: List<String>) {
        activeAgentJob?.cancel()
        activeAgentJob = viewModelScope.launch {
            val project = projectFlow.firstOrNull() ?: return@launch
            agentOrchestrator.sendMessage(project, prompt, mode, selectedFiles, buildsFlow.firstOrNull()?.firstOrNull())
        }
    }

    fun askAiToFixLatestBuild() {
        val latestBuild = uiState.value.builds.firstOrNull() ?: return
        askAi(
            prompt = "Analyze the latest failed build and propose the safest file changes needed to fix it.",
            mode = AgentMode.APPLY,
            selectedFiles = uiState.value.openTabs,
        )
    }

    fun cancelAgent() {
        activeAgentJob?.cancel()
        activeAgentJob = null
    }

    fun applyChanges(message: ChatMessage) {
        viewModelScope.launch {
            val project = projectFlow.firstOrNull() ?: return@launch
            agentOrchestrator.applyChanges(project, message.proposedChanges)
            compatibility.value = buildEngine.assess(project)
        }
    }

    fun installArtifact(artifact: Artifact) {
        apkInstaller.install(artifact)
    }
}
