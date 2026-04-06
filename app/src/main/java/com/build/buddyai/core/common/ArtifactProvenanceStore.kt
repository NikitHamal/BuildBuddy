package com.build.buddyai.core.common

import android.content.Context
import com.build.buddyai.core.model.BuildProblem
import com.build.buddyai.core.model.BuildProfile
import com.build.buddyai.core.model.BuildRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtifactProvenanceStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    fun save(record: ArtifactProvenance) {
        file(record.artifactId).apply {
            parentFile?.mkdirs()
            writeText(json.encodeToString(record))
        }
    }

    fun load(artifactId: String): ArtifactProvenance? {
        val file = file(artifactId)
        return if (!file.exists()) null else runCatching { json.decodeFromString<ArtifactProvenance>(file.readText()) }.getOrNull()
    }

    private fun file(artifactId: String) = File(context.filesDir, "artifact_provenance/$artifactId.json")
}

@Serializable
data class ArtifactProvenance(
    val artifactId: String,
    val buildRecordId: String,
    val projectId: String,
    val projectName: String,
    val artifactPath: String,
    val artifactName: String,
    val createdAt: Long,
    val buildProfile: BuildProfile,
    val changeSetIds: List<String>,
    val signerAlias: String?,
    val warnings: List<String>,
    val problems: List<BuildProblem>,
    val timeline: List<String>,
    val templateOrigin: String?,
    val validationSummary: String,
    val buildRecordSummary: String
) {
    companion object {
        private const val MAX_PLACEHOLDERS = 24
        private const val MAX_PLACEHOLDER_KEY_LENGTH = 64
        private const val MAX_PLACEHOLDER_VALUE_LENGTH = 256

        fun from(
            artifactId: String,
            artifactPath: String,
            projectId: String,
            projectName: String,
            buildRecord: BuildRecord,
            buildProfile: BuildProfile,
            changeSetIds: List<String>,
            warnings: List<String>,
            problems: List<BuildProblem>,
            timeline: List<String>,
            templateOrigin: String?,
            validationSummary: String
        ): ArtifactProvenance = ArtifactProvenance(
            artifactId = artifactId,
            buildRecordId = buildRecord.id,
            projectId = projectId,
            projectName = projectName,
            artifactPath = artifactPath,
            artifactName = File(artifactPath).name,
            createdAt = buildRecord.completedAt ?: System.currentTimeMillis(),
            buildProfile = compactBuildProfile(buildProfile),
            changeSetIds = changeSetIds,
            signerAlias = buildProfile.signing?.keyAlias?.takeIf { it.isNotBlank() },
            warnings = warnings,
            problems = problems,
            timeline = timeline,
            templateOrigin = templateOrigin,
            validationSummary = validationSummary,
            buildRecordSummary = listOfNotNull(
                buildRecord.status.displayName,
                buildRecord.buildVariant,
                buildRecord.errorSummary
            ).joinToString(" | ")
        )

        private fun compactBuildProfile(profile: BuildProfile): BuildProfile {
            val compactPlaceholders = profile.manifestPlaceholders
                .entries
                .sortedBy { it.key }
                .take(MAX_PLACEHOLDERS)
                .associate { entry ->
                    entry.key.take(MAX_PLACEHOLDER_KEY_LENGTH) to entry.value.take(MAX_PLACEHOLDER_VALUE_LENGTH)
                }

            return profile.copy(
                flavorName = profile.flavorName.take(40),
                applicationIdSuffix = profile.applicationIdSuffix.take(80),
                versionNameSuffix = profile.versionNameSuffix.take(80),
                versionNameOverride = profile.versionNameOverride?.take(80),
                signing = profile.signing?.copy(
                    keystoreFileName = profile.signing.keystoreFileName.take(120),
                    keystorePath = profile.signing.keystorePath.substringAfterLast(File.separatorChar),
                    keyAlias = profile.signing.keyAlias.take(80)
                ),
                manifestPlaceholders = compactPlaceholders
            )
        }
    }
}
