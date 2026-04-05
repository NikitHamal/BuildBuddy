package com.build.buddyai.feature.agent

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.agent.AgentChangeSetManager
import com.build.buddyai.core.agent.AgentContextAssembler
import com.build.buddyai.core.agent.AgentExecutionPlan
import com.build.buddyai.core.agent.AgentTaskProtocol
import com.build.buddyai.core.agent.ProjectFailureMemoryStore
import com.build.buddyai.core.agent.ProjectIntegrityChecker
import com.build.buddyai.core.agent.AgentToolMemoryStore
import com.build.buddyai.core.common.BuildArtifactInstaller
import com.build.buddyai.core.common.BuildProfileManager
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
import com.build.buddyai.core.model.BuildProblem
import com.build.buddyai.core.model.BuildProfile
import com.build.buddyai.core.model.BuildRecord
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.ChatMessage
import com.build.buddyai.core.model.ChatSession
import com.build.buddyai.core.model.FileDiff
import com.build.buddyai.core.model.MessageRole
import com.build.buddyai.core.model.MessageStatus
import com.build.buddyai.core.model.ProblemSeverity
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
    val recentChangeSets: List<AgentChangeSetManager.ChangeSet> = emptyList(),
    val isStreaming: Boolean = false,
    val hasProvider: Boolean = false,
    val providerName: String? = null,
    val modelName: String? = null,
    val currentActions: List<AgentAction> = emptyList(),
    val currentPlanGoal: String? = null,
    val currentPlanSteps: List<String> = emptyList(),
    val lastBuildStatus: BuildStatus? = null,
    val lastBuildSummary: String? = null,
    val latestArtifact: BuildArtifact? = null,
    val integrityWarnings: List<String> = emptyList(),
    val problems: List<BuildProblem> = emptyList()
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
    private val changeSetManager: AgentChangeSetManager,
    private val failureMemoryStore: ProjectFailureMemoryStore,
    private val toolMemoryStore: AgentToolMemoryStore,
    private val integrityChecker: ProjectIntegrityChecker,
    private val buildProfileManager: BuildProfileManager,
    private val artifactInstaller: BuildArtifactInstaller,
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
        loadProviderState()
        observeSessions(projectId)
        observeArtifacts(projectId)
        refreshChangeSets()
    }

    private fun loadProviderState() {
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
    }

    private fun observeSessions(projectId: String) {
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

    private fun observeArtifacts(projectId: String) {
        artifactsJob?.cancel()
        artifactsJob = viewModelScope.launch {
            artifactRepository.getArtifactsByProject(projectId).collectLatest { artifacts ->
                _uiState.update { it.copy(latestArtifact = artifacts.firstOrNull()) }
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
            chatRepository.insertMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = MessageRole.USER,
                    content = input,
                    attachedFiles = state.attachedFiles
                )
            )
            _uiState.update {
                it.copy(
                    currentInput = "",
                    attachedFiles = emptyList(),
                    isStreaming = true,
                    recentDiffs = emptyList(),
                    lastBuildStatus = null,
                    lastBuildSummary = null,
                    integrityWarnings = emptyList(),
                    problems = emptyList()
                )
            }

            currentProject()?.let { project ->
                val projectDir = File(project.projectPath)
                if (projectDir.exists()) snapshotManager.createSnapshot(project.id, projectDir, "pre_agent")
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
            val contextSnapshot = contextAssembler.assemble(project.id, projectDir, attachedFiles)
            val plan = requestExecutionPlan(
                providerType = provider.type,
                apiKey = apiKey,
                modelId = modelId,
                projectContext = contextSnapshot.prompt,
                userInput = visibleUserInput,
                repairContext = repairContext
            )
            applyPlanUi(plan)
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
            _uiState.update { state -> state.copy(isStreaming = true, messages = state.messages + placeholder) }

            val systemPrompt = buildExecutionSystemPrompt(contextSnapshot.prompt, plan)
            val turnPrompt = buildTurnPrompt(visibleUserInput, repairContext)
            val requestMessages = buildRequestMessages(systemPrompt, turnPrompt)
            val rawContent = streamModelResponse(
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
        } catch (e: Exception) {
            finishWithError(sessionId, e.message ?: "AI task failed")
        }
    }

    private suspend fun requestExecutionPlan(
        providerType: com.build.buddyai.core.model.ProviderType,
        apiKey: String,
        modelId: String,
        projectContext: String,
        userInput: String,
        repairContext: String?
    ): AgentExecutionPlan? {
        val system = buildString {
            appendLine("You are the BuildBuddy planning engine.")
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
                mapOf("role" to "system", "content" to system),
                mapOf("role" to "user", "content" to user)
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
        messages: List<Map<String, String>>,
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
        providerType: com.build.buddyai.core.model.ProviderType,
        apiKey: String,
        modelId: String,
        messages: List<Map<String, String>>,
        assistantMsgId: String,
        placeholder: ChatMessage,
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
                is StreamEvent.Token -> {
                    contentBuilder.append(event.content)
                    val updated = placeholder.copy(content = contentBuilder.toString())
                    _uiState.update { state ->
                        state.copy(messages = state.messages.map { if (it.id == assistantMsgId) updated else it })
                    }
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

        if (!parsed.isTask) {
            clearActions()
            return
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
        _uiState.update { it.copy(recentDiffs = diffs) }
        toolMemoryStore.record(project.id, "executor", parsed.displayMessage, diffs.map { it.filePath })

        val integrity = integrityChecker.validate(project, projectDir)
        val integrityProblems = integrity.errors.map { BuildProblem(ProblemSeverity.ERROR, "Integrity error", it) } +
            integrity.warnings.map { BuildProblem(ProblemSeverity.WARNING, "Integrity warning", it) }
        _uiState.update {
            it.copy(
                integrityWarnings = integrity.warnings,
                problems = integrityProblems
            )
        }

        if (!integrity.isValid) {
            val signature = failureMemoryStore.recordFailure(project.id, integrity.summary(), diffs.map { it.filePath }, visibleUserInput)
            setActions(
                action(AgentActionType.EDITING_FILE, "Applied ${diffs.size} file change(s)", ActionStatus.COMPLETED),
                action(AgentActionType.VERIFYING, "Integrity validation failed", ActionStatus.FAILED)
            )
            if (repairAttempt < 1) {
                persistSystemMessage(sessionId, "Integrity validation failed, so I am running one automatic repair pass now.")
                executeAutonomousTurn(
                    sessionId = sessionId,
                    visibleUserInput = visibleUserInput,
                    attachedFiles = (diffs.map { it.filePath } + attachedFiles).distinct(),
                    repairAttempt = repairAttempt + 1,
                    repairContext = integrity.summary() + "\n\nFailure signature: $signature"
                )
            }
            return
        }

        if (!parsed.shouldBuild) {
            setActions(action(AgentActionType.VERIFYING, "Integrity validation passed", ActionStatus.COMPLETED))
            return
        }

        val buildProfile = buildProfileManager.loadProfile(project.id)
        val buildOutcome = runValidationBuild(project, buildProfile)
        _uiState.update {
            it.copy(
                lastBuildStatus = buildOutcome.status,
                lastBuildSummary = buildOutcome.summary,
                problems = it.problems + buildOutcome.problems
            )
        }

        if (buildOutcome.status == BuildStatus.SUCCESS) {
            buildOutcome.failureSignature?.let { failureMemoryStore.markResolved(project.id, it, parsed.displayMessage) }
            toolMemoryStore.record(project.id, "build", buildOutcome.summary)
            setActions(
                action(AgentActionType.EDITING_FILE, "Applied ${diffs.size} file change(s)", ActionStatus.COMPLETED),
                action(AgentActionType.BUILDING, buildOutcome.summary, ActionStatus.COMPLETED)
            )
            return
        }

        val failureSignature = failureMemoryStore.recordFailure(project.id, buildOutcome.failureContext ?: buildOutcome.summary, diffs.map { it.filePath }, visibleUserInput)
        setActions(
            action(AgentActionType.EDITING_FILE, "Applied ${diffs.size} file change(s)", ActionStatus.COMPLETED),
            action(AgentActionType.BUILDING, buildOutcome.summary, ActionStatus.FAILED),
            action(AgentActionType.ANALYZING_LOGS, "Preparing automatic repair pass", ActionStatus.IN_PROGRESS)
        )

        if (repairAttempt >= 1) {
            persistSystemMessage(sessionId, "Automatic validation failed after the repair pass. Review the latest build logs and problems pane for the remaining issue.")
            return
        }

        persistSystemMessage(sessionId, "Build validation failed, so I am running one automatic repair pass now.")
        executeAutonomousTurn(
            sessionId = sessionId,
            visibleUserInput = visibleUserInput,
            attachedFiles = (diffs.map { it.filePath } + attachedFiles).distinct(),
            repairAttempt = repairAttempt + 1,
            repairContext = (buildOutcome.failureContext ?: buildOutcome.summary) + "\n\nFailure signature: $failureSignature"
        )
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
                if (buildProfile.installAfterBuild) {
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
        streamJob?.cancel()
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
        _uiState.value.messages.takeLast(20).forEach { message ->
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

    private fun buildExecutionSystemPrompt(projectContext: String, plan: AgentExecutionPlan?): String = buildString {
        appendLine("You are BuildBuddy, a production Android builder operating inside a sandboxed project.")
        appendLine("Use the local symbol index, failure memory, and tool-result memory to avoid brute forcing whole-file context.")
        appendLine("Default behavior: investigate, execute the task directly, apply surgical edits when possible, and validate changes when appropriate.")
        appendLine("Do not ask the user to pick plan/apply/auto modes.")
        appendLine("When the user only wants an explanation or advice, reply normally and do not change files.")
        appendLine("When the user asks for implementation, fixes, refactors, audits, or feature work, emit actionable file edits or structured edit operations.")
        appendLine("Never use placeholders, pseudo-diffs, or partial snippets for files.")
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
        visibleUserInput
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

    private data class BuildOutcome(
        val status: BuildStatus,
        val summary: String,
        val failureContext: String?,
        val problems: List<BuildProblem> = emptyList(),
        val failureSignature: String? = null
    )
}
