package com.build.buddyai.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.build.buddyai.core.model.BuildRecord
import com.build.buddyai.core.model.BuildStatus

@Entity(tableName = "build_records")
data class BuildRecordEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val status: String,
    val startedAt: Long,
    val completedAt: Long?,
    val durationMs: Long?,
    val artifactPath: String?,
    val artifactSizeBytes: Long?,
    val logEntriesJson: String,
    val errorSummary: String?,
    val buildVariant: String
) {
    fun toBuildRecord(parseLogEntries: (String) -> List<com.build.buddyai.core.model.BuildLogEntry>): BuildRecord = BuildRecord(
        id = id, projectId = projectId,
        status = BuildStatus.valueOf(status),
        startedAt = startedAt, completedAt = completedAt,
        durationMs = durationMs, artifactPath = artifactPath,
        artifactSizeBytes = artifactSizeBytes,
        logEntries = parseLogEntries(logEntriesJson),
        errorSummary = errorSummary, buildVariant = buildVariant
    )

    companion object {
        fun fromBuildRecord(record: BuildRecord, serializeLogEntries: (List<com.build.buddyai.core.model.BuildLogEntry>) -> String) = BuildRecordEntity(
            id = record.id, projectId = record.projectId,
            status = record.status.name, startedAt = record.startedAt,
            completedAt = record.completedAt, durationMs = record.durationMs,
            artifactPath = record.artifactPath, artifactSizeBytes = record.artifactSizeBytes,
            logEntriesJson = serializeLogEntries(record.logEntries),
            errorSummary = record.errorSummary, buildVariant = record.buildVariant
        )
    }
}
