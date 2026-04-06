package com.build.buddyai.core.common

import android.content.Context
import com.build.buddyai.core.data.datastore.SettingsDataStore
import com.build.buddyai.core.data.local.dao.ArtifactDao
import com.build.buddyai.core.data.local.dao.BuildRecordDao
import com.build.buddyai.core.data.local.dao.ChatDao
import com.build.buddyai.core.data.local.dao.ProjectDao
import com.build.buddyai.core.data.local.dao.ProviderConfigDao
import com.build.buddyai.core.model.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDataManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectDao: ProjectDao,
    private val buildRecordDao: BuildRecordDao,
    private val artifactDao: ArtifactDao,
    private val chatDao: ChatDao,
    private val providerConfigDao: ProviderConfigDao,
    private val settingsDataStore: SettingsDataStore,
    private val secureKeyStore: SecureKeyStore
) {
    enum class DataScope(val displayName: String) {
        PROJECTS("Projects"),
        BUILDS("Build outputs"),
        ARTIFACTS("Artifacts"),
        SNAPSHOTS("Restore points"),
        CHATS("Chat history"),
        PROVIDERS("AI providers"),
        SIGNING("Signing material"),
        CACHE("Cache"),
        SETTINGS("Settings")
    }

    data class StorageBucket(
        val scope: DataScope,
        val bytes: Long
    )

    suspend fun storageBreakdown(): List<StorageBucket> = withContext(Dispatchers.IO) {
        listOf(
            StorageBucket(DataScope.PROJECTS, FileUtils.calculateDirectorySize(File(context.filesDir, "projects"))),
            StorageBucket(DataScope.BUILDS, FileUtils.calculateDirectorySize(File(context.filesDir, "builds")) + FileUtils.calculateDirectorySize(File(context.filesDir, "project_problems"))),
            StorageBucket(DataScope.ARTIFACTS, FileUtils.calculateDirectorySize(File(context.filesDir, "artifacts")) + FileUtils.calculateDirectorySize(File(context.filesDir, "artifact_provenance"))),
            StorageBucket(DataScope.SNAPSHOTS, FileUtils.calculateDirectorySize(File(context.filesDir, "snapshots"))),
            StorageBucket(DataScope.CHATS, FileUtils.calculateDirectorySize(File(context.filesDir, "chat_attachments"))),
            StorageBucket(DataScope.PROVIDERS, 0L),
            StorageBucket(DataScope.SIGNING, FileUtils.calculateDirectorySize(File(context.filesDir, "signing")) + FileUtils.calculateDirectorySize(File(context.filesDir, "build_profiles"))),
            StorageBucket(DataScope.CACHE, FileUtils.calculateDirectorySize(context.cacheDir)),
            StorageBucket(DataScope.SETTINGS, 0L)
        )
    }

    suspend fun clear(scopes: Set<DataScope>): Long = withContext(Dispatchers.IO) {
        val before = storageBreakdown().filter { it.scope in scopes }.sumOf { it.bytes }
        val projectIds = projectDao.getAllProjectsNow().map { it.id }
        if (DataScope.PROJECTS in scopes) {
            File(context.filesDir, "projects").deleteRecursively()
            File(context.filesDir, "builds").deleteRecursively()
            File(context.filesDir, "project_problems").deleteRecursively()
            File(context.filesDir, "artifacts").deleteRecursively()
            File(context.filesDir, "artifact_provenance").deleteRecursively()
            File(context.filesDir, "snapshots").deleteRecursively()
            File(context.filesDir, "chat_attachments").deleteRecursively()
            File(context.filesDir, "agent_change_sets").deleteRecursively()
            File(context.filesDir, "signing").deleteRecursively()
            File(context.filesDir, "build_profiles").deleteRecursively()
            projectDao.clearAllProjects()
            buildRecordDao.clearAllRecords()
            artifactDao.clearAllArtifacts()
            chatDao.clearAllMessages()
            chatDao.clearAllSessions()
            projectIds.forEach { projectId ->
                secureKeyStore.deleteApiKey("sign_store_$projectId")
                secureKeyStore.deleteApiKey("sign_key_$projectId")
            }
        }
        if (DataScope.BUILDS in scopes) {
            File(context.filesDir, "builds").deleteRecursively()
            File(context.filesDir, "project_problems").deleteRecursively()
            buildRecordDao.clearAllRecords()
        }
        if (DataScope.ARTIFACTS in scopes) {
            File(context.filesDir, "artifacts").deleteRecursively()
            File(context.filesDir, "artifact_provenance").deleteRecursively()
            artifactDao.clearAllArtifacts()
        }
        if (DataScope.SNAPSHOTS in scopes) {
            File(context.filesDir, "snapshots").deleteRecursively()
        }
        if (DataScope.CHATS in scopes) {
            File(context.filesDir, "chat_attachments").deleteRecursively()
            chatDao.clearAllMessages()
            chatDao.clearAllSessions()
        }
        if (DataScope.PROVIDERS in scopes) {
            ProviderType.entries.forEach { secureKeyStore.deleteApiKey(it.name) }
            providerConfigDao.clearAllProviderConfigs()
        }
        if (DataScope.SIGNING in scopes) {
            File(context.filesDir, "signing").deleteRecursively()
            File(context.filesDir, "build_profiles").deleteRecursively()
            projectIds.forEach { projectId ->
                secureKeyStore.deleteApiKey("sign_store_$projectId")
                secureKeyStore.deleteApiKey("sign_key_$projectId")
            }
        }
        if (DataScope.CACHE in scopes) {
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()
        }
        if (DataScope.SETTINGS in scopes) {
            settingsDataStore.clearAll()
        }
        before
    }
}
