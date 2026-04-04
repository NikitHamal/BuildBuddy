package com.build.buddyai.core.data.repository

import com.build.buddyai.core.data.database.dao.ProjectDao
import com.build.buddyai.core.data.database.converter.toDomain
import com.build.buddyai.core.data.database.converter.toEntity
import com.build.buddyai.core.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao
) {
    fun observeAll(): Flow<List<Project>> = projectDao.observeAll().map { list -> list.map { it.toDomain() } }
    fun observeRecent(limit: Int = 5): Flow<List<Project>> = projectDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }
    fun observeById(id: String): Flow<Project?> = projectDao.observeById(id).map { it?.toDomain() }
    suspend fun getById(id: String): Project? = projectDao.getById(id)?.toDomain()
    fun search(query: String): Flow<List<Project>> = projectDao.search(query).map { list -> list.map { it.toDomain() } }
    suspend fun create(project: Project) = projectDao.insert(project.toEntity())
    suspend fun update(project: Project) = projectDao.update(project.toEntity())
    suspend fun delete(id: String) = projectDao.deleteById(id)
    suspend fun archive(id: String) = projectDao.setArchived(id, true)
    suspend fun unarchive(id: String) = projectDao.setArchived(id, false)
    fun observeCount(): Flow<Int> = projectDao.observeCount()
    suspend fun updateBuildStatus(id: String, buildAt: Long, status: String) = projectDao.updateBuildStatus(id, buildAt, status)
}
