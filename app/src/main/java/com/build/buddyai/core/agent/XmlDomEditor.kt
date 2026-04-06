package com.build.buddyai.core.agent

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

@Singleton
class XmlDomEditor @Inject constructor() {
    private val documentFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isIgnoringComments = false
    }
    private val xPathFactory = XPathFactory.newInstance()

    fun apply(source: String, operations: List<AgentEditOperation>): String {
        if (operations.isEmpty()) return source
        val document = runCatching { parse(source) }.getOrElse { return source }
        operations.forEach { operation ->
            runCatching { applyOperation(document, operation) }
        }
        return runCatching { render(document) }.getOrDefault(source)
    }

    private fun applyOperation(document: Document, op: AgentEditOperation) {
        when (op.kind) {
            "xml_set_attribute" -> findNodes(document, op.target).forEach { node ->
                op.attributes.forEach { (name, value) -> (node as? Element)?.setAttribute(name, value) }
            }
            "xml_remove_attribute" -> findNodes(document, op.target).forEach { node ->
                (node as? Element)?.removeAttribute(op.payload)
            }
            "xml_replace_text" -> findNodes(document, op.target).forEach { it.textContent = op.payload }
            "xml_append_under" -> {
                val parent = findNodes(document, op.target).firstOrNull() as? Element ?: return
                val payload = op.payload.trim()
                if (payload.isBlank()) return
                val appended = runCatching {
                    val fragment = parse("<wrapper>$payload</wrapper>")
                    val children = fragment.documentElement.childNodes
                    for (i in 0 until children.length) {
                        parent.appendChild(document.importNode(children.item(i), true))
                    }
                    true
                }.getOrDefault(false)
                if (!appended) {
                    // Keep execution resilient when model emits non-XML payload text.
                    parent.appendChild(document.createTextNode(op.payload))
                }
            }
            "xml_remove_nodes" -> findNodes(document, op.target).forEach { it.parentNode?.removeChild(it) }
            else -> Unit
        }
    }

    private fun findNodes(document: Document, target: String): List<Node> {
        val expressions = candidateExpressions(target)
        expressions.forEach { expression ->
            val nodes = evaluateXPath(document, expression)
            if (nodes.isNotEmpty()) return nodes
        }
        return emptyList()
    }

    private fun evaluateXPath(document: Document, expression: String): List<Node> = runCatching {
        val nodeList = xPathFactory.newXPath().evaluate(expression, document, XPathConstants.NODESET) as org.w3c.dom.NodeList
        buildList {
            for (i in 0 until nodeList.length) add(nodeList.item(i))
        }
    }.getOrDefault(emptyList())

    private fun candidateExpressions(target: String): List<String> {
        val cleaned = target
            .trim()
            .removePrefix("xpath:")
            .trim()
            .removeSurrounding("`")

        if (cleaned.isBlank()) return listOf("/*")

        val candidates = linkedSetOf(cleaned)
        extractTagFromSnippet(cleaned)?.let { tag ->
            candidates += "//*[local-name()='$tag']"
        }
        extractIdToken(cleaned)?.let { id ->
            candidates += "//*[@*[local-name()='id' and (.='@+id/$id' or .='@id/$id')]]"
        }
        if (cleaned.matches(Regex("""[A-Za-z_][A-Za-z0-9_.:-]*"""))) {
            candidates += "//*[local-name()='$cleaned']"
        }
        return candidates.toList()
    }

    private fun extractTagFromSnippet(target: String): String? =
        Regex("""<\s*([A-Za-z_][A-Za-z0-9_.:-]*)""")
            .find(target)
            ?.groupValues
            ?.getOrNull(1)

    private fun extractIdToken(target: String): String? {
        val direct = Regex("""@(?:\+)?id/([A-Za-z0-9_]+)""").find(target)?.groupValues?.getOrNull(1)
        if (!direct.isNullOrBlank()) return direct
        return Regex("""(?:android:)?id\s*=\s*["']@(?:\+)?id/([A-Za-z0-9_]+)["']""")
            .find(target)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun parse(xml: String): Document = documentFactory.newDocumentBuilder().parse(InputSource(StringReader(xml)))

    private fun render(document: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        }
        return StringWriter().also { writer ->
            transformer.transform(DOMSource(document), StreamResult(writer))
        }.toString().trim()
    }
}
