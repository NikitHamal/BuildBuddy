package com.build.buddyai.core.data.database.dao

import androidx.room.*
import com.build.buddyai.core.data.database.entity.SnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeByProject(projectId: String): Flow<List<SnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: SnapshotEntity)

    @Query("DELETE FROM snapshots WHERE id = :id")
    suspend fun deleteById(id: String)
}
