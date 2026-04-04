package com.build.buddyai.core.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.build.buddyai.core.model.AgentMode
import com.build.buddyai.core.model.Artifact
import com.build.buddyai.core.model.ArtifactType
import com.build.buddyai.core.model.BuildDiagnostic
import com.build.buddyai.core.model.BuildMode
import com.build.buddyai.core.model.BuildRecord
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.BuildSupportLevel
import com.build.buddyai.core.model.ChangeOperation
import com.build.buddyai.core.model.ChatMessage
import com.build.buddyai.core.model.Conversation
import com.build.buddyai.core.model.MessageRole
import com.build.buddyai.core.model.MessageStatus
import com.build.buddyai.core.model.Project
import com.build.buddyai.core.model.ProjectLanguage
import com.build.buddyai.core.model.ProjectTemplate
import com.build.buddyai.core.model.ProviderId
import com.build.buddyai.core.model.Snapshot
import com.build.buddyai.core.model.UiToolkit
import com.build.buddyai.core.model.WorkspaceChange
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "projects", indices = [Index(value = ["name"]), Index(value = ["lastOpenedAt"])])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val packageName: String,
    val description: String,
    val language: ProjectLanguage,
    val uiToolkit: UiToolkit,
    val minSdk: Int,
    val targetSdk: Int,
    val accentColor: String,
    val template: ProjectTemplate,
    val workspacePath: String,
    val iconPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long,
    val archived: Boolean,
    val defaultProvider: ProviderId?,
    val defaultModel: String?,
    val lastBuildStatus: BuildStatus?,
)

@Entity(tableName = "build_records", indices = [Index(value = ["projectId"]), Index(value = ["startedAt"])])
data class BuildRecordEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val mode: BuildMode,
    val status: BuildStatus,
    val supportLevel: BuildSupportLevel,
    val startedAt: Long,
    val finishedAt: Long?,
    val summary: String,
    val rawLog: String,
    val diagnosticsJson: String,
    val artifactId: String?,
)

@Entity(tableName = "artifacts", indices = [Index(value = ["projectId"]), Index(value = ["createdAt"])])
data class ArtifactEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val buildId: String?,
    val type: ArtifactType,
    val filePath: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val createdAt: Long,
    val fileSizeBytes: Long,
    val installable: Boolean,
)

@Entity(tableName = "conversations", indices = [Index(value = ["projectId"])])
data class ConversationEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val providerId: ProviderId,
    val modelId: String,
    val mode: AgentMode,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "messages", indices = [Index(value = ["conversationId"]), Index(value = ["createdAt"])])
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus,
    val timelineJson: String,
    val attachmentsJson: String,
    val proposedChangesJson: String,
    val createdAt: Long,
)

@Entity(tableName = "snapshots", indices = [Index(value = ["projectId"]), Index(value = ["createdAt"])])
data class SnapshotEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val label: String,
    val filePath: String,
    val createdAt: Long,
    val reason: String,
)

class DbConverters {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @TypeConverter
    fun diagnosticsToJson(value: List<BuildDiagnostic>): String = json.encodeToString(value)

    @TypeConverter
    fun diagnosticsFromJson(value: String): List<BuildDiagnostic> = runCatching {
        json.decodeFromString<List<BuildDiagnostic>>(value)
    }.getOrDefault(emptyList())

    @TypeConverter
    fun changesToJson(value: List<WorkspaceChange>): String = json.encodeToString(value)

