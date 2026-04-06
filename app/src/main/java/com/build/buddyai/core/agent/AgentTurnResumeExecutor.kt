package com.build.buddyai.core.agent

import com.build.buddyai.core.common.ExecutionNotificationManager
import com.build.buddyai.core.data.datastore.SettingsDataStore
import com.build.buddyai.core.data.local.entity.AgentTurnExecutionEntity
import com.build.buddyai.core.data.repository.AgentTurnExecutionRepository
import com.build.buddyai.core.data.repository.BuildRepository
import com.build.buddyai.core.data.repository.ChatRepository
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.data.repository.ProviderRepository
import com.build.buddyai.core.model.ChatMessage
import com.build.buddyai.core.model.MessageRole
import com.build.buddyai.core.model.MessageStatus
import com.build.buddyai.core.model.ModelMetadataRegistry
import com.build.buddyai.core.network.AiChatMessage
import com.build.buddyai.core.network.AiStreamingService
import com.build.buddyai.core.network.ModelCapabilityRegistry
import com.build.buddyai.core.network.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentTurnResumeExecutor @Inject constructor(
    private val executionRepository: AgentTurnExecutionRepository,
    private val chatRepository: ChatRepository,
    private val projectRepository: ProjectRepository,
    private val providerRepository: ProviderRepository,
    private val buildRepository: BuildRepository,
    private val streamingService: AiStreamingService,
    private val contextAssembler: AgentContextAssembler,
    private val toolMemoryStore: AgentToolMemoryStore,
    private val executionNotificationManager: ExecutionNotificationManager,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val MAX_HISTORY_MESSAGES = 60
        private const val MAX_SUMMARY_LINES = 24
    }

    enum class ResumeResult {
        COMPLETED,
        READY_FOR_APPLY_CONTINUATION,
        NOOP,
        FAILED
    }

    suspend fun resumeExecution(executionId: String, owner: String): ResumeResult {
        val execution = executionRepository.getById(executionId) ?: return ResumeResult.NOOP
        if (execution.status != AgentTurnExecutionStatus.RUNNING.name) return ResumeResult.NOOP
        if (execution.phase == AgentTurnExecutionPhase.APPLYING_CHANGES.name && execution.finalRawResponse?.isNotBlank() == true) {
            return ResumeResult.READY_FOR_APPLY_CONTINUATION
        }

        return runCatching {
            executeStreamingPhase(execution, owner)
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            executionRepository.markFailed(executionId, throwable.message ?: "Resume streaming failed")
            notifyAgentOutcome(execution.projectId, success = false, detail = throwable.message ?: "Resume streaming failed")
            ResumeResult.FAILED
        }
    }

    private suspend fun executeStreamingPhase(execution: AgentTurnExecutionEntity, owner: String): ResumeResult {
        val project = projectRepository.getProjectById(execution.projectId) ?: run {
            executionRepository.markFailed(execution.id, "Project not found for resumed execution")
            return ResumeResult.FAILED
        }
        val projectDir = File(project.projectPath)

        val provider = providerRepository.getDefaultProvider() ?: run {
            executionRepository.markFailed(execution.id, "No AI provider configured")
            return ResumeResult.FAILED
        }
        val apiKey = providerRepository.getApiKey(provider.id) ?: run {
            executionRepository.markFailed(execution.id, "Missing API key")
            return ResumeResult.FAILED
        }
        val modelId = execution.modelId
            ?: provider.selectedModelId
            ?: provider.models.firstOrNull()?.id
            ?: run {
                executionRepository.markFailed(execution.id, "No model selected")
                return ResumeResult.FAILED
            }
        val modelContextWindow = provider.models.firstOrNull { it.id == modelId }?.contextWindow
        val attachedFiles = executionRepository.decodeAttachedFiles(execution.attachedFilesJson)

        executionRepository.update(execution.id) { current ->
            current.copy(
                owner = owner,
                providerType = provider.type.name,
                providerId = provider.id,
                modelId = modelId,
                temperature = provider.parameters.temperature,
                maxTokens = provider.parameters.maxTokens,
                topP = provider.parameters.topP,
                phase = AgentTurnExecutionPhase.STREAMING.name,
                heartbeatAt = System.currentTimeMillis()
            )
        }

        val requestMessages = ensureRequestMessages(
            execution = execution,
            project = project,
            projectDir = projectDir,
            modelId = modelId,
            modelContextWindow = modelContextWindow,
            attachedFiles = attachedFiles
        )
        val assistantMessageId = ensurePlaceholderMessage(execution.id, execution.sessionId, modelId)
        val raw = streamAndPersist(
            executionId = execution.id,
            sessionId = execution.sessionId,
            assistantMessageId = assistantMessageId,
            providerType = provider.type,
            apiKey = apiKey,
            modelId = modelId,
            messages = requestMessages,
            temperature = provider.parameters.temperature,
            maxTokens = provider.parameters.maxTokens,
            topP = provider.parameters.topP
        )
        val parsed = AgentTaskProtocol.parse(raw)

        chatRepository.insertMessage(
            ChatMessage(
                id = assistantMessageId,
                sessionId = execution.sessionId,
                role = MessageRole.ASSISTANT,
                content = parsed.displayMessage,
                status = MessageStatus.COMPLETE,
                modelId = modelId
            )
        )

        return if (parsed.isTask) {
            executionRepository.update(execution.id) { current ->
                current.copy(
                    status = AgentTurnExecutionStatus.WAITING_REVIEW.name,
                    finalRawResponse = raw,
                    finalDisplayResponse = parsed.displayMessage,
                    partialResponse = parsed.displayMessage.takeLast(120_000),
                    phase = AgentTurnExecutionPhase.APPLYING_CHANGES.name,
                    heartbeatAt = System.currentTimeMillis()
                )
            }
            notifyAgentOutcome(project.id, success = true, detail = "Streaming resumed and response is ready to apply.")
            ResumeResult.READY_FOR_APPLY_CONTINUATION
        } else {
            executionRepository.markCompleted(
                executionId = execution.id,
                finalDisplayResponse = parsed.displayMessage,
                finalRawResponse = raw
            )
            notifyAgentOutcome(project.id, success = true, detail = "Agent response resumed successfully.")
            ResumeResult.COMPLETED
        }
    }

    private suspend fun ensureRequestMessages(
        execution: AgentTurnExecutionEntity,
        project: com.build.buddyai.core.model.Project,
        projectDir: File,
        modelId: String,
        modelContextWindow: Int?,
        attachedFiles: List<String>
    ): List<AiChatMessage> {
        execution.requestMessagesJson
            ?.takeIf { it.isNotBlank() }
            ?.let { encoded ->
                val decoded = executionRepository.decodeRequestMessages(encoded)
                if (decoded.isNotEmpty()) return decoded
            }

        val contextSnapshot = contextAssembler.assemble(
            projectId = project.id,
            projectDir = projectDir,
            attachedFiles = attachedFiles,
            focusHint = execution.userInput,
            buildHistory = buildRepository.getBuildRecordsByProjectNow(project.id).take(8),
            memoryContext = ProjectFailureMemoryStore.MemoryContext(
                templateFamily = project.template.name,
                buildEngine = "on-device-aapt2-ecj-d8",
                language = project.language.name,
                requestHint = execution.userInput
            ),
            maxChars = dynamicContextBudget(modelId, modelContextWindow)
        )
        toolMemoryStore.record(project.id, "resume_context", "Worker rebuilt context for restart-safe streaming", contextSnapshot.includedFiles.take(20))
        val plan = requestExecutionPlan(
            providerType = execution.providerType?.let { runCatching { com.build.buddyai.core.model.ProviderType.valueOf(it) }.getOrNull() }
                ?: providerRepository.getDefaultProvider()?.type
                ?: com.build.buddyai.core.model.ProviderType.OPENROUTER,
            apiKey = providerRepository.getApiKey(execution.providerId ?: providerRepository.getDefaultProvider()?.id.orEmpty()).orEmpty(),
            modelId = modelId,
            projectContext = contextSnapshot.prompt,
            userInput = execution.userInput,
            repairContext = execution.repairContext,
            attachedFiles = attachedFiles
        )
        val systemPrompt = buildExecutionSystemPrompt(contextSnapshot.prompt, plan)
        val turnPrompt = buildTurnPrompt(execution.userInput, execution.repairContext)
        val requestMessages = buildRequestMessages(
            sessionId = execution.sessionId,
            systemPrompt = systemPrompt,
            turnPrompt = turnPrompt,
            attachedFiles = attachedFiles,
            modelId = modelId,
            liveContextWindow = modelContextWindow
        )
        executionRepository.update(execution.id) { current ->
            current.copy(
                requestMessagesJson = executionRepository.encodeRequestMessages(requestMessages),
                planJson = executionRepository.encodePlan(plan),
                heartbeatAt = System.currentTimeMillis()
            )
        }
        return requestMessages
    }

    private suspend fun ensurePlaceholderMessage(executionId: String, sessionId: String, modelId: String): String {
        val existing = executionRepository.getById(executionId)?.assistantMessageId
        if (!existing.isNullOrBlank()) return existing
        val id = UUID.randomUUID().toString()
        chatRepository.insertMessage(
            ChatMessage(
                id = id,
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.STREAMING,
                modelId = modelId
            )
        )
        executionRepository.update(executionId) { current ->
            current.copy(assistantMessageId = id, heartbeatAt = System.currentTimeMillis())
        }
        return id
    }

    private suspend fun streamAndPersist(
        executionId: String,
        sessionId: String,
        assistantMessageId: String,
        providerType: com.build.buddyai.core.model.ProviderType,
        apiKey: String,
        modelId: String,
        messages: List<AiChatMessage>,
        temperature: Float,
        maxTokens: Int,
        topP: Float
    ): String {
        val builder = StringBuilder()
        var lastCheckpointAt = 0L
        streamingService.streamMessage(
            providerType = providerType,
            apiKey = apiKey,
            modelId = modelId,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens,
            topP = topP
        ).collectLatest { event ->
            when (event) {
                is StreamEvent.Token -> {
                    builder.append(event.content)
                    val partial = builder.toString()
                    chatRepository.insertMessage(
                        ChatMessage(
                            id = assistantMessageId,
                            sessionId = sessionId,
                            role = MessageRole.ASSISTANT,
                            content = partial,
                            status = MessageStatus.STREAMING,
                            modelId = modelId
                        )
                    )
                    val now = System.currentTimeMillis()
                    if (now - lastCheckpointAt >= 900L) {
                        executionRepository.update(executionId) { current ->
                            current.copy(
                                partialResponse = partial.takeLast(120_000),
                                heartbeatAt = now
                            )
                        }
                        lastCheckpointAt = now
                    }
                }
                is StreamEvent.Done -> Unit
                is StreamEvent.Error -> throw RuntimeException(event.message)
            }
        }
        val raw = builder.toString().trim()
        executionRepository.update(executionId) { current ->
            current.copy(
                partialResponse = raw.takeLast(120_000),
                finalRawResponse = raw,
                heartbeatAt = System.currentTimeMillis()
            )
        }
        return raw
    }

    private suspend fun requestExecutionPlan(
        providerType: com.build.buddyai.core.model.ProviderType,
        apiKey: String,
        modelId: String,
        projectContext: String,
        userInput: String,
        repairContext: String?,
        attachedFiles: List<String>
    ): AgentExecutionPlan? {
        if (apiKey.isBlank()) return null
        val system = buildString {
            appendLine("You are an Android app planning engine.")
            appendLine(AgentTaskProtocol.planningInstructions())
            appendLine()
            appendLine("Project context:")
            appendLine(projectContext)
        }
        val user = buildString {
            appendLine(userInput)
            repairContext?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("Repair context:")
                appendLine(it)
            }
        }
        val raw = collectModelResponse(
            providerType = providerType,
            apiKey = apiKey,
            modelId = modelId,
            messages = listOf(
                AiChatMessage(role = "system", text = system),
                AiChatMessage(role = "user", text = user, imagePaths = attachedFiles.filter { isImageAttachment(it) })
            ),
            temperature = 0.2f,
            maxTokens = 1200,
            topP = 0.9f
        )
        return AgentTaskProtocol.parsePlan(raw)
    }

    private suspend fun collectModelResponse(
        providerType: com.build.buddyai.core.model.ProviderType,
        apiKey: String,
        modelId: String,
        messages: List<AiChatMessage>,
        temperature: Float,
        maxTokens: Int,
        topP: Float
    ): String {
        val builder = StringBuilder()
        streamingService.streamMessage(
            providerType = providerType,
            apiKey = apiKey,
            modelId = modelId,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens,
            topP = topP
        ).collectLatest { event ->
            when (event) {
                is StreamEvent.Token -> builder.append(event.content)
                is StreamEvent.Done -> Unit
                is StreamEvent.Error -> throw RuntimeException(event.message)
            }
        }
        return builder.toString().trim()
    }

    private suspend fun buildRequestMessages(
        sessionId: String,
        systemPrompt: String,
        turnPrompt: String,
        attachedFiles: List<String>,
        modelId: String,
        liveContextWindow: Int? = null
    ): List<AiChatMessage> = buildList {
        add(AiChatMessage(role = "system", text = systemPrompt))
        val history = chatRepository.getMessagesBySessionNow(sessionId)
        val historyBudget = dynamicHistoryTokenBudget(modelId, liveContextWindow)
        val selected = ArrayDeque<ChatMessage>()
        val truncated = ArrayDeque<ChatMessage>()
        var consumed = estimateTokens(systemPrompt) + estimateTokens(turnPrompt)
        var truncating = false
        history.asReversed().forEach { message ->
            val estimatedCost = estimateMessageTokens(message)
            if (truncating || selected.size >= MAX_HISTORY_MESSAGES || consumed + estimatedCost > historyBudget) {
                truncating = true
                truncated.addFirst(message)
                return@forEach
            }
            selected.addFirst(message)
            consumed += estimatedCost
        }
        summarizeTruncatedHistory(truncated.toList()).takeIf { it.isNotBlank() }?.let { summary ->
            add(AiChatMessage(role = "system", text = "Conversation summary for earlier turns (compressed to fit context):\n$summary"))
        }
        selected.forEach { message ->
            add(
                AiChatMessage(
                    role = when (message.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.SYSTEM -> "system"
                    },
                    text = message.content,
                    imagePaths = message.attachedFiles.filter { isImageAttachment(it) }
                )
            )
        }
        add(AiChatMessage(role = "user", text = turnPrompt, imagePaths = attachedFiles.filter { isImageAttachment(it) }))
    }

    private fun buildExecutionSystemPrompt(projectContext: String, plan: AgentExecutionPlan?): String = buildString {
        appendLine("You are a senior Android product engineer working inside an existing project.")
        appendLine("Use local context and return concrete actions.")
        plan?.let {
            appendLine()
            appendLine("Planner goal: ${it.goal}")
            if (it.steps.isNotEmpty()) appendLine("Planner steps: ${it.steps.joinToString(" | ")}")
            if (it.risks.isNotEmpty()) appendLine("Planner risks: ${it.risks.joinToString(" | ")}")
        }
        appendLine()
        appendLine(AgentTaskProtocol.protocolInstructions())
        appendLine()
        appendLine("Project context:")
        appendLine(projectContext)
    }

    private fun buildTurnPrompt(input: String, repairContext: String?): String = if (repairContext.isNullOrBlank()) input else buildString {
        appendLine("Original user request:")
        appendLine(input)
        appendLine()
        appendLine("Repair context:")
        appendLine("```text")
        appendLine(repairContext)
        appendLine("```")
    }

    private suspend fun notifyAgentOutcome(projectId: String, success: Boolean, detail: String) {
        if (!settingsDataStore.settings.first().buildNotifications) return
        val projectName = projectRepository.getProjectById(projectId)?.name ?: "Project"
        executionNotificationManager.notifyAgentOutcome(projectName, success, detail)
    }

    private fun dynamicContextBudget(modelId: String, liveContextWindow: Int? = null): Int {
        val modelInfo = ModelMetadataRegistry.getModelInfo(modelId)
        val contextWindow = liveContextWindow ?: modelInfo?.contextWindow ?: 32_768
        return (contextWindow * 4).coerceIn(40_000, 300_000)
    }

    private fun dynamicHistoryTokenBudget(modelId: String, liveContextWindow: Int? = null): Int {
        val modelInfo = ModelMetadataRegistry.getModelInfo(modelId)
        val contextWindow = liveContextWindow ?: modelInfo?.contextWindow ?: 32_768
        val reserveForOutput = (contextWindow * 0.4f).toInt().coerceAtLeast(1_200)
        return (contextWindow - reserveForOutput).coerceIn(1_600, 96_000)
    }

    private fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        val chars = text.length
        val words = text.count { it == ' ' || it == '\n' || it == '\t' } + 1
        return (chars / 4 + words / 3).coerceAtLeast(1)
    }

    private fun estimateMessageTokens(message: ChatMessage): Int {
        val explicit = message.tokenCount ?: 0
        val contentTokens = estimateTokens(message.content)
        val attachmentTokens = message.attachedFiles.size * 180
        return explicit.coerceAtLeast(contentTokens) + attachmentTokens + 16
    }

    private fun summarizeTruncatedHistory(messages: List<ChatMessage>): String {
        if (messages.isEmpty()) return ""
        val summary = mutableListOf<String>()
        var consumed = 0
        messages.takeLast(MAX_HISTORY_MESSAGES).forEach { message ->
            val prefix = when (message.role) {
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT -> "Assistant"
                MessageRole.SYSTEM -> "System"
            }
            val line = "$prefix: ${message.content.lineSequence().firstOrNull().orEmpty().trim()}".trim()
            if (line.length < 10) return@forEach
            if (consumed + line.length > 2_400 || summary.size >= MAX_SUMMARY_LINES) return@forEach
            summary += line.take(200)
            consumed += line.length
        }
        return summary.joinToString("\n")
    }

    private fun isImageAttachment(path: String): Boolean {
        val extension = path.substringAfterLast('.', "").lowercase()
        return extension in ModelCapabilityRegistry.supportedImageExtensions()
    }
}
