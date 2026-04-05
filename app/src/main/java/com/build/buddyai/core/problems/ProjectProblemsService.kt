package com.build.buddyai.core.problems

import android.content.Context
import com.build.buddyai.core.model.BuildProblem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectProblemsService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val streams = mutableMapOf<String, MutableStateFlow<List<BuildProblem>>>()

    fun observe(projectId: String): Flow<List<BuildProblem>> =
        streams.getOrPut(projectId) { MutableStateFlow(load(projectId)) }.asStateFlow()

    fun replace(projectId: String, problems: List<BuildProblem>) {
        val normalized = problems.distinctBy { listOf(it.severity, it.filePath, it.lineNumber, it.title, it.detail) }
        streams.getOrPut(projectId) { MutableStateFlow(emptyList()) }.value = normalized
        save(projectId, normalized)
    }

    fun merge(projectId: String, source: String, problems: List<BuildProblem>) {
        val existing = load(projectId).toMutableList()
        existing.removeAll { it.detail.startsWith("[$source]") }
        val merged = (existing + problems.map { it.copy(detail = "[$source] ${it.detail}") })
            .distinctBy { listOf(it.severity, it.filePath, it.lineNumber, it.title, it.detail) }
        streams.getOrPut(projectId) { MutableStateFlow(emptyList()) }.value = merged
        save(projectId, merged)
    }

    fun clear(projectId: String) {
        replace(projectId, emptyList())
    }

    private fun load(projectId: String): List<BuildProblem> {
        val file = file(projectId)
        return if (!file.exists()) emptyList() else runCatching {
            json.decodeFromString<StoredProblems>(file.readText()).problems
        }.getOrDefault(emptyList())
    }

    private fun save(projectId: String, problems: List<BuildProblem>) {
        val file = file(projectId)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(StoredProblems(problems)))
    }

    private fun file(projectId: String) = File(context.filesDir, "project_problems/$projectId.json")

    @Serializable
    private data class StoredProblems(val problems: List<BuildProblem>)
}
