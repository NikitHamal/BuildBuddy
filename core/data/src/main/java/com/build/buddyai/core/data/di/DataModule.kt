package com.build.buddyai.core.data.di

import android.content.Context
import androidx.room.Room
import com.build.buddyai.core.data.database.BuildBuddyDatabase
import com.build.buddyai.core.data.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BuildBuddyDatabase =
        Room.databaseBuilder(
            context,
            BuildBuddyDatabase::class.java,
            "buildbuddy.db"
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideProjectDao(db: BuildBuddyDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideChatDao(db: BuildBuddyDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideBuildDao(db: BuildBuddyDatabase): BuildDao = db.buildDao()

    @Provides
    fun provideSnapshotDao(db: BuildBuddyDatabase): SnapshotDao = db.snapshotDao()
}
