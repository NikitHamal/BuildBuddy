package com.build.buddyai.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_turn_executions",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["status"]),
        Index(value = ["updatedAt"])
    ]
)
data class AgentTurnExecutionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val sessionId: String,
    val userInput: String,
    val attachedFilesJson: String,
    val repairAttempt: Int,
    val repairContext: String?,
    val status: String,
    val phase: String,
    val owner: String,
    val heartbeatAt: Long,
    val assistantMessageId: String?,
    val partialResponse: String,
    val finalRawResponse: String?,
    val finalDisplayResponse: String?,
    val lastError: String?,
    val providerType: String?,
    val providerId: String?,
    val modelId: String?,
    val temperature: Float?,
    val maxTokens: Int?,
    val topP: Float?,
    val requestMessagesJson: String?,
    val planJson: String?,
    val resumeCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
