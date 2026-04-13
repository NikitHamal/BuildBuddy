package com.build.buddyai.core.agent

import com.build.buddyai.core.model.BuildRecord
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectSemanticGraphIndexer @Inject constructor() {

    data class ProjectSemanticGraph(
        val modules: List<ModuleNode>,
        val resources: ResourceIndex,
        val manifest: ManifestIndex,
        val navigation: List<NavigationNode>,
        val dependencies: List<DependencyNode>,
        val apis: List<ApiNode>,
        val packageOwnership: List<PackageOwnership>,
        val resourceOwnership: List<ResourceOwner>,
        val buildHistory: List<BuildHistoryNode>,
        val candidateFiles: List<String>
    ) {
        fun summary(maxEntries: Int = 160): String = buildString {
            appendLine("Project semantic graph:")
            if (modules.isNotEmpty()) {
                appendLine("Modules:")
                modules.forEach { appendLine("- ${it.name} @ ${it.buildFile} roots=${it.sourceRoots.joinToString()}") }
            }
            if (manifest.packageName.isNotBlank()) appendLine("Manifest package: ${manifest.packageName}")
            if (manifest.placeholders.isNotEmpty()) appendLine("Manifest placeholders: ${manifest.placeholders.joinToString()}")
            if (manifest.components.isNotEmpty()) {
                appendLine("Manifest components:")
                manifest.components.take(20).forEach { appendLine("- ${it.kind}: ${it.name}") }
            }
            if (navigation.isNotEmpty()) {
                appendLine("Navigation graph:")
                navigation.take(24).forEach { appendLine("- ${it.kind}: ${it.name} @ ${it.filePath}") }
            }
            if (dependencies.isNotEmpty()) {
                appendLine("Dependencies:")
                dependencies.take(24).forEach { dep ->
                    appendLine("- ${dep.configuration}: ${dep.notation} @ ${dep.buildFile}")
                }
            }
            if (resources.layouts.isNotEmpty() || resources.strings.isNotEmpty()) {
                appendLine("Resources:")
                if (resources.layouts.isNotEmpty()) appendLine("- layouts: ${resources.layouts.take(20).joinToString()}")
                if (resources.strings.isNotEmpty()) appendLine("- strings: ${resources.strings.take(30).joinToString()}")
                if (resources.viewIds.isNotEmpty()) appendLine("- ids: ${resources.viewIds.take(30).joinToString()}")
            }
            if (resourceOwnership.isNotEmpty()) {
                appendLine("Resource ownership:")
                resourceOwnership.take(20).forEach { appendLine("- ${it.resourceType}/${it.name} -> ${it.filePath}") }
            }
            if (apis.isNotEmpty()) {
                appendLine("APIs:")
                apis.take(maxEntries.coerceAtMost(60)).forEach { api -> appendLine("- ${api.kind}: ${api.qualifiedName} @ ${api.filePath}") }
            }
            if (packageOwnership.isNotEmpty()) {
                appendLine("Package ownership:")
                packageOwnership.take(20).forEach { appendLine("- ${it.packageName} owns ${it.rootPath}") }
            }
            if (buildHistory.isNotEmpty()) {
                appendLine("Build history:")
                buildHistory.take(8).forEach { appendLine("- ${it.status} ${it.variant} ${it.errorSummary.orEmpty()}".trim()) }
            }
            if (candidateFiles.isNotEmpty()) {
                appendLine("Graph-selected focus files:")
                candidateFiles.take(40).forEach { appendLine("- $it") }
            }
        }.trim()
    }

    data class ModuleNode(val name: String, val buildFile: String, val sourceRoots: List<String>)
    data class ManifestIndex(val packageName: String, val components: List<ManifestComponent>, val placeholders: List<String>)
    data class ManifestComponent(val kind: String, val name: String)
    data class ResourceIndex(
        val layouts: List<String>,
        val strings: List<String>,
        val drawables: List<String>,
        val viewIds: List<String>,
        val stringReferences: List<String>
    )
    data class DependencyNode(val configuration: String, val notation: String, val buildFile: String)
    data class NavigationNode(val kind: String, val name: String, val filePath: String)
    data class ApiNode(val kind: String, val qualifiedName: String, val filePath: String)
    data class PackageOwnership(val packageName: String, val rootPath: String)
    data class ResourceOwner(val resourceType: String, val name: String, val filePath: String)
    data class BuildHistoryNode(val status: String, val variant: String, val errorSummary: String?)

    fun index(projectDir: File, focusHint: String, buildHistory: List<BuildRecord>): ProjectSemanticGraph {
        val files = projectDir.walkTopDown()
            .filter { it.isFile }
            .filterNot { isIgnored(projectDir, it) }
            .sortedBy { it.relativeTo(projectDir).invariantSeparatorsPath }
            .toList()
        val textCache = mutableMapOf<File, String>()
        fun readTextCached(file: File): String = textCache.getOrPut(file) {
            runCatching { file.readText() }.getOrDefault("")
        }
        val lowerTextCache = mutableMapOf<File, String>()
        fun readLowerTextCached(file: File): String = lowerTextCache.getOrPut(file) {
            readTextCached(file).lowercase()
        }

        val modules = files.filter { it.name == "build.gradle.kts" || it.name == "build.gradle" }
            .map { buildFile ->
                val relative = buildFile.relativeTo(projectDir).invariantSeparatorsPath
                val root = buildFile.parentFile.relativeTo(projectDir).invariantSeparatorsPath.ifBlank { "." }
                ModuleNode(
                    name = if (root == ".") "root" else root.substringAfterLast('/'),
                    buildFile = relative,
                    sourceRoots = listOf("$root/src/main/java", "$root/src/main/kotlin", "$root/src/main/res").map { it.removePrefix("./") }
                )
            }

        val manifestFile = File(projectDir, "app/src/main/AndroidManifest.xml")
        val manifestText = if (manifestFile.exists()) readTextCached(manifestFile) else ""
        val manifest = ManifestIndex(
            packageName = Regex("""package\s*=\s*[\"']([^\"']+)[\"']""").find(manifestText)?.groupValues?.get(1).orEmpty(),
            components = buildList {
                listOf("activity", "service", "receiver", "provider", "application").forEach { kind ->
                    Regex("""<$kind[^>]+android:name=[\"']([^\"']+)[\"']""")
                        .findAll(manifestText)
                        .forEach { add(ManifestComponent(kind, it.groupValues[1])) }
                }
            },
            placeholders = Regex("""\$\{([A-Za-z0-9_.-]+)\}""").findAll(manifestText).map { it.groupValues[1] }.distinct().toList()
        )

        val resFiles = files.filter { it.relativeTo(projectDir).invariantSeparatorsPath.startsWith("app/src/main/res/") }
        val layoutFiles = resFiles.filter { it.parentFile?.name?.startsWith("layout") == true }
        val valuesFiles = resFiles.filter { it.parentFile?.name?.startsWith("values") == true }
        val drawableFiles = resFiles.filter { it.parentFile?.name?.startsWith("drawable") == true }
        val allXmlText = (layoutFiles + valuesFiles).associateWith { readTextCached(it) }

        val resources = ResourceIndex(
            layouts = layoutFiles.map { it.nameWithoutExtension }.distinct(),
            strings = valuesFiles.flatMap { file -> Regex("""<string\s+name=[\"']([^\"']+)[\"']""").findAll(allXmlText[file].orEmpty()).map { it.groupValues[1] }.toList() }.distinct(),
            drawables = drawableFiles.map { it.nameWithoutExtension }.distinct(),
            viewIds = layoutFiles.flatMap { file -> Regex("""android:id=[\"']@\+id/([^\"']+)[\"']""").findAll(allXmlText[file].orEmpty()).map { it.groupValues[1] }.toList() }.distinct(),
            stringReferences = files.filter { it.extension.lowercase() in setOf("kt", "java", "xml") }.flatMap { file ->
                val text = readTextCached(file)
                Regex("""R\.string\.([A-Za-z0-9_]+)|@string/([A-Za-z0-9_]+)""").findAll(text)
                    .mapNotNull { match -> match.groupValues.drop(1).firstOrNull { it.isNotBlank() } }
                    .toList()
            }.distinct()
        )

        val resourceOwnership = buildList {
            layoutFiles.forEach { add(ResourceOwner("layout", it.nameWithoutExtension, it.relativeTo(projectDir).invariantSeparatorsPath)) }
            valuesFiles.forEach { file ->
                Regex("""<string\s+name=[\"']([^\"']+)[\"']""").findAll(allXmlText[file].orEmpty()).forEach {
                    add(ResourceOwner("string", it.groupValues[1], file.relativeTo(projectDir).invariantSeparatorsPath))
                }
            }
        }.distinctBy { Triple(it.resourceType, it.name, it.filePath) }

        val dependencies = modules.flatMap { module ->
            val buildFile = File(projectDir, module.buildFile)
            val text = readTextCached(buildFile)
            Regex("""\b(implementation|api|ksp|kapt|compileOnly|runtimeOnly|testImplementation|androidTestImplementation|debugImplementation|releaseImplementation)\s*\(([^\n]+)\)""")
                .findAll(text)
                .map { DependencyNode(it.groupValues[1], it.groupValues[2].trim(), module.buildFile) }
                .toList()
        }

        val apis = files.filter { it.extension.lowercase() in setOf("kt", "java") }.flatMap { file ->
            val relative = file.relativeTo(projectDir).invariantSeparatorsPath
            val text = readTextCached(file)
            val packageName = Regex("""package\s+([A-Za-z0-9_.]+)""").find(text)?.groupValues?.get(1).orEmpty()
            val types = Regex("""\b(class|interface|object|enum\s+class)\s+([A-Za-z0-9_]+)""")
                .findAll(text)
                .map { ApiNode(kind = it.groupValues[1], qualifiedName = listOf(packageName, it.groupValues[2]).filter { s -> s.isNotBlank() }.joinToString("."), filePath = relative) }
                .toList()
            val functions = Regex("""\bfun\s+([A-Za-z0-9_]+)\s*\(|\b(public|private|protected)?\s*(static\s+)?[A-Za-z0-9_<>,?\[\] ]+\s+([A-Za-z0-9_]+)\s*\(""")
                .findAll(text)
                .mapNotNull { match -> match.groupValues[1].ifBlank { match.groupValues[4] }.takeIf { it.isNotBlank() } }
                .filterNot { it in setOf("if", "for", "while", "switch") }
                .map { ApiNode(kind = "member", qualifiedName = listOf(packageName, it).filter { s -> s.isNotBlank() }.joinToString("."), filePath = relative) }
                .toList()
            (types + functions).distinctBy { it.kind to it.qualifiedName }
        }

        val packageOwnership = files.filter { it.extension.lowercase() in setOf("kt", "java") }.mapNotNull { file ->
            val text = readTextCached(file)
            val packageName = Regex("""package\s+([A-Za-z0-9_.]+)""").find(text)?.groupValues?.get(1) ?: return@mapNotNull null
            val rootPath = file.parentFile?.relativeTo(projectDir)?.invariantSeparatorsPath.orEmpty()
            PackageOwnership(packageName, rootPath)
        }.distinctBy { it.packageName to it.rootPath }

        val navigation = buildList {
            files.filter { it.extension.lowercase() == "xml" || it.extension.lowercase() == "kt" || it.extension.lowercase() == "java" }.forEach { file ->
                val relative = file.relativeTo(projectDir).invariantSeparatorsPath
                val text = readTextCached(file)
                FRAGMENT_REGEX
                    .findAll(text)
                    .forEach { add(NavigationNode("fragment", "${it.groupValues[1]} -> ${it.groupValues[2]}", relative)) }
                COMPOSE_ROUTE_REGEX
                    .findAll(text)
                    .forEach { add(NavigationNode("compose-route", it.groupValues[1], relative)) }
                NAVIGATE_CALL_REGEX
                    .findAll(text)
                    .forEach { add(NavigationNode("navigate-call", it.groupValues[1], relative)) }
                ACTIVITY_LAUNCH_REGEX
                    .findAll(text)
                    .forEach { add(NavigationNode("activity-launch", it.groupValues[1], relative)) }
            }
            manifest.components.forEach { add(NavigationNode(it.kind, it.name, "app/src/main/AndroidManifest.xml")) }
        }.distinctBy { Triple(it.kind, it.name, it.filePath) }

        val focusTokens = focusHint.lowercase().split(Regex("[^a-z0-9_./]+"))
            .filter { it.length >= 3 }
            .distinct()

        val candidateScores = mutableMapOf<String, Int>()
        fun score(path: String, amount: Int) { candidateScores[path] = (candidateScores[path] ?: 0) + amount }
        manifest.components.forEach { component -> apis.firstOrNull { it.qualifiedName.endsWith(component.name.trimStart('.')) }?.let { score(it.filePath, 8) } }
        navigation.forEach { score(it.filePath, 6) }
        dependencies.forEach { score(it.buildFile, 6) }
        resourceOwnership.forEach { score(it.filePath, 4) }
        layoutFiles.forEach { file -> score(file.relativeTo(projectDir).invariantSeparatorsPath, 4) }
        focusTokens.forEach { token ->
            files.forEach { file ->
                val relative = file.relativeTo(projectDir).invariantSeparatorsPath
                if (relative.lowercase().contains(token)) score(relative, 10)
                if (readLowerTextCached(file).contains(token)) score(relative, 4)
            }
        }

        return ProjectSemanticGraph(
            modules = modules,
            resources = resources,
            manifest = manifest,
            navigation = navigation,
            dependencies = dependencies.distinctBy { Triple(it.configuration, it.notation, it.buildFile) },
            apis = apis.distinctBy { Triple(it.kind, it.qualifiedName, it.filePath) },
            packageOwnership = packageOwnership,
            resourceOwnership = resourceOwnership,
            buildHistory = buildHistory.map { BuildHistoryNode(it.status.name, it.buildVariant, it.errorSummary) },
            candidateFiles = candidateScores.entries.sortedByDescending { it.value }.map { it.key }.take(40)
        )
    }

    private fun isIgnored(projectDir: File, file: File): Boolean {
        val relative = file.relativeTo(projectDir).invariantSeparatorsPath
        return relative.startsWith(".git/") ||
            relative.startsWith(".gradle/") ||
            relative.startsWith(".build/") ||
            relative.startsWith("build/") ||
            relative.startsWith("app/build/") ||
            relative.startsWith("artifacts/") ||
            relative.startsWith("snapshots/")
    }

    companion object {
        private val FRAGMENT_REGEX = Regex("""<fragment[^>]+android:id=[\"']@\+id/([^\"']+)[\"'][^>]+android:name=[\"']([^\"']+)[\"']""")
        private val COMPOSE_ROUTE_REGEX = Regex("""composable\s*\(\s*route\s*=\s*[\"']([^\"']+)[\"']""")
        private val NAVIGATE_CALL_REGEX = Regex("""findNavController\(\)\.navigate\([\"']([^\"']+)[\"']\)""")
        private val ACTIVITY_LAUNCH_REGEX = Regex("""startActivity\([^\n]+([A-Za-z0-9_]+)::class\.java""")
    }
}
