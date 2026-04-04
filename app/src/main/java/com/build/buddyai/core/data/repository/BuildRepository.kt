package com.build.buddyai.core.data.repository

import com.build.buddyai.core.data.local.dao.BuildRecordDao
import com.build.buddyai.core.data.local.entity.BuildRecordEntity
import com.build.buddyai.core.model.BuildLogEntry
import com.build.buddyai.core.model.BuildRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildRepository @Inject constructor(
    private val buildRecordDao: BuildRecordDao,
    private val json: Json
) {
    fun getBuildRecordsByProject(projectId: String): Flow<List<BuildRecord>> =
        buildRecordDao.getBuildRecordsByProject(projectId).map { entities ->
            entities.map { it.toBuildRecord { logs -> deserializeLogs(logs) } }
        }

    fun getRecentBuildRecords(limit: Int = 10): Flow<List<BuildRecord>> =
        buildRecordDao.getRecentBuildRecords(limit).map { entities ->
            entities.map { it.toBuildRecord { logs -> deserializeLogs(logs) } }
        }

    suspend fun getBuildRecordById(id: String): BuildRecord? =
        buildRecordDao.getBuildRecordById(id)?.toBuildRecord { logs -> deserializeLogs(logs) }

    suspend fun insertBuildRecord(record: BuildRecord) {
        buildRecordDao.insertBuildRecord(
            BuildRecordEntity.fromBuildRecord(record) { logs -> serializeLogs(logs) }
        )
    }

    suspend fun updateBuildRecord(record: BuildRecord) {
        buildRecordDao.updateBuildRecord(
            BuildRecordEntity.fromBuildRecord(record) { logs -> serializeLogs(logs) }
        )
    }

    suspend fun deleteRecordsByProject(projectId: String) {
        buildRecordDao.deleteRecordsByProject(projectId)
    }

    private fun serializeLogs(logs: List<BuildLogEntry>): String = json.encodeToString(logs)
    private fun deserializeLogs(logsJson: String): List<BuildLogEntry> {
        return try { json.decodeFromString(logsJson) } catch (_: Exception) { emptyList() }
    }
}
