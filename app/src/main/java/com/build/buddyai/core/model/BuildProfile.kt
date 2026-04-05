package com.build.buddyai.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BuildProfile(
    val variant: BuildVariant = BuildVariant.DEBUG,
    val installAfterBuild: Boolean = true,
    val signing: SigningConfig? = null
)

@Serializable
enum class BuildVariant(val displayName: String) {
    DEBUG("Debug"),
    RELEASE("Release")
}

@Serializable
data class SigningConfig(
    val keystoreFileName: String,
    val keystorePath: String,
    val keyAlias: String,
    val importedAt: Long = System.currentTimeMillis()
)

@Serializable
data class BuildProblem(
    val severity: ProblemSeverity,
    val title: String,
    val detail: String,
    val filePath: String? = null,
    val lineNumber: Int? = null
)

@Serializable
enum class ProblemSeverity { INFO, WARNING, ERROR }

@Serializable
data class BuildTimelineEntry(
    val label: String,
    val detail: String,
    val status: ActionStatus,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class RestorePoint(
    val id: String,
    val fileName: String,
    val path: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val label: String
)
