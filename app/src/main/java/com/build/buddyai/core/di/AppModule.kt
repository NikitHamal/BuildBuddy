package com.build.buddyai.core.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.build.buddyai.core.build.BuildEngine
import com.build.buddyai.core.build.ToolchainBackedBuildEngine
import com.build.buddyai.core.data.db.BuildBuddyDatabase
import com.build.buddyai.core.network.AiProviderClient
import com.build.buddyai.core.network.GeminiProvider
import com.build.buddyai.core.network.NvidiaProvider
import com.build.buddyai.core.network.OpenRouterProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BuildBuddyDatabase =
        Room.databaseBuilder(context, BuildBuddyDatabase::class.java, "buildbuddy.db").build()

    @Provides
    fun provideProjectDao(database: BuildBuddyDatabase) = database.projectDao()

    @Provides
    fun provideBuildDao(database: BuildBuddyDatabase) = database.buildDao()

    @Provides
    fun provideArtifactDao(database: BuildBuddyDatabase) = database.artifactDao()

    @Provides
    fun provideConversationDao(database: BuildBuddyDatabase) = database.conversationDao()

    @Provides
    fun provideMessageDao(database: BuildBuddyDatabase) = database.messageDao()

    @Provides
    fun provideSnapshotDao(database: BuildBuddyDatabase) = database.snapshotDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
        }
        return OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface BindingModule {
    @Binds
    @Singleton
    fun bindBuildEngine(impl: ToolchainBackedBuildEngine): BuildEngine

    @Binds
    @IntoSet
    fun bindOpenRouter(impl: OpenRouterProvider): AiProviderClient

    @Binds
    @IntoSet
    fun bindGemini(impl: GeminiProvider): AiProviderClient

    @Binds
    @IntoSet
    fun bindNvidia(impl: NvidiaProvider): AiProviderClient
}
