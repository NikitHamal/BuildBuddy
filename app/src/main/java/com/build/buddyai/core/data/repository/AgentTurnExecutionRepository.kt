package com.build.buddyai.core.data.repository

import com.build.buddyai.core.agent.AgentExecutionPlan
import com.build.buddyai.core.agent.AgentTurnExecutionPhase
import com.build.buddyai.core.agent.AgentTurnExecutionPolicy
import com.build.buddyai.core.agent.AgentTurnExecutionStatus
import com.build.buddyai.core.data.local.dao.AgentTurnExecutionDao
import com.build.buddyai.core.data.local.entity.AgentTurnExecutionEntity
import com.build.buddyai.core.network.AiChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentTurnExecutionRepository @Inject constructor(
    private val dao: AgentTurnExecutionDao,
    private val json: Json
) {
    data class NewExecution(
        val id: String,
        val projectId: String,
        val sessionId: String,
        val userInput: String,
        val attachedFiles: List<String>,
        val repairAttempt: Int,
        val repairContext: String?,
        val owner: String
    )

    suspend fun create(newExecution: NewExecution): AgentTurnExecutionEntity {
        val now = System.currentTimeMillis()
        val entity = AgentTurnExecutionEntity(
            id = newExecution.id,
            projectId = newExecution.projectId,
            sessionId = newExecution.sessionId,
            userInput = newExecution.userInput,
            attachedFilesJson = encodeAttachedFiles(newExecution.attachedFiles),
            repairAttempt = newExecution.repairAttempt,
            repairContext = newExecution.repairContext,
            status = AgentTurnExecutionStatus.RUNNING.name,
            phase = AgentTurnExecutionPhase.QUEUED.name,
            owner = newExecution.owner,
            heartbeatAt = now,
            assistantMessageId = null,
            partialResponse = "",
            finalRawResponse = null,
            finalDisplayResponse = null,
            lastError = null,
            providerType = null,
            providerId = null,
            modelId = null,
            temperature = null,
            maxTokens = null,
            topP = null,
            requestMessagesJson = null,
            planJson = null,
            resumeCount = 0,
            createdAt = now,
            updatedAt = now
        )
        dao.insert(entity)
        return entity
    }

    suspend fun getById(executionId: String): AgentTurnExecutionEntity? = dao.getById(executionId)

    fun observeById(executionId: String): Flow<AgentTurnExecutionEntity?> = dao.observeById(executionId)

    suspend fun listActive(): List<AgentTurnExecutionEntity> =
        dao.listByStatuses(
            listOf(
                AgentTurnExecutionStatus.RUNNING.name,
                AgentTurnExecutionStatus.WAITING_REVIEW.name
            )
        )

    suspend fun findLatestActiveForSession(sessionId: String): AgentTurnExecutionEntity? =
        dao.findLatestBySessionAndStatuses(
            sessionId = sessionId,
            statuses = listOf(
                AgentTurnExecutionStatus.RUNNING.name,
                AgentTurnExecutionStatus.WAITING_REVIEW.name
            )
        )

    suspend fun listStaleRunning(now: Long = System.currentTimeMillis()): List<AgentTurnExecutionEntity> =
        dao.listStaleRunning(
            runningStatus = AgentTurnExecutionStatus.RUNNING.name,
            staleBefore = now - AgentTurnExecutionPolicy.HEARTBEAT_STALE_MS
        )

    suspend fun claimIfStale(executionId: String, newOwner: String, now: Long = System.currentTimeMillis()): Boolean {
        val updated = dao.claimIfStale(
            executionId = executionId,
            runningStatus = AgentTurnExecutionStatus.RUNNING.name,
            staleBefore = now - AgentTurnExecutionPolicy.HEARTBEAT_STALE_MS,
            newOwner = newOwner,
            now = now
        )
        return updated > 0
    }

    suspend fun update(
        executionId: String,
        transform: (AgentTurnExecutionEntity) -> AgentTurnExecutionEntity
    ): AgentTurnExecutionEntity? {
        val current = dao.getById(executionId) ?: return null
        val updated = transform(current).copy(updatedAt = System.currentTimeMillis())
        dao.update(updated)
        return updated
    }

    suspend fun heartbeat(
        executionId: String,
        phase: AgentTurnExecutionPhase? = null,
        status: AgentTurnExecutionStatus? = null
    ): AgentTurnExecutionEntity? = update(executionId) { current ->
        current.copy(
            phase = phase?.name ?: current.phase,
            status = status?.name ?: current.status,
            heartbeatAt = System.currentTimeMillis()
        )
    }

    suspend fun markCompleted(
        executionId: String,
        finalDisplayResponse: String?,
        finalRawResponse: String?
    ): AgentTurnExecutionEntity? = update(executionId) { current ->
        current.copy(
            status = AgentTurnExecutionStatus.COMPLETED.name,
            phase = AgentTurnExecutionPhase.COMPLETED.name,
            finalDisplayResponse = finalDisplayResponse ?: current.finalDisplayResponse,
            finalRawResponse = finalRawResponse ?: current.finalRawResponse,
            heartbeatAt = System.currentTimeMillis(),
            lastError = null
        )
    }

    suspend fun markWaitingReview(
        executionId: String,
        finalDisplayResponse: String?,
        finalRawResponse: String?
    ): AgentTurnExecutionEntity? = update(executionId) { current ->
        current.copy(
            status = AgentTurnExecutionStatus.WAITING_REVIEW.name,
            phase = AgentTurnExecutionPhase.WAITING_FOR_REVIEW.name,
            finalDisplayResponse = finalDisplayResponse ?: current.finalDisplayResponse,
            finalRawResponse = finalRawResponse ?: current.finalRawResponse,
            heartbeatAt = System.currentTimeMillis()
        )
    }

    suspend fun markFailed(executionId: String, errorMessage: String): AgentTurnExecutionEntity? =
        update(executionId) { current ->
            current.copy(
                status = AgentTurnExecutionStatus.FAILED.name,
                phase = AgentTurnExecutionPhase.FAILED.name,
                lastError = errorMessage.take(2000),
                heartbeatAt = System.currentTimeMillis()
            )
        }

    suspend fun markCancelled(executionId: String, message: String? = null): AgentTurnExecutionEntity? =
        update(executionId) { current ->
            current.copy(
                status = AgentTurnExecutionStatus.CANCELLED.name,
                phase = AgentTurnExecutionPhase.CANCELLED.name,
                lastError = message ?: current.lastError,
                heartbeatAt = System.currentTimeMillis()
            )
        }

    suspend fun pruneTerminal(now: Long = System.currentTimeMillis()) {
        dao.deleteTerminalOlderThan(
            terminalStatuses = listOf(
                AgentTurnExecutionStatus.COMPLETED.name,
                AgentTurnExecutionStatus.FAILED.name,
                AgentTurnExecutionStatus.CANCELLED.name
            ),
            olderThan = now - AgentTurnExecutionPolicy.TERMINAL_RETENTION_MS
        )
    }

    fun encodeAttachedFiles(paths: List<String>): String = json.encodeToString(paths.distinct())

    fun decodeAttachedFiles(encoded: String): List<String> = runCatching {
        json.decodeFromString<List<String>>(encoded)
    }.getOrDefault(emptyList())

    fun encodeRequestMessages(messages: List<AiChatMessage>): String =
        json.encodeToString(ListSerializer(AiChatMessage.serializer()), messages)

    fun decodeRequestMessages(encoded: String): List<AiChatMessage> = runCatching {
        json.decodeFromString(ListSerializer(AiChatMessage.serializer()), encoded)
    }.getOrDefault(emptyList())

    fun encodePlan(plan: AgentExecutionPlan?): String? =
        plan?.let { json.encodeToString(it) }
}
