package com.build.buddyai.core.data.database.converter

import com.build.buddyai.core.data.database.entity.*
import com.build.buddyai.core.model.*

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    name = name,
    packageName = packageName,
    description = description,
    language = ProjectLanguage.valueOf(language),
    uiFramework = UiFramework.valueOf(uiFramework),
    template = ProjectTemplate.valueOf(template),
    minSdk = minSdk,
    targetSdk = targetSdk,
    iconUri = iconUri,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastBuildAt = lastBuildAt,
    lastBuildStatus = lastBuildStatus?.let { runCatching { BuildStatus.valueOf(it) }.getOrNull() },
    isArchived = isArchived,
    modelOverrideId = modelOverrideId,
    projectPath = projectPath
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    packageName = packageName,
    description = description,
    language = language.name,
    uiFramework = uiFramework.name,
    template = template.name,
    minSdk = minSdk,
    targetSdk = targetSdk,
    iconUri = iconUri,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastBuildAt = lastBuildAt,
    lastBuildStatus = lastBuildStatus?.name,
    isArchived = isArchived,
    modelOverrideId = modelOverrideId,
    projectPath = projectPath
)

fun ChatSessionEntity.toDomain(): ChatSession = ChatSession(
    id = id,
    projectId = projectId,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    messageCount = messageCount,
    modelId = modelId,
    providerId = providerId
)

fun ChatSession.toEntity(): ChatSessionEntity = ChatSessionEntity(
    id = id,
    projectId = projectId,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    messageCount = messageCount,
    modelId = modelId,
    providerId = providerId
)

fun ChatMessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    sessionId = sessionId,
    role = MessageRole.valueOf(role),
    content = content,
    timestamp = timestamp,
    status = MessageStatus.valueOf(status),
    toolActions = emptyList(),
    attachedFiles = attachedFilesJson?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
    tokenCount = tokenCount,
    modelId = modelId
)

fun ChatMessage.toEntity(): ChatMessageEntity = ChatMessageEntity(
    id = id,
    sessionId = sessionId,
    role = role.name,
    content = content,
    timestamp = timestamp,
    status = status.name,
    toolActionsJson = null,
    attachedFilesJson = attachedFiles.joinToString(","),
    tokenCount = tokenCount,
    modelId = modelId
)

fun BuildRecordEntity.toDomain(): BuildRecord = BuildRecord(
    id = id,
    projectId = projectId,
    status = BuildStatus.valueOf(status),
    startedAt = startedAt,
    completedAt = completedAt,
    durationMs = durationMs,
    variant = variant,
    artifactPath = artifactPath,
    artifactSizeBytes = artifactSizeBytes,
    logEntries = emptyList(),
    errorSummary = errorSummary
)

fun BuildRecord.toEntity(): BuildRecordEntity = BuildRecordEntity(
    id = id,
    projectId = projectId,
    status = status.name,
    startedAt = startedAt,
    completedAt = completedAt,
    durationMs = durationMs,
    variant = variant,
    artifactPath = artifactPath,
    artifactSizeBytes = artifactSizeBytes,
    logEntriesJson = null,
    errorSummary = errorSummary
)

fun BuildArtifactEntity.toDomain(): BuildArtifact = BuildArtifact(
    id = id,
    projectId = projectId,
    buildId = buildId,
    fileName = fileName,
    filePath = filePath,
    sizeBytes = sizeBytes,
    createdAt = createdAt,
    packageName = packageName,
    versionName = versionName,
    versionCode = versionCode,
    isInstalled = isInstalled
)

fun BuildArtifact.toEntity(): BuildArtifactEntity = BuildArtifactEntity(
    id = id,
    projectId = projectId,
    buildId = buildId,
    fileName = fileName,
    filePath = filePath,
    sizeBytes = sizeBytes,
    createdAt = createdAt,
    packageName = packageName,
    versionName = versionName,
    versionCode = versionCode,
    isInstalled = isInstalled
)

fun SnapshotEntity.toDomain(): Snapshot = Snapshot(
    id = id,
    projectId = projectId,
    label = label,
    createdAt = createdAt,
    description = description,
    fileCount = fileCount,
    isAutoSnapshot = isAutoSnapshot
)

fun Snapshot.toEntity(): SnapshotEntity = SnapshotEntity(
    id = id,
    projectId = projectId,
    label = label,
    createdAt = createdAt,
    description = description,
    fileCount = fileCount,
    isAutoSnapshot = isAutoSnapshot
)
