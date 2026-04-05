package com.build.buddyai.core.agent

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentContextAssembler @Inject constructor() {

    data class ProjectContextSnapshot(
        val prompt: String,
        val containsKotlinSources: Boolean,
        val includedFiles: List<String>,
        val omittedFiles: List<String>,
        val focusFiles: List<String>,
        val indexSummary: String
    )

    fun assemble(
        projectDir: File,
        userRequest: String,
        attachedFiles: List<String>,
        preferredFiles: List<String> = emptyList(),
        maxChars: Int = 120_000
    ): ProjectContextSnapshot {
        val index = ProjectSymbolIndex.build(projectDir)
        val focusFiles = index.selectFocusFiles(
            userRequest = userRequest,
            attachedFiles = attachedFiles,
            preferredFiles = preferredFiles,
            limit = 12
        )
        val fileMap = index.files.associateBy { it.path }
        val importantFiles = listOf(
            "settings.gradle.kts",
            "build.gradle.kts",
            "gradle.properties",
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml"
        )
        val orderedFiles = (focusFiles + importantFiles + index.files.map { it.path }).distinct()

        val promptBuilder = StringBuilder()
        val included = mutableListOf<String>()
        val omitted = mutableListOf<String>()
        var consumed = 0

        fun appendChunk(text: String) {
            promptBuilder.append(text)
            consumed += text.length
        }

        appendChunk("Project root: ${projectDir.absolutePath}\n")
        appendChunk("Indexed project summary:\n${index.summary()}\n\n")
        appendChunk("Focused files for this turn:\n${focusFiles.joinToString("\n").ifBlank { "(none)" }}\n\n")

        orderedFiles.forEach { relativePath ->
            val indexedFile = fileMap[relativePath] ?: return@forEach
            val file = File(projectDir, indexedFile.path)
            if (!file.exists() || !file.isFile) return@forEach
            val content = runCatching { file.readText() }.getOrElse { return@forEach }
            val chunk = buildString {
                append("File: ")
                append(relativePath)
                append("\n```")
                append(languageTag(relativePath))
                append("\n")
                append(content)
                append("\n```\n\n")
            }
            if (consumed + chunk.length <= maxChars || included.isEmpty()) {
                appendChunk(chunk)
                included += relativePath
            } else {
                omitted += relativePath
            }
        }

        if (omitted.isNotEmpty()) {
            appendChunk("Omitted files due to context budget:\n${omitted.joinToString("\n")}\n")
        }

        return ProjectContextSnapshot(
            prompt = promptBuilder.toString().trim(),
            containsKotlinSources = index.hasKotlin,
            includedFiles = included,
            omittedFiles = omitted,
            focusFiles = focusFiles,
            indexSummary = index.summary()
        )
    }

    private fun languageTag(relativePath: String): String = when {
        relativePath.endsWith(".kt") || relativePath.endsWith(".kts") -> "kotlin"
        relativePath.endsWith(".java") -> "java"
        relativePath.endsWith(".xml") -> "xml"
        relativePath.endsWith(".json") -> "json"
        relativePath.endsWith(".md") -> "markdown"
        relativePath.endsWith(".toml") -> "toml"
        relativePath.endsWith(".yaml") || relativePath.endsWith(".yml") -> "yaml"
        else -> "text"
    }
}
