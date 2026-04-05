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
        val omittedFiles: List<String>
    )

    fun assemble(
        projectDir: File,
        attachedFiles: List<String>,
        maxChars: Int = 120_000
    ): ProjectContextSnapshot {
        val normalizedAttached = attachedFiles.map { it.replace('\\', '/').trimStart('/') }.distinct()
        val allFiles = projectDir.walkTopDown()
            .filter { it.isFile }
            .filterNot { isExcluded(projectDir, it) }
            .filter { isTextLike(it) }
            .sortedBy { it.relativeTo(projectDir).invariantSeparatorsPath }
            .toList()

        val importantPrefixes = listOf(
            "settings.gradle",
            "settings.gradle.kts",
            "build.gradle",
            "build.gradle.kts",
            "gradle.properties",
            "app/build.gradle",
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml"
        )

        val orderedFiles = buildList {
            normalizedAttached.forEach { attachedPath ->
                allFiles.firstOrNull { it.relativeTo(projectDir).invariantSeparatorsPath == attachedPath }?.let { add(it) }
            }
            allFiles.filter { file ->
                val relative = file.relativeTo(projectDir).invariantSeparatorsPath
                importantPrefixes.any { relative == it }
            }.forEach { if (!contains(it)) add(it) }
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
        appendChunk("Project file tree:\n$fileTree\n\n")

        orderedFiles.forEach { file ->
            val relativePath = file.relativeTo(projectDir).invariantSeparatorsPath
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
            omittedFiles = omitted
        )
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
