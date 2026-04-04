package com.build.buddyai.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatSession(
    val id: String,
    val projectId: String,
    val title: String = "New Conversation",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
)

@Serializable
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.COMPLETE,
    val actions: List<AgentAction> = emptyList(),
    val attachedFiles: List<String> = emptyList(),
    val modelId: String? = null,
    val tokenCount: Int? = null
)

@Serializable
enum class MessageRole { USER, ASSISTANT, SYSTEM }

@Serializable
enum class MessageStatus { SENDING, STREAMING, COMPLETE, ERROR, CANCELLED }

@Serializable
data class AgentAction(
    val id: String,
    val type: AgentActionType,
    val description: String,
    val status: ActionStatus = ActionStatus.PENDING,
    val data: String? = null,
    val filePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class AgentActionType {
    READING_FILE, PLANNING, EDITING_FILE, CREATING_FILE,
    DELETING_FILE, GENERATING_PATCH, BUILDING, ANALYZING_LOGS,
    SEARCHING, EXPLAINING
}

@Serializable
enum class ActionStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }

@Serializable
enum class AgentMode(val displayName: String) {
    ASK("Ask"),
    PLAN("Plan"),
    APPLY("Apply"),
    AUTO("Auto")
}

@Serializable
data class FileDiff(
    val filePath: String,
    val originalContent: String,
    val modifiedContent: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val isNewFile: Boolean = false,
    val isDeleted: Boolean = false
)
