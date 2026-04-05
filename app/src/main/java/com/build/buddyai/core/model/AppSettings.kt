package com.build.buddyai.core.model

import kotlinx.serialization.Serializable


@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val editorFontSize: Int = 14,
    val editorTabWidth: Int = 4,
    val editorSoftWrap: Boolean = true,
    val editorLineNumbers: Boolean = true,
    val editorAutosave: Boolean = true,
    val buildNotifications: Boolean = true,
    val buildCacheEnabled: Boolean = true,
    val autonomyMode: AgentAutonomyMode = AgentAutonomyMode.AUTONOMOUS_SAFE,
    val onboardingCompleted: Boolean = false,
    val defaultProviderId: String? = null,
    val defaultModelId: String? = null
)

@Serializable
enum class ThemeMode(val displayName: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}
