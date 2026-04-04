package com.build.buddyai.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = true
)
abstract class BuildBuddyDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun buildRecordDao(): BuildRecordDao
    abstract fun chatDao(): ChatDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun providerConfigDao(): ProviderConfigDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE provider_configs ADD COLUMN cached_models TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE provider_configs ADD COLUMN last_model_fetch_time INTEGER")
    }
}
