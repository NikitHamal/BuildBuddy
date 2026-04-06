package com.build.buddyai.core.dependency

import com.build.buddyai.core.common.FileUtils
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val DISABLED_DEP_PREFIX = "// buildbuddy-disabled:dep "
private const val DISABLED_REPO_PREFIX = "// buildbuddy-disabled:repo "

@Singleton
class ProjectDependencyManager @Inject constructor() {

    data class DependencyEntry(
        val id: String,
        val configuration: String,
        val notation: String,
        val enabled: Boolean,
        val sourceFile: String
    )

    data class RepositoryEntry(
        val id: String,
        val declaration: String,
        val enabled: Boolean,
        val sourceFile: String
    )

    data class DependencyWarning(
        val title: String,
        val detail: String,
        val severity: Severity = Severity.WARNING
    )

    enum class Severity { INFO, WARNING, ERROR }

    data class Snapshot(
        val dependencies: List<DependencyEntry>,
        val repositories: List<RepositoryEntry>,
        val warnings: List<DependencyWarning>
    )

    fun scan(projectDir: File): Snapshot {
        val appBuildFile = File(projectDir, "app/build.gradle.kts")
        val settingsFile = File(projectDir, "settings.gradle.kts")
        val dependencies = parseDependencies(appBuildFile)
        val repositories = parseRepositories(settingsFile)
        val warnings = buildWarnings(dependencies, repositories)
        return Snapshot(dependencies, repositories, warnings)
    }

    fun addDependency(
        projectDir: File,
        notation: String,
        configuration: String,
        repositoryUrl: String?
    ): Snapshot {
        val normalizedNotation = notation.trim()
        require(normalizedNotation.isNotBlank()) { "Dependency notation is required" }
        require(isValidConfiguration(configuration)) { "Unsupported configuration: $configuration" }

        val appBuildFile = File(projectDir, "app/build.gradle.kts")
        require(appBuildFile.exists()) { "Missing app/build.gradle.kts" }

        val line = "    $configuration($normalizedNotation)"
        updateBlock(appBuildFile, "dependencies") { lines ->
            if (lines.any { normalizeDependencyLine(it) == normalizeDependencyLine(line) }) lines
            else lines + line
        }

        repositoryUrl?.trim()?.takeIf { it.isNotBlank() }?.let { addRepository(projectDir, it) }
        return scan(projectDir)
    }

    fun toggleDependency(projectDir: File, entryId: String, enabled: Boolean): Snapshot {
        val appBuildFile = File(projectDir, "app/build.gradle.kts")
        updateBlock(appBuildFile, "dependencies") { lines ->
            lines.map { line ->
                val parsed = parseDependencyLine(line, appBuildFile.relativeTo(projectDir).invariantSeparatorsPath)
                if (parsed?.id == entryId) {
                    if (enabled) enableDependencyLine(line) else disableDependencyLine(line)
                } else {
                    line
                }
            }
        }
        return scan(projectDir)
    }

    fun deleteDependency(projectDir: File, entryId: String): Snapshot {
        val appBuildFile = File(projectDir, "app/build.gradle.kts")
        updateBlock(appBuildFile, "dependencies") { lines ->
            lines.filterNot { line ->
                parseDependencyLine(line, appBuildFile.relativeTo(projectDir).invariantSeparatorsPath)?.id == entryId
            }
        }
        return scan(projectDir)
    }

    fun addRepository(projectDir: File, url: String): Snapshot {
        val trimmedUrl = url.trim()
        require(trimmedUrl.isNotBlank()) { "Repository URL is required" }
        val settingsFile = File(projectDir, "settings.gradle.kts")
        require(settingsFile.exists()) { "Missing settings.gradle.kts" }
        val repoLine = "        maven(\"$trimmedUrl\")"

        updateRepositoryBlock(settingsFile) { lines ->
            if (lines.any { normalizeRepoLine(it) == normalizeRepoLine(repoLine) }) lines
            else lines + repoLine
        }
        return scan(projectDir)
    }

    fun toggleRepository(projectDir: File, entryId: String, enabled: Boolean): Snapshot {
        val settingsFile = File(projectDir, "settings.gradle.kts")
        updateRepositoryBlock(settingsFile) { lines ->
            lines.map { line ->
                val parsed = parseRepositoryLine(line, settingsFile.relativeTo(projectDir).invariantSeparatorsPath)
                if (parsed?.id == entryId) {
                    if (enabled) enableRepositoryLine(line) else disableRepositoryLine(line)
                } else {
                    line
                }
            }
        }
        return scan(projectDir)
    }

    fun deleteRepository(projectDir: File, entryId: String): Snapshot {
        val settingsFile = File(projectDir, "settings.gradle.kts")
        updateRepositoryBlock(settingsFile) { lines ->
            lines.filterNot { line ->
                parseRepositoryLine(line, settingsFile.relativeTo(projectDir).invariantSeparatorsPath)?.id == entryId
            }
        }
        return scan(projectDir)
    }

