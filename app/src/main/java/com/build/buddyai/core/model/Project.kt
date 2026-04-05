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
    val template: ProjectTemplate = ProjectTemplate.JAVA_DASHBOARD,
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
    VIEWS("Android Views")
}

@Serializable
enum class PreferredBuildEngine(val displayName: String) {
    LEGACY("Legacy on-device"),
    GRADLE("Gradle + Kotlin"),
    AUTO("Auto")
}

@Serializable
enum class ProjectTemplate(
    val displayName: String,
    val description: String,
    val language: ProjectLanguage,
    val uiFramework: UiFramework,
    val preferredBuildEngine: PreferredBuildEngine,
    val validationLabel: String
) {
    BLANK_COMPOSE(
        displayName = "Compose Starter",
        description = "Production-ready Compose starter with edge-to-edge shell and action cards",
        language = ProjectLanguage.KOTLIN,
        uiFramework = UiFramework.COMPOSE,
        preferredBuildEngine = PreferredBuildEngine.GRADLE,
        validationLabel = "Gradle verified"
    ),
    SINGLE_ACTIVITY_COMPOSE(
        displayName = "Compose Dashboard",
        description = "Compose dashboard shell with stats, quick actions, and clean Material 3 layout",
        language = ProjectLanguage.KOTLIN,
        uiFramework = UiFramework.COMPOSE,
        preferredBuildEngine = PreferredBuildEngine.GRADLE,
        validationLabel = "Gradle verified"
    ),
    COMPOSE_SETTINGS(
        displayName = "Compose Settings",
        description = "Compose settings surface with grouped toggles and status cards",
        language = ProjectLanguage.KOTLIN,
        uiFramework = UiFramework.COMPOSE,
        preferredBuildEngine = PreferredBuildEngine.GRADLE,
        validationLabel = "Gradle verified"
    ),
    BLANK_VIEWS(
        displayName = "Views Starter",
        description = "Build-safe Java/XML starter using platform widgets only",
        language = ProjectLanguage.JAVA,
        uiFramework = UiFramework.VIEWS,
        preferredBuildEngine = PreferredBuildEngine.LEGACY,
        validationLabel = "Legacy verified"
    ),
    JAVA_DASHBOARD(
        displayName = "Java Dashboard",
        description = "Modern Java dashboard starter with hero card and action surface",
        language = ProjectLanguage.JAVA,
        uiFramework = UiFramework.VIEWS,
        preferredBuildEngine = PreferredBuildEngine.LEGACY,
        validationLabel = "Legacy verified"
    ),
    BASIC_UTILITY(
        displayName = "Java Utility",
        description = "Input-process-output utility starter with clean result card",
        language = ProjectLanguage.JAVA,
        uiFramework = UiFramework.VIEWS,
        preferredBuildEngine = PreferredBuildEngine.LEGACY,
        validationLabel = "Legacy verified"
    ),
    JAVA_FORM(
        displayName = "Java Form",
        description = "Validated form starter with scrollable sections and submission flow",
        language = ProjectLanguage.JAVA,
        uiFramework = UiFramework.VIEWS,
        preferredBuildEngine = PreferredBuildEngine.LEGACY,
        validationLabel = "Legacy verified"
    ),
    JAVA_LIST_DETAIL(
        displayName = "Java List + Detail",
        description = "List/detail starter with selection state and clean empty panel",
        language = ProjectLanguage.JAVA,
        uiFramework = UiFramework.VIEWS,
        preferredBuildEngine = PreferredBuildEngine.LEGACY,
        validationLabel = "Legacy verified"
    );

    companion object {
        val default: ProjectTemplate = JAVA_DASHBOARD
    }
}

@Serializable
enum class BuildStatus(val displayName: String) {
    NONE("No builds"),
    BUILDING("Building"),
    SUCCESS("Success"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}
