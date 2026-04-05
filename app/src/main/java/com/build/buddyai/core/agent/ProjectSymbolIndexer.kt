package com.build.buddyai.core.agent

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectSymbolIndexer @Inject constructor() {

    data class SymbolIndex(
        val filesIndexed: Int,
        val symbols: List<ProjectSymbol>
    ) {
        fun toPrompt(maxEntries: Int = 140): String {
            if (symbols.isEmpty()) return "No symbols indexed."
            return buildString {
                appendLine("Project symbol index:")
                symbols.take(maxEntries).forEach { symbol ->
                    append("- ")
                    append(symbol.kind)
                    append(": ")
                    append(symbol.name)
                    symbol.container?.takeIf { it.isNotBlank() }?.let { append(" in $it") }
                    append(" @ ")
                    appendLine(symbol.filePath)
                }
            }.trim()
        }
    }

    data class ProjectSymbol(
        val kind: String,
        val name: String,
        val filePath: String,
        val container: String? = null
    )

    fun index(projectDir: File): SymbolIndex {
        val files = projectDir.walkTopDown()
            .filter { it.isFile }
            .filterNot { isIgnored(projectDir, it) }
            .filter { it.extension.lowercase() in setOf("kt", "kts", "java", "xml") }
            .sortedBy { it.relativeTo(projectDir).invariantSeparatorsPath }
            .toList()

        val symbols = buildList {
            files.forEach { file ->
                addAll(extractSymbols(projectDir, file))
            }
        }
        return SymbolIndex(filesIndexed = files.size, symbols = symbols.distinctBy { listOf(it.kind, it.name, it.filePath, it.container) })
    }

    private fun extractSymbols(projectDir: File, file: File): List<ProjectSymbol> {
        val relative = file.relativeTo(projectDir).invariantSeparatorsPath
        val text = runCatching { file.readText() }.getOrDefault("")
        if (text.isBlank()) return emptyList()
        return when (file.extension.lowercase()) {
            "java", "kt", "kts" -> sourceSymbols(relative, text)
            "xml" -> xmlSymbols(relative, text)
            else -> emptyList()
        }
    }

    private fun sourceSymbols(relative: String, text: String): List<ProjectSymbol> {
        val packageName = Regex("""package\s+([A-Za-z0-9_.]+)""").find(text)?.groupValues?.get(1)
        val classes = Regex("""\b(class|interface|object|enum\s+class)\s+([A-Za-z0-9_]+)""")
            .findAll(text).map { it.groupValues[2] }.toList()
        val methods = Regex("""\b(fun|void|int|long|boolean|String|List<[^>]+>|MutableList<[^>]+>|[A-Z][A-Za-z0-9_<>,? ]+)\s+([a-zA-Z_][A-Za-zA-Z0-9_]*)\s*\(""")
            .findAll(text)
            .map { it.groupValues[2] }
            .filterNot { it in setOf("if", "for", "while", "switch", "catch") }
            .distinct()
            .toList()
        val results = mutableListOf<ProjectSymbol>()
        packageName?.let { results += ProjectSymbol(kind = "package", name = it, filePath = relative) }
        classes.forEach { className ->
            results += ProjectSymbol(kind = "type", name = className, filePath = relative, container = packageName)
        }
        methods.take(25).forEach { methodName ->
            results += ProjectSymbol(kind = "member", name = methodName, filePath = relative, container = classes.firstOrNull() ?: packageName)
        }
        return results
    }

    private fun xmlSymbols(relative: String, text: String): List<ProjectSymbol> {
        val results = mutableListOf<ProjectSymbol>()
        Regex("""android:id=\"@\+id/([^\"]+)\"""")
            .findAll(text)
            .forEach { results += ProjectSymbol(kind = "view-id", name = it.groupValues[1], filePath = relative) }
        Regex("""<string\s+name=\"([^\"]+)\"""")
            .findAll(text)
            .forEach { results += ProjectSymbol(kind = "string", name = it.groupValues[1], filePath = relative) }
        Regex("""<style\s+name=\"([^\"]+)\"""")
            .findAll(text)
            .forEach { results += ProjectSymbol(kind = "style", name = it.groupValues[1], filePath = relative) }
        Regex("""<activity[^>]+android:name=\"([^\"]+)\"""")
            .findAll(text)
            .forEach { results += ProjectSymbol(kind = "activity", name = it.groupValues[1], filePath = relative) }
        Regex("""<service[^>]+android:name=\"([^\"]+)\"""")
            .findAll(text)
            .forEach { results += ProjectSymbol(kind = "service", name = it.groupValues[1], filePath = relative) }
        Regex("""<receiver[^>]+android:name=\"([^\"]+)\"""")
            .findAll(text)
            .forEach { results += ProjectSymbol(kind = "receiver", name = it.groupValues[1], filePath = relative) }
        return results
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
