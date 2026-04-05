package com.build.buddyai.core.data.repository

import com.build.buddyai.core.data.local.dao.ChatDao
import com.build.buddyai.core.data.local.entity.ChatMessageEntity
import com.build.buddyai.core.data.local.entity.ChatSessionEntity
import com.build.buddyai.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val json: Json
) {
    fun getSessionsByProject(projectId: String): Flow<List<ChatSession>> =
        chatDao.getSessionsByProject(projectId).map { entities ->
            entities.map { e ->
                ChatSession(id = e.id, projectId = e.projectId, title = e.title, createdAt = e.createdAt, updatedAt = e.updatedAt)
            }
        }

    fun getMessagesBySession(sessionId: String): Flow<List<ChatMessage>> =
        chatDao.getMessagesBySession(sessionId).map { entities ->
            entities.map { e ->
                ChatMessage(
                    id = e.id, sessionId = e.sessionId,
                    role = MessageRole.valueOf(e.role),
                    content = e.content, timestamp = e.timestamp,
                    status = MessageStatus.valueOf(e.status),
                    actions = deserializeActions(e.actionsJson),
                    attachedFiles = deserializeFiles(e.attachedFilesJson),
                    modelId = e.modelId, tokenCount = e.tokenCount
                )
            }
        }

    suspend fun createSession(session: ChatSession) {
        chatDao.insertSession(
            ChatSessionEntity(
                id = session.id, projectId = session.projectId,
                title = session.title, createdAt = session.createdAt,
                updatedAt = session.updatedAt
            )
        )
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) {
        val session = chatDao.getSessionById(sessionId) ?: return
        chatDao.updateSession(session.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteMessagesBySession(sessionId)
        chatDao.deleteSession(sessionId)
    }

    suspend fun insertMessage(message: ChatMessage) {
        chatDao.insertMessage(
            ChatMessageEntity(
                id = message.id, sessionId = message.sessionId,
                role = message.role.name, content = message.content,
                timestamp = message.timestamp, status = message.status.name,
                actionsJson = json.encodeToString(message.actions),
                attachedFilesJson = json.encodeToString(message.attachedFiles),
                modelId = message.modelId, tokenCount = message.tokenCount
            )
        )
    }

    suspend fun updateMessage(message: ChatMessage) {
        chatDao.updateMessage(
            ChatMessageEntity(
                id = message.id, sessionId = message.sessionId,
                role = message.role.name, content = message.content,
                timestamp = message.timestamp, status = message.status.name,
                actionsJson = json.encodeToString(message.actions),
                attachedFilesJson = json.encodeToString(message.attachedFiles),
                modelId = message.modelId, tokenCount = message.tokenCount
            )
        )
    }

    private fun deserializeActions(actionsJson: String): List<AgentAction> {
        return try { json.decodeFromString(actionsJson) } catch (_: Exception) { emptyList() }
    }

    private fun deserializeFiles(filesJson: String): List<String> {
        return try { json.decodeFromString(filesJson) } catch (_: Exception) { emptyList() }
    }
}
