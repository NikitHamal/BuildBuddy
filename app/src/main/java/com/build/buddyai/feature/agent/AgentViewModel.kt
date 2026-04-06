package com.build.buddyai.feature.agent

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.agent.AgentChangeSetManager
import com.build.buddyai.core.agent.AgentEditOperation
import com.build.buddyai.core.agent.AgentFileWrite
import com.build.buddyai.core.agent.AgentReviewPolicy
import com.build.buddyai.core.agent.AgentContextAssembler
import com.build.buddyai.core.agent.AgentExecutionPlan
import com.build.buddyai.core.agent.AgentBackgroundExecutionRegistry
import com.build.buddyai.core.agent.AgentTurnExecutionPhase
import com.build.buddyai.core.agent.AgentTurnExecutionStatus
import com.build.buddyai.core.agent.AgentTurnWorkScheduler
import com.build.buddyai.core.agent.AgentTaskProtocol
import com.build.buddyai.core.agent.TextDiffEngine
import com.build.buddyai.core.agent.ProjectFailureMemoryStore
import com.build.buddyai.core.agent.ParsedAgentResponse
import com.build.buddyai.core.agent.ProjectIntegrityChecker
import com.build.buddyai.core.agent.AgentToolMemoryStore
import com.build.buddyai.core.common.ArtifactProvenance
import com.build.buddyai.core.common.ArtifactProvenanceStore
import com.build.buddyai.core.common.BuildArtifactInstaller
import com.build.buddyai.core.common.BuildProfileManager
import com.build.buddyai.core.common.AgentForegroundService
import com.build.buddyai.core.common.ExecutionNotificationManager
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.common.SnapshotManager
import com.build.buddyai.core.data.datastore.SettingsDataStore
import com.build.buddyai.core.data.repository.ArtifactRepository
import com.build.buddyai.core.data.repository.AgentTurnExecutionRepository
import com.build.buddyai.core.data.repository.BuildRepository
import com.build.buddyai.core.data.repository.ChatRepository
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.data.repository.ProviderRepository
import com.build.buddyai.core.model.ActionStatus
import com.build.buddyai.core.model.AgentAutonomyMode
import com.build.buddyai.core.model.AgentAction
import com.build.buddyai.core.model.AgentActionType
import com.build.buddyai.core.model.BuildArtifact
import com.build.buddyai.core.model.BuildProblem
import com.build.buddyai.core.model.BuildProfile
import com.build.buddyai.core.model.BuildRecord
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.ChatMessage
import com.build.buddyai.core.model.ChatSession
import com.build.buddyai.core.model.FileDiff
import com.build.buddyai.core.model.ModelMetadataRegistry
import com.build.buddyai.core.model.MessageRole
import com.build.buddyai.core.model.MessageStatus
import com.build.buddyai.core.model.ProblemSeverity
import com.build.buddyai.core.network.AiChatMessage
import com.build.buddyai.core.network.AiStreamingService
import com.build.buddyai.core.network.ModelCapabilityRegistry
import com.build.buddyai.core.network.StreamEvent
import com.build.buddyai.core.problems.ProjectProblemsService
import com.build.buddyai.domain.usecase.BuildProjectUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
    val recentChangeSets: List<AgentChangeSetManager.ChangeSet> = emptyList(),
    val isStreaming: Boolean = false,
    val hasProvider: Boolean = false,
    val providerName: String? = null,
    val modelName: String? = null,
    val supportsImageAttachments: Boolean = false,
    val autonomyMode: AgentAutonomyMode = AgentAutonomyMode.AUTONOMOUS_SAFE,
    val currentActions: List<AgentAction> = emptyList(),
    val currentPlanGoal: String? = null,
    val currentPlanSteps: List<String> = emptyList(),
    val lastBuildStatus: BuildStatus? = null,
    val lastBuildSummary: String? = null,
    val latestArtifact: BuildArtifact? = null,
    val integrityWarnings: List<String> = emptyList(),
    val problems: List<BuildProblem> = emptyList(),
    val pendingReview: PendingAgentReview? = null,
    val allProviders: List<com.build.buddyai.core.model.AiProvider> = emptyList()
)

data class ReviewHunkPreview(
    val id: String,
    val filePath: String,
    val title: String,
    val preview: String,
    val accepted: Boolean = true
)

