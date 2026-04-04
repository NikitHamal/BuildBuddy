package com.build.buddyai.core.data.database.dao

import androidx.room.*
import com.build.buddyai.core.data.database.entity.BuildArtifactEntity
import com.build.buddyai.core.data.database.entity.BuildRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildDao {
    @Query("SELECT * FROM build_records WHERE projectId = :projectId ORDER BY startedAt DESC")
    fun observeRecordsByProject(projectId: String): Flow<List<BuildRecordEntity>>

    @Query("SELECT * FROM build_records WHERE projectId = :projectId ORDER BY startedAt DESC LIMIT 1")
    fun observeLatestRecord(projectId: String): Flow<BuildRecordEntity?>

    @Query("SELECT * FROM build_records ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentRecords(limit: Int): Flow<List<BuildRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: BuildRecordEntity)

    @Update
    suspend fun updateRecord(record: BuildRecordEntity)

    @Query("SELECT * FROM build_artifacts WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeArtifactsByProject(projectId: String): Flow<List<BuildArtifactEntity>>

    @Query("SELECT * FROM build_artifacts WHERE id = :id")
    suspend fun getArtifactById(id: String): BuildArtifactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtifact(artifact: BuildArtifactEntity)

    @Update
    suspend fun updateArtifact(artifact: BuildArtifactEntity)

    @Query("DELETE FROM build_artifacts WHERE id = :id")
    suspend fun deleteArtifact(id: String)
}
