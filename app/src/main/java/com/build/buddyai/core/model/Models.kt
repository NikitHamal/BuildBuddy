package com.build.buddyai.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ProjectLanguage {
    KOTLIN,
    JAVA,
}

enum class UiToolkit {
    COMPOSE,
    XML,
}

enum class ProjectTemplate {
    BLANK_COMPOSE,
    BLANK_VIEWS,
    SINGLE_ACTIVITY_COMPOSE,
    JAVA_ACTIVITY,
    BASIC_UTILITY,
}

enum class SortMode {
    RECENT,
    NAME,
}

enum class ProjectFilter {
    ALL,
    ACTIVE,
    ARCHIVED,
}

enum class BuildMode {
    DEBUG,
    RELEASE,
}

enum class BuildStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

enum class BuildSupportLevel {
    SUPPORTED,
    PARTIAL,
    UNSUPPORTED,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AgentMode {
    CHAT,
    PLAN,
    APPLY,
    AUTO_APPLY,
}

enum class ProviderId {
    OPENROUTER,
    GEMINI,
    NVIDIA,
}

enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

enum class MessageStatus {
    COMPLETE,
    STREAMING,
    ERROR,
    PROPOSED,
}

enum class TimelineStage {
    READING_FILES,
    PLANNING,
    EDITING,
    BUILDING,
    ANALYZING_LOGS,
}

enum class ChangeOperation {
    CREATE,
    UPDATE,
    DELETE,
}

enum class ArtifactType {
    APK,
    ZIP,
}

enum class InstallStatus {
    IDLE,
    REQUESTED,
    INSTALLING,
    SUCCESS,
    FAILED,
}

@Serializable
data class ProjectDraft(
    val name: String = "",
    val packageName: String = "",
    val description: String = "",
    val language: ProjectLanguage = ProjectLanguage.KOTLIN,
    val uiToolkit: UiToolkit = UiToolkit.COMPOSE,
    val minSdk: Int = 27,
    val targetSdk: Int = 35,
    val accentColor: String = "#C58940",
    val template: ProjectTemplate = ProjectTemplate.BLANK_COMPOSE,
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val packageName: String,
    val description: String,
    val language: ProjectLanguage,
    val uiToolkit: UiToolkit,
    val minSdk: Int,
    val targetSdk: Int,
    val accentColor: String,
    val template: ProjectTemplate,
    val workspacePath: String,
    val iconPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long,
    val archived: Boolean,
    val defaultProvider: ProviderId? = null,
    val defaultModel: String? = null,
    val lastBuildStatus: BuildStatus? = null,
)

@Serializable
data class BuildRecord(
    val id: String,
    val projectId: String,
    val mode: BuildMode,
    val status: BuildStatus,
    val supportLevel: BuildSupportLevel,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val summary: String,
    val rawLog: String,
    val diagnostics: List<BuildDiagnostic> = emptyList(),
    val artifactId: String? = null,
)

@Serializable
data class BuildDiagnostic(
    val title: String,
    val detail: String,
    val path: String? = null,
    val line: Int? = null,
)

@Serializable
data class Artifact(
    val id: String,
    val projectId: String,
    val buildId: String?,
    val type: ArtifactType,
    val filePath: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val createdAt: Long,
    val fileSizeBytes: Long,
    val installable: Boolean,
)

@Serializable
data class Snapshot(
    val id: String,
    val projectId: String,
    val label: String,
    val filePath: String,
    val createdAt: Long,
    val reason: String,
)

@Serializable
data class AgentAttachment(
    val path: String,
    val label: String,
)

@Serializable
data class WorkspaceChange(
    val operation: ChangeOperation,
    val path: String,
    val content: String? = null,
    val reason: String,
)

@Serializable
data class AgentTimelineEvent(
    val stage: TimelineStage,
    val label: String,
    val createdAt: Long,
)

@Serializable
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus,
    val timeline: List<AgentTimelineEvent> = emptyList(),
    val attachments: List<AgentAttachment> = emptyList(),
    val proposedChanges: List<WorkspaceChange> = emptyList(),
    val createdAt: Long,
)

@Serializable
data class Conversation(
    val id: String,
    val projectId: String,
    val providerId: ProviderId,
    val modelId: String,
    val mode: AgentMode,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class WorkspaceFile(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val depth: Int,
    val modifiedAt: Long,
)

@Serializable
data class EditorSession(
    val path: String,
    val content: String,
    val languageHint: String,
    val isDirty: Boolean = false,
    val searchQuery: String = "",
    val replaceQuery: String = "",
)

@Serializable
data class AppPreferences(
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val editorFontScaleSp: Int = 14,
    val tabWidth: Int = 4,
    val softWrap: Boolean = true,
    val showLineNumbers: Boolean = true,
    val autosave: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val defaultProvider: ProviderId = ProviderId.OPENROUTER,
    val defaultModel: String = "openai/gpt-4o-mini",
    val defaultBuildMode: BuildMode = BuildMode.DEBUG,
    val toolchainRootOverride: String? = null,
)

@Serializable
data class ProviderSecret(
    val providerId: ProviderId,
    val apiKeyPresent: Boolean,
)

@Serializable
data class ProviderSettings(
    val providerId: ProviderId,
    val selectedModel: String,
    val temperature: Double = 0.2,
    val maxTokens: Int = 4096,
    val topP: Double = 0.95,
    val enabled: Boolean = true,
)

@Serializable
data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val supportsStreaming: Boolean,
    val supportsToolUse: Boolean,
)

@Serializable
data class ProviderConnectionResult(
    val success: Boolean,
    val message: String,
    val models: List<ModelDescriptor> = emptyList(),
)

@Serializable
data class BuildCompatibilityReport(
    val level: BuildSupportLevel,
    val summary: String,
    val diagnostics: List<BuildDiagnostic>,
)

@Serializable
data class ToolchainManifest(
    val version: String,
    val buildExecutable: String,
    val supportsProjectSchema: Int,
    val abi: String? = null,
)

@Serializable
data class WorkspaceManifest(
    val schemaVersion: Int = 1,
    val projectId: String,
    val packageName: String,
    val appName: String,
    val description: String,
    val language: ProjectLanguage,
    val uiToolkit: UiToolkit,
    val template: ProjectTemplate,
    val minSdk: Int,
    val targetSdk: Int,
    val mainActivityPath: String,
    val manifestPath: String,
    val entryResourcePath: String? = null,
    val supportedByBuildBuddy: Boolean = true,
)

@Serializable
data class AgentRequest(
    val project: Project,
    val mode: AgentMode,
    val provider: ProviderId,
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
    val topP: Double,
    val prompt: String,
    val selectedFiles: List<String> = emptyList(),
    val buildContext: BuildRecord? = null,
)

sealed interface AiStreamEvent {
    data class Delta(val chunk: String) : AiStreamEvent
    data class ProposedPatch(val summary: String, val changes: List<WorkspaceChange>) : AiStreamEvent
    data class Completed(val finalMessage: String) : AiStreamEvent
    data class Failed(val reason: String) : AiStreamEvent
}

@Serializable
data class AgentEnvelope(
    val message: String,
    val timeline: List<AgentTimelineEvent> = emptyList(),
    @SerialName("changes")
    val proposedChanges: List<WorkspaceChange> = emptyList(),
)

@Serializable
data class DashboardState(
    val projects: List<Project> = emptyList(),
    val recentBuilds: List<BuildRecord> = emptyList(),
    val search: String = "",
    val sortMode: SortMode = SortMode.RECENT,
    val filter: ProjectFilter = ProjectFilter.ALL,
)

