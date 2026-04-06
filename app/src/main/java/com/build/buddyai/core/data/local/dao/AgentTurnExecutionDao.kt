package com.build.buddyai.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.build.buddyai.core.data.local.entity.AgentTurnExecutionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentTurnExecutionDao {
    @Query("SELECT * FROM agent_turn_executions WHERE id = :executionId LIMIT 1")
    suspend fun getById(executionId: String): AgentTurnExecutionEntity?

    @Query("SELECT * FROM agent_turn_executions WHERE id = :executionId LIMIT 1")
    fun observeById(executionId: String): Flow<AgentTurnExecutionEntity?>

    @Query("SELECT * FROM agent_turn_executions WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun listByStatus(status: String): List<AgentTurnExecutionEntity>

    @Query("SELECT * FROM agent_turn_executions WHERE status IN (:statuses) ORDER BY updatedAt DESC")
    suspend fun listByStatuses(statuses: List<String>): List<AgentTurnExecutionEntity>

    @Query("SELECT * FROM agent_turn_executions WHERE sessionId = :sessionId AND status IN (:statuses) ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findLatestBySessionAndStatuses(sessionId: String, statuses: List<String>): AgentTurnExecutionEntity?

    @Query("SELECT * FROM agent_turn_executions WHERE status = :runningStatus AND heartbeatAt <= :staleBefore ORDER BY heartbeatAt ASC")
    suspend fun listStaleRunning(runningStatus: String, staleBefore: Long): List<AgentTurnExecutionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AgentTurnExecutionEntity)

    @Update
    suspend fun update(entity: AgentTurnExecutionEntity)

    @Query(
        """
        UPDATE agent_turn_executions
        SET owner = :newOwner,
            heartbeatAt = :now,
            updatedAt = :now,
            resumeCount = resumeCount + 1
        WHERE id = :executionId
          AND status = :runningStatus
          AND heartbeatAt <= :staleBefore
        """
    )
    suspend fun claimIfStale(
        executionId: String,
        runningStatus: String,
        staleBefore: Long,
        newOwner: String,
        now: Long
    ): Int

    @Query("DELETE FROM agent_turn_executions WHERE status IN (:terminalStatuses) AND updatedAt < :olderThan")
    suspend fun deleteTerminalOlderThan(terminalStatuses: List<String>, olderThan: Long): Int
}
