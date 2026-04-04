package com.build.buddyai.feature.agent

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.common.SnapshotManager
import com.build.buddyai.core.data.repository.ChatRepository
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.data.repository.ProviderRepository
import com.build.buddyai.core.model.ActionStatus
import com.build.buddyai.core.model.AgentAction
import com.build.buddyai.core.model.AgentActionType
import com.build.buddyai.core.model.AgentMode
import com.build.buddyai.core.model.ChatMessage
import com.build.buddyai.core.model.ChatSession
import com.build.buddyai.core.model.FileDiff
import com.build.buddyai.core.model.MessageRole
import com.build.buddyai.core.model.MessageStatus
import com.build.buddyai.core.network.AiStreamingService
import com.build.buddyai.core.network.StreamEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class AgentUiState(
    val sessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val currentInput: String = "",
    val attachedFiles: List<String> = emptyList(),
    val pendingDiffs: List<FileDiff> = emptyList(),
    val isStreaming: Boolean = false,
    val hasProvider: Boolean = false,
    val providerName: String? = null,
    val modelName: String? = null,
    val agentMode: AgentMode = AgentMode.ASK,
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
    private var sessionsJob: Job? = null
    private var messagesJob: Job? = null

    fun initialize(projectId: String) {
        currentProjectId = projectId

        viewModelScope.launch {
            val provider = providerRepository.getDefaultProvider()
            _uiState.update {
                it.copy(
                    hasProvider = provider != null,
                    providerName = provider?.name,
                    modelName = provider?.models?.find { model -> model.id == provider.selectedModelId }?.name
                )
            }
        }

        sessionsJob?.cancel()
        sessionsJob = viewModelScope.launch {
            chatRepository.getSessionsByProject(projectId).collectLatest { sessions ->
                val session = sessions.firstOrNull()
                _uiState.update { it.copy(sessionId = session?.id) }
                observeMessages(session?.id)
            }
        }
    }

    private fun observeMessages(sessionId: String?) {
        messagesJob?.cancel()
        if (sessionId == null) {
            _uiState.update { it.copy(messages = emptyList()) }
            return
        }
        messagesJob = viewModelScope.launch {
            chatRepository.getMessagesBySession(sessionId).collectLatest { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun updateInput(input: String) = _uiState.update { it.copy(currentInput = input) }
    fun updateAgentMode(mode: AgentMode) = _uiState.update { it.copy(agentMode = mode) }

    fun toggleFileAttachment(path: String) {
        val normalized = normalizePathOrNull(path) ?: return
        _uiState.update { state ->
            val files = state.attachedFiles.toMutableList()
            if (files.contains(normalized)) files.remove(normalized) else files.add(normalized)
            state.copy(attachedFiles = files)
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val input = state.currentInput.trim()
        if (input.isBlank() || !state.hasProvider) return

        viewModelScope.launch {
            val projectId = currentProjectId ?: return@launch
            val sessionId = state.sessionId ?: createSession(projectId, input)
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
                    currentInput = "",
                    attachedFiles = emptyList(),
                    isStreaming = true,
                    currentActions = listOf(
                        AgentAction(
                            UUID.randomUUID().toString(),
                            AgentActionType.PLANNING,
                            "Analyzing request",
                            ActionStatus.IN_PROGRESS
                        )
                    )
                )
            }

            currentProject()?.let { project ->
                val projectDir = File(project.projectPath)
                if (projectDir.exists()) {
                    snapshotManager.createSnapshot(project.id, projectDir, "pre_ai")
                }
            }

            streamAiResponse(sessionId, input, state.attachedFiles)
        }
    }

    private suspend fun createSession(projectId: String, seedTitle: String): String {
        val sessionId = UUID.randomUUID().toString()
        chatRepository.createSession(ChatSession(id = sessionId, projectId = projectId, title = seedTitle.take(50)))
        _uiState.update { it.copy(sessionId = sessionId) }
        return sessionId
    }

    private fun streamAiResponse(sessionId: String, userInput: String, attachedFiles: List<String>) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val provider = providerRepository.getDefaultProvider() ?: return@launch finishWithError(sessionId, "No AI provider configured")
            val apiKey = providerRepository.getApiKey(provider.id) ?: return@launch finishWithError(sessionId, "Missing API key")
            val modelId = provider.selectedModelId ?: provider.models.firstOrNull()?.id ?: return@launch finishWithError(sessionId, "No model selected")
            val project = currentProject() ?: return@launch finishWithError(sessionId, "Project not found")
            val projectDir = File(project.projectPath)

            val fileContextParts = attachedFiles.mapNotNull { path ->
                val normalized = normalizePathOrNull(path) ?: return@mapNotNull null
                val content = FileUtils.readFileContent(projectDir, normalized) ?: return@mapNotNull null
                "File: $normalized\n```\n$content\n```"
            }

            val systemPrompt = buildString {
                appendLine("You are BuildBuddy, an expert Android development AI assistant.")
                appendLine("You help users build Android apps by generating, modifying, and explaining code.")
                appendLine("When generating code, output complete file contents wrapped in code blocks with the file path.")
                appendLine("Format: ```filepath:path/to/File.kt\\n<code>\\n```")
                appendLine("Only write files inside the current project sandbox.")
                appendLine("Be concise, accurate, and production-quality in all code output.")
                if (fileContextParts.isNotEmpty()) {
                    appendLine()
                    appendLine("Project files for context:")
                    fileContextParts.forEach { appendLine(it) }
                }
            }

            val messages = buildList {
                add(mapOf("role" to "system", "content" to systemPrompt))
                _uiState.value.messages.takeLast(20).forEach { msg ->
                    add(mapOf("role" to if (msg.role == MessageRole.USER) "user" else "assistant", "content" to msg.content))
                }
                add(mapOf("role" to "user", "content" to userInput))
            }

            val assistantMsgId = UUID.randomUUID().toString()
            val placeholder = ChatMessage(
                id = assistantMsgId,
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.STREAMING,
                modelId = modelId
            )
            _uiState.update { it.copy(messages = it.messages + placeholder) }

            val contentBuilder = StringBuilder()
            streamingService.streamMessage(
                providerType = provider.type,
                apiKey = apiKey,
                modelId = modelId,
                messages = messages,
                temperature = provider.parameters.temperature,
                maxTokens = provider.parameters.maxTokens,
                topP = provider.parameters.topP
            ).collectLatest { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        contentBuilder.append(event.content)
                        val updated = placeholder.copy(content = contentBuilder.toString())
                        _uiState.update { state ->
                            state.copy(messages = state.messages.map { if (it.id == assistantMsgId) updated else it })
                        }
                    }
                    is StreamEvent.Done -> {
                        val finalMessage = placeholder.copy(content = contentBuilder.toString(), status = MessageStatus.COMPLETE)
                        chatRepository.insertMessage(finalMessage)
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map { if (it.id == assistantMsgId) finalMessage else it },
                                isStreaming = false,
                                currentActions = emptyList()
                            )
                        }
                        parseDiffsFromResponse(contentBuilder.toString(), projectDir)
                    }
                    is StreamEvent.Error -> {
                        finishWithError(sessionId, event.message, assistantMsgId, placeholder)
                    }
                }
            }
        }
    }

    private suspend fun parseDiffsFromResponse(content: String, projectDir: File) {
        val regex = Regex("```filepath:(.*?)\\n([\\s\\S]*?)```")
        val diffs = regex.findAll(content).mapNotNull { match ->
            val rawPath = match.groupValues[1].trim()
            val normalized = normalizePathOrNull(rawPath) ?: return@mapNotNull null
            val code = match.groupValues[2].trim()
            if (code.isBlank()) return@mapNotNull null
            val originalContent = FileUtils.readFileContent(projectDir, normalized).orEmpty()
            FileDiff(
                filePath = normalized,
                originalContent = originalContent,
                modifiedContent = code,
                isNewFile = originalContent.isEmpty()
            )
        }.toList()

        if (diffs.isNotEmpty()) {
            _uiState.update { it.copy(pendingDiffs = diffs) }
        }
    }

    fun applyDiffs() {
        viewModelScope.launch {
            val project = currentProject() ?: return@launch
            val projectDir = File(project.projectPath)
            _uiState.value.pendingDiffs.forEach { diff ->
                FileUtils.writeFileContent(projectDir, diff.filePath, diff.modifiedContent)
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
        val lastAssistant = messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        if (lastAssistant?.status == MessageStatus.ERROR) {
            _uiState.update { it.copy(messages = it.messages.filter { message -> message.id != lastAssistant.id }) }
        }
        _uiState.update { it.copy(currentInput = lastUserMsg.content) }
        sendMessage()
    }

    private suspend fun finishWithError(
        sessionId: String,
        message: String,
        assistantMsgId: String? = null,
        placeholder: ChatMessage? = null
    ) {
        val resolvedId = assistantMsgId ?: UUID.randomUUID().toString()
        val resolvedPlaceholder = placeholder ?: ChatMessage(
            id = resolvedId,
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.STREAMING
        )
        val errorMsg = resolvedPlaceholder.copy(content = "Error: $message", status = MessageStatus.ERROR)
        chatRepository.insertMessage(errorMsg)
        _uiState.update { state ->
            val existing = state.messages.any { it.id == resolvedId }
            state.copy(
                messages = if (existing) state.messages.map { if (it.id == resolvedId) errorMsg else it } else state.messages + errorMsg,
                isStreaming = false,
                currentActions = emptyList()
            )
        }
    }

    private suspend fun currentProject() = currentProjectId?.let { projectRepository.getProjectById(it) }

    private fun normalizePathOrNull(path: String): String? =
        try {
            FileUtils.normalizeRelativePath(path)
        } catch (_: IllegalArgumentException) {
            null
        }
}
