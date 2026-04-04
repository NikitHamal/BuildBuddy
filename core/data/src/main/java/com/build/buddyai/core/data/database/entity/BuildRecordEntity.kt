package com.build.buddyai.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "build_records")
data class BuildRecordEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val status: String,
    val startedAt: Long,
    val completedAt: Long?,
    val durationMs: Long?,
    val variant: String,
    val artifactPath: String?,
    val artifactSizeBytes: Long?,
    val logEntriesJson: String?,
    val errorSummary: String?
)
