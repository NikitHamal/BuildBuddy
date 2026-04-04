package com.build.buddyai.core.model

import java.util.UUID

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String = "New Chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val modelId: String? = null,
    val providerId: String? = null
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.COMPLETE,
    val toolActions: List<ToolAction> = emptyList(),
    val attachedFiles: List<String> = emptyList(),
    val tokenCount: Int? = null,
    val modelId: String? = null
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }

enum class MessageStatus { PENDING, STREAMING, COMPLETE, ERROR, CANCELLED }

data class ToolAction(
    val id: String = UUID.randomUUID().toString(),
    val type: ToolActionType,
    val description: String,
    val filePath: String? = null,
    val status: ToolActionStatus = ToolActionStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ToolActionType {
    READING_FILE,
    PLANNING,
    EDITING_FILE,
    CREATING_FILE,
    DELETING_FILE,
    GENERATING_PATCH,
    BUILDING_PROJECT,
    ANALYZING_LOGS,
    SEARCHING_CODE,
    REFACTORING
}

enum class ToolActionStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }

enum class AgentMode(val displayName: String, val description: String) {
    ASK("Ask", "Chat and get answers about your code"),
    PLAN("Plan", "AI creates a step-by-step implementation plan"),
    APPLY("Apply", "AI generates and applies code changes"),
    AUTO("Auto", "AI applies changes with confirmation for destructive actions")
}

data class CodeDiff(
    val filePath: String,
    val hunks: List<DiffHunk>,
    val isNewFile: Boolean = false,
    val isDeletedFile: Boolean = false
)

data class DiffHunk(
    val startLine: Int,
    val endLine: Int,
    val oldContent: String,
    val newContent: String
)