data class PendingAgentReview(
    val executionId: String?,
    val sessionId: String,
    val summary: String,
    val reasons: List<String>,
    val writes: List<AgentFileWrite>,
    val deletes: List<String>,
    val operations: List<AgentEditOperation>,
    val shouldBuild: Boolean,
    val visibleUserInput: String,
    val attachedFiles: List<String>,
    val repairAttempt: Int,
    val hunks: List<ReviewHunkPreview>
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
    private val contextAssembler: AgentContextAssembler,
    private val backgroundExecutionRegistry: AgentBackgroundExecutionRegistry,
    private val agentTurnExecutionRepository: AgentTurnExecutionRepository,
    private val agentTurnWorkScheduler: AgentTurnWorkScheduler,
    private val textDiffEngine: TextDiffEngine,
    private val changeSetManager: AgentChangeSetManager,
    private val failureMemoryStore: ProjectFailureMemoryStore,
    private val toolMemoryStore: AgentToolMemoryStore,
    private val integrityChecker: ProjectIntegrityChecker,
    private val buildProfileManager: BuildProfileManager,
    private val artifactInstaller: BuildArtifactInstaller,
    private val executionNotificationManager: ExecutionNotificationManager,
    private val provenanceStore: ArtifactProvenanceStore,
    private val settingsDataStore: SettingsDataStore,
    private val problemsService: ProjectProblemsService,
    @ApplicationContext private val context: Context
) : ViewModel() {
    companion object {
        private const val MAX_AUTOMATIC_REPAIR_PASSES = 4
        private const val MAX_HISTORY_MESSAGES = 60
        private const val MAX_SUMMARY_LINES = 24
        private const val MAX_DIFF_PREVIEW_LINES = 90
    }

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private var currentProjectId: String? = null
    private var streamJob: Job? = null
    private var sessionsJob: Job? = null
    private var messagesJob: Job? = null
    private var artifactsJob: Job? = null
    private var problemsJob: Job? = null
    private var settingsJob: Job? = null
    private var providersJob: Job? = null
    private var notificationsEnabled: Boolean = true
    private var currentProjectName: String = "Project"
    private var activeExecutionId: String? = null
    private var executionOwner: String? = null

    override fun onCleared() {
        // Keep active agent execution running in background registry even if the screen is destroyed.
        sessionsJob?.cancel()
        messagesJob?.cancel()
        artifactsJob?.cancel()
        problemsJob?.cancel()
        settingsJob?.cancel()
        providersJob?.cancel()
        streamJob = null
        sessionsJob = null
        messagesJob = null
        artifactsJob = null
        problemsJob = null
        settingsJob = null
        providersJob = null
        super.onCleared()
    }

    fun initialize(projectId: String) {
        currentProjectId = projectId
        viewModelScope.launch {
            currentProjectName = projectRepository.getProjectById(projectId)?.name ?: "Project"
        }
        observeSettings()
        observeProviders()
        loadProviderState()
        observeSessions(projectId)
        observeArtifacts(projectId)
        observeProblems(projectId)
        refreshChangeSets()
    }

    private fun observeProviders() {
        providersJob?.cancel()
        providersJob = viewModelScope.launch {
            providerRepository.getAllProviders().collectLatest { providers ->
                _uiState.update { it.copy(allProviders = providers) }
            }
        }
    }

    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            settingsDataStore.settings.collectLatest { settings ->
                notificationsEnabled = settings.buildNotifications
                _uiState.update { it.copy(autonomyMode = settings.autonomyMode) }
                loadProviderState()
            }
        }
    }

    private fun loadProviderState() {
        viewModelScope.launch {
            val provider = providerRepository.getDefaultProvider()
            val selectedModel = provider?.models?.find { model -> model.id == provider.selectedModelId }
            _uiState.update {
                it.copy(
                    hasProvider = provider != null,
                    providerName = provider?.name,
                    modelName = selectedModel?.name,
                    supportsImageAttachments = selectedModel?.let { model -> ModelCapabilityRegistry.supportsVision(provider.type, model.id) } == true
                )
            }
        }
    }

    private fun observeSessions(projectId: String) {
        sessionsJob?.cancel()
        sessionsJob = viewModelScope.launch {
            chatRepository.getSessionsByProject(projectId).collectLatest { sessions ->
                val session = sessions.firstOrNull()
                _uiState.update { it.copy(sessionId = session?.id) }
                observeMessages(session?.id)
                resumeCheckpointedExecution(session?.id)
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

    private fun observeArtifacts(projectId: String) {
        artifactsJob?.cancel()
        artifactsJob = viewModelScope.launch {
            artifactRepository.getArtifactsByProject(projectId).collectLatest { artifacts ->
                _uiState.update { it.copy(latestArtifact = artifacts.firstOrNull()) }
            }
        }
    }

    private fun observeProblems(projectId: String) {
        problemsJob?.cancel()
        problemsJob = viewModelScope.launch {
            problemsService.observe(projectId).collectLatest { problems ->
                _uiState.update { it.copy(problems = problems) }
            }
        }
    }

    fun updateInput(input: String) = _uiState.update { it.copy(currentInput = input) }

    fun updateAutonomyMode(mode: AgentAutonomyMode) {
        viewModelScope.launch {
            settingsDataStore.updateAutonomyMode(mode)
        }
    }

    fun selectModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            providerRepository.setDefaultProvider(providerId)
            providerRepository.updateProviderModel(providerId, modelId)
            loadProviderState()
        }
    }

    fun toggleFileAttachment(path: String) {
        val normalized = normalizePathOrNull(path) ?: return
        _uiState.update { state ->
            val files = state.attachedFiles.toMutableList()
            if (files.contains(normalized)) files.remove(normalized) else files.add(normalized)
            state.copy(attachedFiles = files)
        }
    }

    fun removeAttachment(path: String) {
        _uiState.update { state -> state.copy(attachedFiles = state.attachedFiles.filterNot { it == path }) }
    }

    fun addImageAttachment(uri: android.net.Uri) {
        if (!_uiState.value.supportsImageAttachments) return
        viewModelScope.launch {
            runCatching {
                val projectId = currentProjectId ?: return@runCatching null
                val extension = context.contentResolver.getType(uri)?.substringAfterLast('/')
                    ?.lowercase()
                    ?.takeIf { it in ModelCapabilityRegistry.supportedImageExtensions() }
                    ?: uri.toString().substringAfterLast('.', "png").lowercase()
                val safeExtension = extension.takeIf { it in ModelCapabilityRegistry.supportedImageExtensions() } ?: "png"
                val dir = File(context.filesDir, "chat_attachments/$projectId").apply { mkdirs() }
                val target = File(dir, "${UUID.randomUUID()}.$safeExtension")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to read image")
                target.absolutePath
            }.onSuccess { path ->
                if (path != null) _uiState.update { state -> state.copy(attachedFiles = (state.attachedFiles + path).distinct()) }
            }
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val input = state.currentInput.trim()
        if ((input.isBlank() && state.attachedFiles.isEmpty()) || !state.hasProvider) return

        viewModelScope.launch {
            val projectId = currentProjectId ?: return@launch
            val sessionId = state.sessionId ?: createSession(projectId, input.ifBlank { "Image request" })
            val effectiveInput = input.ifBlank { "Analyze the attached image and help with the project." }
            val executionId = UUID.randomUUID().toString()
            val owner = "vm:${UUID.randomUUID()}"
            activeExecutionId = executionId
            executionOwner = owner
            chatRepository.insertMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = MessageRole.USER,
                    content = input.ifBlank { "Attached image" },
                    attachedFiles = state.attachedFiles
                )
            )
            problemsService.clear(projectId)
            _uiState.update {
                it.copy(
                    currentInput = "",
                    attachedFiles = emptyList(),
                    isStreaming = true,
                    recentDiffs = emptyList(),
                    lastBuildStatus = null,
                    lastBuildSummary = null,
                    integrityWarnings = emptyList(),
                    pendingReview = null
                )
            }

            currentProject()?.let { project ->
                val projectDir = File(project.projectPath)
                currentProjectName = project.name
                if (projectDir.exists()) snapshotManager.createSnapshot(project.id, projectDir, "pre_agent")
            }

            agentTurnExecutionRepository.create(
                AgentTurnExecutionRepository.NewExecution(
                    id = executionId,
                    projectId = projectId,
                    sessionId = sessionId,
                    userInput = effectiveInput,
                    attachedFiles = state.attachedFiles,
                    repairAttempt = 0,
                    repairContext = null,
                    owner = owner
                )
            )
            agentTurnWorkScheduler.scheduleWatchdog(executionId)

            streamJob?.cancel()
            startAgentForeground(sessionId, "Planning and analyzing request...")
            streamJob = backgroundExecutionRegistry.launch(sessionId) {
                try {
                    executeAutonomousTurn(
                        executionId = executionId,
                        owner = owner,
                        sessionId = sessionId,
                        visibleUserInput = effectiveInput,
                        attachedFiles = state.attachedFiles,
                        repairAttempt = 0,
                        repairContext = null
                    )
                } finally {
                    stopAgentForeground()
                }
            }
        }
    }

    private suspend fun executeAutonomousTurn(
        executionId: String,
        owner: String,
        sessionId: String,
        visibleUserInput: String,
        attachedFiles: List<String>,
        repairAttempt: Int,
        repairContext: String?
    ) {
        try {
            checkpointExecution(
                executionId = executionId,
                phase = AgentTurnExecutionPhase.CONTEXT_ASSEMBLY,
                status = AgentTurnExecutionStatus.RUNNING,
                owner = owner
            )
            val provider = providerRepository.getDefaultProvider() ?: return finishWithError(sessionId, "No AI provider configured")
            val apiKey = providerRepository.getApiKey(provider.id) ?: return finishWithError(sessionId, "Missing API key")
            val modelId = provider.selectedModelId ?: provider.models.firstOrNull()?.id ?: return finishWithError(sessionId, "No model selected")
            val modelContextWindow = provider.models.firstOrNull { it.id == modelId }?.contextWindow
            val project = currentProject() ?: return finishWithError(sessionId, "Project not found")
            val projectDir = File(project.projectPath)
            currentProjectName = project.name
            updateAgentForeground(sessionId, "Using ${provider.name} • ${modelId}")
            val contextSnapshot = contextAssembler.assemble(
                projectId = project.id,
                projectDir = projectDir,
                attachedFiles = attachedFiles,
                focusHint = visibleUserInput,
                buildHistory = buildRepository.getBuildRecordsByProjectNow(project.id).take(8),
                memoryContext = buildMemoryContext(project, visibleUserInput),
                maxChars = dynamicContextBudget(modelId, modelContextWindow)
            )
            val plan = requestExecutionPlan(
                providerType = provider.type,
                apiKey = apiKey,
                modelId = modelId,
                projectContext = contextSnapshot.prompt,
                userInput = visibleUserInput,
                repairContext = repairContext,
                attachedFiles = attachedFiles
            )
            applyPlanUi(plan)
            checkpointExecution(executionId = executionId, phase = AgentTurnExecutionPhase.PLANNING)
            toolMemoryStore.record(project.id, "planner", plan?.steps?.joinToString(" • ") ?: "No explicit plan returned", plan?.readFiles.orEmpty())

            setActions(
                action(AgentActionType.READING_FILE, if (repairContext == null) "Using symbol index + local memory to scope the task" else "Reviewing failure memory and repair context"),
                action(AgentActionType.PLANNING, plan?.steps?.firstOrNull() ?: "Preparing execution plan")
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
            chatRepository.insertMessage(placeholder)
            _uiState.update { state -> state.copy(isStreaming = true, messages = state.messages + placeholder) }

            val systemPrompt = buildExecutionSystemPrompt(contextSnapshot.prompt, plan)
            val turnPrompt = buildTurnPrompt(visibleUserInput, repairContext)
            val requestMessages = buildRequestMessages(
                systemPrompt = systemPrompt,
                turnPrompt = turnPrompt,
                attachedFiles = attachedFiles,
                modelId = modelId,
                liveContextWindow = modelContextWindow
            )
            agentTurnExecutionRepository.update(executionId) { current ->
                current.copy(
                    providerType = provider.type.name,
                    providerId = provider.id,
                    modelId = modelId,
                    temperature = provider.parameters.temperature,
                    maxTokens = provider.parameters.maxTokens,
                    topP = provider.parameters.topP,
                    requestMessagesJson = agentTurnExecutionRepository.encodeRequestMessages(requestMessages),
                    planJson = agentTurnExecutionRepository.encodePlan(plan),
                    assistantMessageId = assistantMsgId,
                    phase = AgentTurnExecutionPhase.STREAMING.name,
                    heartbeatAt = System.currentTimeMillis()
                )
            }
            checkpointExecution(executionId = executionId, phase = AgentTurnExecutionPhase.STREAMING)
            val rawContent = streamModelResponse(
                executionId = executionId,
                providerType = provider.type,
                apiKey = apiKey,
                modelId = modelId,
                messages = requestMessages,
                assistantMsgId = assistantMsgId,
                placeholder = placeholder,
                temperature = provider.parameters.temperature,
                maxTokens = provider.parameters.maxTokens,
                topP = provider.parameters.topP
            )

            handleTurnCompletion(
                executionId = executionId,
                owner = owner,
                sessionId = sessionId,
                assistantMsgId = assistantMsgId,
                placeholder = placeholder,
                modelId = modelId,
                rawContent = rawContent,
                visibleUserInput = visibleUserInput,
                attachedFiles = attachedFiles,
                repairAttempt = repairAttempt,
                projectDir = projectDir,
                project = project
            )
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            agentTurnExecutionRepository.markFailed(executionId, e.toAgentFailureMessage())
            finishWithError(sessionId, e.toAgentFailureMessage())
        }
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

    private fun applyPlanUi(plan: AgentExecutionPlan?) {
        _uiState.update {
            it.copy(
                currentPlanGoal = plan?.goal,
                currentPlanSteps = plan?.steps.orEmpty()
            )
        }
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
                is StreamEvent.Token -> contentBuilder.append(event.content)
                is StreamEvent.Done -> Unit
                is StreamEvent.Error -> throw RuntimeException(event.message)
            }
        }
        return contentBuilder.toString().trim()
    }

    private suspend fun streamModelResponse(
        executionId: String,
        providerType: com.build.buddyai.core.model.ProviderType,
        apiKey: String,
        modelId: String,
        messages: List<AiChatMessage>,
        assistantMsgId: String,
        placeholder: ChatMessage,
        temperature: Float,
        maxTokens: Int,
        topP: Float
    ): String {
        val contentBuilder = StringBuilder()
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
                    contentBuilder.append(event.content)
                    val updated = placeholder.copy(content = contentBuilder.toString())
                    chatRepository.insertMessage(updated)
                    _uiState.update { state ->
                        state.copy(messages = state.messages.map { if (it.id == assistantMsgId) updated else it })
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastCheckpointAt >= 900L) {
                        checkpointExecution(
                            executionId = executionId,
                            phase = AgentTurnExecutionPhase.STREAMING,
                            partialResponse = updated.content.takeLast(120_000),
                            scheduleWatchdog = false
                        )
                        lastCheckpointAt = now
                    }
                }
                is StreamEvent.Done -> Unit
                is StreamEvent.Error -> throw RuntimeException(event.message)
            }
        }
        val raw = contentBuilder.toString().trim()
        checkpointExecution(
            executionId = executionId,
            phase = AgentTurnExecutionPhase.STREAMING,
            partialResponse = raw.takeLast(120_000),
            finalRawResponse = raw,
            scheduleWatchdog = false
        )
        return raw
    }

    private suspend fun handleTurnCompletion(
        executionId: String,
        owner: String,
        sessionId: String,
        assistantMsgId: String,
        placeholder: ChatMessage,
        modelId: String,
        rawContent: String,
        visibleUserInput: String,
        attachedFiles: List<String>,
        repairAttempt: Int,
        projectDir: File,
        project: com.build.buddyai.core.model.Project
    ) {
        val parsed = AgentTaskProtocol.parse(rawContent)
        val finalMessage = placeholder.copy(content = parsed.displayMessage, status = MessageStatus.COMPLETE, modelId = modelId)
        chatRepository.insertMessage(finalMessage)
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { if (it.id == assistantMsgId) finalMessage else it },
                isStreaming = false,
                currentPlanSteps = parsed.envelope?.plan ?: state.currentPlanSteps
            )
        }
        checkpointExecution(
            executionId = executionId,
            phase = AgentTurnExecutionPhase.APPLYING_CHANGES,
            finalRawResponse = rawContent,
            finalDisplayResponse = parsed.displayMessage
        )

        if (!parsed.isTask) {
            clearActions()
            notifyAgentOutcome(success = true, detail = "Assistant response is ready.")
            agentTurnExecutionRepository.markCompleted(executionId, parsed.displayMessage, rawContent)
            agentTurnWorkScheduler.cancel(executionId)
            activeExecutionId = null
            stopAgentForeground()
            return
        }

        val decision = AgentReviewPolicy.decide(
            mode = _uiState.value.autonomyMode,
            writes = parsed.writes,
            deletes = parsed.deletes,
            operations = parsed.operations
        )
        if (decision.requiresReview) {
            _uiState.update {
                it.copy(
                    pendingReview = buildPendingReview(
                        executionId = executionId,
                        sessionId = sessionId,
                        parsed = parsed,
                        visibleUserInput = visibleUserInput,
                        attachedFiles = attachedFiles,
                        repairAttempt = repairAttempt,
                        projectDir = projectDir
                    )
                )
            }
            setActions(
                action(AgentActionType.PLANNING, "Prepared a staged patch for review", ActionStatus.COMPLETED),
                action(AgentActionType.EDITING_FILE, "Waiting for review approval", ActionStatus.IN_PROGRESS)
            )
            notifyAgentOutcome(success = false, detail = "Review approval required before applying staged changes.")
            agentTurnExecutionRepository.markWaitingReview(executionId, parsed.displayMessage, rawContent)
            agentTurnWorkScheduler.cancel(executionId)
            stopAgentForeground()
            return
        }

        applyTurnChanges(
            executionId = executionId,
            owner = owner,
            sessionId = sessionId,
            parsed = parsed,
            visibleUserInput = visibleUserInput,
            attachedFiles = attachedFiles,
            repairAttempt = repairAttempt,
            projectDir = projectDir,
            project = project
        )
    }

    fun approvePendingReview() {
        val pending = _uiState.value.pendingReview ?: return
        viewModelScope.launch {
            val project = currentProject() ?: return@launch
            val executionId = pending.executionId
            val owner = executionOwner ?: "vm:${UUID.randomUUID()}"
            if (executionId != null) {
                checkpointExecution(
                    executionId = executionId,
                    phase = AgentTurnExecutionPhase.APPLYING_CHANGES,
                    status = AgentTurnExecutionStatus.RUNNING,
                    owner = owner
                )
            }
            try {
                applyTurnChanges(
                    executionId = executionId,
                    owner = owner,
                    sessionId = pending.sessionId,
                    parsed = filteredReviewPayload(pending),
                    visibleUserInput = pending.visibleUserInput,
                    attachedFiles = pending.attachedFiles,
                    repairAttempt = pending.repairAttempt,
                    projectDir = File(project.projectPath),
                    project = project
                )
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                finishWithError(pending.sessionId, e.toAgentFailureMessage())
            }
            _uiState.update { it.copy(pendingReview = null) }
        }
    }

    fun rejectPendingReview() {
        val executionId = _uiState.value.pendingReview?.executionId
        _uiState.update { it.copy(pendingReview = null) }
        clearActions()
        if (executionId != null) {
            viewModelScope.launch {
                agentTurnExecutionRepository.markCancelled(executionId, "Review rejected by user")
                agentTurnWorkScheduler.cancel(executionId)
            }
            activeExecutionId = null
        }
    }

    fun toggleReviewHunkAcceptance(hunkId: String) {
        _uiState.update { state ->
            val pending = state.pendingReview ?: return@update state
            state.copy(
                pendingReview = pending.copy(
                    hunks = pending.hunks.map { hunk ->
                        if (hunk.id == hunkId) hunk.copy(accepted = !hunk.accepted) else hunk
                    }
                )
            )
        }
    }

    private fun buildPendingReview(
        executionId: String?,
        sessionId: String,
        parsed: ParsedAgentResponse,
        visibleUserInput: String,
        attachedFiles: List<String>,
        repairAttempt: Int,
        projectDir: File
    ): PendingAgentReview {
        val review = AgentReviewPolicy.decide(
            mode = _uiState.value.autonomyMode,
            writes = parsed.writes,
            deletes = parsed.deletes,
            operations = parsed.operations
        )
        val hunks = buildList {
            parsed.writes.forEach { write ->
                val originalContent = runCatching { FileUtils.readFileContent(projectDir, write.path).orEmpty() }.getOrDefault("")
                add(
                    ReviewHunkPreview(
                        id = "write:${write.path}",
                        filePath = write.path,
                        title = "Write ${write.path}",
                        preview = renderUnifiedPreview(write.path, originalContent, write.content)
                    )
                )
            }
            parsed.deletes.forEach { deletePath ->
                val originalContent = runCatching { FileUtils.readFileContent(projectDir, deletePath).orEmpty() }.getOrDefault("")
                add(
                    ReviewHunkPreview(
                        id = "delete:$deletePath",
                        filePath = deletePath,
                        title = "Delete $deletePath",
                        preview = if (originalContent.isNotBlank()) {
                            renderUnifiedPreview(deletePath, originalContent, "")
                        } else {
                            "This file or directory will be removed from the project."
                        }
                    )
                )
            }
            parsed.operations.forEachIndexed { index, operation ->
                add(
                    ReviewHunkPreview(
                        id = "op:${operation.path}:$index",
                        filePath = operation.path,
                        title = "${operation.kind} on ${operation.path}",
                        preview = listOf(operation.target, operation.payload)
                            .filter { it.isNotBlank() }
                            .joinToString("\n→ ")
                            .take(600)
                    )
                )
            }
        }
        return PendingAgentReview(
            executionId = executionId,
            sessionId = sessionId,
            summary = parsed.displayMessage,
            reasons = review.reasons,
            writes = parsed.writes,
            deletes = parsed.deletes,
            operations = parsed.operations,
            shouldBuild = parsed.shouldBuild,
            visibleUserInput = visibleUserInput,
            attachedFiles = attachedFiles,
            repairAttempt = repairAttempt,
            hunks = hunks
        )
    }

    private fun filteredReviewPayload(pending: PendingAgentReview): ParsedAgentResponse {
        val acceptedIds = pending.hunks.filter { it.accepted }.map { it.id }.toSet()
        val acceptedWrites = pending.writes.filter { "write:${it.path}" in acceptedIds }
        val acceptedDeletes = pending.deletes.filter { "delete:$it" in acceptedIds }
        val acceptedOperations = pending.operations.filterIndexed { index, operation ->
            "op:${operation.path}:$index" in acceptedIds
        }
        val summarySuffix = if (acceptedIds.size == pending.hunks.size) "" else "\n\nReview applied a subset of the staged hunks."
        return ParsedAgentResponse(
            envelope = pending.shouldBuild.let { shouldBuild ->
                com.build.buddyai.core.agent.AgentTaskEnvelope(
                    mode = com.build.buddyai.core.agent.AgentTaskEnvelope.MODE_TASK,
                    summary = pending.summary,
                    shouldBuild = shouldBuild
                )
            },
            plan = null,
            writes = acceptedWrites,
            deletes = acceptedDeletes,
            operations = acceptedOperations,
            displayMessage = pending.summary + summarySuffix,
            rawContent = pending.summary
        )
    }

    private fun renderUnifiedPreview(filePath: String, originalContent: String, modifiedContent: String): String {
        val hunks = textDiffEngine.createHunks(originalContent, modifiedContent, contextLines = 2)
        if (hunks.isEmpty()) return "No textual changes detected."
        val lines = mutableListOf<String>()
        lines += "--- a/$filePath"
        lines += "+++ b/$filePath"
        hunks.forEach { hunk ->
            lines += hunk.header
            hunk.lines.forEach { line ->
                val prefix = when (line.type) {
                    TextDiffEngine.DiffLine.Type.CONTEXT -> " "
                    TextDiffEngine.DiffLine.Type.ADDED -> "+"
                    TextDiffEngine.DiffLine.Type.REMOVED -> "-"
                }
                lines += "$prefix${line.text}"
            }
        }
        return lines.take(MAX_DIFF_PREVIEW_LINES).joinToString("\n")
    }

    private suspend fun applyTurnChanges(
        executionId: String?,
        owner: String,
        sessionId: String,
        parsed: ParsedAgentResponse,
        visibleUserInput: String,
        attachedFiles: List<String>,
        repairAttempt: Int,
        projectDir: File,
        project: com.build.buddyai.core.model.Project
    ) {
        try {
            val effectiveExecutionId = executionId ?: activeExecutionId
            if (effectiveExecutionId != null) {
                checkpointExecution(executionId = effectiveExecutionId, phase = AgentTurnExecutionPhase.APPLYING_CHANGES, owner = owner)
            }
            setActions(
                action(AgentActionType.EDITING_FILE, "Applying planned changes"),
                action(AgentActionType.VERIFYING, if (parsed.shouldBuild) "Running integrity and build validation" else "Running integrity validation")
            )

            val applied = changeSetManager.apply(
                projectId = project.id,
                projectDir = projectDir,
                summary = parsed.displayMessage,
                writes = parsed.writes,
                deletes = parsed.deletes,
                operations = parsed.operations
            )
            val diffs = applied.diffs
            refreshChangeSets()
            _uiState.update { it.copy(recentDiffs = diffs, pendingReview = null) }
            toolMemoryStore.record(project.id, "executor", parsed.displayMessage, diffs.map { it.filePath })

            val integrity = integrityChecker.validate(project, projectDir)
            val integrityProblems = integrity.errors.map { BuildProblem(ProblemSeverity.ERROR, "Integrity error", it) } +
                integrity.warnings.map { BuildProblem(ProblemSeverity.WARNING, "Integrity warning", it) }
            problemsService.replace(project.id, integrityProblems)
            _uiState.update {
                it.copy(
                    integrityWarnings = integrity.warnings,
                    problems = integrityProblems
                )
            }

            if (!integrity.isValid) {
                val signature = failureMemoryStore.recordFailure(
                    projectId = project.id,
                    contextText = integrity.summary(),
                    editedFiles = diffs.map { it.filePath },
                    request = visibleUserInput,
                    memoryContext = buildMemoryContext(project, visibleUserInput)
                )
                setActions(
                    action(AgentActionType.EDITING_FILE, "Applied ${diffs.size} file change(s)", ActionStatus.COMPLETED),
                    action(AgentActionType.VERIFYING, "Integrity validation failed", ActionStatus.FAILED)
                )
                if (repairAttempt < MAX_AUTOMATIC_REPAIR_PASSES) {
                    val nextAttempt = repairAttempt + 1
                    val strategy = repairStrategyForAttempt(nextAttempt)
                    persistSystemMessage(
                        sessionId,
                        "Integrity validation failed. Running automatic repair pass $nextAttempt of $MAX_AUTOMATIC_REPAIR_PASSES using ${strategy.label} strategy."
                    )
                    executeAutonomousTurn(
                        executionId = effectiveExecutionId ?: UUID.randomUUID().toString(),
                        owner = owner,
                        sessionId = sessionId,
                        visibleUserInput = visibleUserInput,
                        attachedFiles = (
                            diffs.map { it.filePath } +
                                attachedFiles +
                                inferPathsFromFailureContext(integrity.summary())
                            ).distinct(),
                        repairAttempt = nextAttempt,
                        repairContext = composeRepairContext(
                            baseContext = integrity.summary(),
                            failureSignature = signature,
                            attemptNumber = nextAttempt,
                            strategy = strategy,
                            failureType = "integrity"
                        )
                    )
                } else {
                    persistSystemMessage(
                        sessionId,
                        "Integrity repair attempts are exhausted after $MAX_AUTOMATIC_REPAIR_PASSES passes. Reply with your priority (fast compile, minimal code churn, or aggressive refactor), and I will continue with that strategy."
                    )
                    if (effectiveExecutionId != null) {
                        agentTurnExecutionRepository.markFailed(
                            effectiveExecutionId,
                            "Integrity repair attempts exhausted after $MAX_AUTOMATIC_REPAIR_PASSES passes."
                        )
                    }
                    notifyAgentOutcome(success = false, detail = "Integrity repair exhausted after $MAX_AUTOMATIC_REPAIR_PASSES passes.")
                    stopAgentForeground()
                }
                return
            }

            if (!parsed.shouldBuild) {
                setActions(action(AgentActionType.VERIFYING, "Integrity validation passed", ActionStatus.COMPLETED))
                val goalGap = detectGoalGap(visibleUserInput, diffs)
                if (goalGap != null && repairAttempt < MAX_AUTOMATIC_REPAIR_PASSES) {
                    val nextAttempt = repairAttempt + 1
                    val strategy = repairStrategyForAttempt(nextAttempt)
                    persistSystemMessage(sessionId, "Code builds, but goal coverage looks incomplete: $goalGap")
                    executeAutonomousTurn(
                        executionId = effectiveExecutionId ?: UUID.randomUUID().toString(),
                        owner = owner,
                        sessionId = sessionId,
                        visibleUserInput = visibleUserInput,
                        attachedFiles = (
                            diffs.map { it.filePath } +
                                attachedFiles +
                                inferPathsFromFailureContext(goalGap)
                            ).distinct(),
                        repairAttempt = nextAttempt,
                        repairContext = composeRepairContext(
                            baseContext = goalGap,
                            failureSignature = "goal_gap_${nextAttempt}",
                            attemptNumber = nextAttempt,
                            strategy = strategy,
                            failureType = "goal-verification"
                        )
                    )
                    return
                }
                if (effectiveExecutionId != null) {
                    agentTurnExecutionRepository.markCompleted(effectiveExecutionId, parsed.displayMessage, parsed.rawContent)
                    agentTurnWorkScheduler.cancel(effectiveExecutionId)
                    activeExecutionId = null
                }
                notifyAgentOutcome(success = true, detail = "Changes applied and integrity checks passed.")
                stopAgentForeground()
                return
            }

            if (effectiveExecutionId != null) {
                checkpointExecution(executionId = effectiveExecutionId, phase = AgentTurnExecutionPhase.VALIDATING)
            }
            val buildProfile = buildProfileManager.loadProfile(project.id)
            val buildOutcome = runValidationBuild(project, buildProfile)
            val combinedProblems = integrityProblems + buildOutcome.problems
            problemsService.replace(project.id, combinedProblems)
            _uiState.update {
                it.copy(
                    lastBuildStatus = buildOutcome.status,
                    lastBuildSummary = buildOutcome.summary,
                    problems = combinedProblems
                )
            }

            if (buildOutcome.status == BuildStatus.SUCCESS) {
                buildOutcome.failureSignature?.let { failureMemoryStore.markResolved(project.id, it, parsed.displayMessage) }
                toolMemoryStore.record(project.id, "build", buildOutcome.summary)
                setActions(
                    action(AgentActionType.EDITING_FILE, "Applied ${diffs.size} file change(s)", ActionStatus.COMPLETED),
                    action(AgentActionType.BUILDING, buildOutcome.summary, ActionStatus.COMPLETED)
                )
                val goalGap = detectGoalGap(visibleUserInput, diffs)
                if (goalGap != null && repairAttempt < MAX_AUTOMATIC_REPAIR_PASSES) {
                    val nextAttempt = repairAttempt + 1
                    val strategy = repairStrategyForAttempt(nextAttempt)
                    persistSystemMessage(sessionId, "Build passed, but goal coverage looks incomplete: $goalGap")
                    executeAutonomousTurn(
                        executionId = effectiveExecutionId ?: UUID.randomUUID().toString(),
                        owner = owner,
                        sessionId = sessionId,
                        visibleUserInput = visibleUserInput,
                        attachedFiles = (
                            diffs.map { it.filePath } +
                                attachedFiles +
                                inferPathsFromFailureContext(goalGap)
                            ).distinct(),
                        repairAttempt = nextAttempt,
                        repairContext = composeRepairContext(
                            baseContext = goalGap,
                            failureSignature = "goal_gap_${nextAttempt}",
                            attemptNumber = nextAttempt,
                            strategy = strategy,
                            failureType = "goal-verification"
                        )
                    )
                    return
                }
                if (effectiveExecutionId != null) {
                    agentTurnExecutionRepository.markCompleted(effectiveExecutionId, parsed.displayMessage, parsed.rawContent)
                    agentTurnWorkScheduler.cancel(effectiveExecutionId)
                    activeExecutionId = null
                }
                notifyAgentOutcome(success = true, detail = buildOutcome.summary)
                stopAgentForeground()
                return
            }

            val failureSignature = failureMemoryStore.recordFailure(
                projectId = project.id,
                contextText = buildOutcome.failureContext ?: buildOutcome.summary,
                editedFiles = diffs.map { it.filePath },
                request = visibleUserInput,
                memoryContext = buildMemoryContext(project, visibleUserInput)
            )
            setActions(
                action(AgentActionType.EDITING_FILE, "Applied ${diffs.size} file change(s)", ActionStatus.COMPLETED),
                action(AgentActionType.BUILDING, buildOutcome.summary, ActionStatus.FAILED),
                action(AgentActionType.ANALYZING_LOGS, "Preparing automatic repair pass", ActionStatus.IN_PROGRESS)
            )

            if (repairAttempt >= MAX_AUTOMATIC_REPAIR_PASSES) {
                persistSystemMessage(
                    sessionId,
                    "Automatic repair attempts are exhausted after $MAX_AUTOMATIC_REPAIR_PASSES passes. Reply with your priority (fast compile, minimal code churn, or aggressive refactor), and I will continue with that strategy."
                )
                if (effectiveExecutionId != null) {
                    agentTurnExecutionRepository.markFailed(
                        effectiveExecutionId,
                        "Automatic repair attempts exhausted after $MAX_AUTOMATIC_REPAIR_PASSES passes."
                    )
                    activeExecutionId = null
                }
                notifyAgentOutcome(success = false, detail = "Automatic repair exhausted after $MAX_AUTOMATIC_REPAIR_PASSES passes.")
                stopAgentForeground()
                return
            }

            val nextAttempt = repairAttempt + 1
            val strategy = repairStrategyForAttempt(nextAttempt)
            persistSystemMessage(
                sessionId,
                "Build validation failed. Running automatic repair pass $nextAttempt of $MAX_AUTOMATIC_REPAIR_PASSES using ${strategy.label} strategy."
            )
            executeAutonomousTurn(
                executionId = effectiveExecutionId ?: UUID.randomUUID().toString(),
                owner = owner,
                sessionId = sessionId,
                visibleUserInput = visibleUserInput,
                attachedFiles = (
                    diffs.map { it.filePath } +
                        attachedFiles +
                        inferPathsFromFailureContext(buildOutcome.failureContext ?: buildOutcome.summary)
                    ).distinct(),
                repairAttempt = nextAttempt,
                repairContext = composeRepairContext(
                    baseContext = buildOutcome.failureContext ?: buildOutcome.summary,
                    failureSignature = failureSignature,
                    attemptNumber = nextAttempt,
                    strategy = strategy,
                    failureType = "build"
                )
            )
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            if (effectiveExecutionId != null) {
                agentTurnExecutionRepository.markFailed(effectiveExecutionId, e.toAgentFailureMessage())
            }
            finishWithError(sessionId, e.toAgentFailureMessage())
        }
    }

    private suspend fun runValidationBuild(project: com.build.buddyai.core.model.Project, buildProfile: BuildProfile): BuildOutcome {
        val buildId = UUID.randomUUID().toString()
        val buildRecord = BuildRecord(
            id = buildId,
            projectId = project.id,
            status = BuildStatus.BUILDING,
            startedAt = System.currentTimeMillis(),
            buildVariant = buildProfile.variant.name.lowercase()
        )
        buildRepository.insertBuildRecord(buildRecord)
        projectRepository.updateProject(project.copy(lastBuildStatus = BuildStatus.BUILDING))

        val logs = mutableListOf<com.build.buddyai.core.model.BuildLogEntry>()
        var successPath: String? = null
        var successSize: Long = 0L
        var failureMessage: String? = null
        var cancelled = false
        val problems = mutableListOf<BuildProblem>()

        setActions(action(AgentActionType.BUILDING, "Running on-device ${buildProfile.variant.displayName.lowercase()} validation build"))

        buildProjectUseCase(project, buildId, buildProfile) { event ->
            when (event) {
                is BuildProjectUseCase.BuildEvent.Progress -> setActions(action(AgentActionType.BUILDING, event.message))
                is BuildProjectUseCase.BuildEvent.Log -> logs += event.entry
                is BuildProjectUseCase.BuildEvent.Warning -> problems += BuildProblem(ProblemSeverity.WARNING, "Build warning", event.message)
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
                val artifact = BuildArtifact(
                    id = UUID.randomUUID().toString(),
                    projectId = project.id,
                    projectName = project.name,
                    buildRecordId = buildId,
                    filePath = successPath!!,
                    fileName = File(successPath!!).name,
                    sizeBytes = successSize,
                    packageName = project.packageName,
                    versionName = buildProfile.versionNameOverride ?: "1.0.0${buildProfile.versionNameSuffix}",
                    versionCode = buildProfile.versionCodeOverride ?: 1,
                    createdAt = completedAt,
                    minSdk = project.minSdk,
                    targetSdk = project.targetSdk
                )
                artifactRepository.insertArtifact(artifact)
                provenanceStore.save(
                    ArtifactProvenance.from(
                        artifactId = artifact.id,
                        artifactPath = artifact.filePath,
                        projectId = project.id,
                        projectName = project.name,
                        buildRecord = successRecord,
                        buildProfile = buildProfile,
                        changeSetIds = changeSetManager.list(project.id).take(10).map { it.id },
                        warnings = problems.filter { it.severity == ProblemSeverity.WARNING }.map { it.detail },
                        problems = problems,
                        timeline = logs.takeLast(30).map { "${it.level.name}: ${it.message}" },
                        templateOrigin = project.template.displayName,
                        validationSummary = if (problems.isEmpty()) "Validation passed" else problems.joinToString(" | ") { it.title }
                    )
                )
                if (buildProfile.installAfterBuild && buildProfile.artifactFormat == com.build.buddyai.core.model.ArtifactFormat.APK) {
                    artifactInstaller.install(context, File(successPath!!))
                }
                BuildOutcome(
                    status = BuildStatus.SUCCESS,
                    summary = "Validation build passed • ${File(successPath!!).name}",
                    failureContext = null,
                    problems = problems,
                    failureSignature = null
                )
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
                    failureContext = failureMessage,
                    problems = problems
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
                val parsedProblems = parseProblems(failureMessage, logs)
                BuildOutcome(
                    status = BuildStatus.FAILED,
                    summary = failureMessage ?: "Validation build failed",
                    failureContext = buildString {
                        appendLine(failureMessage ?: "Build failed")
                        logs.takeLast(80).forEach { appendLine(it.message) }
                    }.trim(),
                    problems = problems + parsedProblems
                )
            }
        }
    }

    private fun parseProblems(failureMessage: String?, logs: List<com.build.buddyai.core.model.BuildLogEntry>): List<BuildProblem> {
        val lines = buildList {
            failureMessage?.lineSequence()?.forEach { add(it) }
            logs.takeLast(80).forEach { add(it.message) }
        }
        val pattern = Regex("""([^:\s]+\.(?:xml|java|kt)):(\d+):\s*error:?\s*(.*)""")
        val matched = lines.mapNotNull { line ->
            val match = pattern.find(line) ?: return@mapNotNull null
            BuildProblem(
                severity = ProblemSeverity.ERROR,
                title = match.groupValues[3].ifBlank { "Build error" },
                detail = line.trim(),
                filePath = match.groupValues[1],
                lineNumber = match.groupValues[2].toIntOrNull()
            )
        }
        return if (matched.isNotEmpty()) matched.distinctBy { listOf(it.filePath, it.lineNumber, it.title) } else lines
            .filter { it.contains("error", ignoreCase = true) }
            .take(10)
            .map { BuildProblem(ProblemSeverity.ERROR, "Build error", it.trim()) }
    }

    fun rollbackLatestChangeSet() {
        viewModelScope.launch {
            val project = currentProject() ?: return@launch
            val latest = _uiState.value.recentChangeSets.firstOrNull() ?: return@launch
            val diffs = changeSetManager.rollback(project.id, latest.id, File(project.projectPath))
            refreshChangeSets()
            _uiState.update { it.copy(recentDiffs = diffs, lastBuildSummary = "Rolled back latest change set") }
        }
    }

    private fun refreshChangeSets() {
        val projectId = currentProjectId ?: return
        _uiState.update { it.copy(recentChangeSets = changeSetManager.list(projectId).take(8)) }
    }

    fun installLatestArtifact(targetContext: Context): BuildArtifactInstaller.InstallResult {
        val artifact = _uiState.value.latestArtifact ?: return BuildArtifactInstaller.InstallResult.Error("No built artifact available")
        return artifactInstaller.install(targetContext, File(artifact.filePath))
    }

    fun shareLatestArtifact(targetContext: Context): Result<Unit> {
        val artifact = _uiState.value.latestArtifact ?: return Result.failure(IllegalStateException("No built artifact available"))
        return artifactInstaller.share(targetContext, File(artifact.filePath))
    }

    fun cancelStream() {
        backgroundExecutionRegistry.cancel(_uiState.value.sessionId)
        streamJob?.cancel()
        val executionId = activeExecutionId
        _uiState.update {
            it.copy(
                isStreaming = false,
                currentActions = emptyList(),
                messages = it.messages.map { message ->
                    if (message.status == MessageStatus.STREAMING) message.copy(status = MessageStatus.CANCELLED, content = message.content.ifBlank { "Cancelled." })
                    else message
                }
            )
        }
        stopAgentForeground()
        notifyAgentOutcome(success = false, detail = "Agent task cancelled.")
        if (executionId != null) {
            viewModelScope.launch {
                agentTurnExecutionRepository.markCancelled(executionId, "Cancelled by user")
                agentTurnWorkScheduler.cancel(executionId)
            }
        }
        activeExecutionId = null
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
        notifyAgentOutcome(success = false, detail = message.take(180))
        stopAgentForeground()
        activeExecutionId?.let { executionId ->
            agentTurnExecutionRepository.markFailed(executionId, message)
            agentTurnWorkScheduler.cancel(executionId)
        }
        activeExecutionId = null
    }

    private fun Throwable.toAgentFailureMessage(): String {
        val detail = message?.trim().orEmpty().ifBlank { this::class.java.simpleName }
        return when (this) {
            is NoSuchFieldError, is LinkageError ->
                "Agent runtime compatibility error ($detail). The operation was stopped safely. Please update to the latest app build."
            else -> detail.ifBlank { "AI task failed" }
        }
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
        return (explicit.coerceAtLeast(contentTokens) + attachmentTokens + 16)
    }

    private fun summarizeTruncatedHistory(messages: List<ChatMessage>): String {
        if (messages.isEmpty()) return ""
        val summaryLines = mutableListOf<String>()
        var consumed = 0
        messages.takeLast(MAX_HISTORY_MESSAGES).forEach { message ->
            val prefix = when (message.role) {
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT -> "Assistant"
                MessageRole.SYSTEM -> "System"
            }
            val line = "$prefix: ${message.content.lineSequence().firstOrNull().orEmpty().trim()}".trim()
            if (line.length < 10) return@forEach
            if (consumed + line.length > 2_400 || summaryLines.size >= MAX_SUMMARY_LINES) return@forEach
            summaryLines += line.take(200)
            consumed += line.length
        }
        return summaryLines.joinToString("\n")
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

    private fun buildRequestMessages(
        systemPrompt: String,
        turnPrompt: String,
        attachedFiles: List<String>,
        modelId: String,
        liveContextWindow: Int? = null
    ): List<AiChatMessage> = buildList {
        add(AiChatMessage(role = "system", text = systemPrompt))

        val historyBudgetTokens = dynamicHistoryTokenBudget(modelId, liveContextWindow)
        val selected = ArrayDeque<ChatMessage>()
        val truncated = ArrayDeque<ChatMessage>()
        var consumedTokens = estimateTokens(systemPrompt) + estimateTokens(turnPrompt)
        var truncating = false
        _uiState.value.messages.asReversed().forEach { message ->
            val estimatedCost = estimateMessageTokens(message)
            if (truncating || selected.size >= MAX_HISTORY_MESSAGES || consumedTokens + estimatedCost > historyBudgetTokens) {
                truncating = true
                truncated.addFirst(message)
                return@forEach
            }
            selected.addFirst(message)
            consumedTokens += estimatedCost
        }

        summarizeTruncatedHistory(truncated.toList()).takeIf { it.isNotBlank() }?.let { summary ->
            add(
                AiChatMessage(
                    role = "system",
                    text = "Conversation summary for earlier turns (compressed to fit context):\n$summary"
                )
            )
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
        appendLine("Use the local symbol index, failure memory, and tool-result memory to avoid brute forcing whole-file context.")
        appendLine("Default behavior: investigate, execute the task directly, apply surgical edits when possible, and validate changes when appropriate.")
        appendLine("Do not ask the user to pick plan/apply/auto modes.")
        appendLine("When the user only wants an explanation or advice, reply normally and do not change files.")
        appendLine("When the user asks for implementation, fixes, refactors, audits, or feature work, emit actionable file edits or structured edit operations.")
        appendLine("Never use placeholders, pseudo-diffs, or partial snippets for files.")
        appendLine("Do not leak internal tooling language into the user app. Never add words like BuildBuddy, starter template, on-device, sandbox, validator, or production-safe to app UI copy, comments, or source unless the user explicitly asked for them.")
        appendLine("If the request is to build or redesign an app, replace scaffold/demo copy with product-specific UX, stronger visual hierarchy, and realistic screen structure instead of leaving a generic starter screen.")
        appendLine()
        if (plan != null) {
            appendLine("Planner goal: ${plan.goal}")
            if (plan.steps.isNotEmpty()) appendLine("Planner steps: ${plan.steps.joinToString(" | ")}")
            if (plan.risks.isNotEmpty()) appendLine("Planner risks: ${plan.risks.joinToString(" | ")}")
            appendLine()
        }
        appendLine(AgentTaskProtocol.protocolInstructions())
        appendLine()
        appendLine("Project context:")
        appendLine(projectContext)
    }

    private fun buildTurnPrompt(visibleUserInput: String, repairContext: String?): String = if (repairContext == null) {
        buildString {
            appendLine(visibleUserInput)
            if (isProductBuildPrompt(visibleUserInput)) {
                appendLine()
                appendLine("Implementation quality bar:")
                appendLine("- Replace starter/demo copy with product-specific text and flow.")
                appendLine("- Build a polished first screen with strong spacing, hierarchy, and realistic actions/states.")
                appendLine("- Do not leave framework/internal wording in the generated app UI.")
                appendLine("- Tailor the layout and copy to the requested product instead of reusing the same generic screen.")
            }
        }.trim()
    } else {
        buildString {
            appendLine("Original user request:")
            appendLine(visibleUserInput)
            appendLine()
            appendLine("The previous automated implementation failed. Repair the project without asking the user to choose a mode.")
            appendLine()
            appendLine("Repair context:")
            appendLine("```text")
            appendLine(repairContext)
            appendLine("```")
        }
    }

    private fun isProductBuildPrompt(input: String): Boolean {
        val normalized = input.lowercase()
        return listOf("build", "create", "make", "generate", "app", "screen", "ui", "ux").count { it in normalized } >= 2
    }

    private fun detectGoalGap(request: String, diffs: List<FileDiff>): String? {
        val normalized = request.lowercase()
        if (normalized.isBlank()) return null
        val changedPaths = diffs.map { it.filePath.lowercase() }

        if ((normalized.contains("persist") || normalized.contains("across restarts")) &&
            changedPaths.none {
                it.contains("datastore") ||
                    it.contains("preferences") ||
                    it.contains("sharedpref") ||
                    it.contains("settings")
            }
        ) {
            return "The request asks for persistence, but no persistence-related file changes were detected."
        }

        if ((normalized.contains("dark mode") || normalized.contains("theme")) &&
            changedPaths.none {
                it.contains("theme") ||
                    it.contains("settings") ||
                    it.contains("style") ||
                    it.contains("colors")
            }
        ) {
            return "The request mentions dark mode/theme behavior, but no theme-related files appear in the patch."
        }

        if ((normalized.contains("toggle") || normalized.contains("switch")) &&
            changedPaths.none {
                it.contains("layout") ||
                    it.contains("screen") ||
                    it.contains("activity") ||
                    it.contains("fragment") ||
                    it.contains("compose")
            }
        ) {
            return "The request expects a toggle control, but no UI surface changes were detected."
        }

        return null
    }

    private fun inferPathsFromFailureContext(contextText: String): List<String> {
        if (contextText.isBlank()) return emptyList()
        val pattern = Regex("""([A-Za-z0-9_./-]+\.(?:kt|java|xml|kts|gradle|properties|pro|json))""")
        return pattern.findAll(contextText)
            .map { it.groupValues[1].trim() }
            .mapNotNull { normalizePathOrNull(it) }
            .distinct()
            .take(24)
    }

    private data class RepairStrategy(val label: String, val instructions: String)

    private fun repairStrategyForAttempt(attemptNumber: Int): RepairStrategy = when (attemptNumber) {
        1 -> RepairStrategy(
            label = "conservative",
            instructions = "Use minimal, surgical edits first. Prioritize obvious compile errors, unresolved symbols, and XML/resource mismatches. Avoid broad refactors."
        )
        2 -> RepairStrategy(
            label = "targeted",
            instructions = "Address dependency chains and related files together. If the same failure persists, change approach from pass 1 and adjust the nearest build/config edges."
        )
        3 -> RepairStrategy(
            label = "structural",
            instructions = "Apply broader structural fixes where needed (API contract alignment, manifest/resource consistency, build config coherence) while preserving behavior."
        )
        else -> RepairStrategy(
            label = "aggressive",
            instructions = "Use decisive recovery: rewrite problematic sections or affected files end-to-end if necessary, then re-validate assumptions against current errors."
        )
    }

    private fun composeRepairContext(
        baseContext: String,
        failureSignature: String,
        attemptNumber: Int,
        strategy: RepairStrategy,
        failureType: String
    ): String = buildString {
        appendLine(baseContext)
        appendLine()
        appendLine("Failure signature: $failureSignature")
        appendLine("Failure type: $failureType")
        appendLine("Autonomous repair pass: $attemptNumber of $MAX_AUTOMATIC_REPAIR_PASSES")
        appendLine("Strategy: ${strategy.label}")
        appendLine("Instructions: ${strategy.instructions}")
        appendLine("Do not repeat the exact same patch pattern from earlier failed passes.")
    }.trim()

    private suspend fun checkpointExecution(
        executionId: String,
        phase: AgentTurnExecutionPhase? = null,
        status: AgentTurnExecutionStatus? = null,
        owner: String? = null,
        partialResponse: String? = null,
        finalRawResponse: String? = null,
        finalDisplayResponse: String? = null,
        scheduleWatchdog: Boolean = true
    ) {
        agentTurnExecutionRepository.update(executionId) { current ->
            current.copy(
                phase = phase?.name ?: current.phase,
                status = status?.name ?: current.status,
                owner = owner ?: current.owner,
                partialResponse = partialResponse ?: current.partialResponse,
                finalRawResponse = finalRawResponse ?: current.finalRawResponse,
                finalDisplayResponse = finalDisplayResponse ?: current.finalDisplayResponse,
                heartbeatAt = System.currentTimeMillis()
            )
        }
        if (scheduleWatchdog) {
            agentTurnWorkScheduler.scheduleWatchdog(executionId)
        }
    }

    private fun resumeCheckpointedExecution(sessionId: String?) {
        if (sessionId.isNullOrBlank()) return
        viewModelScope.launch {
            val execution = agentTurnExecutionRepository.findLatestActiveForSession(sessionId) ?: return@launch
            activeExecutionId = execution.id
            if (execution.status == AgentTurnExecutionStatus.RUNNING.name) {
                _uiState.update { it.copy(isStreaming = true) }
                agentTurnWorkScheduler.scheduleWatchdog(execution.id)
                return@launch
            }
            val waitingForManualReview = execution.status == AgentTurnExecutionStatus.WAITING_REVIEW.name &&
                execution.phase == AgentTurnExecutionPhase.WAITING_FOR_REVIEW.name &&
                !execution.finalRawResponse.isNullOrBlank()
            if (waitingForManualReview) {
                val project = currentProject() ?: return@launch
                val parsed = AgentTaskProtocol.parse(execution.finalRawResponse.orEmpty())
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        pendingReview = buildPendingReview(
                            executionId = execution.id,
                            sessionId = execution.sessionId,
                            parsed = parsed,
                            visibleUserInput = execution.userInput,
                            attachedFiles = agentTurnExecutionRepository.decodeAttachedFiles(execution.attachedFilesJson),
                            repairAttempt = execution.repairAttempt,
                            projectDir = File(project.projectPath)
                        )
                    )
                }
                return@launch
            }
            val readyForForegroundContinuation = execution.status == AgentTurnExecutionStatus.WAITING_REVIEW.name &&
                execution.phase == AgentTurnExecutionPhase.APPLYING_CHANGES.name &&
                !execution.finalRawResponse.isNullOrBlank()
            if (!readyForForegroundContinuation) return@launch

            val project = currentProject() ?: return@launch
            val modelId = execution.modelId ?: providerRepository.getDefaultProvider()?.selectedModelId.orEmpty()
            val assistantId = execution.assistantMessageId ?: UUID.randomUUID().toString()
            val placeholder = _uiState.value.messages.firstOrNull { it.id == assistantId } ?: ChatMessage(
                id = assistantId,
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = execution.partialResponse,
                status = MessageStatus.STREAMING,
                modelId = modelId
            )
            val owner = "vm:${UUID.randomUUID()}"
            executionOwner = owner
            checkpointExecution(
                executionId = execution.id,
                phase = AgentTurnExecutionPhase.APPLYING_CHANGES,
                status = AgentTurnExecutionStatus.RUNNING,
                owner = owner
            )
            handleTurnCompletion(
                executionId = execution.id,
                owner = owner,
                sessionId = sessionId,
                assistantMsgId = assistantId,
                placeholder = placeholder,
                modelId = modelId,
                rawContent = execution.finalRawResponse.orEmpty(),
                visibleUserInput = execution.userInput,
                attachedFiles = agentTurnExecutionRepository.decodeAttachedFiles(execution.attachedFilesJson),
                repairAttempt = execution.repairAttempt,
                projectDir = File(project.projectPath),
                project = project
            )
        }
    }

    private fun startAgentForeground(sessionId: String, status: String) {
        context.startForegroundService(
            Intent(context, AgentForegroundService::class.java).apply {
                putExtra(AgentForegroundService.EXTRA_SESSION_ID, sessionId)
                putExtra(AgentForegroundService.EXTRA_PROJECT_NAME, currentProjectName)
                putExtra(AgentForegroundService.EXTRA_STATUS, status.take(120))
            }
        )
    }

    private fun updateAgentForeground(sessionId: String, status: String) {
        context.startForegroundService(
            Intent(context, AgentForegroundService::class.java).apply {
                action = AgentForegroundService.ACTION_UPDATE
                putExtra(AgentForegroundService.EXTRA_SESSION_ID, sessionId)
                putExtra(AgentForegroundService.EXTRA_PROJECT_NAME, currentProjectName)
                putExtra(AgentForegroundService.EXTRA_STATUS, status.take(120))
            }
        )
    }

    private fun stopAgentForeground() {
        context.stopService(Intent(context, AgentForegroundService::class.java))
    }

    private fun notifyAgentOutcome(success: Boolean, detail: String) {
        if (!notificationsEnabled) return
        executionNotificationManager.notifyAgentOutcome(
            projectName = currentProjectName,
            success = success,
            detail = detail
        )
    }

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
        val sessionId = _uiState.value.sessionId
        if (sessionId != null && backgroundExecutionRegistry.isRunning(sessionId)) {
            updateAgentForeground(
                sessionId = sessionId,
                status = actions.firstOrNull()?.description?.take(100) ?: "Agent is working..."
            )
        }
    }

    private fun clearActions() {
        _uiState.update { it.copy(currentActions = emptyList()) }
    }

    private suspend fun currentProject() = currentProjectId?.let { projectRepository.getProjectById(it) }

    private fun isImageAttachment(path: String): Boolean {
        val extension = path.substringAfterLast('.', "").lowercase()
        return extension in ModelCapabilityRegistry.supportedImageExtensions()
    }

    private fun normalizePathOrNull(path: String): String? =
        try {
            FileUtils.normalizeRelativePath(path)
        } catch (_: IllegalArgumentException) {
            null
        }

    private data class BuildOutcome(
        val status: BuildStatus,
        val summary: String,
        val failureContext: String?,
        val problems: List<BuildProblem> = emptyList(),
        val failureSignature: String? = null
    )
    private fun buildMemoryContext(project: com.build.buddyai.core.model.Project, requestHint: String = ""): ProjectFailureMemoryStore.MemoryContext {
        return ProjectFailureMemoryStore.MemoryContext(
            templateFamily = project.template.name,
            buildEngine = "on-device-aapt2-ecj-d8",
            language = project.language.name,
            requestHint = requestHint
        )
    }
}
