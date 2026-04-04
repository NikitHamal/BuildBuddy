package com.build.buddyai.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.build.buddyai.core.model.*

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
        id = id, name = name, packageName = packageName,
        description = description,
        language = ProjectLanguage.valueOf(language),
        uiFramework = UiFramework.valueOf(uiFramework),
        template = ProjectTemplate.valueOf(template),
        minSdk = minSdk, targetSdk = targetSdk,
        createdAt = createdAt, updatedAt = updatedAt,
        lastBuildStatus = BuildStatus.valueOf(lastBuildStatus),
        lastBuildAt = lastBuildAt, projectPath = projectPath,
        iconUri = iconUri
    )

    companion object {
        fun fromProject(project: Project) = ProjectEntity(
            id = project.id, name = project.name,
            packageName = project.packageName, description = project.description,
            language = project.language.name, uiFramework = project.uiFramework.name,
            template = project.template.name, minSdk = project.minSdk,
            targetSdk = project.targetSdk, createdAt = project.createdAt,
            updatedAt = project.updatedAt, lastBuildStatus = project.lastBuildStatus.name,
            lastBuildAt = project.lastBuildAt, projectPath = project.projectPath,
            iconUri = project.iconUri
        )
    }
}
