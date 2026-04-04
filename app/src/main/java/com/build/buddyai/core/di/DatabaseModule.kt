package com.build.buddyai.core.di

import android.content.Context
import androidx.room.Room
import com.build.buddyai.core.data.local.BuildBuddyDatabase
import com.build.buddyai.core.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BuildBuddyDatabase {
        return Room.databaseBuilder(
            context,
            BuildBuddyDatabase::class.java,
            "buildbuddy.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides fun provideProjectDao(db: BuildBuddyDatabase): ProjectDao = db.projectDao()
    @Provides fun provideBuildRecordDao(db: BuildBuddyDatabase): BuildRecordDao = db.buildRecordDao()
    @Provides fun provideChatDao(db: BuildBuddyDatabase): ChatDao = db.chatDao()
    @Provides fun provideArtifactDao(db: BuildBuddyDatabase): ArtifactDao = db.artifactDao()
    @Provides fun provideProviderConfigDao(db: BuildBuddyDatabase): ProviderConfigDao = db.providerConfigDao()
}
