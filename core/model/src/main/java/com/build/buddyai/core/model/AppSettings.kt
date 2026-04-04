package com.build.buddyai.core.model

data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val editorSettings: EditorSettings = EditorSettings(),
    val aiSettings: AiSettings = AiSettings(),
    val buildSettings: BuildSettings = BuildSettings(),
    val notificationSettings: NotificationSettings = NotificationSettings(),
    val privacySettings: PrivacySettings = PrivacySettings()
)

enum class AppTheme(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System")
}

data class EditorSettings(
    val fontSize: Int = 14,
    val tabWidth: Int = 4,
    val softWrap: Boolean = true,
    val showLineNumbers: Boolean = true,
    val autoSave: Boolean = true,
    val autoSaveIntervalSeconds: Int = 30,
    val highlightCurrentLine: Boolean = true,
    val fontFamily: String = "JetBrains Mono"
)

data class AiSettings(
    val defaultProviderId: String? = null,
    val defaultModelId: String? = null,
    val parameters: ModelParameters = ModelParameters(),
    val autoApplyMode: Boolean = false,
    val showTokenCount: Boolean = true,
    val streamResponses: Boolean = true
)

data class BuildSettings(
    val autoBuildOnSave: Boolean = false,
    val showNotificationOnComplete: Boolean = true,
    val cleanBeforeBuild: Boolean = false,
    val parallelExecution: Boolean = true
)

data class NotificationSettings(
    val buildComplete: Boolean = true,
    val buildFailed: Boolean = true,
    val aiTaskComplete: Boolean = false
)

data class PrivacySettings(
    val analyticsEnabled: Boolean = false,
    val crashReportingEnabled: Boolean = false
)
