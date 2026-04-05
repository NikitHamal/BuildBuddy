package com.build.buddyai.core.dependency

import com.build.buddyai.core.model.BuildProblem
import com.build.buddyai.core.model.ProblemSeverity
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectDependencyManager @Inject constructor() {

    data class DependencyEntry(
        val id: String,
        val configuration: String,
        val notation: String,
        val sourceFile: String,
        val lineNumber: Int,
        val enabled: Boolean,
        val rawLine: String
    )

    data class RepositoryEntry(
        val id: String,
        val declaration: String,
        val sourceFile: String,
        val lineNumber: Int,
        val enabled: Boolean,
        val rawLine: String
    )

    data class DependencySnapshot(
        val buildFiles: List<String>,
        val dependencies: List<DependencyEntry>,
        val repositories: List<RepositoryEntry>,
        val issues: List<BuildProblem>
    )

    private val dependencyRegex = Regex(
        """^(\s*)(//\s*buildbuddy-disabled:\s*)?(implementation|api|ksp|kapt|compileOnly|runtimeOnly|debugImplementation|releaseImplementation|testImplementation|androidTestImplementation)\s*\((.+)\)\s*$"""
    )

    fun snapshot(projectDir: File): DependencySnapshot {
        val buildFiles = listOf("app/build.gradle.kts", "app/build.gradle", "gradle/libs.versions.toml", "settings.gradle.kts")
            .filter { File(projectDir, it).exists() }
        val dependencies = mutableListOf<DependencyEntry>()
        val repositories = mutableListOf<RepositoryEntry>()

        buildFiles.forEach { relative ->
            val file = File(projectDir, relative)
            file.readLines().forEachIndexed { index, line ->
                dependencyRegex.matchEntire(line.trimEnd())?.let { match ->
                    dependencies += DependencyEntry(
                        id = "$relative:${index + 1}",
                        configuration = match.groupValues[3],
                        notation = match.groupValues[4].trim(),
                        sourceFile = relative,
                        lineNumber = index + 1,
                        enabled = match.groupValues[2].isBlank(),
                        rawLine = line
                    )
                }
                parseRepositoryDeclaration(line.trimEnd())?.let { declaration ->
                    repositories += RepositoryEntry(
                        id = "$relative:repo:${index + 1}",
                        declaration = declaration,
                        sourceFile = relative,
                        lineNumber = index + 1,
                        enabled = !line.trimStart().startsWith("//"),
                        rawLine = line
                    )
                }
            }
        }

        return DependencySnapshot(
            buildFiles = buildFiles,
            dependencies = dependencies,
            repositories = repositories,
            issues = diagnostics(projectDir, dependencies, repositories)
        )
    }

    fun toggleDependency(projectDir: File, entry: DependencyEntry, enabled: Boolean) {
        updateLine(projectDir, entry.sourceFile, entry.lineNumber) { current ->
            val trimmed = current.trimStart()
            if (enabled) trimmed.removePrefix("// buildbuddy-disabled: ") else {
                val line = trimmed.removePrefix("// buildbuddy-disabled: ")
                if (line.startsWith("// buildbuddy-disabled:")) line else "// buildbuddy-disabled: $line"
            }
        }
    }

    fun deleteDependency(projectDir: File, entry: DependencyEntry) {
        removeLine(projectDir, entry.sourceFile, entry.lineNumber)
    }

    fun addDependency(projectDir: File, configuration: String, notation: String, repositoryUrl: String? = null) {
        repositoryUrl?.takeIf { it.isNotBlank() }?.let { addRepository(projectDir, it) }
        ensureDependenciesBlock(projectDir, "app/build.gradle.kts")
        insertIntoDependenciesBlock(projectDir, "app/build.gradle.kts", "    $configuration($notation)")
    }

    fun toggleRepository(projectDir: File, entry: RepositoryEntry, enabled: Boolean) {
        updateLine(projectDir, entry.sourceFile, entry.lineNumber) { current ->
            val trimmed = current.trimStart()
            if (enabled) trimmed.removePrefix("// ") else if (trimmed.startsWith("//")) trimmed else "// $trimmed"
        }
    }

    fun deleteRepository(projectDir: File, entry: RepositoryEntry) {
        removeLine(projectDir, entry.sourceFile, entry.lineNumber)
    }

    fun addRepository(projectDir: File, url: String) {
        val settingsFile = File(projectDir, "settings.gradle.kts")
        if (!settingsFile.exists()) return
        val lines = settingsFile.readLines().toMutableList()
        val blockStart = lines.indexOfFirst { it.contains("dependencyResolutionManagement") }
        if (blockStart < 0) return
        val repoIndex = (blockStart until lines.size).firstOrNull { index ->
            lines[index].contains("repositories {")
        } ?: -1
        if (repoIndex < 0) return
        val alreadyPresent = lines.any { it.contains(url) }
        if (alreadyPresent) return
        lines.add(repoIndex + 1, "        maven { url = uri(\"$url\") }")
        settingsFile.writeText(lines.joinToString("\n"))
    }

    private fun ensureDependenciesBlock(projectDir: File, relativePath: String) {
        val file = File(projectDir, relativePath)
        if (!file.exists()) return
        val content = file.readText()
        if (Regex("""\bdependencies\s*\{""").containsMatchIn(content)) return
        val updated = content.trimEnd() + "\n\ndependencies {\n}\n"
        file.writeText(updated)
    }


    fun analyzeImpact(filePath: String, before: String, after: String): List<String> {
        if (!filePath.endsWith(".gradle.kts") && !filePath.endsWith(".gradle") && !filePath.endsWith("libs.versions.toml") && !filePath.endsWith("settings.gradle.kts")) return emptyList()
        val beforeDeps = dependencyRegex.findAll(before).map { it.groupValues[4].trim() }.toSet()
        val afterDeps = dependencyRegex.findAll(after).map { it.groupValues[4].trim() }.toSet()
        val beforeRepos = before.lineSequence().mapNotNull(::parseRepositoryDeclaration).toSet()
        val afterRepos = after.lineSequence().mapNotNull(::parseRepositoryDeclaration).toSet()
        val warnings = mutableListOf<String>()
        (afterDeps - beforeDeps).take(4).forEach { warnings += "Adds dependency $it" }
        (beforeDeps - afterDeps).take(4).forEach { warnings += "Removes dependency $it" }
        (afterRepos - beforeRepos).take(3).forEach { warnings += "Adds repository $it" }
        if (after.contains("+\"") || after.contains(":+\"")) warnings += "Dynamic dependency versions are risky for reproducible builds."
        if (after.contains("SNAPSHOT", ignoreCase = true)) warnings += "Snapshot dependencies reduce reproducibility and can break on-device builds."
        return warnings.distinct()
    }

    private fun diagnostics(projectDir: File, dependencies: List<DependencyEntry>, repositories: List<RepositoryEntry>): List<BuildProblem> {
        val problems = mutableListOf<BuildProblem>()
        val duplicates = dependencies.filter { it.enabled }.groupBy { it.configuration to it.notation }.filterValues { it.size > 1 }
        duplicates.values.flatten().forEach { entry ->
            problems += BuildProblem(
                severity = ProblemSeverity.WARNING,
                title = "Duplicate dependency declaration",
                detail = "${entry.configuration}(${entry.notation}) is declared more than once.",
                filePath = entry.sourceFile,
                lineNumber = entry.lineNumber
            )
        }
        dependencies.filter { it.enabled && it.notation.contains("+") }.forEach { entry ->
            problems += BuildProblem(
                severity = ProblemSeverity.WARNING,
                title = "Dynamic dependency version",
                detail = "Avoid ${entry.notation} because dynamic versions reduce reproducibility.",
                filePath = entry.sourceFile,
                lineNumber = entry.lineNumber
            )
        }
        dependencies.filter { it.enabled && "files(" in it.notation }.forEach { entry ->
            val fileNames = Regex("""files\((.+)\)""").find(entry.notation)?.groupValues?.get(1).orEmpty()
            problems += BuildProblem(
                severity = ProblemSeverity.INFO,
                title = "Local file dependency",
                detail = "Verify the local dependency exists and is checked into the project: $fileNames",
                filePath = entry.sourceFile,
                lineNumber = entry.lineNumber
            )
        }
        if (repositories.none { it.enabled && (it.declaration.contains("google") || it.declaration.contains("mavenCentral") || it.declaration.contains("jitpack")) }) {
            problems += BuildProblem(
                severity = ProblemSeverity.WARNING,
                title = "No standard repositories configured",
                detail = "At least one of google(), mavenCentral(), or a valid maven URL should be available for dependency resolution.",
                filePath = "settings.gradle.kts"
            )
        }
        val catalog = File(projectDir, "gradle/libs.versions.toml")
        if (catalog.exists()) {
            val aliases = catalog.readLines()
                .mapNotNull { Regex("""^([A-Za-z0-9_.-]+)\s*=\s*\{""").find(it.trim())?.groupValues?.get(1) }
                .toSet()
            dependencies.filter { it.enabled && it.notation.startsWith("libs.") }.forEach { entry ->
                val alias = entry.notation.removePrefix("libs.").substringBefore(')').replace('.', '-')
                if (aliases.none { it.equals(alias, ignoreCase = true) || it.equals(alias.replace('-', '.'), ignoreCase = true) }) {
                    problems += BuildProblem(
                        severity = ProblemSeverity.WARNING,
                        title = "Missing version catalog alias",
                        detail = "${entry.notation} is referenced but no matching alias was found in gradle/libs.versions.toml.",
                        filePath = entry.sourceFile,
                        lineNumber = entry.lineNumber
                    )
                }
            }
        }
        return problems.distinctBy { listOf(it.title, it.detail, it.filePath, it.lineNumber) }
    }

    private fun parseRepositoryDeclaration(line: String): String? {
        val trimmed = line.trim()
        return when {
            trimmed.startsWith("google()") -> "google()"
            trimmed.startsWith("mavenCentral()") -> "mavenCentral()"
            trimmed.startsWith("gradlePluginPortal()") -> "gradlePluginPortal()"
            "maven" in trimmed && "url" in trimmed -> Regex("""uri\("([^"]+)"\)""").find(trimmed)?.groupValues?.get(1) ?: trimmed
            else -> null
        }
    }

    private fun insertIntoDependenciesBlock(projectDir: File, relativePath: String, declaration: String) {
        val file = File(projectDir, relativePath)
        if (!file.exists()) return
        val lines = file.readLines().toMutableList()
        val start = lines.indexOfFirst { it.contains("dependencies {") }
        if (start < 0) {
            lines += ""
            lines += "dependencies {"
            lines += declaration
            lines += "}"
        } else {
            var end = start + 1
            var depth = 1
            while (end < lines.size && depth > 0) {
                depth += lines[end].count { it == '{' }
                depth -= lines[end].count { it == '}' }
                end++
            }
            lines.add((end - 1).coerceAtLeast(start + 1), declaration)
        }
        file.writeText(lines.joinToString("\n"))
    }

    private fun updateLine(projectDir: File, relativePath: String, lineNumber: Int, transform: (String) -> String) {
        val file = File(projectDir, relativePath)
        val lines = file.readLines().toMutableList()
        val index = lineNumber - 1
        if (index !in lines.indices) return
        lines[index] = transform(lines[index])
        file.writeText(lines.joinToString("\n"))
    }

    private fun removeLine(projectDir: File, relativePath: String, lineNumber: Int) {
        val file = File(projectDir, relativePath)
        val lines = file.readLines().toMutableList()
        val index = lineNumber - 1
        if (index !in lines.indices) return
        lines.removeAt(index)
        file.writeText(lines.joinToString("\n"))
    }
}
