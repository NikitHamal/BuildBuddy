package com.build.buddyai.core.model

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class Project(
    val id: String,
    val name: String,
    val packageName: String,
    val description: String = "",
    val language: ProjectLanguage = ProjectLanguage.JAVA,
    val uiFramework: UiFramework = UiFramework.VIEWS,
    val template: ProjectTemplate = ProjectTemplate.JAVA_ACTIVITY,
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
    BLANK_VIEWS("Blank Views App", "Empty project with XML layouts and View system", ProjectLanguage.JAVA, UiFramework.VIEWS),
    SINGLE_ACTIVITY_COMPOSE("Single Activity Compose", "Compose project with navigation and scaffold", ProjectLanguage.KOTLIN, UiFramework.COMPOSE),
    JAVA_ACTIVITY("Java Activity App", "Basic Java project with AppCompatActivity", ProjectLanguage.JAVA, UiFramework.VIEWS),
    BASIC_UTILITY("Basic Utility App", "Minimal utility app template", ProjectLanguage.JAVA, UiFramework.VIEWS)
}

@Serializable
enum class BuildStatus(val displayName: String) {
    NONE("No builds"),
    BUILDING("Building"),
    SUCCESS("Success"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}
