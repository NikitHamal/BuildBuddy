package com.build.buddyai.core.data.repository

import com.build.buddyai.core.data.database.dao.ChatDao
import com.build.buddyai.core.data.database.converter.toDomain
import com.build.buddyai.core.data.database.converter.toEntity
import com.build.buddyai.core.model.ChatMessage
import com.build.buddyai.core.model.ChatSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    fun observeSessionsByProject(projectId: String): Flow<List<ChatSession>> =
        chatDao.observeSessionsByProject(projectId).map { list -> list.map { it.toDomain() } }

    suspend fun getSessionById(id: String): ChatSession? = chatDao.getSessionById(id)?.toDomain()

    suspend fun createSession(session: ChatSession) = chatDao.insertSession(session.toEntity())

    suspend fun updateSession(session: ChatSession) = chatDao.updateSession(session.toEntity())

    suspend fun deleteSession(id: String) {
        chatDao.deleteMessagesBySession(id)
        chatDao.deleteSession(id)
    }

    fun observeMessages(sessionId: String): Flow<List<ChatMessage>> =
        chatDao.observeMessagesBySession(sessionId).map { list -> list.map { it.toDomain() } }

    suspend fun getMessages(sessionId: String): List<ChatMessage> =
        chatDao.getMessagesBySession(sessionId).map { it.toDomain() }

    suspend fun addMessage(message: ChatMessage) {
        chatDao.insertMessage(message.toEntity())
        chatDao.refreshSessionMessageCount(message.sessionId)
    }

    suspend fun updateMessage(message: ChatMessage) = chatDao.updateMessage(message.toEntity())

    suspend fun deleteMessage(id: String, sessionId: String) {
        chatDao.deleteMessage(id)
        chatDao.refreshSessionMessageCount(sessionId)
    }
}
