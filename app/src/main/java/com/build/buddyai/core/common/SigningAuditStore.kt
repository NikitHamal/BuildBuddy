package com.build.buddyai.core.common

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SigningAuditEntry(
    val projectId: String,
    val eventType: String,
    val detail: String,
    val signerAlias: String? = null,
    val variant: String? = null,
    val artifactFormat: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class SigningAuditStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    fun record(entry: SigningAuditEntry) {
        val entries = loadAll(entry.projectId).toMutableList()
        entries.add(0, entry)
        fileFor(entry.projectId).apply {
            parentFile?.mkdirs()
            writeText(json.encodeToString(entries.take(100)))
        }
    }

    fun load(projectId: String, limit: Int = 20): List<SigningAuditEntry> = loadAll(projectId).take(limit)

    private fun loadAll(projectId: String): List<SigningAuditEntry> {
        val file = fileFor(projectId)
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<SigningAuditEntry>>(file.readText()) }.getOrDefault(emptyList())
    }

    private fun fileFor(projectId: String) = File(context.filesDir, "signing_audit/$projectId.json")
}
