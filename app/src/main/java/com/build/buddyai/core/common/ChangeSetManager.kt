package com.build.buddyai.core.common

import android.content.Context
import com.build.buddyai.core.model.FileDiff
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ChangeSetPayload(
    val id: String,
    val projectId: String,
    val summary: String,
    val source: String,
    val createdAt: Long,
    val diffs: List<FileDiff>
)

data class ChangeSetInfo(
    val id: String,
    val summary: String,
    val source: String,
    val path: String,
    val createdAt: Long,
    val changeCount: Int,
    val diffs: List<FileDiff>
)

@Singleton
class ChangeSetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun recordChangeSet(
        projectId: String,
        summary: String,
        source: String,
        diffs: List<FileDiff>
    ): ChangeSetInfo {
        val id = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val payload = ChangeSetPayload(
            id = id,
            projectId = projectId,
            summary = summary,
            source = source,
            createdAt = createdAt,
            diffs = diffs
        )
        val dir = projectChangeSetDir(projectId).apply { mkdirs() }
        val file = File(dir, "${dateFormat.format(Date(createdAt))}_${id}.json")
        file.writeText(json.encodeToString(payload))
        return payload.toInfo(file)
    }

    fun listChangeSets(projectId: String): List<ChangeSetInfo> {
        val dir = projectChangeSetDir(projectId)
        return dir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.mapNotNull { file ->
                runCatching {
                    json.decodeFromString<ChangeSetPayload>(file.readText()).toInfo(file)
                }.getOrNull()
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    fun rollbackChangeSet(projectDir: File, changeSetPath: String): List<FileDiff> {
        val payload = readChangeSet(changeSetPath)
        val rollbackDiffs = mutableListOf<FileDiff>()
        payload.diffs.asReversed().forEach { diff ->
            val normalizedPath = FileUtils.normalizeRelativePath(diff.filePath)
            when {
                diff.isDeleted -> {
                    FileUtils.writeFileContent(projectDir, normalizedPath, diff.originalContent)
                    rollbackDiffs += diff.copy(
                        modifiedContent = diff.originalContent,
                        additions = diff.originalContent.lineSequence().count(),
                        deletions = 0,
                        isDeleted = false,
                        isNewFile = false
                    )
                }
                diff.isNewFile -> {
                    FileUtils.deleteFileOrDir(projectDir, normalizedPath)
                    rollbackDiffs += diff.copy(
                        modifiedContent = "",
                        additions = 0,
                        deletions = diff.modifiedContent.lineSequence().count(),
                        isDeleted = true,
                        isNewFile = false
                    )
                }
                else -> {
                    FileUtils.writeFileContent(projectDir, normalizedPath, diff.originalContent)
                    rollbackDiffs += diff.copy(
                        modifiedContent = diff.originalContent,
                        additions = diff.originalContent.lineSequence().count(),
                        deletions = diff.modifiedContent.lineSequence().count()
                    )
                }
            }
        }
        return rollbackDiffs
    }

    fun deleteChangeSet(changeSetPath: String) {
        File(changeSetPath).takeIf { it.exists() }?.delete()
    }

    private fun readChangeSet(changeSetPath: String): ChangeSetPayload {
        val file = File(changeSetPath).canonicalFile
        val root = File(context.filesDir, "changesets").canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "Change set path escapes sandbox" }
        require(file.exists() && file.isFile) { "Change set file not found" }
        return json.decodeFromString(file.readText())
    }

    private fun projectChangeSetDir(projectId: String): File = File(File(context.filesDir, "changesets"), projectId)

    private fun ChangeSetPayload.toInfo(file: File) = ChangeSetInfo(
        id = id,
        summary = summary,
        source = source,
        path = file.absolutePath,
        createdAt = createdAt,
        changeCount = diffs.size,
        diffs = diffs
    )
}
