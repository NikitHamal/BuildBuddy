package com.build.buddyai.feature.build

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.agent.AgentChangeSetManager
import com.build.buddyai.core.agent.ProjectDiagnosticsEngine
import com.build.buddyai.core.common.ArtifactProvenance
import com.build.buddyai.core.common.ArtifactProvenanceStore
import com.build.buddyai.core.common.BuildArtifactInstaller
import com.build.buddyai.core.common.BuildCancellationRegistry
import com.build.buddyai.core.common.BuildForegroundService
import com.build.buddyai.core.common.BuildProfileManager
import com.build.buddyai.core.common.SigningAuditEntry
import com.build.buddyai.core.common.SigningAuditStore
import com.build.buddyai.core.common.SnapshotManager
import com.build.buddyai.core.data.repository.ArtifactRepository
import com.build.buddyai.core.data.repository.BuildRepository
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.ArtifactFormat
import com.build.buddyai.core.model.BuildArtifact
import com.build.buddyai.core.model.BuildLogEntry
import com.build.buddyai.core.model.BuildProblem
import com.build.buddyai.core.model.BuildProfile
import com.build.buddyai.core.model.BuildRecord
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.BuildTimelineEntry
import com.build.buddyai.core.model.BuildVariant
import com.build.buddyai.core.model.LogLevel
import com.build.buddyai.core.model.ProblemSeverity
import com.build.buddyai.core.model.RestorePoint
import com.build.buddyai.core.problems.ProjectProblemsService
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

data class BuildUiState(
    val isBuilding: Boolean = false,
    val buildProgress: Float = 0f,
    val buildStatus: BuildStatus = BuildStatus.NONE,
    val statusMessage: String = "",
    val logEntries: List<BuildLogEntry> = emptyList(),
    val errorSummary: String? = null,
    val buildHistory: List<BuildRecord> = emptyList(),
    val currentBuildId: String? = null,
    val lastSuccessfulBuild: BuildRecord? = null,
    val compatibilityWarnings: List<String> = emptyList(),
    val buildProfile: BuildProfile = BuildProfile(),
    val problems: List<BuildProblem> = emptyList(),
    val timeline: List<BuildTimelineEntry> = emptyList(),
    val latestArtifact: BuildArtifact? = null,
    val artifactProvenance: ArtifactProvenance? = null,
    val restorePoints: List<RestorePoint> = emptyList(),
    val signingAudit: List<SigningAuditEntry> = emptyList()
)

