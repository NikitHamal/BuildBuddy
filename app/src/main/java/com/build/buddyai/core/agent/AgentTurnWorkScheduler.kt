package com.build.buddyai.core.agent

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentTurnWorkScheduler @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        const val KEY_EXECUTION_ID = "execution_id"
        private const val UNIQUE_BOOTSTRAP = "agent-turn-resume-bootstrap"
        private const val UNIQUE_PREFIX = "agent-turn-resume-"
    }

    private val workManager = WorkManager.getInstance(context)
    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleWatchdog(executionId: String, delayMs: Long = AgentTurnExecutionPolicy.WATCHDOG_DELAY_MS) {
        val request = OneTimeWorkRequestBuilder<AgentTurnResumeWorker>()
            .setInputData(workDataOf(KEY_EXECUTION_ID to executionId))
            .setConstraints(constraints)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("agent-turn-resume")
            .addTag("agent-turn-resume:$executionId")
            .build()
        workManager.enqueueUniqueWork(uniqueName(executionId), ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduleBootstrapResume() {
        val request = OneTimeWorkRequestBuilder<AgentTurnResumeWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("agent-turn-resume")
            .build()
        workManager.enqueueUniqueWork(UNIQUE_BOOTSTRAP, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(executionId: String) {
        workManager.cancelUniqueWork(uniqueName(executionId))
    }

    private fun uniqueName(executionId: String): String = "$UNIQUE_PREFIX$executionId"
}
