package com.build.buddyai.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.build.buddyai.core.data.local.dao.*
import com.build.buddyai.core.data.local.entity.*

@Database(
    entities = [
        ProjectEntity::class,
        BuildRecordEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        ArtifactEntity::class,
        ProviderConfigEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class BuildBuddyDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun buildRecordDao(): BuildRecordDao
    abstract fun chatDao(): ChatDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun providerConfigDao(): ProviderConfigDao
}
