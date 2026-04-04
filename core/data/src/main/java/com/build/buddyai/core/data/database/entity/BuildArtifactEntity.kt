package com.build.buddyai.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "build_artifacts")
data class BuildArtifactEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val buildId: String,
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val packageName: String,
    val versionName: String?,
    val versionCode: Int?,
    val isInstalled: Boolean
)
