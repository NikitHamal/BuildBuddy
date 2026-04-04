package com.build.buddyai.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import com.build.buddyai.core.data.db.ArtifactDao
import com.build.buddyai.core.data.db.BuildDao
import com.build.buddyai.core.data.db.ConversationDao
import com.build.buddyai.core.data.db.MessageDao
import com.build.buddyai.core.data.db.ProjectDao
import com.build.buddyai.core.data.db.SnapshotDao
import com.build.buddyai.core.data.db.asEntity
import com.build.buddyai.core.data.db.asExternal
import com.build.buddyai.core.data.secure.SecureStore
import com.build.buddyai.core.data.workspace.WorkspaceManager
import com.build.buddyai.core.model.AppPreferences
import com.build.buddyai.core.model.Artifact
import com.build.buddyai.core.model.BuildMode
import com.build.buddyai.core.model.BuildRecord
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.ChatMessage
import com.build.buddyai.core.model.Conversation
import com.build.buddyai.core.model.DashboardState
import com.build.buddyai.core.model.Project
import com.build.buddyai.core.model.ProjectDraft
import com.build.buddyai.core.model.ProjectFilter
import com.build.buddyai.core.model.ProviderId
import com.build.buddyai.core.model.ProviderSecret
import com.build.buddyai.core.model.ProviderSettings
import com.build.buddyai.core.model.Snapshot
import com.build.buddyai.core.model.SortMode
import com.build.buddyai.core.model.ThemeMode
import com.build.buddyai.core.model.WorkspaceManifest
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext
    context: Context,
    private val json: Json,
) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("buildbuddy_preferences.preferences_pb") },
    )

    val preferences: Flow<AppPreferences> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map(::mapPreferences)

    suspend fun setOnboardingComplete(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING] = completed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateEditorSettings(
        fontSize: Int,
        tabWidth: Int,
        softWrap: Boolean,
        lineNumbers: Boolean,
        autosave: Boolean,
    ) {
        dataStore.edit {
            it[Keys.FONT_SIZE] = fontSize
            it[Keys.TAB_WIDTH] = tabWidth
            it[Keys.SOFT_WRAP] = softWrap
            it[Keys.LINE_NUMBERS] = lineNumbers
            it[Keys.AUTOSAVE] = autosave
        }
    }

    suspend fun updateDefaults(providerId: ProviderId, modelId: String, buildMode: BuildMode) {
        dataStore.edit {
            it[Keys.DEFAULT_PROVIDER] = providerId.name
            it[Keys.DEFAULT_MODEL] = modelId
            it[Keys.DEFAULT_BUILD_MODE] = buildMode.name
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun setToolchainRootOverride(path: String?) {
        dataStore.edit {
            if (path.isNullOrBlank()) it.remove(Keys.TOOLCHAIN_ROOT) else it[Keys.TOOLCHAIN_ROOT] = path
        }
    }

    suspend fun exportDebugBundle(output: File, diagnostics: DashboardState) {
        output.writeText(json.encodeToString(diagnostics))
    }

    private fun mapPreferences(preferences: Preferences): AppPreferences = AppPreferences(
        onboardingComplete = preferences[Keys.ONBOARDING] ?: false,
        themeMode = preferences[Keys.THEME_MODE]?.let(ThemeMode::valueOf) ?: ThemeMode.SYSTEM,
        editorFontScaleSp = preferences[Keys.FONT_SIZE] ?: 14,
        tabWidth = preferences[Keys.TAB_WIDTH] ?: 4,
        softWrap = preferences[Keys.SOFT_WRAP] ?: true,
        showLineNumbers = preferences[Keys.LINE_NUMBERS] ?: true,
        autosave = preferences[Keys.AUTOSAVE] ?: true,
        notificationsEnabled = preferences[Keys.NOTIFICATIONS] ?: true,
        defaultProvider = preferences[Keys.DEFAULT_PROVIDER]?.let(ProviderId::valueOf) ?: ProviderId.OPENROUTER,
        defaultModel = preferences[Keys.DEFAULT_MODEL] ?: "openai/gpt-4o-mini",
        defaultBuildMode = preferences[Keys.DEFAULT_BUILD_MODE]?.let(BuildMode::valueOf) ?: BuildMode.DEBUG,
        toolchainRootOverride = preferences[Keys.TOOLCHAIN_ROOT],
    )

    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SIZE = intPreferencesKey("editor_font_size")
        val TAB_WIDTH = intPreferencesKey("editor_tab_width")
        val SOFT_WRAP = booleanPreferencesKey("editor_soft_wrap")
        val LINE_NUMBERS = booleanPreferencesKey("editor_line_numbers")
        val AUTOSAVE = booleanPreferencesKey("editor_autosave")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
        val DEFAULT_MODEL = stringPreferencesKey("default_model")
        val DEFAULT_BUILD_MODE = stringPreferencesKey("default_build_mode")
        val TOOLCHAIN_ROOT = stringPreferencesKey("toolchain_root_override")
    }
}

