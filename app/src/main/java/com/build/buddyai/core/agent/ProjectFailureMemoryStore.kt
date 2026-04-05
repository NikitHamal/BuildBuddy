package com.build.buddyai.core.agent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectFailureMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    fun recordFailure(projectId: String, contextText: String, editedFiles: List<String>, request: String): String {
        val signature = signatureFor(contextText)
        val rootCause = classifyRootCause(contextText)
        val affectedAreas = inferAreas(editedFiles, contextText)
        val store = load(projectId).toMutableList()
        val existing = store.indexOfFirst { it.signature == signature || (it.rootCauseClass == rootCause && it.affectedAreas == affectedAreas) }
        val updated = if (existing >= 0) {
            store[existing].copy(
                occurrences = store[existing].occurrences + 1,
                lastSeenAt = System.currentTimeMillis(),
                recentContext = contextText.take(3000),
                recentRequest = request.take(1500),
                recentEditedFiles = (editedFiles + store[existing].recentEditedFiles).distinct().take(20),
                rootCauseClass = rootCause,
                affectedAreas = affectedAreas
            )
        } else {
            FailurePattern(
                signature = signature,
                occurrences = 1,
                lastSeenAt = System.currentTimeMillis(),
                recentContext = contextText.take(3000),
                recentRequest = request.take(1500),
                recentEditedFiles = editedFiles.take(20),
                rootCauseClass = rootCause,
                affectedAreas = affectedAreas
            )
        }
        if (existing >= 0) store[existing] = updated else store += updated
        save(projectId, store)
        return updated.signature
    }

    fun markResolved(projectId: String, signature: String, resolution: String) {
        val store = load(projectId).map { pattern ->
            if (pattern.signature == signature) {
                pattern.copy(
                    lastResolution = resolution.take(1200),
                    resolvedCount = pattern.resolvedCount + 1,
                    successfulRepairSequences = (pattern.successfulRepairSequences + resolution.take(400)).takeLast(8)
                )
            } else pattern
        }
        save(projectId, store)
    }

    fun summarize(projectId: String, maxEntries: Int = 6): String {
        val entries = load(projectId)
            .sortedByDescending { it.localSuccessScore() }
            .take(maxEntries)
        if (entries.isEmpty()) return "No prior local failure memory."
        return buildString {
            appendLine("Local learned repair memory:")
            entries.forEachIndexed { index, entry ->
                append(index + 1)
                append(". signature=")
                append(entry.signature)
                append(" rootCause=")
                append(entry.rootCauseClass)
                append(" areas=")
                append(entry.affectedAreas.joinToString())
                append(" occurrences=")
                append(entry.occurrences)
                append(" resolved=")
                append(entry.resolvedCount)
                append(" successScore=")
                append(String.format("%.2f", entry.localSuccessScore()))
                appendLine()
                appendLine("   context: ${entry.recentContext.lineSequence().take(3).joinToString(" | ")}")
                if (entry.lastResolution.isNotBlank()) appendLine("   last successful repair: ${entry.lastResolution}")
            }
        }.trim()
    }

    private fun signatureFor(raw: String): String = raw
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(4)
        .joinToString(" | ")
        .lowercase()
        .replace(Regex("/data/user/0/[^ ]+"), "<project>")
        .replace(Regex("[0-9]{2,}"), "#")
        .take(220)
        .ifBlank { "unknown_failure" }

    private fun classifyRootCause(raw: String): String {
        val text = raw.lowercase()
        return when {
            "manifest" in text -> "manifest"
            "aapt2" in text || "resource" in text || "@string/" in text || "r." in text -> "resources"
            "unresolved reference" in text || "cannot find symbol" in text || "import" in text -> "symbol-resolution"
            "package" in text && "namespace" in text -> "package-namespace"
            "keystore" in text || "sign" in text || "apk signer" in text -> "signing"
            "build.gradle" in text || "gradle" in text || "dependency" in text -> "build-config"
            "xml" in text || ".xml:" in text -> "xml-layout"
            "kotlin" in text || ".kt:" in text -> "kotlin"
            ".java:" in text || "ecj" in text -> "java"
            else -> "general"
        }
    }

    private fun inferAreas(editedFiles: List<String>, raw: String): List<String> {
        val fileAreas = editedFiles.map { path ->
            when {
                path.contains("AndroidManifest.xml", ignoreCase = true) -> "manifest"
                path.contains("build.gradle", ignoreCase = true) || path.contains("settings.gradle", ignoreCase = true) -> "gradle"
                path.contains("res/", ignoreCase = true) -> "resources"
                path.endsWith(".xml", true) -> "xml"
                path.endsWith(".kt", true) -> "kotlin"
                path.endsWith(".java", true) -> "java"
                else -> "project"
            }
        }
        val classified = classifyRootCause(raw)
        return (fileAreas + classified).distinct().take(6)
    }

    private fun load(projectId: String): List<FailurePattern> {
        val file = file(projectId)
        return if (!file.exists()) emptyList() else runCatching { json.decodeFromString<List<FailurePattern>>(file.readText()) }.getOrDefault(emptyList())
    }

    private fun save(projectId: String, entries: List<FailurePattern>) {
        file(projectId).apply {
            parentFile?.mkdirs()
            writeText(json.encodeToString<List<FailurePattern>>(entries.sortedByDescending { it.lastSeenAt }.take(40)))
        }
    }

    private fun file(projectId: String) = File(context.filesDir, "agent_failure_memory/$projectId.json")

    @Serializable
    data class FailurePattern(
        val signature: String,
        val occurrences: Int,
        val resolvedCount: Int = 0,
        val lastSeenAt: Long,
        val recentContext: String,
        val recentRequest: String,
        val recentEditedFiles: List<String>,
        val lastResolution: String = "",
        val rootCauseClass: String = "general",
        val affectedAreas: List<String> = emptyList(),
        val successfulRepairSequences: List<String> = emptyList()
    ) {
        fun localSuccessScore(): Double = (resolvedCount.toDouble() + 1.0) / (occurrences.toDouble() + 1.0)
    }
}
