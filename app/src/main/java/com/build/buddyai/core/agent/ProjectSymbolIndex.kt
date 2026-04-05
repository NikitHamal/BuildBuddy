package com.build.buddyai.core.agent

import com.build.buddyai.core.common.FileUtils
import java.io.File

private val PACKAGE_REGEX = Regex("""^\s*package\s+([A-Za-z0-9_.]+)""", RegexOption.MULTILINE)
private val IMPORT_REGEX = Regex("""^\s*import\s+([A-Za-z0-9_.]+)""", RegexOption.MULTILINE)
private val CLASS_REGEX = Regex("""\b(class|interface|object|enum\s+class|sealed\s+class|data\s+class)\s+([A-Za-z_][A-Za-z0-9_]*)""")
private val FUN_REGEX = Regex("""\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
private val METHOD_REGEX = Regex("""\b(public|protected|private)?\s*(static\s+)?[A-Za-z0-9_<>,.?\[\]\s]+\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
private val XML_ID_REGEX = Regex("""android:id="@\+id/([A-Za-z0-9_]+)"""")
private val XML_TAG_REGEX = Regex("""<([A-Za-z0-9._]+)(\s|>)""")
private val MANIFEST_ACTIVITY_REGEX = Regex("""<activity[^>]*android:name="([^"]+)"""")
private val MANIFEST_PACKAGE_REGEX = Regex("""package\s*=\s*"([A-Za-z0-9_.]+)"""")
private val NAMESPACE_REGEX = Regex("""namespace\s*=\s*"([A-Za-z0-9_.]+)"""")
private val APPLICATION_ID_REGEX = Regex("""applicationId\s*=\s*"([A-Za-z0-9_.]+)"""")
private val RES_NAME_REGEX = Regex("""^[a-z0-9_]+$""")
private val RESOURCE_REF_REGEX = Regex("""R\.(layout|string|id|drawable|color|menu|xml)\.([A-Za-z0-9_]+)""")
private val TOOLS_CONTEXT_REGEX = Regex("""tools:context="([^"]+)"""")

data class IndexedFile(
    val path: String,
    val packageName: String? = null,
    val imports: List<String> = emptyList(),
    val symbols: List<String> = emptyList(),
    val lineCount: Int = 0,
    val extension: String = "",
    val hasCompose: Boolean = false,
    val hasConstraintLayout: Boolean = false,
    val resourceIds: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)

data class SymbolOccurrence(
    val name: String,
    val kind: String,
    val filePath: String
)

data class ProjectSymbolIndex(
    val files: List<IndexedFile>,
    val symbols: List<SymbolOccurrence>,
    val manifestPackageName: String?,
    val gradleNamespace: String?,
    val applicationId: String?,
    val launcherActivities: List<String>,
    val hasKotlin: Boolean,
    val hasCompose: Boolean,
    val hasGradleWrapper: Boolean,
    val invalidResourceNames: List<String>,
    val projectGraph: ProjectGraph
) {
    fun summary(maxFiles: Int = 16, maxSymbols: Int = 32, maxEdges: Int = 20): String = buildString {
        appendLine("Indexed files: ${files.size}")
        appendLine("Kotlin sources: ${if (hasKotlin) "yes" else "no"}")
        appendLine("Compose usage: ${if (hasCompose) "yes" else "no"}")
        appendLine("Gradle wrapper: ${if (hasGradleWrapper) "present" else "missing"}")
        appendLine("Project graph edges: ${projectGraph.edges.size}")
        listOfNotNull(
            manifestPackageName?.let { "Manifest package: $it" },
            gradleNamespace?.let { "Gradle namespace: $it" },
            applicationId?.let { "Application ID: $it" }
        ).forEach { appendLine(it) }
        if (launcherActivities.isNotEmpty()) {
            appendLine("Launcher activities: ${launcherActivities.joinToString()}")
        }
        if (invalidResourceNames.isNotEmpty()) {
            appendLine("Invalid resource names: ${invalidResourceNames.take(8).joinToString()}")
        }
        appendLine()
        appendLine("Relevant files:")
        files.take(maxFiles).forEach { file ->
            appendLine("- ${file.path}${file.packageName?.let { " [pkg=$it]" } ?: ""}")
        }
        if (symbols.isNotEmpty()) {
            appendLine()
            appendLine("Key symbols:")
            symbols.distinctBy { it.filePath + ":" + it.name }
                .take(maxSymbols)
                .forEach { symbol -> appendLine("- ${symbol.kind}: ${symbol.name} (${symbol.filePath})") }
        }
        if (projectGraph.edges.isNotEmpty()) {
            appendLine()
            appendLine("Key graph edges:")
            projectGraph.edges.take(maxEdges).forEach { edge ->
                appendLine("- ${edge.kind}: ${edge.fromPath} -> ${edge.toPath}${edge.symbol?.let { " [$it]" } ?: ""}")
            }
        }
    }.trim()

    fun selectFocusFiles(
        userRequest: String,
        attachedFiles: List<String>,
        preferredFiles: List<String> = emptyList(),
        limit: Int = 12
    ): List<String> {
        val requestedTokens = tokenize(userRequest)
        val preferred = (preferredFiles + attachedFiles)
            .mapNotNull { raw -> runCatching { FileUtils.normalizeRelativePath(raw) }.getOrNull() }
            .distinct()

        val baseScores = files.associate { file ->
            val pathTokens = tokenize(file.path)
            val symbolTokens = file.symbols.flatMap(::tokenize)
            val importTokens = file.imports.flatMap(::tokenize)
            var score = 0
            if (preferred.contains(file.path)) score += 600
            requestedTokens.forEach { token ->
                if (file.path.contains(token, ignoreCase = true)) score += 40
                if (pathTokens.contains(token)) score += 24
                if (symbolTokens.contains(token)) score += 20
                if (importTokens.contains(token)) score += 10
                if (file.packageName?.contains(token, ignoreCase = true) == true) score += 12
                if (file.resourceIds.any { it.contains(token, ignoreCase = true) }) score += 12
                if (file.tags.any { it.contains(token, ignoreCase = true) }) score += 8
            }
            if (file.path == "app/src/main/AndroidManifest.xml") score += 25
            if (file.path.endsWith("build.gradle.kts") || file.path.endsWith("settings.gradle.kts")) score += 20
            if (file.path.endsWith("activity_main.xml")) score += 15
            file.path to score
        }

        val seeds = (preferred + baseScores.entries.sortedByDescending { it.value }.take(6).map { it.key }).distinct()
        val neighborBoosts = mutableMapOf<String, Int>()
        seeds.forEach { seed ->
            projectGraph.neighbors(seed).forEach { edge ->
                neighborBoosts[edge.toPath] = neighborBoosts.getOrDefault(edge.toPath, 0) + when (edge.kind) {
                    "manifest-entry", "layout-usage", "tools-context" -> 80
                    "import", "resource-usage" -> 48
                    else -> 24
                }
            }
        }

        return files
            .map { file -> file.path to ((baseScores[file.path] ?: 0) + neighborBoosts.getOrDefault(file.path, 0)) }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .map { it.first }
            .let { preferred + it }
            .distinct()
            .take(limit)
    }

    companion object {
        fun build(projectDir: File): ProjectSymbolIndex {
            val canonicalRoot = projectDir.canonicalFile
            val allFiles = canonicalRoot.walkTopDown()
                .filter { it.isFile }
                .filterNot { file ->
                    val relative = file.relativeTo(canonicalRoot).invariantSeparatorsPath
                    relative.startsWith(".git/") ||
                        relative.startsWith(".gradle/") ||
                        relative.startsWith("build/") ||
                        relative.startsWith(".build/") ||
                        relative.startsWith("app/build/") ||
                        relative.startsWith("artifacts/") ||
                        relative.startsWith("snapshots/") ||
                        relative.startsWith("changesets/")
                }
                .sortedBy { it.relativeTo(canonicalRoot).invariantSeparatorsPath }
                .toList()

            val textByPath = mutableMapOf<String, String>()
            val indexedFiles = mutableListOf<IndexedFile>()
            val symbols = mutableListOf<SymbolOccurrence>()
            val invalidResourceNames = mutableListOf<String>()
            var manifestPackage: String? = null
            var gradleNamespace: String? = null
            var applicationId: String? = null
            val launcherActivities = mutableListOf<String>()
            var hasKotlin = false
            var hasCompose = false

            allFiles.forEach { file ->
                val relative = file.relativeTo(canonicalRoot).invariantSeparatorsPath
                val extension = file.extension.lowercase()
                val text = runCatching { file.readText() }.getOrDefault("")
                textByPath[relative] = text
                if (text.isBlank() && extension !in setOf("properties", "pro", "toml")) return@forEach

                if (relative.startsWith("app/src/main/res/")) {
                    val resourceName = file.nameWithoutExtension
                    if (!RES_NAME_REGEX.matches(resourceName)) {
                        invalidResourceNames += relative
                    }
                }

                when (relative) {
                    "app/src/main/AndroidManifest.xml" -> {
                        manifestPackage = MANIFEST_PACKAGE_REGEX.find(text)?.groupValues?.getOrNull(1)
                        launcherActivities += MANIFEST_ACTIVITY_REGEX.findAll(text)
                            .mapNotNull { it.groupValues.getOrNull(1) }
                            .toList()
                    }

                    "app/build.gradle.kts", "app/build.gradle" -> {
                        gradleNamespace = NAMESPACE_REGEX.find(text)?.groupValues?.getOrNull(1) ?: gradleNamespace
                        applicationId = APPLICATION_ID_REGEX.find(text)?.groupValues?.getOrNull(1) ?: applicationId
                    }
                }

                val packageName = PACKAGE_REGEX.find(text)?.groupValues?.getOrNull(1)
                val imports = IMPORT_REGEX.findAll(text).mapNotNull { it.groupValues.getOrNull(1) }.toList()
                val symbolNames = mutableListOf<String>()
                CLASS_REGEX.findAll(text).forEach { match ->
                    val name = match.groupValues.getOrNull(2).orEmpty()
                    if (name.isNotBlank()) {
                        symbolNames += name
                        symbols += SymbolOccurrence(name = name, kind = match.groupValues.getOrNull(1).orEmpty(), filePath = relative)
                    }
                }
                if (extension == "kt" || extension == "kts") {
                    hasKotlin = true
                    FUN_REGEX.findAll(text).forEach { match ->
                        val name = match.groupValues.getOrNull(1).orEmpty()
                        if (name.isNotBlank()) {
                            symbolNames += name
                            symbols += SymbolOccurrence(name = name, kind = "fun", filePath = relative)
                        }
                    }
                }
                if (extension == "java") {
                    METHOD_REGEX.findAll(text).forEach { match ->
                        val name = match.groupValues.getOrNull(3).orEmpty()
                        if (name.isNotBlank() && name !in setOf("if", "for", "while", "switch", "catch")) {
                            symbolNames += name
                            symbols += SymbolOccurrence(name = name, kind = "method", filePath = relative)
                        }
                    }
                }

                val composeUsage = text.contains("@Composable") || text.contains("androidx.compose") || text.contains("setContent {")
                if (composeUsage) hasCompose = true
                val hasConstraintLayout = text.contains("ConstraintLayout") || text.contains("layout_constraint")
                val resourceIds = XML_ID_REGEX.findAll(text).mapNotNull { it.groupValues.getOrNull(1) }.toList()
                val tags = XML_TAG_REGEX.findAll(text).mapNotNull { it.groupValues.getOrNull(1) }.distinct().toList()

                indexedFiles += IndexedFile(
                    path = relative,
                    packageName = packageName,
                    imports = imports,
                    symbols = symbolNames.distinct(),
                    lineCount = text.lineSequence().count(),
                    extension = extension,
                    hasCompose = composeUsage,
                    hasConstraintLayout = hasConstraintLayout,
                    resourceIds = resourceIds,
                    tags = tags
                )
            }

            val symbolFileByQualifiedName = indexedFiles.flatMap { file ->
                file.symbols.mapNotNull { symbol ->
                    val pkg = file.packageName ?: return@mapNotNull null
                    "$pkg.$symbol" to file.path
                }
            }.toMap()
            val symbolFileBySimpleName = indexedFiles.flatMap { file -> file.symbols.map { it to file.path } }
                .groupBy({ it.first }, { it.second })

            val resourcePathByKey = indexedFiles.mapNotNull { file ->
                if (!file.path.startsWith("app/src/main/res/")) return@mapNotNull null
                val parts = file.path.split('/')
                val dir = parts.getOrNull(parts.indexOf("res") + 1) ?: return@mapNotNull null
                val type = dir.substringBefore('-')
                "$type/${File(file.path).nameWithoutExtension}" to file.path
            }.toMap()

            fun resolveActivityPath(activityName: String): String? {
                val fqcn = when {
                    activityName.startsWith(".") && manifestPackage != null -> "$manifestPackage$activityName"
                    '.' !in activityName && manifestPackage != null -> "$manifestPackage.$activityName"
                    else -> activityName
                }
                val simple = fqcn.substringAfterLast('.')
                return symbolFileByQualifiedName[fqcn] ?: symbolFileBySimpleName[simple]?.firstOrNull()
            }

            val edges = mutableListOf<ProjectGraphEdge>()
            indexedFiles.forEach { file ->
                val text = textByPath[file.path].orEmpty()
                file.imports.forEach { imported ->
                    val target = symbolFileByQualifiedName[imported] ?: symbolFileBySimpleName[imported.substringAfterLast('.')]
                        ?.firstOrNull()
                    if (target != null && target != file.path) {
                        edges += ProjectGraphEdge(file.path, target, "import", imported)
                    }
                }
                RESOURCE_REF_REGEX.findAll(text).forEach { match ->
                    val type = match.groupValues.getOrNull(1).orEmpty()
                    val name = match.groupValues.getOrNull(2).orEmpty()
                    val target = resourcePathByKey["$type/$name"]
                    if (target != null) {
                        edges += ProjectGraphEdge(file.path, target, if (type == "layout") "layout-usage" else "resource-usage", "R.$type.$name")
                    }
                }
                if (file.path == "app/src/main/AndroidManifest.xml") {
                    launcherActivities.forEach { activity ->
                        resolveActivityPath(activity)?.let { target ->
                            edges += ProjectGraphEdge(file.path, target, "manifest-entry", activity)
                        }
                    }
                }
                if (file.path.startsWith("app/src/main/res/layout/")) {
                    TOOLS_CONTEXT_REGEX.find(text)?.groupValues?.getOrNull(1)?.let { contextValue ->
                        resolveActivityPath(contextValue)?.let { target ->
                            edges += ProjectGraphEdge(file.path, target, "tools-context", contextValue)
                        }
                    }
                }
                if (file.path == "app/build.gradle.kts" || file.path == "app/build.gradle") {
                    if (manifestPackage != null) {
                        edges += ProjectGraphEdge(file.path, "app/src/main/AndroidManifest.xml", "build-config", manifestPackage)
                    }
                }
            }

            return ProjectSymbolIndex(
                files = indexedFiles,
                symbols = symbols,
                manifestPackageName = manifestPackage,
                gradleNamespace = gradleNamespace,
                applicationId = applicationId,
                launcherActivities = launcherActivities.distinct(),
                hasKotlin = hasKotlin,
                hasCompose = hasCompose,
                hasGradleWrapper = File(canonicalRoot, "gradlew").exists() && File(canonicalRoot, "gradle/wrapper/gradle-wrapper.jar").exists(),
                invalidResourceNames = invalidResourceNames.distinct().sorted(),
                projectGraph = ProjectGraph(edges.distinct())
            )
        }

        private fun tokenize(value: String): List<String> = value
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }

    }
}
