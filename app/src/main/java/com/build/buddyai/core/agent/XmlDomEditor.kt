package com.build.buddyai.core.agent

import org.w3c.dom.Document
import org.w3c.dom.Element
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
        val document = parse(source)
        operations.forEach { applyOperation(document, it) }
        return render(document)
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
                val fragment = parse("<wrapper>${op.payload}</wrapper>")
                val children = fragment.documentElement.childNodes
                for (i in 0 until children.length) {
                    parent.appendChild(document.importNode(children.item(i), true))
                }
            }
            "xml_remove_nodes" -> findNodes(document, op.target).forEach { it.parentNode?.removeChild(it) }
            else -> Unit
        }
    }

    private fun findNodes(document: Document, target: String) = buildList {
        val expression = if (target.isBlank()) "/*" else target
        val nodes = xPathFactory.newXPath().evaluate(expression, document, XPathConstants.NODESET) as org.w3c.dom.NodeList
        for (i in 0 until nodes.length) add(nodes.item(i))
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
