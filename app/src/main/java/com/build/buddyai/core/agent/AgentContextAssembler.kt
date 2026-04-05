package com.build.buddyai.core.agent

import com.build.buddyai.core.model.BuildRecord
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentContextAssembler @Inject constructor(
    private val semanticGraphIndexer: ProjectSemanticGraphIndexer,
    private val symbolIndexer: ProjectSymbolIndexer,
    private val failureMemoryStore: ProjectFailureMemoryStore,
    private val toolMemoryStore: AgentToolMemoryStore
) {

    data class ProjectContextSnapshot(
        val prompt: String,
        val containsKotlinSources: Boolean,
        val includedFiles: List<String>,
        val omittedFiles: List<String>,
        val symbolSummary: String,
        val graphSummary: String
    )

    fun assemble(
        projectId: String,
        projectDir: File,
        attachedFiles: List<String>,
        focusHint: String,
        buildHistory: List<BuildRecord>,
        memoryContext: ProjectFailureMemoryStore.MemoryContext,
        maxChars: Int = 120_000
    ): ProjectContextSnapshot {
        val normalizedAttached = attachedFiles
            .mapNotNull { normalizeProjectRelative(projectDir, it) }
            .distinct()
        val symbolIndex = symbolIndexer.index(projectDir)
        val semanticGraph = semanticGraphIndexer.index(projectDir, focusHint = focusHint, buildHistory = buildHistory)
        val allFiles = projectDir.walkTopDown()
            .filter { it.isFile }
            .filterNot { isExcluded(projectDir, it) }
            .filter { isTextLike(it) }
            .sortedBy { it.relativeTo(projectDir).invariantSeparatorsPath }
            .toList()

        val highPriorityFiles = linkedSetOf<String>().apply {
            addAll(normalizedAttached)
            add("settings.gradle.kts")
            add("build.gradle.kts")
            add("gradle.properties")
            add("app/build.gradle.kts")
            add("app/src/main/AndroidManifest.xml")
            addAll(semanticGraph.candidateFiles)
            addAll(symbolIndex.symbols.take(40).map { it.filePath })
        }

        val orderedFiles = buildList {
            highPriorityFiles.forEach { target ->
                allFiles.firstOrNull { it.relativeTo(projectDir).invariantSeparatorsPath == target }?.let { add(it) }
            }
            allFiles.forEach { if (!contains(it)) add(it) }
        }

        val fileTree = allFiles.joinToString("\n") { it.relativeTo(projectDir).invariantSeparatorsPath }
        val promptBuilder = StringBuilder()
        val included = mutableListOf<String>()
        val omitted = mutableListOf<String>()
        var consumed = 0

        fun appendChunk(text: String) {
            promptBuilder.append(text)
            consumed += text.length
        }

        appendChunk("Project root: ${projectDir.absolutePath}\n")
        appendChunk("User focus: $focusHint\n\n")
        appendChunk("Project file tree:\n$fileTree\n\n")
        appendChunk(semanticGraph.summary())
        appendChunk("\n\n")
        appendChunk(symbolIndex.toPrompt())
        appendChunk("\n\n")
        appendChunk(failureMemoryStore.summarize(projectId, memoryContext))
        appendChunk("\n\n")
        appendChunk(toolMemoryStore.summarize(projectId))
        appendChunk("\n\n")

        orderedFiles.forEach { file ->
            val relativePath = file.relativeTo(projectDir).invariantSeparatorsPath
            val content = runCatching { sanitizePromptContent(file.readText()) }.getOrElse { return@forEach }
            val chunk = buildString {
                append("File: ")
                append(relativePath)
                append("\n```")
                append(languageTag(relativePath))
                append("\n")
                append(content)
                append("\n```\n\n")
            }
            if (consumed + chunk.length <= maxChars || relativePath in highPriorityFiles) {
                appendChunk(chunk)
                included += relativePath
            } else {
                omitted += relativePath
            }
        }

        if (omitted.isNotEmpty()) {
            appendChunk("Omitted files due to context budget:\n${omitted.joinToString("\n")}\n")
        }

        val containsKotlinSources = allFiles.any { it.extension == "kt" || it.extension == "kts" }
        if (containsKotlinSources) {
            appendChunk(
                "\nBuild engine capability note: the current on-device validator can compile Android resources, Java, and DEX, but it cannot validate Kotlin or Compose sources yet. Never claim that Kotlin code was build-validated on-device unless no Kotlin compilation was required.\n"
            )
        }

        return ProjectContextSnapshot(
            prompt = promptBuilder.toString().trim(),
            containsKotlinSources = containsKotlinSources,
            includedFiles = included,
            omittedFiles = omitted,
            symbolSummary = symbolIndex.toPrompt(),
            graphSummary = semanticGraph.summary()
        )
    }

    private fun sanitizePromptContent(content: String): String = content
        .replace("A production-safe starter template that builds cleanly on-device.", "A polished starting point for your app experience.")
        .replace("Built with BuildBuddy", "Designed for your next Android release")

    private fun normalizeProjectRelative(projectDir: File, path: String): String? {
        return runCatching {
            val raw = path.replace('\\', '/').trim()
            if (raw.startsWith("/")) {
                val file = File(raw).canonicalFile
                if (file.path.startsWith(projectDir.canonicalPath + File.separator)) {
                    file.relativeTo(projectDir.canonicalFile).invariantSeparatorsPath
                } else null
            } else {
                raw.trimStart('/').takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun isExcluded(projectDir: File, file: File): Boolean {
        val relative = file.relativeTo(projectDir).invariantSeparatorsPath
        return relative.startsWith(".git/") ||
            relative.startsWith(".gradle/") ||
            relative.startsWith(".build/") ||
            relative.startsWith("build/") ||
            relative.startsWith("app/build/") ||
            relative.startsWith("artifacts/") ||
            relative.startsWith("snapshots/")
    }

    private fun isTextLike(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in setOf(
            "kt", "kts", "java", "xml", "gradle", "properties", "json", "md", "txt", "pro", "toml", "yaml", "yml"
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
