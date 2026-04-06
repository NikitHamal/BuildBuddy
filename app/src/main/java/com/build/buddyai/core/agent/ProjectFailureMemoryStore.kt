package com.build.buddyai.core.agent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectFailureMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {

    data class MemoryContext(
        val templateFamily: String,
        val buildEngine: String,
        val language: String,
        val requestHint: String = ""
    )

    fun recordFailure(projectId: String, contextText: String, editedFiles: List<String>, request: String, memoryContext: MemoryContext): String {
        val signature = signatureFor(contextText)
        val rootCause = classifyRootCause(contextText)
        val affectedAreas = inferAreas(editedFiles, contextText)
        val store = load(projectId).toMutableList()
        val existing = store.indexOfFirst {
            it.signature == signature ||
                (it.rootCauseClass == rootCause && it.affectedAreas == affectedAreas && it.templateFamily == memoryContext.templateFamily)
        }
        val updated = if (existing >= 0) {
            store[existing].copy(
                occurrences = store[existing].occurrences + 1,
                lastSeenAt = System.currentTimeMillis(),
                recentContext = contextText.take(3000),
                recentRequest = request.take(1500),
                recentEditedFiles = (editedFiles + store[existing].recentEditedFiles).distinct().take(20),
                rootCauseClass = rootCause,
                affectedAreas = affectedAreas,
                templateFamily = memoryContext.templateFamily,
                buildEngine = memoryContext.buildEngine,
                language = memoryContext.language
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
                affectedAreas = affectedAreas,
                templateFamily = memoryContext.templateFamily,
                buildEngine = memoryContext.buildEngine,
                language = memoryContext.language
            )
        }
        if (existing >= 0) store[existing] = updated else store += updated
        save(projectId, store)
        return updated.signature
    }

    fun markResolved(projectId: String, signature: String, resolution: String, repairSequence: String = resolution) {
        val store = load(projectId).map { pattern ->
            if (pattern.signature == signature) {
                pattern.copy(
                    lastResolution = resolution.take(1200),
                    resolvedCount = pattern.resolvedCount + 1,
                    successfulRepairSequences = (pattern.successfulRepairSequences + repairSequence.take(500)).takeLast(12)
                )
            } else pattern
        }
        save(projectId, store)
    }

    fun summarize(projectId: String, memoryContext: MemoryContext, maxEntries: Int = 6): String {
        val localEntries = load(projectId)
            .sortedByDescending { rank(it, memoryContext) }
            .take(maxEntries)
        val globalFallback = loadGlobalFallback(projectId, memoryContext, maxEntries = 4)
            .filterNot { global -> localEntries.any { it.signature == global.signature } }
            .take(3)

        if (localEntries.isEmpty() && globalFallback.isEmpty()) return "No prior failure memory."
        return buildString {
            if (localEntries.isNotEmpty()) {
                appendLine("Local learned repair memory:")
                localEntries.forEachIndexed { index, entry ->
                    appendLine(
                        "${index + 1}. signature=${entry.signature} rootCause=${entry.rootCauseClass} template=${entry.templateFamily} engine=${entry.buildEngine} occurrences=${entry.occurrences} resolved=${entry.resolvedCount} rank=${"%.2f".format(rank(entry, memoryContext))}"
                    )
                    appendLine("   areas: ${entry.affectedAreas.joinToString()}")
                    appendLine("   context: ${entry.recentContext.lineSequence().take(3).joinToString(" | ")}")
                    if (entry.lastResolution.isNotBlank()) appendLine("   last successful repair: ${entry.lastResolution}")
                    if (entry.successfulRepairSequences.isNotEmpty()) appendLine("   winning sequence: ${entry.successfulRepairSequences.last()}")
                }
            }
            if (globalFallback.isNotEmpty()) {
                if (localEntries.isNotEmpty()) appendLine()
                appendLine("Cross-project fallback memory:")
                globalFallback.forEachIndexed { index, entry ->
                    appendLine(
                        "${index + 1}. signature=${entry.signature} rootCause=${entry.rootCauseClass} template=${entry.templateFamily} engine=${entry.buildEngine} occurrences=${entry.occurrences} resolved=${entry.resolvedCount} rank=${"%.2f".format(rank(entry, memoryContext))}"
                    )
                    appendLine("   areas: ${entry.affectedAreas.joinToString()}")
                    appendLine("   context: ${entry.recentContext.lineSequence().take(2).joinToString(" | ")}")
                    if (entry.lastResolution.isNotBlank()) appendLine("   last successful repair: ${entry.lastResolution}")
                }
            }
        }.trim()
    }

    private fun loadGlobalFallback(projectId: String, memoryContext: MemoryContext, maxEntries: Int): List<FailurePattern> {
        val dir = File(context.filesDir, "agent_failure_memory")
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .filterNot { it.nameWithoutExtension == projectId }
            .mapNotNull { file ->
                runCatching { json.decodeFromString<List<FailurePattern>>(file.readText()) }.getOrNull()
            }
            .flatten()
            .sortedByDescending { rank(it, memoryContext) }
            .take(maxEntries)
            .toList()
    }

    private fun rank(entry: FailurePattern, context: MemoryContext): Double {
        var score = entry.localSuccessScore() * 10.0
        if (entry.templateFamily == context.templateFamily) score += 4.0
        if (entry.buildEngine == context.buildEngine) score += 3.0
        if (entry.language == context.language) score += 2.0
        if (context.requestHint.isNotBlank() && entry.recentRequest.contains(context.requestHint, ignoreCase = true)) score += 1.5
        return score
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
            writeText(json.encodeToString<List<FailurePattern>>(entries.sortedByDescending { it.lastSeenAt }.take(60)))
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
        val successfulRepairSequences: List<String> = emptyList(),
        val templateFamily: String = "general",
        val buildEngine: String = "on-device-aapt2-ecj-d8",
        val language: String = "unknown"
    ) {
        fun localSuccessScore(): Double = (resolvedCount.toDouble() + 1.0) / (occurrences.toDouble() + 1.0)
    }
}
