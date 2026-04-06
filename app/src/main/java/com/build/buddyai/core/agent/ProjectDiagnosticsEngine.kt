package com.build.buddyai.core.agent

import com.build.buddyai.core.dependency.ProjectDependencyManager
import com.build.buddyai.core.model.BuildLogEntry
import com.build.buddyai.core.model.BuildProblem
import com.build.buddyai.core.model.BuildProfile
import com.build.buddyai.core.model.ProblemSeverity
import com.build.buddyai.core.model.Project
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

@Singleton
class ProjectDiagnosticsEngine @Inject constructor(
    private val integrityChecker: ProjectIntegrityChecker,
    private val semanticGraphIndexer: ProjectSemanticGraphIndexer,
    private val dependencyManager: ProjectDependencyManager,
    private val manifestPlaceholderResolver: ManifestPlaceholderResolver
) {

    fun scan(
        project: Project,
        projectDir: File,
        buildProfile: BuildProfile,
        buildLogs: List<BuildLogEntry> = emptyList(),
        agentValidationContext: String? = null
    ): List<BuildProblem> {
        val problems = mutableListOf<BuildProblem>()

        val integrity = integrityChecker.validate(project, projectDir)
        problems += integrity.errors.map { BuildProblem(ProblemSeverity.ERROR, "Integrity error", it) }
        problems += integrity.warnings.map { BuildProblem(ProblemSeverity.WARNING, "Integrity warning", it) }

        val semanticGraph = semanticGraphIndexer.index(projectDir, focusHint = agentValidationContext.orEmpty(), buildHistory = emptyList())

        val manifestFile = File(projectDir, "app/src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val placeholderValidation = manifestPlaceholderResolver.resolve(project, manifestFile.readText(), buildProfile)
            placeholderValidation.unresolvedKeys.forEach { key ->
                problems += BuildProblem(
                    severity = ProblemSeverity.ERROR,
                    title = "Manifest placeholder unresolved",
                    detail = "No value was provided for \${$key} in the active build profile.",
                    filePath = "app/src/main/AndroidManifest.xml"
                )
            }
            placeholderValidation.warnings.forEach { warning ->
                problems += BuildProblem(
                    severity = ProblemSeverity.WARNING,
                    title = "Manifest placeholder warning",
                    detail = warning,
                    filePath = "app/src/main/AndroidManifest.xml"
                )
            }
        }

        problems += dependencyManager.scan(projectDir).warnings.map {
            BuildProblem(
                severity = when (it.severity) {
                    ProjectDependencyManager.Severity.INFO -> ProblemSeverity.INFO
                    ProjectDependencyManager.Severity.WARNING -> ProblemSeverity.WARNING
                    ProjectDependencyManager.Severity.ERROR -> ProblemSeverity.ERROR
                },
                title = it.title,
                detail = it.detail
            )
        }

        if (buildProfile.variant.name == "RELEASE") {
            val signing = buildProfile.signing
            if (signing == null || signing.keystorePath.isBlank() || signing.keyAlias.isBlank()) {
                problems += BuildProblem(
                    severity = ProblemSeverity.ERROR,
                    title = "Release signing incomplete",
                    detail = "Release builds require an imported keystore and a non-empty key alias."
                )
            } else if (!File(signing.keystorePath).exists()) {
                problems += BuildProblem(
                    severity = ProblemSeverity.ERROR,
                    title = "Release keystore missing",
                    detail = "The configured keystore file no longer exists at ${signing.keystorePath}."
                )
            }
        }

        val definedStrings = semanticGraph.resources.strings.toSet()
        semanticGraph.resources.stringReferences.filterNot { it in definedStrings }.distinct().forEach { missing ->
            problems += BuildProblem(
                severity = ProblemSeverity.WARNING,
                title = "Missing string resource",
                detail = "String reference $missing is used but not defined in values resources."
            )
        }

        semanticGraph.manifest.components.forEach { component ->
            if (component.kind == "activity" || component.kind == "service" || component.kind == "receiver") {
                val normalized = component.name.trimStart('.')
                val exists = semanticGraph.apis.any { it.qualifiedName.endsWith(normalized) || it.qualifiedName == normalized }
                if (!exists) {
                    problems += BuildProblem(
                        severity = ProblemSeverity.WARNING,
                        title = "Navigation/component target missing",
                        detail = "Manifest declares ${component.kind} ${component.name}, but no matching source type was indexed.",
                        filePath = "app/src/main/AndroidManifest.xml"
                    )
                }
            }
        }

        collectXmlWellFormedness(projectDir, problems)
        collectJavaParserDiagnostics(projectDir, problems)
        parseBuildLogs(buildLogs, problems)

        agentValidationContext?.takeIf { it.isNotBlank() }?.let {
            problems += BuildProblem(ProblemSeverity.INFO, "Agent validation context", it.take(500))
        }

        return problems.distinctBy { listOf(it.severity, it.title, it.detail, it.filePath, it.lineNumber) }
    }

    private fun collectXmlWellFormedness(projectDir: File, problems: MutableList<BuildProblem>) {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = runCatching { factory.newDocumentBuilder() }.getOrNull()
            ?: return
        projectDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
            .filterNot { isIgnored(projectDir, it) }
            .forEach { file ->
                runCatching {
                    builder.parse(file)
                }.onFailure { error ->
                    problems += BuildProblem(
                        severity = ProblemSeverity.WARNING,
                        title = "XML parse warning",
                        detail = error.message ?: "Unable to parse XML",
                        filePath = file.relativeTo(projectDir).invariantSeparatorsPath
                    )
                }
            }
    }

    private fun collectJavaParserDiagnostics(projectDir: File, problems: MutableList<BuildProblem>) {
        projectDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("java", ignoreCase = true) }
            .filterNot { isIgnored(projectDir, it) }
            .forEach { file ->
                val text = runCatching { file.readText() }.getOrDefault("")
                val packageName = Regex("""package\s+([A-Za-z0-9_.]+)""").find(text)?.groupValues?.get(1)
                if (packageName == null) {
                    problems += BuildProblem(
                        severity = ProblemSeverity.WARNING,
                        title = "Missing package declaration",
                        detail = "Java sources should declare an explicit package.",
                        filePath = file.relativeTo(projectDir).invariantSeparatorsPath
                    )
                }
                if (text.contains("TODO(")) {
                    problems += BuildProblem(
                        severity = ProblemSeverity.INFO,
                        title = "TODO marker found",
                        detail = "Source file still contains TODO markers.",
                        filePath = file.relativeTo(projectDir).invariantSeparatorsPath
                    )
                }
            }
    }

    private fun parseBuildLogs(logs: List<BuildLogEntry>, problems: MutableList<BuildProblem>) {
        val pattern = Regex("""([^:\s]+\.(?:xml|java|kt)):(\d+):\s*(error|warning):?\s*(.*)""")
        logs.takeLast(100).forEach { entry ->
            val match = pattern.find(entry.message) ?: return@forEach
            problems += BuildProblem(
                severity = if (match.groupValues[3].equals("error", true)) ProblemSeverity.ERROR else ProblemSeverity.WARNING,
                title = match.groupValues[4].ifBlank { "Build ${match.groupValues[3]}" },
                detail = entry.message,
                filePath = match.groupValues[1],
                lineNumber = match.groupValues[2].toIntOrNull()
            )
        }
    }

    private fun isIgnored(projectDir: File, file: File): Boolean {
        val relative = file.relativeTo(projectDir).invariantSeparatorsPath
        return relative.startsWith(".git/") ||
            relative.startsWith(".gradle/") ||
            relative.startsWith(".build/") ||
            relative.startsWith("build/") ||
            relative.startsWith("app/build/")
    }
}
