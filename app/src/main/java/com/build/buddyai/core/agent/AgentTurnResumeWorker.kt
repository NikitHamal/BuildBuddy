package com.build.buddyai.core.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.build.buddyai.R
import com.build.buddyai.core.data.repository.AgentTurnExecutionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID

@HiltWorker
class AgentTurnResumeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val executionRepository: AgentTurnExecutionRepository,
    private val workScheduler: AgentTurnWorkScheduler,
    private val resumeExecutor: AgentTurnResumeExecutor
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val CHANNEL_ID = "agent_resume_channel"
        private const val NOTIFICATION_ID = 1102
    }

    override suspend fun doWork(): Result {
        return runCatching {
            ensureChannel()
            val executionId = inputData.getString(AgentTurnWorkScheduler.KEY_EXECUTION_ID)
            if (executionId.isNullOrBlank()) {
                processAllKnownExecutions()
            } else {
                processExecution(executionId)
            }
            executionRepository.pruneTerminal()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private suspend fun processAllKnownExecutions() {
        val now = System.currentTimeMillis()
        val active = executionRepository.listActive()
        active.forEach { execution ->
            if (execution.status != AgentTurnExecutionStatus.RUNNING.name) return@forEach
            if (now - execution.heartbeatAt >= AgentTurnExecutionPolicy.HEARTBEAT_STALE_MS) {
                processExecution(execution.id)
            } else {
                workScheduler.scheduleWatchdog(execution.id, AgentTurnExecutionPolicy.WATCHDOG_DELAY_MS)
            }
        }
    }

    private suspend fun processExecution(executionId: String) {
        val execution = executionRepository.getById(executionId) ?: return
        if (execution.status != AgentTurnExecutionStatus.RUNNING.name) return

        val ageMs = System.currentTimeMillis() - execution.heartbeatAt
        if (ageMs < AgentTurnExecutionPolicy.HEARTBEAT_STALE_MS) {
            workScheduler.scheduleWatchdog(executionId, AgentTurnExecutionPolicy.WATCHDOG_DELAY_MS)
            return
        }

        val owner = "worker:${UUID.randomUUID()}"
        val claimed = executionRepository.claimIfStale(executionId, newOwner = owner)
        if (!claimed) {
            workScheduler.scheduleWatchdog(executionId, AgentTurnExecutionPolicy.WATCHDOG_DELAY_MS)
            return
        }

        setForeground(buildForegroundInfo(execution.userInput))
        when (resumeExecutor.resumeExecution(executionId, owner)) {
            AgentTurnResumeExecutor.ResumeResult.COMPLETED -> Unit
            AgentTurnResumeExecutor.ResumeResult.READY_FOR_APPLY_CONTINUATION -> Unit
            AgentTurnResumeExecutor.ResumeResult.NOOP -> Unit
            AgentTurnResumeExecutor.ResumeResult.FAILED -> Unit
        }
    }

    private suspend fun buildForegroundInfo(prompt: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Resuming agent task")
            .setContentText(prompt.take(96))
            .setStyle(NotificationCompat.BigTextStyle().bigText(prompt.take(180)))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Agent resume",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background worker notifications for restart-safe agent task resume."
            }
        )
    }
}
