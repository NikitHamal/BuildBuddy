package com.build.buddyai.core.data.repository

import android.content.Context
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.data.local.dao.ProjectDao
import com.build.buddyai.core.data.local.entity.ProjectEntity
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.Project
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    @ApplicationContext private val context: Context
) {
    fun getAllProjects(): Flow<List<Project>> =
        projectDao.getAllProjects().map { entities -> entities.map { it.toProject() } }

    fun getRecentProjects(limit: Int = 5): Flow<List<Project>> =
        projectDao.getRecentProjects(limit).map { entities -> entities.map { it.toProject() } }

    fun observeProject(id: String): Flow<Project?> =
        projectDao.observeProject(id).map { it?.toProject() }

    fun searchProjects(query: String): Flow<List<Project>> =
        projectDao.searchProjects(query).map { entities -> entities.map { it.toProject() } }

    fun getProjectCount(): Flow<Int> = projectDao.getProjectCount()

    suspend fun getProjectById(id: String): Project? = projectDao.getProjectById(id)?.toProject()

    suspend fun createProject(project: Project): Project {
        val projectDir = File(FileUtils.getProjectsDir(context), project.id).apply { mkdirs() }
        val updatedProject = project.copy(projectPath = projectDir.absolutePath)
        projectDao.insertProject(ProjectEntity.fromProject(updatedProject))
        return updatedProject
    }

    suspend fun updateProject(project: Project) {
        projectDao.updateProject(ProjectEntity.fromProject(project.copy(updatedAt = System.currentTimeMillis())))
    }

    suspend fun deleteProject(projectId: String) {
        withContext(Dispatchers.IO) {
            val project = projectDao.getProjectById(projectId)
            if (project != null) {
                File(project.projectPath).deleteRecursively()
                projectDao.deleteProjectById(projectId)
            }
        }
    }

    suspend fun duplicateProject(projectId: String): Project? = withContext(Dispatchers.IO) {
        val original = projectDao.getProjectById(projectId)?.toProject() ?: return@withContext null
        val newId = UUID.randomUUID().toString()
        val newProject = original.copy(
            id = newId,
            name = "${original.name} (Copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastBuildStatus = BuildStatus.NONE,
            lastBuildAt = null
        )
        val newDir = File(FileUtils.getProjectsDir(context), newId)
        File(original.projectPath).copyRecursively(newDir, overwrite = true)
        val saved = newProject.copy(projectPath = newDir.absolutePath)
        projectDao.insertProject(ProjectEntity.fromProject(saved))
        saved
    }

    suspend fun exportProject(projectId: String): File? = withContext(Dispatchers.IO) {
        val project = projectDao.getProjectById(projectId)?.toProject() ?: return@withContext null
        val projectDir = File(project.projectPath)
        if (!projectDir.exists()) return@withContext null
        val outputFile = File(context.cacheDir, "${project.name.replace(" ", "_")}.zip")
        FileUtils.zipDirectory(projectDir, outputFile)
        outputFile
    }

    suspend fun importProject(zipFile: File, name: String, packageName: String): Project = withContext(Dispatchers.IO) {
        val newId = UUID.randomUUID().toString()
        val projectDir = File(FileUtils.getProjectsDir(context), newId)
        FileUtils.unzipToDirectory(zipFile, projectDir)
        val project = Project(
            id = newId,
            name = name,
            packageName = packageName,
            projectPath = projectDir.absolutePath
        )
        projectDao.insertProject(ProjectEntity.fromProject(project))
        project
    }

    fun getProjectDir(projectId: String): File = File(FileUtils.getProjectsDir(context), projectId)
}
