package com.build.buddyai.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.build.buddyai.core.data.database.dao.*
import com.build.buddyai.core.data.database.entity.*

@Database(
    entities = [
        ProjectEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        BuildRecordEntity::class,
        BuildArtifactEntity::class,
        SnapshotEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class BuildBuddyDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun chatDao(): ChatDao
    abstract fun buildDao(): BuildDao
    abstract fun snapshotDao(): SnapshotDao
}