@HiltViewModel
class BuildViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val buildRepository: BuildRepository,
    private val artifactRepository: ArtifactRepository,
    private val buildProjectUseCase: BuildProjectUseCase,
    private val buildCancellationRegistry: BuildCancellationRegistry,
    private val buildProfileManager: BuildProfileManager,
    private val snapshotManager: SnapshotManager,
    private val buildArtifactInstaller: BuildArtifactInstaller,
    private val problemsService: ProjectProblemsService,
    private val provenanceStore: ArtifactProvenanceStore,
    private val changeSetManager: AgentChangeSetManager,
    private val diagnosticsEngine: ProjectDiagnosticsEngine,
    private val signingAuditStore: SigningAuditStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    private var currentProjectId: String? = null
    private var buildJob: Job? = null
    private var buildHistoryJob: Job? = null
    private var artifactsJob: Job? = null
    private var problemsJob: Job? = null

    fun initialize(projectId: String) {
        currentProjectId = projectId
        _uiState.update {
            it.copy(
                buildProfile = buildProfileManager.loadProfile(projectId),
                signingAudit = signingAuditStore.load(projectId),
                restorePoints = snapshotManager.listSnapshots(projectId).map { info ->
                    RestorePoint(
                        id = info.path,
                        fileName = info.fileName,
                        path = info.path,
                        sizeBytes = info.sizeBytes,
                        createdAt = info.createdAt,
                        label = info.label
                    )
                }
            )
        }
        observeBuildHistory(projectId)
        observeArtifacts(projectId)
        observeProblems(projectId)
    }

    private fun observeBuildHistory(projectId: String) {
        buildHistoryJob?.cancel()
        buildHistoryJob = viewModelScope.launch {
            buildRepository.getBuildRecordsByProject(projectId).collectLatest { records ->
                _uiState.update {
                    it.copy(
                        buildHistory = records,
                        lastSuccessfulBuild = records.firstOrNull { record -> record.status == BuildStatus.SUCCESS }
                    )
                }
            }
        }
    }

    private fun observeArtifacts(projectId: String) {
        artifactsJob?.cancel()
        artifactsJob = viewModelScope.launch {
            artifactRepository.getArtifactsByProject(projectId).collectLatest { artifacts ->
                val latest = artifacts.firstOrNull()
                _uiState.update {
                    it.copy(
                        latestArtifact = latest,
                        artifactProvenance = latest?.let { artifact -> provenanceStore.load(artifact.id) }
                    )
                }
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

    fun updateVariant(variant: BuildVariant) = updateProfile { copy(variant = variant) }
    fun updateArtifactFormat(format: ArtifactFormat) = updateProfile { copy(artifactFormat = format) }
    fun updateInstallAfterBuild(enabled: Boolean) = updateProfile { copy(installAfterBuild = enabled) }
    fun updateFlavorName(name: String) = updateProfile { copy(flavorName = name.trim()) }
    fun updateApplicationIdSuffix(value: String) = updateProfile { copy(applicationIdSuffix = value.trim()) }
    fun updateVersionNameSuffix(value: String) = updateProfile { copy(versionNameSuffix = value.trim()) }
    fun updateVersionNameOverride(value: String) = updateProfile { copy(versionNameOverride = value.trim().ifBlank { null }) }
    fun updateVersionCodeOverride(value: String) = updateProfile { copy(versionCodeOverride = value.trim().toIntOrNull()) }

    fun updateManifestPlaceholders(raw: String) {
        val parsed = raw.lineSequence()
            .map { it.trim() }
            .filter { it.contains('=') }
            .associate {
                val key = it.substringBefore('=').trim()
                val value = it.substringAfter('=').trim()
                key to value
            }
            .filterKeys { it.isNotBlank() }
        updateProfile { copy(manifestPlaceholders = parsed) }
    }

    fun updateSigningAlias(alias: String) {
        val projectId = currentProjectId ?: return
        val currentSigning = _uiState.value.buildProfile.signing ?: return
        val updated = _uiState.value.buildProfile.copy(signing = currentSigning.copy(keyAlias = alias.trim()))
        saveProfile(projectId, updated)
    }

    fun importKeystore(uri: Uri, displayName: String?, keyAlias: String, storePassword: String, keyPassword: String) {
        val projectId = currentProjectId ?: return
        viewModelScope.launch {
            runCatching {
                val signingConfig = buildProfileManager.importKeystore(projectId, uri, displayName).copy(keyAlias = keyAlias.trim())
                val updated = _uiState.value.buildProfile.copy(signing = signingConfig)
                saveProfile(projectId, updated, storePassword, keyPassword)
                _uiState.update { it.copy(signingAudit = signingAuditStore.load(projectId)) }
                addTimeline("Signing", "Imported ${signingConfig.keystoreFileName}")
            }.onFailure { error ->
                addTimeline("Signing", error.message ?: "Failed to import keystore", isError = true)
                _uiState.update { it.copy(errorSummary = error.message ?: "Failed to import keystore") }
            }
        }
    }

    fun clearSigning() {
        val projectId = currentProjectId ?: return
        buildProfileManager.clearSigning(projectId)
        _uiState.update { it.copy(buildProfile = buildProfileManager.loadProfile(projectId), signingAudit = signingAuditStore.load(projectId)) }
    }

    fun startBuild() {
        val projectId = currentProjectId ?: return
        if (_uiState.value.isBuilding) return

        buildJob = viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId) ?: return@launch
            val buildId = UUID.randomUUID().toString()
            val buildProfile = buildProfileManager.loadProfile(projectId)
            val preflightProblems = diagnosticsEngine.scan(project, File(project.projectPath), buildProfile)
            if (preflightProblems.any { it.severity == ProblemSeverity.ERROR }) {
                problemsService.replace(projectId, preflightProblems)
                _uiState.update {
                    it.copy(
                        buildStatus = BuildStatus.FAILED,
                        statusMessage = "Pre-build validation failed",
                        errorSummary = preflightProblems.joinToString(" | ") { problem -> problem.title },
                        problems = preflightProblems,
                        buildProfile = buildProfile
                    )
                }
                addTimeline("Preflight", "Blocked by diagnostics: ${preflightProblems.first().title}", isError = true)
                return@launch
            }

            val snapshot = snapshotManager.createSnapshot(projectId, File(project.projectPath), "pre_build")
            refreshRestorePoints()
            problemsService.clear(projectId)

            _uiState.update {
                it.copy(
                    isBuilding = true,
                    buildProgress = 0f,
                    buildStatus = BuildStatus.BUILDING,
                    statusMessage = "Preparing on-device build…",
                    logEntries = emptyList(),
                    errorSummary = null,
                    currentBuildId = buildId,
                    compatibilityWarnings = emptyList(),
                    buildProfile = buildProfile,
                    problems = emptyList(),
                    timeline = listOf(
                        BuildTimelineEntry(label = "Restore point", detail = "Created ${snapshot.name}", status = com.build.buddyai.core.model.ActionStatus.COMPLETED),
                        BuildTimelineEntry(label = "Build", detail = "Starting ${buildProfile.variant.displayName.lowercase()} ${buildProfile.artifactFormat.displayName} build", status = com.build.buddyai.core.model.ActionStatus.IN_PROGRESS)
                    )
                )
            }

            context.startForegroundService(
                Intent(context, BuildForegroundService::class.java).apply {
                    putExtra(BuildForegroundService.EXTRA_BUILD_ID, buildId)
                    putExtra(BuildForegroundService.EXTRA_PROJECT_NAME, project.name)
                }
            )

            val buildRecord = BuildRecord(
                id = buildId,
                projectId = projectId,
                status = BuildStatus.BUILDING,
                startedAt = System.currentTimeMillis(),
                buildVariant = buildProfile.variant.name.lowercase()
            )
            buildRepository.insertBuildRecord(buildRecord)
            projectRepository.updateProject(project.copy(lastBuildStatus = BuildStatus.BUILDING))

            buildProjectUseCase(project, buildId, buildProfile) { event ->
                when (event) {
                    is BuildProjectUseCase.BuildEvent.Progress -> {
                        _uiState.update { state -> state.copy(buildProgress = event.progress, statusMessage = event.message) }
                        addTimeline("Progress", event.message)
                    }
                    is BuildProjectUseCase.BuildEvent.Log -> {
                        _uiState.update { state -> state.copy(logEntries = state.logEntries + event.entry) }
                    }
                    is BuildProjectUseCase.BuildEvent.Warning -> {
                        val updatedProblems = (_uiState.value.problems + BuildProblem(ProblemSeverity.WARNING, "Build warning", event.message))
                            .distinctBy { listOf(it.title, it.detail, it.filePath, it.lineNumber) }
                        problemsService.replace(projectId, updatedProblems)
                        _uiState.update { state ->
                            state.copy(
                                compatibilityWarnings = state.compatibilityWarnings + event.message,
                                problems = updatedProblems
                            )
                        }
                    }
                    is BuildProjectUseCase.BuildEvent.Success -> {
                        val completedAt = System.currentTimeMillis()
                        val completedRecord = buildRecord.copy(
                            status = BuildStatus.SUCCESS,
                            completedAt = completedAt,
                            durationMs = completedAt - buildRecord.startedAt,
                            artifactPath = event.artifactPath,
                            artifactSizeBytes = event.artifactSize,
                            logEntries = _uiState.value.logEntries
                        )
                        buildRepository.updateBuildRecord(completedRecord)
                        projectRepository.updateProject(project.copy(lastBuildStatus = BuildStatus.SUCCESS, lastBuildAt = completedAt))
                        val artifact = BuildArtifact(
                            id = UUID.randomUUID().toString(),
                            projectId = projectId,
                            projectName = project.name,
                            buildRecordId = buildId,
                            filePath = event.artifactPath,
                            fileName = File(event.artifactPath).name,
                            sizeBytes = event.artifactSize,
                            packageName = project.packageName,
                            versionName = buildProfile.versionNameOverride ?: "1.0.0${buildProfile.versionNameSuffix}",
                            versionCode = buildProfile.versionCodeOverride ?: 1,
                            createdAt = completedAt,
                            minSdk = project.minSdk,
                            targetSdk = project.targetSdk
                        )
                        artifactRepository.insertArtifact(artifact)
                        val finalProblems = diagnosticsEngine.scan(project, File(project.projectPath), buildProfile, _uiState.value.logEntries)
                        problemsService.replace(projectId, finalProblems)
                        val provenance = ArtifactProvenance.from(
                            artifactId = artifact.id,
                            artifactPath = artifact.filePath,
                            projectId = projectId,
                            projectName = project.name,
                            buildRecord = completedRecord,
                            buildProfile = buildProfile,
                            changeSetIds = changeSetManager.list(projectId).take(10).map { it.id },
                            warnings = _uiState.value.compatibilityWarnings,
                            problems = finalProblems,
                            timeline = _uiState.value.timeline.map { "${it.label}: ${it.detail}" },
                            templateOrigin = project.template.displayName,
                            validationSummary = finalProblems.joinToString(" | ") { it.title }.ifBlank { "Validation passed" }
                        )
                        provenanceStore.save(provenance)
                        if (buildProfile.variant == BuildVariant.RELEASE) {
                            signingAuditStore.record(
                                SigningAuditEntry(
                                    projectId = projectId,
                                    eventType = "RELEASE_EXPORT_SUCCESS",
                                    detail = "Produced ${artifact.fileName} with ${buildProfile.artifactFormat.displayName} output.",
                                    signerAlias = buildProfile.signing?.keyAlias?.takeIf { it.isNotBlank() },
                                    variant = buildProfile.variant.name,
                                    artifactFormat = buildProfile.artifactFormat.name
                                )
                            )
                        }
                        _uiState.update {
                            it.copy(
                                isBuilding = false,
                                buildProgress = 1f,
                                buildStatus = BuildStatus.SUCCESS,
                                statusMessage = "Build successful",
                                latestArtifact = artifact,
                                artifactProvenance = provenance,
                                problems = finalProblems,
                                signingAudit = signingAuditStore.load(projectId)
                            )
                        }
                        addTimeline("Artifact", "Built ${artifact.fileName}")
                        if (buildProfile.installAfterBuild && buildProfile.artifactFormat == ArtifactFormat.APK) {
                            when (val result = buildArtifactInstaller.install(context, File(artifact.filePath))) {
                                is BuildArtifactInstaller.InstallResult.Started -> addTimeline("Install", "Installer opened")
                                is BuildArtifactInstaller.InstallResult.PermissionRequired -> addTimeline("Install", result.message, isError = true)
                                is BuildArtifactInstaller.InstallResult.Error -> addTimeline("Install", result.message, isError = true)
                            }
                        }
                    }
                    is BuildProjectUseCase.BuildEvent.Failure -> {
                        val completedAt = System.currentTimeMillis()
                        val failedRecord = buildRecord.copy(
                            status = BuildStatus.FAILED,
                            completedAt = completedAt,
                            durationMs = completedAt - buildRecord.startedAt,
                            errorSummary = event.error,
                            logEntries = _uiState.value.logEntries
                        )
                        buildRepository.updateBuildRecord(failedRecord)
                        projectRepository.updateProject(project.copy(lastBuildStatus = BuildStatus.FAILED, lastBuildAt = completedAt))
                        val parsedProblems = parseProblems(event.error, _uiState.value.logEntries)
                        val diagnosticsProblems = diagnosticsEngine.scan(project, File(project.projectPath), buildProfile, _uiState.value.logEntries)
                        val combinedProblems = (parsedProblems + diagnosticsProblems).distinctBy { listOf(it.title, it.detail, it.filePath, it.lineNumber) }
                        problemsService.replace(projectId, combinedProblems)
                        if (buildProfile.variant == BuildVariant.RELEASE) {
                            signingAuditStore.record(
                                SigningAuditEntry(
                                    projectId = projectId,
                                    eventType = "RELEASE_EXPORT_FAILED",
                                    detail = event.error.take(240),
                                    signerAlias = buildProfile.signing?.keyAlias?.takeIf { it.isNotBlank() },
                                    variant = buildProfile.variant.name,
                                    artifactFormat = buildProfile.artifactFormat.name
                                )
                            )
                        }
                        _uiState.update {
                            it.copy(
                                isBuilding = false,
                                buildStatus = BuildStatus.FAILED,
                                statusMessage = "Build failed",
                                errorSummary = event.error,
                                problems = combinedProblems,
                                signingAudit = signingAuditStore.load(projectId)
                            )
                        }
                        addTimeline("Build", event.error, isError = true)
                    }
                    is BuildProjectUseCase.BuildEvent.Cancelled -> {
                        val completedAt = System.currentTimeMillis()
                        val cancelledRecord = buildRecord.copy(
                            status = BuildStatus.CANCELLED,
                            completedAt = completedAt,
                            durationMs = completedAt - buildRecord.startedAt,
                            errorSummary = event.message,
                            logEntries = _uiState.value.logEntries
                        )
                        buildRepository.updateBuildRecord(cancelledRecord)
                        projectRepository.updateProject(project.copy(lastBuildStatus = BuildStatus.CANCELLED, lastBuildAt = completedAt))
                        _uiState.update {
                            it.copy(isBuilding = false, buildStatus = BuildStatus.CANCELLED, statusMessage = event.message, errorSummary = null)
                        }
                        addTimeline("Build", event.message, isError = true)
                    }
                }
            }

            context.stopService(Intent(context, BuildForegroundService::class.java))
        }
    }

    fun cancelBuild() {
        val buildId = _uiState.value.currentBuildId ?: return
        buildCancellationRegistry.cancelBuild(buildId)
        buildJob?.cancel()
        _uiState.update {
            it.copy(isBuilding = false, buildStatus = BuildStatus.CANCELLED, statusMessage = "Build cancelled")
        }
        addTimeline("Build", "Build cancelled", isError = true)
        context.stopService(Intent(context, BuildForegroundService::class.java))
    }

    fun cleanBuild() {
        viewModelScope.launch {
            val project = currentProjectId?.let { projectRepository.getProjectById(it) } ?: return@launch
            File(project.projectPath, "build").deleteRecursively()
            File(project.projectPath, ".build").deleteRecursively()
            addLogEntry(LogLevel.INFO, "Build outputs cleaned")
            addTimeline("Workspace", "Deleted build outputs")
        }
    }

    fun installLatestArtifact(): BuildArtifactInstaller.InstallResult {
        val artifact = _uiState.value.latestArtifact ?: return BuildArtifactInstaller.InstallResult.Error("No artifact available")
        return buildArtifactInstaller.install(context, File(artifact.filePath))
    }

    fun shareLatestArtifact(): Result<Unit> {
        val artifact = _uiState.value.latestArtifact ?: return Result.failure(IllegalStateException("No artifact available"))
        return buildArtifactInstaller.share(context, File(artifact.filePath))
    }

    fun restorePoint(restorePoint: RestorePoint) {
        val projectId = currentProjectId ?: return
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId) ?: return@launch
            snapshotManager.restoreSnapshot(projectId, restorePoint.path, File(project.projectPath))
            refreshRestorePoints()
            addTimeline("Restore", "Restored ${restorePoint.fileName}")
        }
    }

    fun deleteRestorePoint(restorePoint: RestorePoint) {
        snapshotManager.deleteSnapshot(restorePoint.path)
        refreshRestorePoints()
    }

    private fun refreshRestorePoints() {
        val projectId = currentProjectId ?: return
        _uiState.update {
            it.copy(
                restorePoints = snapshotManager.listSnapshots(projectId).map { info ->
                    RestorePoint(
                        id = info.path,
                        fileName = info.fileName,
                        path = info.path,
                        sizeBytes = info.sizeBytes,
                        createdAt = info.createdAt,
                        label = info.label
                    )
                }
            )
        }
    }

    private fun updateProfile(transform: BuildProfile.() -> BuildProfile) {
        val projectId = currentProjectId ?: return
        saveProfile(projectId, _uiState.value.buildProfile.transform())
    }

    private fun saveProfile(
        projectId: String,
        profile: BuildProfile,
        storePassword: String? = null,
        keyPassword: String? = null
    ) {
        buildProfileManager.saveProfile(projectId, profile, storePassword, keyPassword)
        _uiState.update {
            it.copy(
                buildProfile = buildProfileManager.loadProfile(projectId),
                signingAudit = signingAuditStore.load(projectId)
            )
        }
    }

    private fun parseProblems(failureMessage: String?, logs: List<BuildLogEntry>): List<BuildProblem> {
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
        return if (matched.isNotEmpty()) {
            matched.distinctBy { listOf(it.filePath, it.lineNumber, it.title) }
        } else {
            lines.filter { it.contains("error", ignoreCase = true) }
                .take(10)
                .map { BuildProblem(ProblemSeverity.ERROR, "Build error", it.trim()) }
        }
    }

    private fun addLogEntry(level: LogLevel, message: String) {
        _uiState.update {
            it.copy(logEntries = it.logEntries + BuildLogEntry(System.currentTimeMillis(), level, message))
        }
    }

    private fun addTimeline(label: String, detail: String, isError: Boolean = false) {
        _uiState.update {
            it.copy(
                timeline = (
                    listOf(
                        BuildTimelineEntry(
                            label = label,
                            detail = detail,
                            status = if (isError) com.build.buddyai.core.model.ActionStatus.FAILED else com.build.buddyai.core.model.ActionStatus.COMPLETED
                        )
                    ) + it.timeline
                ).take(30)
            )
        }
    }
}