    private fun parseDependencies(file: File): List<DependencyEntry> {
        if (!file.exists()) return emptyList()
        val relative = file.name.takeIf { file.parentFile?.name == "app" }?.let { "app/$it" } ?: file.name
        val content = file.readText()
        val block = findBlock(content, "dependencies") ?: return emptyList()
        return content.substring(block.bodyStart, block.bodyEnd)
            .lineSequence()
            .mapNotNull { parseDependencyLine(it, relative) }
            .distinctBy { it.id }
            .toList()
    }

    private fun parseRepositories(file: File): List<RepositoryEntry> {
        if (!file.exists()) return emptyList()
        val content = file.readText()
        val blocks = findBlocks(content, "repositories")
        if (blocks.isEmpty()) return emptyList()
        return blocks.flatMap { block ->
            content.substring(block.bodyStart, block.bodyEnd)
                .lineSequence()
                .mapNotNull { parseRepositoryLine(it, file.name) }
                .toList()
        }.distinctBy { it.id }
    }

    private fun parseDependencyLine(line: String, sourceFile: String): DependencyEntry? {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return null
        val disabled = trimmed.startsWith(DISABLED_DEP_PREFIX)
        val body = if (disabled) trimmed.removePrefix(DISABLED_DEP_PREFIX).trim() else trimmed
        val match = Regex("""^([A-Za-z][A-Za-z0-9]*)\s*\((.+)\)\s*$""").matchEntire(body) ?: return null
        val configuration = match.groupValues[1]
        val notation = match.groupValues[2].trim()
        val id = dependencyId(sourceFile, configuration, notation)
        return DependencyEntry(id, configuration, notation, !disabled, sourceFile)
    }

    private fun parseRepositoryLine(line: String, sourceFile: String): RepositoryEntry? {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return null
        val disabled = trimmed.startsWith(DISABLED_REPO_PREFIX)
        val body = if (disabled) trimmed.removePrefix(DISABLED_REPO_PREFIX).trim() else trimmed
        val looksLikeRepository = body == "google()" ||
            body == "mavenCentral()" ||
            body == "gradlePluginPortal()" ||
            body.startsWith("maven(") ||
            body.startsWith("maven {")
        if (!looksLikeRepository) return null
        val id = repoId(sourceFile, body)
        return RepositoryEntry(id, body, !disabled, sourceFile)
    }

    private fun buildWarnings(
        dependencies: List<DependencyEntry>,
        repositories: List<RepositoryEntry>
    ): List<DependencyWarning> {
        val warnings = mutableListOf<DependencyWarning>()

        dependencies.groupBy { it.configuration to it.notation }.forEach { (key, items) ->
            if (items.size > 1) warnings += DependencyWarning(
                title = "Duplicate dependency",
                detail = "${key.first}(${key.second}) appears ${items.size} times."
            )
        }
        dependencies.forEach { dep ->
            val notation = dep.notation
            if (notation.contains("+") || notation.contains("latest.release") || notation.contains("latest.integration")) {
                warnings += DependencyWarning(
                    title = "Dynamic version",
                    detail = "${dep.configuration} uses a floating version: ${dep.notation}"
                )
            }
            if (notation.contains("SNAPSHOT", ignoreCase = true)) {
                warnings += DependencyWarning(
                    title = "Snapshot dependency",
                    detail = "${dep.notation} is a snapshot dependency and can change unexpectedly."
                )
            }
            if (notation.startsWith("project(") || notation.startsWith("files(")) {
                warnings += DependencyWarning(
                    title = "Manual dependency review",
                    detail = "${dep.notation} is a local/project dependency. Verify file paths and module availability.",
                    severity = Severity.INFO
                )
            }
        }
        repositories.forEach { repo ->
            val declaration = repo.declaration
            if (declaration.contains("jcenter", ignoreCase = true)) {
                warnings += DependencyWarning(
                    title = "Deprecated repository",
                    detail = "$declaration uses JCenter, which is deprecated."
                )
            }
            if (declaration.contains("http://", ignoreCase = true)) {
                warnings += DependencyWarning(
                    title = "Insecure repository",
                    detail = "$declaration is not using HTTPS."
                )
            }
        }
        return warnings.distinctBy { listOf(it.title, it.detail, it.severity) }
    }

    private fun updateRepositoryBlock(file: File, transform: (List<String>) -> List<String>) {
        updateBlock(file, "repositories", preferredOccurrence = 1, transform = transform)
    }

    private fun updateBlock(
        file: File,
        blockName: String,
        preferredOccurrence: Int = 0,
        transform: (List<String>) -> List<String>
    ) {
        val content = file.readText()
        val blocks = findBlocks(content, blockName)
        val block = blocks.getOrNull(preferredOccurrence) ?: blocks.firstOrNull()
            ?: throw IllegalArgumentException("Missing $blockName block in ${file.name}")
        val existingLines = content.substring(block.bodyStart, block.bodyEnd)
            .split("\n")
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
        val updatedLines = transform(existingLines)
            .filter { it.isNotBlank() }
        val replacement = if (updatedLines.isEmpty()) "\n" else "\n" + updatedLines.joinToString("\n") + "\n"
        val updatedContent = buildString {
            append(content.substring(0, block.bodyStart))
            append(replacement)
            append(content.substring(block.bodyEnd))
        }
        file.writeText(updatedContent)
    }

