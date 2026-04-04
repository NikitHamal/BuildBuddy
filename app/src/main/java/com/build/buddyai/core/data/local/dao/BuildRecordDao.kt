package com.build.buddyai.core.data.local.dao

import androidx.room.*
import com.build.buddyai.core.data.local.entity.BuildRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildRecordDao {
    @Query("SELECT * FROM build_records WHERE projectId = :projectId ORDER BY startedAt DESC")
    fun getBuildRecordsByProject(projectId: String): Flow<List<BuildRecordEntity>>

    @Query("SELECT * FROM build_records ORDER BY startedAt DESC LIMIT :limit")
    fun getRecentBuildRecords(limit: Int = 10): Flow<List<BuildRecordEntity>>

    @Query("SELECT * FROM build_records WHERE id = :id")
    suspend fun getBuildRecordById(id: String): BuildRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuildRecord(record: BuildRecordEntity)

    @Update
    suspend fun updateBuildRecord(record: BuildRecordEntity)

    @Query("DELETE FROM build_records WHERE projectId = :projectId")
    suspend fun deleteRecordsByProject(projectId: String)
}
