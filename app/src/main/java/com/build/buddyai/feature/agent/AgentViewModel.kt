package com.build.buddyai.feature.agent

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.common.SnapshotManager
import com.build.buddyai.core.data.repository.ChatRepository
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.data.repository.ProviderRepository
import com.build.buddyai.core.model.*
import com.build.buddyai.core.network.AiStreamingService
import com.build.buddyai.core.network.StreamEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class AgentUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentInput: String = "",
    val isStreaming: Boolean = false,
    val agentMode: AgentMode = AgentMode.ASK,
    val attachedFiles: List<String> = emptyList(),
    val hasProvider: Boolean = false,
    val providerName: String? = null,
    val modelName: String? = null,
    val sessionId: String? = null,
    val pendingDiffs: List<FileDiff> = emptyList(),
    val currentActions: List<AgentAction> = emptyList()
)

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val projectRepository: ProjectRepository,
    private val providerRepository: ProviderRepository,
    private val streamingService: AiStreamingService,
    private val snapshotManager: SnapshotManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private var currentProjectId: String? = null
    private var streamJob: Job? = null

    fun initialize(projectId: String) {
        currentProjectId = projectId
        viewModelScope.launch {
            val provider = providerRepository.getDefaultProvider()
            _uiState.update {
                it.copy(
                    hasProvider = provider != null,
                    providerName = provider?.name,
                    modelName = provider?.models?.find { m -> m.id == provider.selectedModelId }?.name
                )
            }
        }
        viewModelScope.launch {
            chatRepository.getSessionsByProject(projectId).collect { sessions ->
                val session = sessions.firstOrNull()
                if (session != null) {
                    _uiState.update { it.copy(sessionId = session.id) }
                    chatRepository.getMessagesBySession(session.id).collect { messages ->
                        _uiState.update { it.copy(messages = messages) }
                    }
                }
            }
        }
    }

    fun updateInput(input: String) = _uiState.update { it.copy(currentInput = input) }
    fun updateAgentMode(mode: AgentMode) = _uiState.update { it.copy(agentMode = mode) }

    fun toggleFileAttachment(path: String) {
        _uiState.update { state ->
            val files = state.attachedFiles.toMutableList()
            if (files.contains(path)) files.remove(path) else files.add(path)
            state.copy(attachedFiles = files)
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val input = state.currentInput.trim()
        if (input.isBlank() || !state.hasProvider) return

        viewModelScope.launch {
            // Ensure session exists
            val sessionId = state.sessionId ?: run {
                val newId = UUID.randomUUID().toString()
                chatRepository.createSession(
                    ChatSession(id = newId, projectId = currentProjectId ?: return@launch, title = input.take(50))
                )
                _uiState.update { it.copy(sessionId = newId) }
                newId
            }

            // Add user message
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = MessageRole.USER,
                content = input,
                attachedFiles = state.attachedFiles
            )
            chatRepository.insertMessage(userMsg)
            _uiState.update {
                it.copy(
                    messages = it.messages + userMsg,
                    currentInput = "",
                    attachedFiles = emptyList(),
                    isStreaming = true,
                    currentActions = listOf(
                        AgentAction(UUID.randomUUID().toString(), AgentActionType.PLANNING, "Analyzing request", ActionStatus.IN_PROGRESS)
                    )
                )
            }

            // Create snapshot before AI changes
            currentProjectId?.let { pid ->
                val project = projectRepository.getProjectById(pid) ?: return@let
                if (File(project.projectPath).exists()) {
                    snapshotManager.createSnapshot(pid, File(project.projectPath), "pre_ai")
                }
            }

            // Stream AI response
            streamAiResponse(sessionId, input, state.attachedFiles)
        }
    }

    private fun streamAiResponse(sessionId: String, userInput: String, attachedFiles: List<String>) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val provider = providerRepository.getDefaultProvider() ?: return@launch
            val apiKey = providerRepository.getApiKey(provider.id) ?: return@launch
            val modelId = provider.selectedModelId ?: provider.models.firstOrNull()?.id ?: return@launch

            // Build context with attached files
            val fileContextParts = attachedFiles.mapNotNull { path ->
                currentProjectId?.let { pid ->
                    val project = projectRepository.getProjectById(pid) ?: return@let null
                    val content = FileUtils.readFileContent(File(project.projectPath), path)
                    if (content != null) "File: $path\n```\n$content\n```" else null
                }
            }

            val systemPrompt = buildString {
                appendLine("You are BuildBuddy, an expert Android development AI assistant.")
                appendLine("You help users build Android apps by generating, modifying, and explaining code.")
                appendLine("When generating code, output complete file contents wrapped in code blocks with the file path.")
                appendLine("Format: ```filepath:path/to/File.kt\\n<code>\\n```")
                appendLine("Be concise, accurate, and production-quality in all code output.")
                if (fileContextParts.isNotEmpty()) {
                    appendLine("\nProject files for context:")
                    fileContextParts.forEach { appendLine(it) }
                }
            }

            val messages = buildList {
                add(mapOf("role" to "system", "content" to systemPrompt))
                _uiState.value.messages.takeLast(20).forEach { msg ->
                    add(mapOf("role" to if (msg.role == MessageRole.USER) "user" else "assistant", "content" to msg.content))
                }
            }

            val assistantMsgId = UUID.randomUUID().toString()
            val assistantMsg = ChatMessage(
                id = assistantMsgId,
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.STREAMING,
                modelId = modelId
            )
            _uiState.update { it.copy(messages = it.messages + assistantMsg) }

            val contentBuilder = StringBuilder()

            streamingService.streamMessage(
                providerType = provider.type,
                apiKey = apiKey,
                modelId = modelId,
                messages = messages,
                temperature = provider.parameters.temperature,
                maxTokens = provider.parameters.maxTokens,
                topP = provider.parameters.topP
            ).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        contentBuilder.append(event.content)
                        val updatedMsg = assistantMsg.copy(content = contentBuilder.toString())
                        _uiState.update { state ->
                            state.copy(messages = state.messages.map { if (it.id == assistantMsgId) updatedMsg else it })
                        }
                    }
                    is StreamEvent.Done -> {
                        val finalMsg = assistantMsg.copy(
                            content = contentBuilder.toString(),
                            status = MessageStatus.COMPLETE
                        )
                        chatRepository.insertMessage(finalMsg)
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map { if (it.id == assistantMsgId) finalMsg else it },
                                isStreaming = false,
                                currentActions = emptyList()
                            )
                        }
                        // Parse and prepare file diffs
                        parseDiffsFromResponse(contentBuilder.toString())
                    }
                    is StreamEvent.Error -> {
                        val errorMsg = assistantMsg.copy(
                            content = "Error: ${event.message}",
                            status = MessageStatus.ERROR
                        )
                        chatRepository.insertMessage(errorMsg)
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map { if (it.id == assistantMsgId) errorMsg else it },
                                isStreaming = false,
                                currentActions = emptyList()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun parseDiffsFromResponse(content: String) {
        val regex = Regex("```filepath:(.*?)\\n([\\s\\S]*?)```")
        val diffs = regex.findAll(content).mapNotNull { match ->
            val path = match.groupValues[1].trim()
            val code = match.groupValues[2].trim()
            if (path.isNotBlank() && code.isNotBlank()) {
                val project = currentProjectId?.let { pid ->
                    kotlinx.coroutines.runBlocking { projectRepository.getProjectById(pid) }
                }
                val originalContent = project?.let {
                    FileUtils.readFileContent(File(it.projectPath), path)
                } ?: ""
                FileDiff(
                    filePath = path,
                    originalContent = originalContent,
                    modifiedContent = code,
                    isNewFile = originalContent.isEmpty()
                )
            } else null
        }.toList()

        if (diffs.isNotEmpty()) {
            _uiState.update { it.copy(pendingDiffs = diffs) }
        }
    }

    fun applyDiffs() {
        viewModelScope.launch {
            val project = currentProjectId?.let { projectRepository.getProjectById(it) } ?: return@launch
            _uiState.value.pendingDiffs.forEach { diff ->
                FileUtils.writeFileContent(File(project.projectPath), diff.filePath, diff.modifiedContent)
            }
            _uiState.update { it.copy(pendingDiffs = emptyList()) }
        }
    }

    fun rejectDiffs() {
        _uiState.update { it.copy(pendingDiffs = emptyList()) }
    }

    fun cancelStream() {
        streamJob?.cancel()
        _uiState.update { it.copy(isStreaming = false, currentActions = emptyList()) }
    }

    fun retryLastMessage() {
        val messages = _uiState.value.messages
        val lastUserMsg = messages.lastOrNull { it.role == MessageRole.USER } ?: return
        // Remove last assistant message if error
        val lastAssistant = messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        if (lastAssistant?.status == MessageStatus.ERROR) {
            _uiState.update { it.copy(messages = it.messages.filter { m -> m.id != lastAssistant.id }) }
        }
        _uiState.update { it.copy(currentInput = lastUserMsg.content) }
        sendMessage()
    }
}
