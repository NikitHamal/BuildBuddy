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
        ProviderConfigEntity::class,
        AgentTurnExecutionEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class BuildBuddyDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun buildRecordDao(): BuildRecordDao
    abstract fun chatDao(): ChatDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun providerConfigDao(): ProviderConfigDao
    abstract fun agentTurnExecutionDao(): AgentTurnExecutionDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE provider_configs ADD COLUMN cached_models TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE provider_configs ADD COLUMN last_model_fetch_time INTEGER")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `agent_turn_executions` (
                `id` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `sessionId` TEXT NOT NULL,
                `userInput` TEXT NOT NULL,
                `attachedFilesJson` TEXT NOT NULL,
                `repairAttempt` INTEGER NOT NULL,
                `repairContext` TEXT,
                `status` TEXT NOT NULL,
                `phase` TEXT NOT NULL,
                `owner` TEXT NOT NULL,
                `heartbeatAt` INTEGER NOT NULL,
                `assistantMessageId` TEXT,
                `partialResponse` TEXT NOT NULL,
                `finalRawResponse` TEXT,
                `finalDisplayResponse` TEXT,
                `lastError` TEXT,
                `providerType` TEXT,
                `providerId` TEXT,
                `modelId` TEXT,
                `temperature` REAL,
                `maxTokens` INTEGER,
                `topP` REAL,
                `requestMessagesJson` TEXT,
                `planJson` TEXT,
                `resumeCount` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_turn_executions_sessionId` ON `agent_turn_executions` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_turn_executions_status` ON `agent_turn_executions` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_turn_executions_updatedAt` ON `agent_turn_executions` (`updatedAt`)")
    }
}
