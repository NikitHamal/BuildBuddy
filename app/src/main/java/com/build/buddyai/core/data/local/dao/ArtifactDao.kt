package com.build.buddyai.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.build.buddyai.core.data.local.entity.ArtifactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtifactDao {
    @Query("SELECT * FROM artifacts WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getArtifactsByProject(projectId: String): Flow<List<ArtifactEntity>>

    @Query("SELECT * FROM artifacts WHERE projectId = :projectId ORDER BY createdAt DESC")
    suspend fun getArtifactsByProjectNow(projectId: String): List<ArtifactEntity>

    @Query("SELECT * FROM artifacts WHERE id = :id")
    suspend fun getArtifactById(id: String): ArtifactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtifact(artifact: ArtifactEntity)

    @Delete
    suspend fun deleteArtifact(artifact: ArtifactEntity)

    @Query("DELETE FROM artifacts WHERE projectId = :projectId")
    suspend fun deleteArtifactsByProject(projectId: String)
}
