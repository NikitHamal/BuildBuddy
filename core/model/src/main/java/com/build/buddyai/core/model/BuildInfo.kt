package com.build.buddyai.core.model

import java.util.UUID

data class BuildRecord(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val status: BuildStatus,
    val startedAt: Long,
    val completedAt: Long? = null,
    val durationMs: Long? = null,
    val variant: String = "debug",
    val artifactPath: String? = null,
    val artifactSizeBytes: Long? = null,
    val logEntries: List<BuildLogEntry> = emptyList(),
    val errorSummary: String? = null
)

enum class BuildStatus(val displayName: String) {
    IDLE("Idle"),
    VALIDATING("Validating"),
    COMPILING("Compiling"),
    DEXING("Dexing"),
    PACKAGING("Packaging"),
    SIGNING("Signing"),
    SUCCESS("Success"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

data class BuildLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val message: String,
    val source: String? = null
)

enum class LogLevel { DEBUG, INFO, WARNING, ERROR }

data class BuildArtifact(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val buildId: String,
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val packageName: String,
    val versionName: String? = null,
    val versionCode: Int? = null,
    val isInstalled: Boolean = false
)

data class BuildDiagnostic(
    val severity: DiagnosticSeverity,
    val message: String,
    val filePath: String? = null,
    val lineNumber: Int? = null,
    val columnNumber: Int? = null,
    val source: String = "build"
)

enum class DiagnosticSeverity { ERROR, WARNING, INFO, HINT }
