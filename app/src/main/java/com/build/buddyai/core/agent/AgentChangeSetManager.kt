package com.build.buddyai.core.agent

import android.content.Context
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.model.FileDiff
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentChangeSetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val javaAstEditor: JavaAstEditor,
    private val xmlDomEditor: XmlDomEditor
) {

    fun preview(
        projectDir: File,
        writes: List<AgentFileWrite>,
        deletes: List<String>,
        operations: List<AgentEditOperation>
    ): List<FileDiff> {
        val touchedPaths = collectTouchedPaths(writes, deletes, operations)
        val before = touchedPaths.associateWith { path -> FileUtils.readFileContent(projectDir, path) }
        val operationOutputs = operations.groupBy { FileUtils.normalizeRelativePath(it.path) }.mapValues { (path, ops) ->
            val original = before[path] ?: ""
            applyOperationsToContent(path, original, ops)
        }

        return touchedPaths.map { path ->
            val updated = when {
                path in deletes.map { FileUtils.normalizeRelativePath(it) } -> null
                writes.any { FileUtils.normalizeRelativePath(it.path) == path } -> writes.last { FileUtils.normalizeRelativePath(it.path) == path }.content
                operationOutputs.containsKey(path) -> operationOutputs[path]
                else -> before[path]
            }
            FileDiff(
                filePath = path,
                originalContent = before[path].orEmpty(),
                modifiedContent = updated.orEmpty(),
                additions = updated?.lineSequence()?.count() ?: 0,
                deletions = before[path]?.lineSequence()?.count() ?: 0,
                isNewFile = before[path] == null && !updated.isNullOrEmpty(),
                isDeleted = before[path] != null && updated == null
            )
        }.sortedBy { it.filePath }
    }

    fun apply(
        projectId: String,
        projectDir: File,
        summary: String,
        writes: List<AgentFileWrite>,
        deletes: List<String>,
        operations: List<AgentEditOperation>
    ): AppliedChangeSet {
        val touchedPaths = collectTouchedPaths(writes, deletes, operations)
        val before = touchedPaths.associateWith { path -> FileUtils.readFileContent(projectDir, path) }

        return try {
            operations.groupBy { FileUtils.normalizeRelativePath(it.path) }.forEach { (path, ops) ->
                val original = before[path] ?: throw IllegalArgumentException("Cannot apply edit operations to missing file: $path")
                FileUtils.writeFileContent(projectDir, path, applyOperationsToContent(path, original, ops))
            }

            writes.forEach { write ->
                val normalized = FileUtils.normalizeRelativePath(write.path)
                FileUtils.writeFileContent(projectDir, normalized, write.content)
            }

            deletes.forEach { deletePath ->
                val normalized = FileUtils.normalizeRelativePath(deletePath)
                FileUtils.deleteFileOrDir(projectDir, normalized)
            }

            val diffs = touchedPaths.map { path ->
                val original = before[path].orEmpty()
                val updated = FileUtils.readFileContent(projectDir, path).orEmpty()
                FileDiff(
                    filePath = path,
                    originalContent = original,
                    modifiedContent = updated,
                    additions = updated.lineSequence().count().coerceAtLeast(if (updated.isEmpty()) 0 else 1),
                    deletions = original.lineSequence().count(),
                    isNewFile = before[path] == null && updated.isNotEmpty(),
                    isDeleted = before[path] != null && updated.isEmpty() && !FileUtils.resolveProjectFile(projectDir, path).exists()
                )
            }.sortedBy { it.filePath }

            val changeSet = ChangeSet(
                id = UUID.randomUUID().toString(),
                summary = summary.ifBlank { "Agent change set" },
                createdAt = System.currentTimeMillis(),
                changes = touchedPaths.map { path ->
                    ChangeEntry(
                        path = path,
                        beforeContent = before[path],
                        afterContent = FileUtils.readFileContent(projectDir, path),
                        deleted = !FileUtils.resolveProjectFile(projectDir, path).exists()
                    )
                }
            )
            persist(projectId, changeSet)
            AppliedChangeSet(changeSet = changeSet, diffs = diffs)
        } catch (e: Exception) {
            before.forEach { (path, content) ->
                val file = FileUtils.resolveProjectFile(projectDir, path)
                if (content == null) {
                    if (file.exists()) file.deleteRecursively()
                } else {
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                }
            }
            throw e
        }
    }

    fun list(projectId: String): List<ChangeSet> = listFile(projectId)
        .takeIf { it.exists() }
        ?.readText()
        ?.let { runCatching { json.decodeFromString<List<ChangeSet>>(it) }.getOrDefault(emptyList()) }
        .orEmpty()
        .sortedByDescending { it.createdAt }

    fun rollback(projectId: String, changeSetId: String, projectDir: File): List<FileDiff> {
        val changeSet = list(projectId).firstOrNull { it.id == changeSetId }
            ?: throw IllegalArgumentException("Change set not found")
        val diffs = mutableListOf<FileDiff>()
        changeSet.changes.forEach { change ->
            val current = FileUtils.readFileContent(projectDir, change.path).orEmpty()
            if (change.beforeContent == null) {
                FileUtils.deleteFileOrDir(projectDir, change.path)
            } else {
                FileUtils.writeFileContent(projectDir, change.path, change.beforeContent)
            }
            diffs += FileDiff(
                filePath = change.path,
                originalContent = current,
                modifiedContent = change.beforeContent.orEmpty(),
                additions = change.beforeContent?.lineSequence()?.count() ?: 0,
                deletions = current.lineSequence().count(),
                isDeleted = change.beforeContent == null
            )
        }
        return diffs.sortedBy { it.filePath }
    }

    private fun collectTouchedPaths(
        writes: List<AgentFileWrite>,
        deletes: List<String>,
        operations: List<AgentEditOperation>
    ): List<String> = (writes.map { it.path } + deletes + operations.map { it.path }).mapNotNull {
        runCatching { FileUtils.normalizeRelativePath(it) }.getOrNull()
    }.distinct()

    private fun applyOperationsToContent(path: String, source: String, operations: List<AgentEditOperation>): String {
        return when (File(path).extension.lowercase()) {
            "java" -> javaAstEditor.apply(source, operations)
            "xml" -> xmlDomEditor.apply(source, operations)
            else -> applyTextOperations(source, operations)
        }
    }

    private fun applyTextOperations(source: String, operations: List<AgentEditOperation>): String {
        var current = source
        operations.forEach { op ->
            if (op.kind == "text_replace") current = current.replace(op.target.toRegex(), op.payload)
        }
        return current
    }

    private fun persist(projectId: String, changeSet: ChangeSet) {
        val entries = (list(projectId) + changeSet).sortedByDescending { it.createdAt }.take(20)
        val file = listFile(projectId)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString<List<ChangeSet>>(entries))
    }

    private fun listFile(projectId: String) = File(context.filesDir, "agent_change_sets/$projectId.json")

    data class AppliedChangeSet(
        val changeSet: ChangeSet,
        val diffs: List<FileDiff>
    )

    @Serializable
    data class ChangeSet(
        val id: String,
        val summary: String,
        val createdAt: Long,
        val changes: List<ChangeEntry>
    )

    @Serializable
    data class ChangeEntry(
        val path: String,
        val beforeContent: String? = null,
        val afterContent: String? = null,
        val deleted: Boolean = false
    )
}
