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
class AgentToolMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    fun record(projectId: String, stage: String, summary: String, files: List<String> = emptyList()) {
        val entries = load(projectId).toMutableList()
        entries += ToolMemoryEntry(stage = stage, summary = summary.take(2500), files = files.take(20))
        save(projectId, entries.takeLast(20))
    }

    fun summarize(projectId: String, maxEntries: Int = 8): String {
        val entries = load(projectId).takeLast(maxEntries)
        if (entries.isEmpty()) return "No prior local tool-result memory."
        return buildString {
            appendLine("Recent local tool results:")
            entries.forEach { entry ->
                appendLine("- [${entry.stage}] ${entry.summary}")
                if (entry.files.isNotEmpty()) appendLine("  files: ${entry.files.joinToString()}")
            }
        }.trim()
    }

    private fun load(projectId: String): List<ToolMemoryEntry> {
        val file = File(context.filesDir, "agent_tool_memory/$projectId.json")
        return if (!file.exists()) emptyList() else runCatching { json.decodeFromString<List<ToolMemoryEntry>>(file.readText()) }.getOrDefault(emptyList())
    }

    private fun save(projectId: String, entries: List<ToolMemoryEntry>) {
        val file = File(context.filesDir, "agent_tool_memory/$projectId.json")
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString<List<ToolMemoryEntry>>(entries))
    }

    @Serializable
    data class ToolMemoryEntry(
        val stage: String,
        val summary: String,
        val files: List<String> = emptyList(),
        val timestamp: Long = System.currentTimeMillis()
    )
}
