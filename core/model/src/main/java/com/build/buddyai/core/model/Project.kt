package com.build.buddyai.core.model

import java.util.UUID

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packageName: String,
    val description: String = "",
    val language: ProjectLanguage = ProjectLanguage.KOTLIN,
    val uiFramework: UiFramework = UiFramework.COMPOSE,
    val template: ProjectTemplate = ProjectTemplate.BLANK_COMPOSE,
    val minSdk: Int = 26,
    val targetSdk: Int = 35,
    val iconUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastBuildAt: Long? = null,
    val lastBuildStatus: BuildStatus? = null,
    val isArchived: Boolean = false,
    val modelOverrideId: String? = null,
    val projectPath: String = ""
)

enum class ProjectLanguage(val displayName: String, val extension: String) {
    KOTLIN("Kotlin", "kt"),
    JAVA("Java", "java")
}

enum class UiFramework(val displayName: String) {
    COMPOSE("Jetpack Compose"),
    XML_VIEWS("XML Views")
}

enum class ProjectTemplate(
    val displayName: String,
    val description: String,
    val language: ProjectLanguage,
    val uiFramework: UiFramework
) {
    BLANK_COMPOSE("Blank Compose App", "Empty Jetpack Compose project with Material 3", ProjectLanguage.KOTLIN, UiFramework.COMPOSE),
    BLANK_VIEWS("Blank Views App", "Empty project with XML layouts and ViewBinding", ProjectLanguage.KOTLIN, UiFramework.XML_VIEWS),
    SINGLE_ACTIVITY_COMPOSE("Single Activity Compose", "Single activity with Compose navigation and scaffold", ProjectLanguage.KOTLIN, UiFramework.COMPOSE),
    JAVA_ACTIVITY("Java Activity App", "Classic Java activity with XML layouts", ProjectLanguage.JAVA, UiFramework.XML_VIEWS),
    BASIC_UTILITY("Basic Utility", "Minimal utility app template", ProjectLanguage.KOTLIN, UiFramework.COMPOSE)
}
