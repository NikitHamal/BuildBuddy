package com.build.buddyai.core.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentBackgroundExecutionRegistry @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobsBySession = ConcurrentHashMap<String, Job>()

    fun launch(sessionId: String, block: suspend () -> Unit): Job {
        jobsBySession.remove(sessionId)?.cancel()
        val job = scope.launch {
            try {
                block()
            } finally {
                jobsBySession.remove(sessionId)
            }
        }
        jobsBySession[sessionId] = job
        return job
    }

    fun cancel(sessionId: String?) {
        if (sessionId.isNullOrBlank()) {
            jobsBySession.values.forEach { it.cancel() }
            jobsBySession.clear()
            return
        }
        jobsBySession.remove(sessionId)?.cancel()
    }

    fun isRunning(sessionId: String?): Boolean {
        if (sessionId.isNullOrBlank()) return jobsBySession.values.any { it.isActive }
        return jobsBySession[sessionId]?.isActive == true
    }
}
