package com.build.buddyai.core.agent

import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.model.FileDiff
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

@Serializable
data class AgentStructuredEdit(
    val path: String,
    val kind: String,
    val target: String? = null,
    val selector: String? = null,
    val content: String? = null
)

object StructuredEditEngine {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(rawContent: String): List<AgentStructuredEdit> {
        val singleRegex = Regex("""```buildbuddy-edit\s*(\{[\s\S]*?\})\s*```""")
        val multiRegex = Regex("""```buildbuddy-edits\s*(\[[\s\S]*?\])\s*```""")
        val edits = mutableListOf<AgentStructuredEdit>()

        multiRegex.findAll(rawContent).forEach { match ->
            val payload = match.groupValues.getOrNull(1).orEmpty()
            edits += runCatching { json.decodeFromString<List<AgentStructuredEdit>>(payload) }.getOrDefault(emptyList())
        }
        singleRegex.findAll(rawContent).forEach { match ->
            val payload = match.groupValues.getOrNull(1).orEmpty()
            runCatching { json.decodeFromString<AgentStructuredEdit>(payload) }.getOrNull()?.let(edits::add)
        }
        return edits.filter { it.path.isNotBlank() && it.kind.isNotBlank() }
    }

    fun protocolInstructions(): String = """
Prefer surgical edit blocks whenever you are updating existing files.

Single edit:
```buildbuddy-edit
{"path":"app/src/main/java/.../MainActivity.kt","kind":"upsert_import|remove_import|replace_declaration|insert_in_class|replace_text|set_xml_attribute|replace_xml_node|append_xml_node|delete_xml_node","target":"optional target","selector":"optional xml selector","content":"new content"}
```

Multiple edits:
```buildbuddy-edits
[{...},{...}]
```

Rules:
- Use filepath blocks only for brand-new files or when a full rewrite is truly necessary.
- For replace_declaration, target must be class:<Name>, fun:<Name>, or method:<Name>.
- For insert_in_class, target must be class:<Name> and content is inserted before the closing brace.
- For replace_text, target is the exact text to replace and content is the replacement.
- For XML edits, selector can be tag:<TagName>, @id/<viewId>, or root.
""".trimIndent()

    fun apply(projectDir: java.io.File, edit: AgentStructuredEdit): FileDiff? {
        val normalized = runCatching { FileUtils.normalizeRelativePath(edit.path) }.getOrNull() ?: return null
        val original = FileUtils.readFileContent(projectDir, normalized).orEmpty()
        if (original.isBlank()) return null
        val updated = when {
            normalized.endsWith(".xml") -> XmlEditApplier.apply(original, edit)
            normalized.endsWith(".kt") || normalized.endsWith(".java") || normalized.endsWith(".kts") -> SourceEditApplier.apply(original, edit)
            else -> when (edit.kind.lowercase()) {
                "replace_text" -> original.replace(edit.target.orEmpty(), edit.content.orEmpty())
                else -> original
            }
        }
        if (updated == original) return null
        FileUtils.writeFileContent(projectDir, normalized, updated)
        return FileDiff(
            filePath = normalized,
            originalContent = original,
            modifiedContent = updated,
            additions = updated.lineSequence().count().coerceAtLeast(1),
            deletions = original.lineSequence().count()
        )
    }
}

private object SourceEditApplier {
    private val importRegex = Regex("""^\s*import\s+([^\n]+)$""", RegexOption.MULTILINE)
    private val classRegexTemplate = { name: String -> Regex("""\b(class|interface|object|enum\s+class|sealed\s+class|data\s+class)\s+${Regex.escape(name)}\b""") }
    private val funRegexTemplate = { name: String -> Regex("""\bfun\s+${Regex.escape(name)}\s*\(""") }
    private val methodRegexTemplate = { name: String -> Regex("""\b(public|protected|private)?\s*(static\s+)?[A-Za-z0-9_<>,.?\[\]\s]+\s+${Regex.escape(name)}\s*\(""") }

    fun apply(source: String, edit: AgentStructuredEdit): String {
        return when (edit.kind.lowercase()) {
            "upsert_import" -> upsertImport(source, edit.content.orEmpty())
            "remove_import" -> removeImport(source, edit.content ?: edit.target.orEmpty())
            "replace_declaration" -> replaceDeclaration(source, edit.target.orEmpty(), edit.content.orEmpty())
            "insert_in_class" -> insertInClass(source, edit.target.orEmpty(), edit.content.orEmpty())
            "replace_text" -> source.replace(edit.target.orEmpty(), edit.content.orEmpty())
            else -> source
        }
    }

    private fun upsertImport(source: String, importValue: String): String {
        if (importValue.isBlank() || source.contains("import $importValue")) return source
        val packageMatch = Regex("""^\s*package\s+[^\n]+\n""", RegexOption.MULTILINE).find(source)
        val imports = importRegex.findAll(source).toList()
        return when {
            imports.isNotEmpty() -> {
                val start = imports.first().range.first
                val end = imports.last().range.last + 1
                val merged = (imports.map { it.groupValues[1].trim() } + importValue).distinct().sorted().joinToString("\n") { "import $it" }
                source.replaceRange(start, end, "$merged\n")
            }
            packageMatch != null -> {
                val insertAt = packageMatch.range.last + 1
                source.replaceRange(insertAt, insertAt, "\nimport $importValue\n")
            }
            else -> "import $importValue\n$source"
        }
    }

