package com.build.buddyai.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snapshots")
data class SnapshotEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val label: String,
    val createdAt: Long,
    val description: String,
    val fileCount: Int,
    val isAutoSnapshot: Boolean
)
