package com.build.buddyai.feature.agent

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.datastore.SecureKeyStore
import com.build.buddyai.core.data.datastore.SettingsDataStore
import com.build.buddyai.core.data.repository.ChatRepository
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.*
import com.build.buddyai.core.network.AiProviderService
import com.build.buddyai.core.network.AiResponse
import com.build.buddyai.core.ui.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class AgentState(
    val session: ChatSession? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val streamingContent: String = "",
    val agentMode: AgentMode = AgentMode.ASK,
    val attachedFiles: List<String> = emptyList(),
    val currentProvider: AiProvider? = null,
    val currentModel: AiModel? = null,
    val hasApiKey: Boolean = false,
    val errorMessage: String? = null,
    val toolActions: List<ToolAction> = emptyList()
)

@HiltViewModel
class AgentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val projectRepository: ProjectRepository,
    private val aiProviderService: AiProviderService,
    private val secureKeyStore: SecureKeyStore,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val projectId: String = savedStateHandle.get<String>("projectId") ?: ""
    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()
    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            val sessions = chatRepository.observeSessionsByProject(projectId).first()
            val session = sessions.firstOrNull() ?: ChatSession(projectId = projectId, title = "Chat").also {
                chatRepository.createSession(it)
            }
            _state.update { it.copy(session = session) }

            chatRepository.observeMessages(session.id).collect { messages ->
                _state.update { it.copy(messages = messages) }
            }
        }

        viewModelScope.launch {
            settingsDataStore.appSettings.collect { settings ->
                val providerId = settings.aiSettings.defaultProviderId
                val modelId = settings.aiSettings.defaultModelId
                val provider = DefaultProviders.ALL.find { it.id == providerId }
                val model = provider?.models?.find { it.id == modelId } ?: provider?.models?.firstOrNull()
                val hasKey = providerId?.let { secureKeyStore.hasApiKey(it) } ?: false
                _state.update { it.copy(currentProvider = provider, currentModel = model, hasApiKey = hasKey) }
            }
        }
    }

    fun updateInput(text: String) { _state.update { it.copy(inputText = text) } }

    fun setAgentMode(mode: AgentMode) { _state.update { it.copy(agentMode = mode) } }

    fun attachFile(filePath: String) {
        _state.update { it.copy(attachedFiles = it.attachedFiles + filePath) }
    }

    fun removeAttachment(filePath: String) {
        _state.update { it.copy(attachedFiles = it.attachedFiles - filePath) }
    }

    fun sendMessage() {
        val s = _state.value
        if (s.inputText.isBlank() || s.isStreaming) return
        val session = s.session ?: return
        val provider = s.currentProvider
        val model = s.currentModel
        if (provider == null || model == null) {
            _state.update { it.copy(errorMessage = "No AI provider configured. Go to Settings to add an API key.") }
            return
        }
        val apiKey = secureKeyStore.getApiKey(provider.id)
        if (apiKey.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "No API key for ${provider.name}. Add one in Settings.") }
            return
        }

        val userContent = buildString {
            append(s.inputText)
            if (s.attachedFiles.isNotEmpty()) {
                append("\n\n--- Attached Files ---\n")
                s.attachedFiles.forEach { path ->
                    val project = projectRepository.observeById(projectId).first()
                    val projectPath = project?.projectPath ?: return@forEach
                    val content = FileUtils.readFileContent(File(projectPath, path))
                    if (content != null) {
                        append("\n// $path\n$content\n")
                    }
                }
            }
        }

        val userMessage = ChatMessage(
            sessionId = session.id,
            role = MessageRole.USER,
            content = s.inputText,
            attachedFiles = s.attachedFiles,
            modelId = model.id
        )

        viewModelScope.launch { chatRepository.addMessage(userMessage) }
        _state.update { it.copy(inputText = "", attachedFiles = emptyList(), isStreaming = true, streamingContent = "", errorMessage = null) }

        val systemPrompt = buildSystemPrompt(s.agentMode)
        val allMessages = listOf(
            ChatMessage(sessionId = "", role = MessageRole.SYSTEM, content = systemPrompt)
        ) + s.messages + userMessage

        streamJob = viewModelScope.launch {
            val assistantMessage = ChatMessage(
                sessionId = session.id,
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.STREAMING,
                modelId = model.id
            )

            val params = ModelParameters()
            val fullContent = StringBuilder()

            aiProviderService.streamMessage(provider, apiKey, model.id, allMessages, params)
                .collect { response ->
                    when (response) {
                        is AiResponse.Chunk -> {
                            fullContent.append(response.text)
                            _state.update { it.copy(streamingContent = fullContent.toString()) }
                        }
                        is AiResponse.Complete -> {
                            val finalMessage = assistantMessage.copy(
                                content = response.text.ifEmpty { fullContent.toString() },
                                status = MessageStatus.COMPLETE,
                                tokenCount = response.tokenCount
                            )
                            chatRepository.addMessage(finalMessage)
                            _state.update { it.copy(isStreaming = false, streamingContent = "") }
                        }
                        is AiResponse.Error -> {
                            val errorMsg = when (response.error) {
                                is AiError.InvalidApiKey -> "Invalid API key for ${provider.name}"
                                is AiError.RateLimited -> "Rate limited. Please wait and try again."
                                is AiError.NetworkError -> "Network error: ${(response.error as AiError.NetworkError).message}"
                                is AiError.ProviderUnavailable -> "${provider.name} is currently unavailable"
                                is AiError.MalformedResponse -> "Received malformed response"
                                is AiError.Unknown -> "An unexpected error occurred"
                            }
                            val errorMessage = assistantMessage.copy(
                                content = "Error: $errorMsg",
                                status = MessageStatus.ERROR
                            )
                            chatRepository.addMessage(errorMessage)
                            _state.update { it.copy(isStreaming = false, streamingContent = "", errorMessage = errorMsg) }
                        }
                    }
                }
        }
    }

    fun cancelStream() {
        streamJob?.cancel()
        _state.update { it.copy(isStreaming = false, streamingContent = "") }
    }

    fun clearError() { _state.update { it.copy(errorMessage = null) } }

    fun deleteMessage(messageId: String) {
        val session = _state.value.session ?: return
        viewModelScope.launch { chatRepository.deleteMessage(messageId, session.id) }
    }

    private fun buildSystemPrompt(mode: AgentMode): String {
        val base = """You are BuildBuddy AI, an expert Android development assistant. You help users create, modify, debug, and improve Android app projects.

You are working on a project. When providing code changes:
- Always specify the file path
- Provide complete, working code
- Follow Android best practices
- Use Kotlin and Jetpack Compose by default unless the project uses Java/XML
"""
        return when (mode) {
            AgentMode.ASK -> "$base\nMode: Answer questions about code, architecture, and Android development."
            AgentMode.PLAN -> "$base\nMode: Create detailed step-by-step implementation plans. Do not write code directly — outline what files to create/modify, what changes to make, and in what order."
            AgentMode.APPLY -> "$base\nMode: Generate code changes. Provide complete file contents or specific diffs. Always specify the file path."
            AgentMode.AUTO -> "$base\nMode: Automatically generate and apply code changes. Confirm before any destructive operations like deleting files."
        }
    }
}