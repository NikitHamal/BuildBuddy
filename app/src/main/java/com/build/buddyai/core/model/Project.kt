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
    val template: ProjectTemplate = ProjectTemplate.BLANK_VIEWS,
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
    LEGACY("On-device"),
    GRADLE("Gradle"),
    AUTO("Auto")
}

@Serializable
enum class ProjectTemplate(
    val displayName: String,
    val description: String,
    val language: ProjectLanguage,
    val uiFramework: UiFramework,
    val preferredBuildEngine: PreferredBuildEngine
) {
    BLANK_COMPOSE(
        displayName = "Blank Kotlin",
        description = "Empty Kotlin + Compose starter with a clean Material 3 shell and no sample product logic",
        language = ProjectLanguage.KOTLIN,
        uiFramework = UiFramework.COMPOSE,
        preferredBuildEngine = PreferredBuildEngine.GRADLE
    ),
    SINGLE_ACTIVITY_COMPOSE(
        displayName = "Compose Dashboard",
        description = "Compose dashboard shell with stats, quick actions, and clean Material 3 layout",
        language = ProjectLanguage.KOTLIN,
        uiFramework = UiFramework.COMPOSE,
        preferredBuildEngine = PreferredBuildEngine.GRADLE
    ),
    COMPOSE_SETTINGS(
        displayName = "Compose Settings",
        description = "Compose settings surface with grouped toggles and status cards",
        language = ProjectLanguage.KOTLIN,
        uiFramework = UiFramework.COMPOSE,
        preferredBuildEngine = PreferredBuildEngine.GRADLE
    ),
    BLANK_VIEWS(
        displayName = "Blank Java",
        description = "Empty Java + XML starter using only build-safe Android Views and resources",
        language = ProjectLanguage.JAVA,
        uiFramework = UiFramework.VIEWS,
        preferredBuildEngine = PreferredBuildEngine.LEGACY
    ),
    JAVA_DASHBOARD(
        displayName = "Java Dashboard",
        description = "Modern Java dashboard starter with hero card and action surface",
        language = ProjectLanguage.JAVA,
        uiFramework = UiFramework.VIEWS,
        preferredBuildEngine = PreferredBuildEngine.LEGACY
    ),
    BASIC_UTILITY(
        displayName = "Java Utility",
        description = "Input-process-output utility starter with clean result card",
        language = ProjectLanguage.JAVA,
        uiFramework = UiFramework.VIEWS,
        preferredBuildEngine = PreferredBuildEngine.LEGACY
    ),
    JAVA_FORM(
        displayName = "Java Form",
        description = "Validated form starter with scrollable sections and submission flow",
        language = ProjectLanguage.JAVA,
        uiFramework = UiFramework.VIEWS,
        preferredBuildEngine = PreferredBuildEngine.LEGACY
    ),
    JAVA_LIST_DETAIL(
        displayName = "Java List + Detail",
        description = "List/detail starter with selection state and clean empty panel",
        language = ProjectLanguage.JAVA,
        uiFramework = UiFramework.VIEWS,
        preferredBuildEngine = PreferredBuildEngine.LEGACY
    );

    companion object {
        val default: ProjectTemplate = BLANK_VIEWS
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