@Singleton
class ProviderSettingsRepository @Inject constructor(
    @ApplicationContext
    context: Context,
    private val secureStore: SecureStore,
) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("provider_settings.preferences_pb") },
    )

    val settings: Flow<List<ProviderSettings>> = dataStore.data.map { preferences ->
        ProviderId.entries.map { providerId ->
            ProviderSettings(
                providerId = providerId,
                selectedModel = preferences[stringPreferencesKey("${providerId.name}_model")] ?: defaultModel(providerId),
                temperature = preferences[doublePreferencesKey("${providerId.name}_temperature")] ?: 0.2,
                maxTokens = preferences[intPreferencesKey("${providerId.name}_max_tokens")] ?: 4096,
                topP = preferences[doublePreferencesKey("${providerId.name}_top_p")] ?: 0.95,
                enabled = preferences[booleanPreferencesKey("${providerId.name}_enabled")] ?: true,
            )
        }
    }

    suspend fun update(settings: ProviderSettings) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("${settings.providerId.name}_model")] = settings.selectedModel
            prefs[doublePreferencesKey("${settings.providerId.name}_temperature")] = settings.temperature
            prefs[intPreferencesKey("${settings.providerId.name}_max_tokens")] = settings.maxTokens
            prefs[doublePreferencesKey("${settings.providerId.name}_top_p")] = settings.topP
            prefs[booleanPreferencesKey("${settings.providerId.name}_enabled")] = settings.enabled
        }
    }

    fun secretsState(): List<ProviderSecret> = ProviderId.entries.map {
        ProviderSecret(it, !secureStore.getApiKey(it).isNullOrBlank())
    }

    fun putApiKey(providerId: ProviderId, apiKey: String) = secureStore.setApiKey(providerId, apiKey)

    fun apiKey(providerId: ProviderId): String? = secureStore.getApiKey(providerId)

    private fun defaultModel(providerId: ProviderId): String = when (providerId) {
        ProviderId.OPENROUTER -> "openai/gpt-4o-mini"
        ProviderId.GEMINI -> "gemini-2.0-flash"
        ProviderId.NVIDIA -> "meta/llama-3.1-70b-instruct"
    }
}

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val buildDao: BuildDao,
    private val artifactDao: ArtifactDao,
    private val snapshotDao: SnapshotDao,
    private val workspaceManager: WorkspaceManager,
    private val json: Json,
) {
    val projects: Flow<List<Project>> = projectDao.observeProjects().map { list -> list.map { it.asExternal() } }

    fun dashboard(search: Flow<String>, sortMode: Flow<SortMode>, filter: Flow<ProjectFilter>): Flow<DashboardState> =
        combine(
            projects,
            buildDao.observeRecentBuilds(limit = 10).map { builds -> builds.map { it.asExternal(json) } },
            search,
            sortMode,
            filter,
        ) { projects, builds, query, sort, filterMode ->
            val filtered = projects
                .filter { project ->
                    val matchesQuery = query.isBlank() || project.name.contains(query, ignoreCase = true) ||
                        project.packageName.contains(query, ignoreCase = true)
                    val matchesFilter = when (filterMode) {
                        ProjectFilter.ALL -> true
                        ProjectFilter.ACTIVE -> !project.archived
                        ProjectFilter.ARCHIVED -> project.archived
                    }
                    matchesQuery && matchesFilter
                }
                .let { list ->
                    when (sort) {
                        SortMode.RECENT -> list.sortedByDescending { it.lastOpenedAt }
                        SortMode.NAME -> list.sortedBy { it.name.lowercase() }
                    }
                }
            DashboardState(
                projects = filtered,
                recentBuilds = builds,
                search = query,
                sortMode = sort,
                filter = filterMode,
            )
        }

    fun observeProject(projectId: String): Flow<Project?> = projectDao.observeProject(projectId).map { it?.asExternal() }

    suspend fun getProject(projectId: String): Project? = projectDao.getProject(projectId)?.asExternal()

    suspend fun createProject(draft: ProjectDraft): Project {
        val project = workspaceManager.createProject(draft)
        projectDao.upsert(project.asEntity())
        return project
    }

    suspend fun importProject(uri: Uri): Project? {
        val root = workspaceManager.importProjectZip(uri)
        val manifestFile = File(root, "buildbuddy.json")
        if (!manifestFile.exists()) return null
        val manifest = runCatching {
            json.decodeFromString<WorkspaceManifest>(manifestFile.readText())
        }.getOrNull() ?: return null
        val now = System.currentTimeMillis()
        val project = Project(
            id = manifest.projectId,
            name = manifest.appName,
            packageName = manifest.packageName,
            description = manifest.description,
            language = manifest.language,
            uiToolkit = manifest.uiToolkit,
            minSdk = manifest.minSdk,
            targetSdk = manifest.targetSdk,
            accentColor = "#C58940",
            template = manifest.template,
            workspacePath = root.absolutePath,
            createdAt = now,
            updatedAt = now,
            lastOpenedAt = now,
            archived = false,
        )
        projectDao.upsert(project.asEntity())
        return project
    }

    suspend fun updateProject(project: Project) {
        projectDao.upsert(project.copy(updatedAt = System.currentTimeMillis()).asEntity())
    }

    suspend fun deleteProject(project: Project) {
        workspaceManager.delete(project, "")
        projectDao.delete(project.id)
    }

    fun listFiles(project: Project) = workspaceManager.listFiles(project)

    fun readFile(project: Project, path: String) = workspaceManager.readText(project, path)

    fun writeFile(project: Project, path: String, content: String) = workspaceManager.writeText(project, path, content)

    fun createFile(project: Project, path: String, content: String = "") = workspaceManager.createFile(project, path, content)

    fun createFolder(project: Project, path: String) = workspaceManager.createFolder(project, path)

    fun rename(project: Project, from: String, to: String) = workspaceManager.rename(project, from, to)

    fun deletePath(project: Project, path: String) = workspaceManager.delete(project, path)

    suspend fun duplicateProject(project: Project): Project {
        val duplicate = workspaceManager.duplicate(project)
        projectDao.upsert(duplicate.asEntity())
        return duplicate
    }

    fun exportProject(project: Project): File = workspaceManager.exportProjectZip(project)

    suspend fun snapshotProject(project: Project, reason: String): Snapshot {
        val snapshot = workspaceManager.createSnapshot(project, reason)
        snapshotDao.upsert(snapshot.asEntity())
        return snapshot
    }

    fun observeSnapshots(projectId: String): Flow<List<Snapshot>> =
        snapshotDao.observeSnapshots(projectId).map { list -> list.map { it.asExternal() } }

    suspend fun restoreSnapshot(project: Project, snapshot: Snapshot) {
        workspaceManager.restoreSnapshot(project, snapshot)
        projectDao.upsert(project.copy(updatedAt = System.currentTimeMillis()).asEntity())
    }

    fun observeArtifacts(projectId: String): Flow<List<Artifact>> =
        artifactDao.observeArtifacts(projectId).map { list -> list.map { it.asExternal() } }
}

