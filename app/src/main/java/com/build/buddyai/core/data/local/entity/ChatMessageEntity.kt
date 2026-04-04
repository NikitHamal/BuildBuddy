package com.build.buddyai.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val status: String,
    val actionsJson: String,
    val attachedFilesJson: String,
    val modelId: String?,
    val tokenCount: Int?
)