    private fun removeImport(source: String, importValue: String): String {
        return source.lineSequence()
            .filterNot { it.trim() == "import $importValue" }
            .joinToString("\n")
    }

    private fun replaceDeclaration(source: String, target: String, replacement: String): String {
        val range = findDeclarationRange(source, target) ?: return source
        return source.replaceRange(range.first, range.last + 1, replacement.trimEnd())
    }

    private fun insertInClass(source: String, target: String, content: String): String {
        val className = target.substringAfter(":", "")
        val range = findDeclarationRange(source, "class:$className") ?: return source
        val insertionPoint = range.last
        val newline = if (source.contains("\r\n")) "\r\n" else "\n"
        val snippet = buildString {
            append(newline)
            append(content.trimEnd())
            append(newline)
        }
        return source.replaceRange(insertionPoint, insertionPoint, snippet)
    }

    private fun findDeclarationRange(source: String, target: String): IntRange? {
        val (kind, name) = target.split(':', limit = 2).let {
            it.firstOrNull().orEmpty() to it.getOrNull(1).orEmpty()
        }
        if (name.isBlank()) return null
        val match = when (kind.lowercase()) {
            "class" -> classRegexTemplate(name).find(source)
            "fun" -> funRegexTemplate(name).find(source)
            "method" -> methodRegexTemplate(name).find(source)
            else -> null
        } ?: return null
        val bodyStart = source.indexOf('{', match.range.last)
        if (bodyStart == -1) return match.range
        val bodyEnd = findMatchingBrace(source, bodyStart) ?: return match.range
        return match.range.first..bodyEnd
    }

    private fun findMatchingBrace(source: String, openIndex: Int): Int? {
        var depth = 0
        var inString = false
        var escape = false
        for (index in openIndex until source.length) {
            val ch = source[index]
            if (inString) {
                if (escape) {
                    escape = false
                } else if (ch == '\\') {
                    escape = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }
}

private object XmlEditApplier {
    fun apply(source: String, edit: AgentStructuredEdit): String {
        return runCatching {
            val document = parse(source)
            when (edit.kind.lowercase()) {
                "set_xml_attribute" -> {
                    val node = findNode(document, edit.selector.orEmpty()) as? Element ?: return source
                    val attributeName = edit.target.orEmpty()
                    if (attributeName.isBlank()) return source
                    node.setAttribute(attributeName, edit.content.orEmpty())
                    toXml(document)
                }
                "replace_xml_node" -> {
                    val node = findNode(document, edit.selector.orEmpty()) ?: return source
                    val replacement = parseFragment(document, edit.content.orEmpty()) ?: return source
                    node.parentNode.replaceChild(replacement, node)
                    toXml(document)
                }
                "append_xml_node" -> {
                    val parent = findNode(document, edit.selector.orEmpty()) ?: return source
                    val child = parseFragment(document, edit.content.orEmpty()) ?: return source
                    parent.appendChild(child)
                    toXml(document)
                }
                "delete_xml_node" -> {
                    val node = findNode(document, edit.selector.orEmpty()) ?: return source
                    node.parentNode?.removeChild(node)
                    toXml(document)
                }
                else -> source
            }
        }.getOrDefault(source)
    }

    private fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isNamespaceAware = false
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
    }

    private fun parseFragment(document: Document, fragment: String): Node? {
        if (fragment.isBlank()) return null
        val wrapper = parse("<wrapper>$fragment</wrapper>")
        val node = wrapper.documentElement.firstChild ?: return null
        return document.importNode(node, true)
    }

    private fun findNode(document: Document, selector: String): Node? {
        val normalized = selector.trim()
        if (normalized.isBlank() || normalized == "root") return document.documentElement
        if (normalized.startsWith("tag:")) {
            return document.getElementsByTagName(normalized.removePrefix("tag:")).item(0)
        }
        if (normalized.startsWith("@id/")) {
            val targetId = normalized.removePrefix("@id/")
            return walk(document.documentElement).filterIsInstance<Element>().firstOrNull { element ->
                val value = element.getAttribute("android:id")
                value == "@+id/$targetId" || value == "@id/$targetId"
            }
        }
        return null
    }

    private fun walk(node: Node): Sequence<Node> = sequence {
        yield(node)
        val children = node.childNodes
        for (index in 0 until children.length) {
            yieldAll(walk(children.item(index)))
        }
    }

    private fun toXml(document: Document): String {
        val writer = StringWriter()
        val transformer = TransformerFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }.newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty(OutputKeys.INDENT, "yes")
        }
        transformer.transform(DOMSource(document), StreamResult(writer))
        return writer.toString().trim()
    }
}
