package com.build.buddyai.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)
