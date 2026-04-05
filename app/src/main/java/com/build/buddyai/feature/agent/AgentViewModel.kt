package com.build.buddyai.feature.agent

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.agent.AgentContextAssembler
import com.build.buddyai.core.agent.AgentExecutionPlan
import com.build.buddyai.core.agent.AgentPlannerProtocol
import com.build.buddyai.core.agent.AgentTaskProtocol
import com.build.buddyai.core.agent.ProjectSymbolIndex
import com.build.buddyai.core.common.ChangeSetInfo
import com.build.buddyai.core.common.ChangeSetManager
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.common.SnapshotManager
import com.build.buddyai.core.data.repository.ArtifactRepository
import com.build.buddyai.core.data.repository.BuildRepository
import com.build.buddyai.core.data.repository.ChatRepository
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.data.repository.ProviderRepository
import com.build.buddyai.core.model.ActionStatus
import com.build.buddyai.core.model.AgentAction
import com.build.buddyai.core.model.AgentActionType
import com.build.buddyai.core.model.BuildArtifact
import com.build.buddyai.core.model.BuildRecord
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.ChatMessage
import com.build.buddyai.core.model.ChatSession
import com.build.buddyai.core.model.FileDiff
import com.build.buddyai.core.model.MessageRole
import com.build.buddyai.core.model.MessageStatus
import com.build.buddyai.core.network.AiStreamingService
import com.build.buddyai.core.network.StreamEvent
import com.build.buddyai.domain.usecase.BuildProjectUseCase
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
    val recentDiffs: List<FileDiff> = emptyList(),
    val latestArtifact: BuildArtifact? = null,
    val changeSets: List<ChangeSetInfo> = emptyList(),
    val plannerSummary: String? = null,
    val isStreaming: Boolean = false,
    val hasProvider: Boolean = false,
    val providerName: String? = null,
    val modelName: String? = null,
    val currentActions: List<AgentAction> = emptyList(),
    val lastBuildStatus: BuildStatus? = null,
    val lastBuildSummary: String? = null
)

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val projectRepository: ProjectRepository,
    private val providerRepository: ProviderRepository,
    private val artifactRepository: ArtifactRepository,
    private val buildRepository: BuildRepository,
    private val buildProjectUseCase: BuildProjectUseCase,
    private val streamingService: AiStreamingService,
    private val snapshotManager: SnapshotManager,
    private val changeSetManager: ChangeSetManager,
    private val contextAssembler: AgentContextAssembler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private var currentProjectId: String? = null
    private var streamJob: Job? = null
    private var sessionsJob: Job? = null
    private var messagesJob: Job? = null
    private var artifactsJob: Job? = null

    fun initialize(projectId: String) {
        currentProjectId = projectId
        refreshChangeSets(projectId)

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

        artifactsJob?.cancel()
        artifactsJob = viewModelScope.launch {
            artifactRepository.getArtifactsByProject(projectId).collectLatest { artifacts ->
                _uiState.update { it.copy(latestArtifact = artifacts.firstOrNull()) }
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
            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = MessageRole.USER,
                content = input,
                attachedFiles = state.attachedFiles
            )
            chatRepository.insertMessage(userMessage)
            _uiState.update {
                it.copy(
                    currentInput = "",
                    attachedFiles = emptyList(),
                    isStreaming = true,
                    recentDiffs = emptyList(),
                    plannerSummary = null,
                    lastBuildStatus = null,
                    lastBuildSummary = null
                )
            }

            currentProject()?.let { project ->
                val projectDir = File(project.projectPath)
                if (projectDir.exists()) {
                    snapshotManager.createSnapshot(project.id, projectDir, "pre_agent")
                }
            }

            streamJob?.cancel()
            streamJob = viewModelScope.launch {
                executeAutonomousTurn(
                    sessionId = sessionId,
                    visibleUserInput = input,
                    attachedFiles = state.attachedFiles,
                    repairAttempt = 0,
                    repairContext = null
                )
            }
        }
    }

    private suspend fun executeAutonomousTurn(
        sessionId: String,
        visibleUserInput: String,
        attachedFiles: List<String>,
        repairAttempt: Int,
        repairContext: String?
    ) {
        try {
            val provider = providerRepository.getDefaultProvider() ?: return finishWithError(sessionId, "No AI provider configured")
            val apiKey = providerRepository.getApiKey(provider.id) ?: return finishWithError(sessionId, "Missing API key")
            val modelId = provider.selectedModelId ?: provider.models.firstOrNull()?.id ?: return finishWithError(sessionId, "No model selected")
            val project = currentProject() ?: return finishWithError(sessionId, "Project not found")
            val projectDir = File(project.projectPath)
            val symbolIndex = ProjectSymbolIndex.build(projectDir)
            val plan = requestExecutionPlan(
                providerType = provider.type,
                apiKey = apiKey,
                modelId = modelId,
                userRequest = visibleUserInput,
                repairContext = repairContext,
                projectIndex = symbolIndex,
                attachedFiles = attachedFiles,
                temperature = provider.parameters.temperature,
                maxTokens = provider.parameters.maxTokens,
                topP = provider.parameters.topP
            )
            val contextSnapshot = contextAssembler.assemble(
                projectDir = projectDir,
                userRequest = visibleUserInput,
                attachedFiles = attachedFiles,
                preferredFiles = plan.focusFiles
            )

            _uiState.update { it.copy(plannerSummary = plan.summary.takeIf(String::isNotBlank)) }
            setActions(
                action(AgentActionType.PLANNING, plan.summary.ifBlank { "Planning the implementation pass" }, ActionStatus.COMPLETED),
                action(AgentActionType.READING_FILE, "Focused context: ${contextSnapshot.focusFiles.joinToString().ifBlank { "project essentials" }}")
            )

            val assistantMsgId = UUID.randomUUID().toString()
            val placeholder = ChatMessage(
                id = assistantMsgId,
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.STREAMING,
                modelId = modelId
            )
            _uiState.update { state ->
                state.copy(
                    isStreaming = true,
                    messages = state.messages + placeholder
                )
            }

            val systemPrompt = buildSystemPrompt(
                projectContext = contextSnapshot.prompt,
                plan = plan,
                executionMemory = buildExecutionMemory()
            )
            val turnPrompt = if (repairContext == null) {
                visibleUserInput
            } else {
                buildString {
                    appendLine("Original user request:")
                    appendLine(visibleUserInput)
                    appendLine()
                    appendLine("The previous automated implementation failed on build validation. Repair the project directly and keep the response actionable.")
                    appendLine()
                    appendLine("Build failure context:")
                    appendLine("```text")
                    appendLine(repairContext)
                    appendLine("```")
                }
            }

            val requestMessages = buildRequestMessages(systemPrompt, turnPrompt)
            val rawContent = collectModelResponse(
                providerType = provider.type,
                apiKey = apiKey,
                modelId = modelId,
                messages = requestMessages,
                temperature = provider.parameters.temperature,
                maxTokens = provider.parameters.maxTokens,
                topP = provider.parameters.topP,
                onToken = { token ->
                    val updated = placeholder.copy(content = token)
                    _uiState.update { state ->
                        state.copy(messages = state.messages.map { if (it.id == assistantMsgId) updated else it })
                    }
                }
            )

            handleTurnCompletion(
                sessionId = sessionId,
                assistantMsgId = assistantMsgId,
                placeholder = placeholder,
                modelId = modelId,
                rawContent = rawContent,
                visibleUserInput = visibleUserInput,
                attachedFiles = attachedFiles,
                repairAttempt = repairAttempt,
                projectDir = projectDir,
                project = project,
                plan = plan
            )
        } catch (e: Exception) {
            finishWithError(sessionId, e.message ?: "AI task failed")
        }
    }

    private suspend fun requestExecutionPlan(
        providerType: com.build.buddyai.core.model.ProviderType,
        apiKey: String,
        modelId: String,
        userRequest: String,
        repairContext: String?,
        projectIndex: ProjectSymbolIndex,
        attachedFiles: List<String>,
        temperature: Float,
        maxTokens: Int,
        topP: Float
    ): AgentExecutionPlan {
        val plannerMessages = listOf(
            mapOf(
                "role" to "system",
                "content" to buildString {
                    appendLine("You are the hidden planner for BuildBuddy. Produce a focused execution plan for the next turn.")
                    appendLine(AgentPlannerProtocol.instructions())
                }
            ),
            mapOf(
                "role" to "user",
                "content" to buildString {
                    appendLine("User request:")
                    appendLine(userRequest)
                    if (repairContext != null) {
                        appendLine()
                        appendLine("Repair context:")
                        appendLine(repairContext)
                    }
                    appendLine()
                    appendLine("Attached files:")
                    appendLine(attachedFiles.joinToString("\n").ifBlank { "(none)" })
                    appendLine()
                    appendLine("Project index:")
                    appendLine(projectIndex.summary())
                }
            )
        )
        val raw = collectModelResponse(
            providerType = providerType,
            apiKey = apiKey,
            modelId = modelId,
            messages = plannerMessages,
            temperature = temperature,
            maxTokens = minOf(maxTokens, 1500),
            topP = topP,
            onToken = null
        )
        return AgentPlannerProtocol.parse(raw)
            ?: AgentExecutionPlan(
                summary = if (repairContext == null) "Direct implementation pass" else "Repair implementation pass",
                focusFiles = attachedFiles,
                shouldBuild = repairContext != null
            )
    }

    private suspend fun collectModelResponse(
        providerType: com.build.buddyai.core.model.ProviderType,
        apiKey: String,
        modelId: String,
        messages: List<Map<String, String>>,
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        onToken: ((String) -> Unit)?
    ): String {
        val contentBuilder = StringBuilder()
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
                    contentBuilder.append(event.content)
                    onToken?.invoke(contentBuilder.toString())
                }
                is StreamEvent.Done -> Unit
                is StreamEvent.Error -> throw RuntimeException(event.message)
            }
        }
        return contentBuilder.toString().trim()
    }

    private suspend fun handleTurnCompletion(
        sessionId: String,
        assistantMsgId: String,
        placeholder: ChatMessage,
        modelId: String,
        rawContent: String,
        visibleUserInput: String,
        attachedFiles: List<String>,
        repairAttempt: Int,
        projectDir: File,
        project: com.build.buddyai.core.model.Project,
        plan: AgentExecutionPlan
    ) {
        val parsed = AgentTaskProtocol.parse(rawContent)
        val finalMessage = placeholder.copy(
            content = parsed.displayMessage,
            status = MessageStatus.COMPLETE,
            modelId = modelId
        )
        chatRepository.insertMessage(finalMessage)
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { if (it.id == assistantMsgId) finalMessage else it },
                isStreaming = false
            )
        }

        if (!parsed.isTask) {
            clearActions()
            return
        }

        setActions(
            action(AgentActionType.EDITING_FILE, "Applying the implementation"),
            action(AgentActionType.VERIFYING, if (parsed.shouldBuild || plan.shouldBuild) "Preparing validation build" else "Finishing without validation")
        )

        val diffs = applyAgentOperations(projectDir, parsed)
        _uiState.update { it.copy(recentDiffs = diffs) }

        if (diffs.isNotEmpty()) {
            changeSetManager.recordChangeSet(
                projectId = project.id,
                summary = parsed.displayMessage.ifBlank { plan.summary.ifBlank { visibleUserInput.take(80) } },
                source = "agent",
                diffs = diffs
            )
            refreshChangeSets(project.id)
        }

        if (!(parsed.shouldBuild || plan.shouldBuild)) {
            setActions(action(AgentActionType.VERIFYING, "Changes applied", ActionStatus.COMPLETED))
            return
        }

        val buildOutcome = runValidationBuild(project)
        _uiState.update {
            it.copy(
                lastBuildStatus = buildOutcome.status,
                lastBuildSummary = buildOutcome.summary
            )
        }

        if (buildOutcome.status == BuildStatus.SUCCESS) {
            setActions(
                action(AgentActionType.EDITING_FILE, "Applied ${diffs.size} file change(s)", ActionStatus.COMPLETED),
                action(AgentActionType.BUILDING, buildOutcome.summary, ActionStatus.COMPLETED)
            )
            return
        }

        setActions(
            action(AgentActionType.EDITING_FILE, "Applied ${diffs.size} file change(s)", ActionStatus.COMPLETED),
            action(AgentActionType.BUILDING, buildOutcome.summary, ActionStatus.FAILED),
            action(AgentActionType.ANALYZING_LOGS, "Analyzing build failure", ActionStatus.IN_PROGRESS)
        )

        if (repairAttempt >= 1) {
            persistSystemMessage(sessionId, "Automatic validation failed after the repair pass. Review the build problems pane and latest logs for the remaining issue.")
            return
        }

        persistSystemMessage(sessionId, "Build validation failed, so BuildBuddy is running one automatic repair pass.")
        executeAutonomousTurn(
            sessionId = sessionId,
            visibleUserInput = visibleUserInput,
            attachedFiles = (diffs.map { it.filePath } + attachedFiles).distinct(),
            repairAttempt = repairAttempt + 1,
            repairContext = buildOutcome.failureContext
        )
    }

    private suspend fun applyAgentOperations(projectDir: File, parsed: com.build.buddyai.core.agent.ParsedAgentResponse): List<FileDiff> {
        val diffs = mutableListOf<FileDiff>()

        parsed.deletes.forEach { rawPath ->
            val normalized = normalizePathOrNull(rawPath) ?: return@forEach
            val original = FileUtils.readFileContent(projectDir, normalized).orEmpty()
            if (original.isNotEmpty()) {
                FileUtils.deleteFileOrDir(projectDir, normalized)
                diffs += FileDiff(
                    filePath = normalized,
                    originalContent = original,
                    modifiedContent = "",
                    additions = 0,
                    deletions = original.lineSequence().count(),
                    isDeleted = true
                )
            }
        }

        parsed.writes.forEach { write ->
            val normalized = normalizePathOrNull(write.path) ?: return@forEach
            val original = FileUtils.readFileContent(projectDir, normalized).orEmpty()
            FileUtils.writeFileContent(projectDir, normalized, write.content)
            diffs += FileDiff(
                filePath = normalized,
                originalContent = original,
                modifiedContent = write.content,
                additions = write.content.lineSequence().count().coerceAtLeast(1),
                deletions = original.lineSequence().count(),
                isNewFile = original.isBlank()
            )
        }

        return diffs.sortedBy { it.filePath }
    }

    private suspend fun runValidationBuild(project: com.build.buddyai.core.model.Project): BuildOutcome {
        val buildId = UUID.randomUUID().toString()
        val buildRecord = BuildRecord(
            id = buildId,
            projectId = project.id,
            status = BuildStatus.BUILDING,
            startedAt = System.currentTimeMillis()
        )
        buildRepository.insertBuildRecord(buildRecord)
        projectRepository.updateProject(project.copy(lastBuildStatus = BuildStatus.BUILDING))

        val logs = mutableListOf<com.build.buddyai.core.model.BuildLogEntry>()
        var successPath: String? = null
        var successSize: Long = 0L
        var failureMessage: String? = null
        var cancelled = false

        setActions(action(AgentActionType.BUILDING, "Running validation build"))

        buildProjectUseCase(project, buildId) { event ->
            when (event) {
                is BuildProjectUseCase.BuildEvent.Progress -> setActions(action(AgentActionType.BUILDING, event.message))
                is BuildProjectUseCase.BuildEvent.Log -> logs += event.entry
                is BuildProjectUseCase.BuildEvent.Warning -> Unit
                is BuildProjectUseCase.BuildEvent.Success -> {
                    successPath = event.artifactPath
                    successSize = event.artifactSize
                }
                is BuildProjectUseCase.BuildEvent.Failure -> failureMessage = event.error
                is BuildProjectUseCase.BuildEvent.Cancelled -> {
                    cancelled = true
                    failureMessage = event.message
                }
            }
        }

        val completedAt = System.currentTimeMillis()
        return when {
            successPath != null -> {
                val successRecord = buildRecord.copy(
                    status = BuildStatus.SUCCESS,
                    completedAt = completedAt,
                    durationMs = completedAt - buildRecord.startedAt,
                    artifactPath = successPath,
                    artifactSizeBytes = successSize,
                    logEntries = logs
                )
                buildRepository.updateBuildRecord(successRecord)
                projectRepository.updateProject(project.copy(lastBuildStatus = BuildStatus.SUCCESS, lastBuildAt = completedAt))
                artifactRepository.insertArtifact(
                    BuildArtifact(
                        id = UUID.randomUUID().toString(),
                        projectId = project.id,
                        projectName = project.name,
                        buildRecordId = buildId,
                        filePath = successPath!!,
                        fileName = File(successPath!!).name,
                        sizeBytes = successSize,
                        packageName = project.packageName,
                        versionName = "1.0.0",
                        versionCode = 1,
                        createdAt = completedAt,
                        minSdk = project.minSdk,
                        targetSdk = project.targetSdk
                    )
                )
                BuildOutcome(BuildStatus.SUCCESS, "Validation build passed • ${File(successPath!!).name}", null)
            }
            cancelled -> {
                val cancelledRecord = buildRecord.copy(
                    status = BuildStatus.CANCELLED,
                    completedAt = completedAt,
                    durationMs = completedAt - buildRecord.startedAt,
                    errorSummary = failureMessage,
                    logEntries = logs
                )
                buildRepository.updateBuildRecord(cancelledRecord)
                projectRepository.updateProject(project.copy(lastBuildStatus = BuildStatus.CANCELLED, lastBuildAt = completedAt))
                BuildOutcome(
                    status = BuildStatus.CANCELLED,
                    summary = failureMessage ?: "Validation build cancelled",
                    failureContext = failureMessage ?: logs.joinToString("\n") { it.message }
                )
            }
            else -> {
                val failureRecord = buildRecord.copy(
                    status = BuildStatus.FAILED,
                    completedAt = completedAt,
                    durationMs = completedAt - buildRecord.startedAt,
                    errorSummary = failureMessage,
                    logEntries = logs
                )
                buildRepository.updateBuildRecord(failureRecord)
                projectRepository.updateProject(project.copy(lastBuildStatus = BuildStatus.FAILED, lastBuildAt = completedAt))
                val failureContext = buildString {
                    appendLine(failureMessage ?: "Build failed")
                    if (logs.isNotEmpty()) {
                        appendLine()
                        appendLine("Recent build logs:")
                        logs.takeLast(80).forEach { appendLine(it.message) }
                    }
                }.trim()
                BuildOutcome(BuildStatus.FAILED, failureMessage ?: "Validation build failed", failureContext)
            }
        }
    }

    fun cancelStream() {
        streamJob?.cancel()
        _uiState.update {
            it.copy(
                isStreaming = false,
                currentActions = emptyList(),
                messages = it.messages.map { message ->
                    if (message.status == MessageStatus.STREAMING) {
                        message.copy(status = MessageStatus.CANCELLED, content = message.content.ifBlank { "Cancelled." })
                    } else {
                        message
                    }
                }
            )
        }
    }

    fun retryLastMessage() {
        val lastUserMessage = _uiState.value.messages.lastOrNull { it.role == MessageRole.USER } ?: return
        _uiState.update { it.copy(currentInput = lastUserMessage.content) }
        sendMessage()
    }

    private suspend fun createSession(projectId: String, seedTitle: String): String {
        val sessionId = UUID.randomUUID().toString()
        chatRepository.createSession(ChatSession(id = sessionId, projectId = projectId, title = seedTitle.take(50)))
        _uiState.update { it.copy(sessionId = sessionId) }
        return sessionId
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
        val errorMessage = resolvedPlaceholder.copy(content = "Error: $message", status = MessageStatus.ERROR)
        chatRepository.insertMessage(errorMessage)
        _uiState.update { state ->
            val existing = state.messages.any { it.id == resolvedId }
            state.copy(
                messages = if (existing) state.messages.map { if (it.id == resolvedId) errorMessage else it } else state.messages + errorMessage,
                isStreaming = false,
                currentActions = listOf(action(AgentActionType.ANALYZING_LOGS, message, ActionStatus.FAILED))
            )
        }
    }

    private suspend fun persistSystemMessage(sessionId: String, content: String) {
        chatRepository.insertMessage(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = MessageRole.SYSTEM,
                content = content,
                status = MessageStatus.COMPLETE
            )
        )
    }

    private fun buildRequestMessages(systemPrompt: String, turnPrompt: String): List<Map<String, String>> = buildList {
        add(mapOf("role" to "system", "content" to systemPrompt))
        _uiState.value.messages.takeLast(16).forEach { message ->
            add(
                mapOf(
                    "role" to when (message.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.SYSTEM -> "assistant"
                    },
                    "content" to message.content
                )
            )
        }
        add(mapOf("role" to "user", "content" to turnPrompt))
    }

    private fun buildSystemPrompt(
        projectContext: String,
        plan: AgentExecutionPlan,
        executionMemory: String
    ): String = buildString {
        appendLine("You are BuildBuddy, a production Android builder operating inside a sandboxed project.")
        appendLine("Default behavior: inspect the index, follow the current execution plan, apply full-file updates directly, and validate changes when appropriate.")
        appendLine("Never use placeholders, pseudo-diffs, TODO markers, partial snippets, or write outside the project root.")
        appendLine()
        appendLine("Planner summary: ${plan.summary.ifBlank { "direct implementation pass" }}")
        if (plan.goals.isNotEmpty()) appendLine("Planner goals: ${plan.goals.joinToString()}")
        if (plan.riskChecks.isNotEmpty()) appendLine("Risk checks: ${plan.riskChecks.joinToString()}")
        if (plan.focusFiles.isNotEmpty()) appendLine("Planned focus files: ${plan.focusFiles.joinToString()}")
        appendLine()
        appendLine("Execution memory:")
        appendLine(executionMemory)
        appendLine()
        appendLine(AgentTaskProtocol.protocolInstructions())
        appendLine()
        appendLine("Project context:")
        appendLine(projectContext)
    }

    private fun buildExecutionMemory(): String = buildString {
        val state = _uiState.value
        appendLine("Recent diffs: ${state.recentDiffs.take(8).joinToString { it.filePath }.ifBlank { "none" }}")
        appendLine("Latest build summary: ${state.lastBuildSummary ?: "none"}")
        if (state.changeSets.isNotEmpty()) {
            appendLine("Recent change sets: ${state.changeSets.take(3).joinToString { it.summary }}")
        }
    }.trim()

    private fun action(
        type: AgentActionType,
        description: String,
        status: ActionStatus = ActionStatus.IN_PROGRESS,
        filePath: String? = null
    ): AgentAction = AgentAction(
        id = UUID.randomUUID().toString(),
        type = type,
        description = description,
        status = status,
        filePath = filePath
    )

    private fun setActions(vararg actions: AgentAction) {
        _uiState.update { it.copy(currentActions = actions.toList()) }
    }

    private fun clearActions() {
        _uiState.update { it.copy(currentActions = emptyList()) }
    }

    private suspend fun currentProject() = currentProjectId?.let { projectRepository.getProjectById(it) }

    private fun normalizePathOrNull(path: String): String? =
        try {
            FileUtils.normalizeRelativePath(path)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun refreshChangeSets(projectId: String) {
        _uiState.update { it.copy(changeSets = changeSetManager.listChangeSets(projectId)) }
    }

    private data class BuildOutcome(
        val status: BuildStatus,
        val summary: String,
        val failureContext: String?
    )
}
