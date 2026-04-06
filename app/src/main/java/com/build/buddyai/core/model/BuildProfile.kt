package com.build.buddyai.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BuildProfile(
    val variant: BuildVariant = BuildVariant.DEBUG,
    val installAfterBuild: Boolean = true,
    val signing: SigningConfig? = null,
    val artifactFormat: ArtifactFormat = ArtifactFormat.APK,
    val flavorName: String = "main",
    val applicationIdSuffix: String = "",
    val versionNameSuffix: String = "",
    val versionCodeOverride: Int? = null,
    val versionNameOverride: String? = null,
    val manifestPlaceholders: Map<String, String> = emptyMap()
)

@Serializable
enum class BuildVariant(val displayName: String) {
    DEBUG("Debug"),
    RELEASE("Release")
}

@Serializable
enum class ArtifactFormat(val displayName: String, val extension: String) {
    APK("APK", "apk"),
    AAB("Android App Bundle", "aab")
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
