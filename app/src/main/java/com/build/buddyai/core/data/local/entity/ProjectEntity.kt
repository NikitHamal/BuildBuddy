package com.build.buddyai.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.Project
import com.build.buddyai.core.model.ProjectLanguage
import com.build.buddyai.core.model.ProjectTemplate
import com.build.buddyai.core.model.UiFramework

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val packageName: String,
    val description: String,
    val language: String,
    val uiFramework: String,
    val template: String,
    val minSdk: Int,
    val targetSdk: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastBuildStatus: String,
    val lastBuildAt: Long?,
    val projectPath: String,
    val iconUri: String?
) {
    fun toProject() = Project(
        id = id,
        name = name,
        packageName = packageName,
        description = description,
        language = ProjectLanguage.entries.firstOrNull { it.name == language } ?: ProjectLanguage.JAVA,
        uiFramework = UiFramework.entries.firstOrNull { it.name == uiFramework } ?: UiFramework.VIEWS,
        template = resolveTemplate(template),
        minSdk = minSdk,
        targetSdk = targetSdk,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastBuildStatus = BuildStatus.entries.firstOrNull { it.name == lastBuildStatus } ?: BuildStatus.NONE,
        lastBuildAt = lastBuildAt,
        projectPath = projectPath,
        iconUri = iconUri
    )

    companion object {
        fun fromProject(project: Project) = ProjectEntity(
            id = project.id,
            name = project.name,
            packageName = project.packageName,
            description = project.description,
            language = project.language.name,
            uiFramework = project.uiFramework.name,
            template = project.template.name,
            minSdk = project.minSdk,
            targetSdk = project.targetSdk,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt,
            lastBuildStatus = project.lastBuildStatus.name,
            lastBuildAt = project.lastBuildAt,
            projectPath = project.projectPath,
            iconUri = project.iconUri
        )

        private fun resolveTemplate(raw: String): ProjectTemplate = when (raw) {
            in ProjectTemplate.entries.map { it.name } -> ProjectTemplate.valueOf(raw)
            "JAVA_ACTIVITY", "JAVA_XML", "JAVA_VIEWS", "LEGACY_JAVA" -> ProjectTemplate.JAVA_DASHBOARD
            "EMPTY_ACTIVITY", "BLANK_ACTIVITY", "BASIC_ACTIVITY" -> ProjectTemplate.BLANK_VIEWS
            "KOTLIN_COMPOSE", "COMPOSE_ACTIVITY", "KOTLIN_ACTIVITY" -> ProjectTemplate.SINGLE_ACTIVITY_COMPOSE
            "SETTINGS_COMPOSE", "COMPOSE_PREFERENCES" -> ProjectTemplate.COMPOSE_SETTINGS
            "UTILITY", "JAVA_TOOL" -> ProjectTemplate.BASIC_UTILITY
            "FORM", "JAVA_FORM_SCREEN" -> ProjectTemplate.JAVA_FORM
            "LIST_DETAIL", "MASTER_DETAIL" -> ProjectTemplate.JAVA_LIST_DETAIL
            else -> ProjectTemplate.default
        }
    }
}