@Singleton
class BuildRepository @Inject constructor(
    private val buildDao: BuildDao,
    private val artifactDao: ArtifactDao,
    private val projectDao: ProjectDao,
    private val json: Json,
) {
    fun observeBuilds(projectId: String): Flow<List<BuildRecord>> =
        buildDao.observeBuilds(projectId).map { list -> list.map { it.asExternal(json) } }

    fun recentBuilds(limit: Int = 10): Flow<List<BuildRecord>> =
        buildDao.observeRecentBuilds(limit).map { list -> list.map { it.asExternal(json) } }

    suspend fun upsertBuild(build: BuildRecord) {
        buildDao.upsert(build.asEntity(json))
        val project = projectDao.getProject(build.projectId)
        if (project != null) {
            projectDao.upsert(project.copy(lastBuildStatus = build.status))
        }
    }

    suspend fun createQueuedBuild(projectId: String, mode: BuildMode): BuildRecord {
        val build = BuildRecord(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            mode = mode,
            status = BuildStatus.QUEUED,
            supportLevel = com.build.buddyai.core.model.BuildSupportLevel.PARTIAL,
            startedAt = System.currentTimeMillis(),
            summary = "Queued for validation and toolchain execution",
            rawLog = "",
        )
        upsertBuild(build)
        return build
    }

    suspend fun upsertArtifact(artifact: Artifact) = artifactDao.upsert(artifact.asEntity())
}

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val json: Json,
) {
    fun observeConversation(projectId: String): Flow<Conversation?> =
        conversationDao.observeConversation(projectId).map { it?.asExternal() }

    suspend fun ensureConversation(
        projectId: String,
        providerId: ProviderId,
        modelId: String,
        mode: com.build.buddyai.core.model.AgentMode,
    ): Conversation {
        val existing = conversationDao.getConversation(projectId)?.asExternal()
        val conversation = existing?.copy(
            providerId = providerId,
            modelId = modelId,
            mode = mode,
            updatedAt = System.currentTimeMillis(),
        ) ?: Conversation(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            providerId = providerId,
            modelId = modelId,
            mode = mode,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        conversationDao.upsert(conversation.asEntity())
        return conversation
    }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        messageDao.observeMessages(conversationId).map { list -> list.map { it.asExternal(json) } }

    suspend fun upsertMessage(message: ChatMessage) = messageDao.upsert(message.asEntity(json))
}
