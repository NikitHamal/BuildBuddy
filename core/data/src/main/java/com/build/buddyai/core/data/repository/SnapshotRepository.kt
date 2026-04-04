package com.build.buddyai.core.data.repository

import com.build.buddyai.core.data.database.dao.SnapshotDao
import com.build.buddyai.core.data.database.converter.toDomain
import com.build.buddyai.core.data.database.converter.toEntity
import com.build.buddyai.core.model.Snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnapshotRepository @Inject constructor(
    private val snapshotDao: SnapshotDao
) {
    fun observeByProject(projectId: String): Flow<List<Snapshot>> =
        snapshotDao.observeByProject(projectId).map { list -> list.map { it.toDomain() } }

    suspend fun create(snapshot: Snapshot) = snapshotDao.insert(snapshot.toEntity())

    suspend fun delete(id: String) = snapshotDao.deleteById(id)
}