    @TypeConverter
    fun changesFromJson(value: String): List<WorkspaceChange> = runCatching {
        json.decodeFromString<List<WorkspaceChange>>(value)
    }.getOrDefault(emptyList())
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastOpenedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    fun observeProject(projectId: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun getProject(projectId: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun delete(projectId: String)
}

@Dao
interface BuildDao {
    @Query("SELECT * FROM build_records WHERE projectId = :projectId ORDER BY startedAt DESC")
    fun observeBuilds(projectId: String): Flow<List<BuildRecordEntity>>

    @Query("SELECT * FROM build_records ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentBuilds(limit: Int): Flow<List<BuildRecordEntity>>

    @Query("SELECT * FROM build_records WHERE id = :buildId LIMIT 1")
    suspend fun getBuild(buildId: String): BuildRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(build: BuildRecordEntity)
}

@Dao
interface ArtifactDao {
    @Query("SELECT * FROM artifacts WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeArtifacts(projectId: String): Flow<List<ArtifactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(artifact: ArtifactEntity)

    @Query("SELECT * FROM artifacts WHERE id = :artifactId LIMIT 1")
    suspend fun getArtifact(artifactId: String): ArtifactEntity?

    @Query("DELETE FROM artifacts WHERE id = :artifactId")
    suspend fun delete(artifactId: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE projectId = :projectId ORDER BY updatedAt DESC LIMIT 1")
    fun observeConversation(projectId: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE projectId = :projectId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getConversation(projectId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeSnapshots(projectId: String): Flow<List<SnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: SnapshotEntity)
}

@Database(
    entities = [
        ProjectEntity::class,
        BuildRecordEntity::class,
        ArtifactEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        SnapshotEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DbConverters::class)
abstract class BuildBuddyDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun buildDao(): BuildDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun snapshotDao(): SnapshotDao
}

fun ProjectEntity.asExternal(): Project = Project(
    id = id,
    name = name,
    packageName = packageName,
    description = description,
    language = language,
    uiToolkit = uiToolkit,
    minSdk = minSdk,
    targetSdk = targetSdk,
    accentColor = accentColor,
    template = template,
    workspacePath = workspacePath,
    iconPath = iconPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastOpenedAt = lastOpenedAt,
    archived = archived,
    defaultProvider = defaultProvider,
    defaultModel = defaultModel,
    lastBuildStatus = lastBuildStatus,
)

fun Project.asEntity(): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    packageName = packageName,
    description = description,
    language = language,
    uiToolkit = uiToolkit,
    minSdk = minSdk,
    targetSdk = targetSdk,
    accentColor = accentColor,
    template = template,
    workspacePath = workspacePath,
    iconPath = iconPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastOpenedAt = lastOpenedAt,
    archived = archived,
    defaultProvider = defaultProvider,
    defaultModel = defaultModel,
    lastBuildStatus = lastBuildStatus,
)

fun BuildRecordEntity.asExternal(json: Json): BuildRecord = BuildRecord(
    id = id,
    projectId = projectId,
    mode = mode,
    status = status,
    supportLevel = supportLevel,
    startedAt = startedAt,
    finishedAt = finishedAt,
    summary = summary,
    rawLog = rawLog,
    diagnostics = runCatching {
        json.decodeFromString<List<BuildDiagnostic>>(diagnosticsJson)
    }.getOrDefault(emptyList()),
    artifactId = artifactId,
)

fun BuildRecord.asEntity(json: Json): BuildRecordEntity = BuildRecordEntity(
    id = id,
    projectId = projectId,
    mode = mode,
    status = status,
    supportLevel = supportLevel,
    startedAt = startedAt,
    finishedAt = finishedAt,
    summary = summary,
    rawLog = rawLog,
    diagnosticsJson = json.encodeToString(diagnostics),
    artifactId = artifactId,
)

fun ArtifactEntity.asExternal(): Artifact = Artifact(
    id = id,
    projectId = projectId,
    buildId = buildId,
    type = type,
    filePath = filePath,
    packageName = packageName,
    versionName = versionName,
    versionCode = versionCode,
    createdAt = createdAt,
    fileSizeBytes = fileSizeBytes,
    installable = installable,
)

fun Artifact.asEntity(): ArtifactEntity = ArtifactEntity(
    id = id,
    projectId = projectId,
    buildId = buildId,
    type = type,
    filePath = filePath,
    packageName = packageName,
    versionName = versionName,
    versionCode = versionCode,
    createdAt = createdAt,
    fileSizeBytes = fileSizeBytes,
    installable = installable,
)

fun ConversationEntity.asExternal(): Conversation = Conversation(
    id = id,
    projectId = projectId,
    providerId = providerId,
    modelId = modelId,
    mode = mode,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Conversation.asEntity(): ConversationEntity = ConversationEntity(
    id = id,
    projectId = projectId,
    providerId = providerId,
    modelId = modelId,
    mode = mode,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MessageEntity.asExternal(json: Json): ChatMessage = ChatMessage(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    status = status,
    timeline = runCatching {
        json.decodeFromString(timelineJson)
    }.getOrDefault(emptyList()),
    attachments = runCatching {
        json.decodeFromString(attachmentsJson)
    }.getOrDefault(emptyList()),
    proposedChanges = runCatching {
        json.decodeFromString(proposedChangesJson)
    }.getOrDefault(emptyList()),
    createdAt = createdAt,
)

fun ChatMessage.asEntity(json: Json): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    status = status,
    timelineJson = json.encodeToString(timeline),
    attachmentsJson = json.encodeToString(attachments),
    proposedChangesJson = json.encodeToString(proposedChanges),
    createdAt = createdAt,
)

fun SnapshotEntity.asExternal(): Snapshot = Snapshot(
    id = id,
    projectId = projectId,
    label = label,
    filePath = filePath,
    createdAt = createdAt,
    reason = reason,
)

fun Snapshot.asEntity(): SnapshotEntity = SnapshotEntity(
    id = id,
    projectId = projectId,
    label = label,
    filePath = filePath,
    createdAt = createdAt,
    reason = reason,
)

