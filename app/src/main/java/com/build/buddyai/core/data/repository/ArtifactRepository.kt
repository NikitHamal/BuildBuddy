package com.build.buddyai.core.data.repository

import android.content.Context
import com.build.buddyai.core.data.local.dao.ArtifactDao
import com.build.buddyai.core.data.local.entity.ArtifactEntity
import com.build.buddyai.core.model.BuildArtifact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtifactRepository @Inject constructor(
    private val artifactDao: ArtifactDao,
    @ApplicationContext private val context: Context
) {
    fun getArtifactsByProject(projectId: String): Flow<List<BuildArtifact>> =
        artifactDao.getArtifactsByProject(projectId).map { entities -> entities.map { it.toArtifact() } }

    suspend fun getArtifactById(id: String): BuildArtifact? =
        artifactDao.getArtifactById(id)?.toArtifact()

    suspend fun insertArtifact(artifact: BuildArtifact) {
        artifactDao.insertArtifact(ArtifactEntity.fromArtifact(artifact))
    }

    suspend fun deleteArtifact(artifact: BuildArtifact) {
        withContext(Dispatchers.IO) {
            File(artifact.filePath).takeIf { it.exists() }?.delete()
            artifactDao.deleteArtifact(ArtifactEntity.fromArtifact(artifact))
        }
    }

    suspend fun deleteArtifactsByProject(projectId: String) {
        withContext(Dispatchers.IO) {
            artifactDao.getArtifactsByProjectNow(projectId)
                .map { it.toArtifact() }
                .forEach { artifact -> File(artifact.filePath).takeIf { it.exists() }?.delete() }
            artifactDao.deleteArtifactsByProject(projectId)
        }
    }

    fun getArtifactFile(artifact: BuildArtifact): File = File(artifact.filePath)
}
