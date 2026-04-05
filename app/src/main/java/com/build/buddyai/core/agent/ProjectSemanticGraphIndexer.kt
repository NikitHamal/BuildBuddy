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
        val buildHistory: List<BuildHistoryNode>,
        val candidateFiles: List<String>
    ) {
        fun summary(maxEntries: Int = 160): String = buildString {
            appendLine("Project semantic graph:")
            if (modules.isNotEmpty()) {
                appendLine("Modules:")
                modules.forEach { module ->
                    appendLine("- ${module.name} @ ${module.buildFile}")
                }
            }
            if (manifest.packageName.isNotBlank()) {
                appendLine("Manifest package: ${manifest.packageName}")
            }
            if (manifest.components.isNotEmpty()) {
                appendLine("Manifest components:")
                manifest.components.take(20).forEach { component ->
                    appendLine("- ${component.kind}: ${component.name}")
                }
            }
            if (dependencies.isNotEmpty()) {
                appendLine("Dependencies:")
                dependencies.take(20).forEach { dep ->
                    appendLine("- ${dep.configuration}: ${dep.notation}")
                }
            }
            if (resources.layouts.isNotEmpty() || resources.strings.isNotEmpty()) {
                appendLine("Resources:")
                if (resources.layouts.isNotEmpty()) appendLine("- layouts: ${resources.layouts.take(20).joinToString()}")
                if (resources.strings.isNotEmpty()) appendLine("- strings: ${resources.strings.take(30).joinToString()}")
                if (resources.viewIds.isNotEmpty()) appendLine("- ids: ${resources.viewIds.take(30).joinToString()}")
            }
            if (navigation.isNotEmpty()) {
                appendLine("Navigation:")
                navigation.take(20).forEach { node ->
                    appendLine("- ${node.kind}: ${node.name} @ ${node.filePath}")
                }
            }
            if (apis.isNotEmpty()) {
                appendLine("APIs:")
                apis.take(maxEntries.coerceAtMost(60)).forEach { api ->
                    appendLine("- ${api.kind}: ${api.qualifiedName} @ ${api.filePath}")
                }
            }
            if (packageOwnership.isNotEmpty()) {
                appendLine("Package ownership:")
                packageOwnership.take(20).forEach {
                    appendLine("- ${it.packageName} owns ${it.rootPath}")
                }
            }
            if (buildHistory.isNotEmpty()) {
                appendLine("Build history:")
                buildHistory.take(8).forEach {
                    appendLine("- ${it.status} ${it.variant} ${it.errorSummary.orEmpty()}".trim())
                }
            }
            if (candidateFiles.isNotEmpty()) {
                appendLine("Graph-selected focus files:")
                candidateFiles.take(40).forEach { appendLine("- $it") }
            }
        }.trim()
    }

    data class ModuleNode(val name: String, val buildFile: String, val sourceRoots: List<String>)
    data class ManifestIndex(val packageName: String, val components: List<ManifestComponent>)
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
    data class BuildHistoryNode(val status: String, val variant: String, val errorSummary: String?)

    fun index(projectDir: File, focusHint: String, buildHistory: List<BuildRecord>): ProjectSemanticGraph {
        val files = projectDir.walkTopDown()
            .filter { it.isFile }
            .filterNot { isIgnored(projectDir, it) }
            .sortedBy { it.relativeTo(projectDir).invariantSeparatorsPath }
            .toList()

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
        val manifestText = runCatching { manifestFile.readText() }.getOrDefault("")
        val manifest = ManifestIndex(
            packageName = Regex("""package\s*=\s*[\"']([^\"']+)[\"']""").find(manifestText)?.groupValues?.get(1).orEmpty(),
            components = buildList {
                listOf("activity", "service", "receiver", "provider", "application").forEach { kind ->
                    Regex("""<$kind[^>]+android:name=[\"']([^\"']+)[\"']""")
                        .findAll(manifestText)
                        .forEach { add(ManifestComponent(kind, it.groupValues[1])) }
                }
            }
        )

        val resDir = File(projectDir, "app/src/main/res")
        val layoutFiles = resDir.walkTopDown().filter { it.isFile && it.parentFile?.name?.startsWith("layout") == true }.toList()
        val valuesFiles = resDir.walkTopDown().filter { it.isFile && it.parentFile?.name?.startsWith("values") == true }.toList()
        val drawableFiles = resDir.walkTopDown().filter { it.isFile && it.parentFile?.name?.startsWith("drawable") == true }.toList()
        val allXmlText = (layoutFiles + valuesFiles).associate { it to runCatching { it.readText() }.getOrDefault("") }
        val resources = ResourceIndex(
            layouts = layoutFiles.map { it.nameWithoutExtension },
            strings = valuesFiles.flatMap { file ->
                Regex("""<string\s+name=[\"']([^\"']+)[\"']""")
                    .findAll(allXmlText[file].orEmpty())
                    .map { it.groupValues[1] }
                    .toList()
            }.distinct(),
            drawables = drawableFiles.map { it.nameWithoutExtension }.distinct(),
            viewIds = layoutFiles.flatMap { file ->
                Regex("""android:id=[\"']@\+id/([^\"']+)[\"']""")
                    .findAll(allXmlText[file].orEmpty())
                    .map { it.groupValues[1] }
                    .toList()
            }.distinct(),
            stringReferences = files.filter { it.extension.lowercase() in setOf("kt", "java", "xml") }.flatMap { file ->
                val text = runCatching { file.readText() }.getOrDefault("")
                Regex("""R\.string\.([A-Za-z0-9_]+)|@string/([A-Za-z0-9_]+)""")
                    .findAll(text)
                    .mapNotNull { match -> match.groupValues.drop(1).firstOrNull { it.isNotBlank() } }
                    .toList()
            }.distinct()
        )

        val dependencies = modules.flatMap { module ->
            val buildFile = File(projectDir, module.buildFile)
            val text = runCatching { buildFile.readText() }.getOrDefault("")
            Regex("""\b(implementation|api|ksp|kapt|compileOnly|runtimeOnly|testImplementation|androidTestImplementation)\s*\(([^\n]+)\)""")
                .findAll(text)
                .map { DependencyNode(it.groupValues[1], it.groupValues[2].trim(), module.buildFile) }
                .toList()
        }

        val apis = files.filter { it.extension.lowercase() in setOf("kt", "java") }.flatMap { file ->
            val relative = file.relativeTo(projectDir).invariantSeparatorsPath
            val text = runCatching { file.readText() }.getOrDefault("")
            val packageName = Regex("""package\s+([A-Za-z0-9_.]+)""").find(text)?.groupValues?.get(1).orEmpty()
            val types = Regex("""\b(class|interface|object|enum\s+class)\s+([A-Za-z0-9_]+)""")
                .findAll(text)
                .map { ApiNode(kind = it.groupValues[1], qualifiedName = listOf(packageName, it.groupValues[2]).filter { s -> s.isNotBlank() }.joinToString("."), filePath = relative) }
                .toList()
            val functions = Regex("""\bfun\s+([A-Za-z0-9_]+)\s*\(|\b(public|private|protected)?\s*(static\s+)?[A-Za-z0-9_<>,?\[\] ]+\s+([A-Za-z0-9_]+)\s*\(""")
                .findAll(text)
                .mapNotNull { match ->
                    val name = match.groupValues[1].ifBlank { match.groupValues[4] }
                    name.takeIf { it.isNotBlank() }
                }
                .filterNot { it in setOf("if", "for", "while", "switch") }
                .map { ApiNode(kind = "member", qualifiedName = listOf(packageName, it).filter { s -> s.isNotBlank() }.joinToString("."), filePath = relative) }
                .toList()
            (types + functions).distinctBy { it.kind to it.qualifiedName }
        }

        val packageOwnership = files.filter { it.extension.lowercase() in setOf("kt", "java") }.mapNotNull { file ->
            val text = runCatching { file.readText() }.getOrDefault("")
            val packageName = Regex("""package\s+([A-Za-z0-9_.]+)""").find(text)?.groupValues?.get(1) ?: return@mapNotNull null
            val rootPath = file.parentFile?.relativeTo(projectDir)?.invariantSeparatorsPath.orEmpty()
            PackageOwnership(packageName, rootPath)
        }.distinctBy { it.packageName to it.rootPath }

        val navigation = buildList {
            files.filter { it.extension.lowercase() == "xml" }.forEach { file ->
                val relative = file.relativeTo(projectDir).invariantSeparatorsPath
                val text = runCatching { file.readText() }.getOrDefault("")
                Regex("""<fragment[^>]+android:id=[\"']@\+id/([^\"']+)[\"'][^>]+android:name=[\"']([^\"']+)[\"']""")
                    .findAll(text)
                    .forEach { add(NavigationNode("fragment", "${it.groupValues[1]} -> ${it.groupValues[2]}", relative)) }
                Regex("""composable\s*\(\s*route\s*=\s*[\"']([^\"']+)[\"']""")
                    .findAll(text)
                    .forEach { add(NavigationNode("compose-route", it.groupValues[1], relative)) }
            }
            manifest.components.forEach { add(NavigationNode(it.kind, it.name, "app/src/main/AndroidManifest.xml")) }
        }

        val focusTokens = focusHint.lowercase().split(Regex("[^a-z0-9_./]+"))
            .filter { it.length >= 3 }
            .distinct()

        val candidateScores = mutableMapOf<String, Int>()
        fun score(path: String, amount: Int) {
            candidateScores[path] = (candidateScores[path] ?: 0) + amount
        }
        manifest.components.forEach { component ->
            val match = apis.firstOrNull { api -> api.qualifiedName.endsWith(component.name.trimStart('.')) }
            match?.let { score(it.filePath, 6) }
        }
        navigation.forEach { score(it.filePath, 4) }
        dependencies.forEach { score(it.buildFile, 5) }
        layoutFiles.forEach { file -> score(file.relativeTo(projectDir).invariantSeparatorsPath, 3) }
        focusTokens.forEach { token ->
            files.forEach { file ->
                val relative = file.relativeTo(projectDir).invariantSeparatorsPath
                if (relative.lowercase().contains(token)) score(relative, 8)
                val text = runCatching { file.readText() }.getOrDefault("")
                if (text.lowercase().contains(token)) score(relative, 4)
            }
        }

        val candidateFiles = candidateScores.entries.sortedByDescending { it.value }.map { it.key }.take(40)

        return ProjectSemanticGraph(
            modules = modules,
            resources = resources,
            manifest = manifest,
            navigation = navigation.distinctBy { it.kind to it.name to it.filePath },
            dependencies = dependencies.distinctBy { it.configuration to it.notation to it.buildFile },
            apis = apis.distinctBy { it.kind to it.qualifiedName to it.filePath },
            packageOwnership = packageOwnership,
            buildHistory = buildHistory.map { BuildHistoryNode(it.status.name, it.buildVariant, it.errorSummary) },
            candidateFiles = candidateFiles
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
}
