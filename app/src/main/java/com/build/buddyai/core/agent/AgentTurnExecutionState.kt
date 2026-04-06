package com.build.buddyai.core.agent

enum class AgentTurnExecutionStatus {
    RUNNING,
    WAITING_REVIEW,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class AgentTurnExecutionPhase {
    QUEUED,
    CONTEXT_ASSEMBLY,
    PLANNING,
    STREAMING,
    APPLYING_CHANGES,
    VALIDATING,
    WAITING_FOR_REVIEW,
    COMPLETED,
    FAILED,
    CANCELLED
}

object AgentTurnExecutionPolicy {
    const val HEARTBEAT_STALE_MS: Long = 45_000L
    const val WATCHDOG_DELAY_MS: Long = 20_000L
    const val TERMINAL_RETENTION_MS: Long = 3L * 24L * 60L * 60L * 1000L
}