    private fun findBlock(content: String, blockName: String): BlockRange? = findBlocks(content, blockName).firstOrNull()

    private fun findBlocks(content: String, blockName: String): List<BlockRange> {
        val matches = Regex("""\b${Regex.escape(blockName)}\s*\{""").findAll(content)
        return matches.mapNotNull { match ->
            val braceIndex = match.range.last
            val closingBrace = findMatchingBrace(content, braceIndex) ?: return@mapNotNull null
            BlockRange(match.range.first, braceIndex, braceIndex + 1, closingBrace, closingBrace + 1)
        }.toList()
    }

    private fun findMatchingBrace(content: String, openingBraceIndex: Int): Int? {
        var depth = 1
        var index = openingBraceIndex + 1
        var inLineComment = false
        var inBlockComment = false
        var inSingleQuote = false
        var inDoubleQuote = false
        var inTripleSingleQuote = false
        var inTripleDoubleQuote = false
        var escaped = false

        while (index < content.length) {
            val ch = content[index]
            val next = content.getOrNull(index + 1)
            val next2 = content.getOrNull(index + 2)

            if (inLineComment) {
                if (ch == '\n') inLineComment = false
                index++
                continue
            }
            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false
                    index += 2
                    continue
                }
                index++
                continue
            }
            if (inTripleDoubleQuote) {
                if (ch == '"' && next == '"' && next2 == '"') {
                    inTripleDoubleQuote = false
                    index += 3
                    continue
                }
                index++
                continue
            }
            if (inTripleSingleQuote) {
                if (ch == '\'' && next == '\'' && next2 == '\'') {
                    inTripleSingleQuote = false
                    index += 3
                    continue
                }
                index++
                continue
            }
            if (inDoubleQuote) {
                if (ch == '"' && !escaped) {
                    inDoubleQuote = false
                }
                escaped = ch == '\\' && !escaped
                index++
                continue
            }
            if (inSingleQuote) {
                if (ch == '\'' && !escaped) {
                    inSingleQuote = false
                }
                escaped = ch == '\\' && !escaped
                index++
                continue
            }

            escaped = false
            when {
                ch == '/' && next == '/' -> {
                    inLineComment = true
                    index += 2
                    continue
                }
                ch == '/' && next == '*' -> {
                    inBlockComment = true
                    index += 2
                    continue
                }
                ch == '"' && next == '"' && next2 == '"' -> {
                    inTripleDoubleQuote = true
                    index += 3
                    continue
                }
                ch == '\'' && next == '\'' && next2 == '\'' -> {
                    inTripleSingleQuote = true
                    index += 3
                    continue
                }
                ch == '"' -> {
                    inDoubleQuote = true
                    index++
                    continue
                }
                ch == '\'' -> {
                    inSingleQuote = true
                    index++
                    continue
                }
                ch == '{' -> depth++
                ch == '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return null
    }

    private fun disableDependencyLine(line: String): String {
        val body = line.trim().removePrefix(DISABLED_DEP_PREFIX).trim()
        return "    $DISABLED_DEP_PREFIX$body"
    }

    private fun enableDependencyLine(line: String): String {
        val body = line.trim().removePrefix(DISABLED_DEP_PREFIX).trim()
        return "    $body"
    }

    private fun disableRepositoryLine(line: String): String {
        val body = line.trim().removePrefix(DISABLED_REPO_PREFIX).trim()
        return "        $DISABLED_REPO_PREFIX$body"
    }

    private fun enableRepositoryLine(line: String): String {
        val body = line.trim().removePrefix(DISABLED_REPO_PREFIX).trim()
        return "        $body"
    }

    private fun normalizeDependencyLine(line: String): String = line.trim().removePrefix(DISABLED_DEP_PREFIX).trim()
    private fun normalizeRepoLine(line: String): String = line.trim().removePrefix(DISABLED_REPO_PREFIX).trim()

    private fun dependencyId(sourceFile: String, configuration: String, notation: String): String =
        listOf(sourceFile, configuration, notation.replace(" ", "")).joinToString("|")

    private fun repoId(sourceFile: String, declaration: String): String =
        listOf(sourceFile, declaration.replace(" ", "")).joinToString("|")

    private fun isValidConfiguration(configuration: String): Boolean = configuration in setOf(
        "implementation",
        "api",
        "compileOnly",
        "runtimeOnly",
        "testImplementation",
        "androidTestImplementation",
        "ksp",
        "kapt"
    )

    private data class BlockRange(
        val start: Int,
        val braceIndex: Int,
        val bodyStart: Int,
        val bodyEnd: Int,
        val end: Int
    )
}
