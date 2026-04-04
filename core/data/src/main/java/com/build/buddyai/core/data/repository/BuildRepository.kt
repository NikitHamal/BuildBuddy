package com.build.buddyai.core.data.repository

import com.build.buddyai.core.data.database.dao.BuildDao
import com.build.buddyai.core.data.database.converter.toDomain
import com.build.buddyai.core.data.database.converter.toEntity
import com.build.buddyai.core.model.BuildArtifact
import com.build.buddyai.core.model.BuildRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildRepository @Inject constructor(
    private val buildDao: BuildDao
) {
    fun observeRecordsByProject(projectId: String): Flow<List<BuildRecord>> =
        buildDao.observeRecordsByProject(projectId).map { list -> list.map { it.toDomain() } }

    fun observeLatestRecord(projectId: String): Flow<BuildRecord?> =
        buildDao.observeLatestRecord(projectId).map { it?.toDomain() }

    fun observeRecentRecords(limit: Int = 10): Flow<List<BuildRecord>> =
        buildDao.observeRecentRecords(limit).map { list -> list.map { it.toDomain() } }

    suspend fun saveRecord(record: BuildRecord) = buildDao.insertRecord(record.toEntity())

    suspend fun updateRecord(record: BuildRecord) = buildDao.updateRecord(record.toEntity())

    fun observeArtifactsByProject(projectId: String): Flow<List<BuildArtifact>> =
        buildDao.observeArtifactsByProject(projectId).map { list -> list.map { it.toDomain() } }

    suspend fun getArtifactById(id: String): BuildArtifact? = buildDao.getArtifactById(id)?.toDomain()

    suspend fun saveArtifact(artifact: BuildArtifact) = buildDao.insertArtifact(artifact.toEntity())

    suspend fun deleteArtifact(id: String) = buildDao.deleteArtifact(id)
}
