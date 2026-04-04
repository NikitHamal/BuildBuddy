package com.build.buddyai.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val iconUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastBuildAt: Long?,
    val lastBuildStatus: String?,
    val isArchived: Boolean,
    val modelOverrideId: String?,
    val projectPath: String
)
