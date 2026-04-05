package com.build.buddyai.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String,
    val packageName: String,
    val description: String = "",
    val language: ProjectLanguage = ProjectLanguage.JAVA,
    val uiFramework: UiFramework = UiFramework.VIEWS,
    val template: ProjectTemplate = ProjectTemplate.BLANK_JAVA_VIEWS,
    val minSdk: Int = 26,
    val targetSdk: Int = 35,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastBuildStatus: BuildStatus = BuildStatus.NONE,
    val lastBuildAt: Long? = null,
    val projectPath: String = "",
    val iconUri: String? = null
)

@Serializable
enum class ProjectLanguage(val displayName: String, val extension: String) {
    KOTLIN("Kotlin", "kt"),
    JAVA("Java", "java")
}

@Serializable
enum class UiFramework(val displayName: String) {
    COMPOSE("Jetpack Compose"),
    VIEWS("XML Views")
}

@Serializable
enum class ProjectTemplate(
    val displayName: String,
    val description: String,
    val language: ProjectLanguage,
    val uiFramework: UiFramework
) {
    BLANK_COMPOSE("Blank Compose App", "Empty Jetpack Compose project with Material 3", ProjectLanguage.KOTLIN, UiFramework.COMPOSE),
    SINGLE_ACTIVITY_COMPOSE("Single Activity Compose", "Compose project with navigation and scaffold", ProjectLanguage.KOTLIN, UiFramework.COMPOSE),

    BLANK_JAVA_VIEWS("Blank Java App", "Production-safe Java/XML starter for on-device builds", ProjectLanguage.JAVA, UiFramework.VIEWS),
    BLANK_KOTLIN_VIEWS("Blank Kotlin App", "Lean Kotlin/XML starter selected by default for Kotlin", ProjectLanguage.KOTLIN, UiFramework.VIEWS),
    JAVA_DASHBOARD("Java Dashboard App", "Analytics-style dashboard with cards and actions", ProjectLanguage.JAVA, UiFramework.VIEWS),
    JAVA_FORM("Java Form App", "Structured form workflow with validation-ready layout", ProjectLanguage.JAVA, UiFramework.VIEWS),
    JAVA_MASTER_DETAIL("Java Master Detail", "Two-pane-ready list/detail starter using framework views", ProjectLanguage.JAVA, UiFramework.VIEWS),

    BLANK_VIEWS("Legacy Blank Views", "Backwards-compatible Java XML template", ProjectLanguage.JAVA, UiFramework.VIEWS),
    JAVA_ACTIVITY("Legacy Java Activity", "Backwards-compatible Java activity template", ProjectLanguage.JAVA, UiFramework.VIEWS),
    BASIC_UTILITY("Legacy Utility App", "Backwards-compatible minimal utility template", ProjectLanguage.JAVA, UiFramework.VIEWS)
}

@Serializable
enum class BuildStatus(val displayName: String) {
    NONE("No builds"),
    BUILDING("Building"),
    SUCCESS("Success"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}
