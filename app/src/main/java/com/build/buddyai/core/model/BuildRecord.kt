package com.build.buddyai.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BuildRecord(
    val id: String,
    val projectId: String,
    val status: BuildStatus,
    val startedAt: Long,
    val completedAt: Long? = null,
    val durationMs: Long? = null,
    val artifactPath: String? = null,
    val artifactSizeBytes: Long? = null,
    val logEntries: List<BuildLogEntry> = emptyList(),
    val errorSummary: String? = null,
    val buildVariant: String = "debug"
)

@Serializable
data class BuildLogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String,
    val source: String = ""
)

@Serializable
enum class LogLevel { VERBOSE, DEBUG, INFO, WARNING, ERROR }

@Serializable
data class BuildArtifact(
    val id: String,
    val projectId: String,
    val projectName: String,
    val buildRecordId: String,
    val filePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val createdAt: Long,
    val minSdk: Int,
    val targetSdk: Int
)
