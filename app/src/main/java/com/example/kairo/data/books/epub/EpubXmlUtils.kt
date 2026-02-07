package com.example.kairo.data.books.epub

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException

internal object EpubXmlUtils {
    val strictXmlErrorHandler: ErrorHandler =
        object : ErrorHandler {
            override fun warning(exception: SAXParseException?) = Unit

            override fun error(exception: SAXParseException?) {
                if (exception != null) throw exception
            }

            override fun fatalError(exception: SAXParseException?) {
                if (exception != null) throw exception
            }
        }

    fun newSafeFactory(): DocumentBuilderFactory {
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
        }
    }

    fun findElementsByLocalName(
        doc: Document,
        localName: String,
    ): List<Element> {
        val nodes = doc.getElementsByTagName("*")
        if (nodes.length <= 0) return emptyList()
        val results = mutableListOf<Element>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                val name = node.localName ?: node.nodeName.substringAfterLast(':', node.nodeName)
                if (name.equals(localName, ignoreCase = true)) {
                    results.add(node)
                }
            }
        }
        return results
    }

    fun findElementsByParentLocalName(
        doc: Document,
        parentLocalName: String,
        childLocalName: String,
    ): List<Element> {
        val parent = findElementsByLocalName(doc, parentLocalName).firstOrNull() ?: return emptyList()
        val nodes = parent.getElementsByTagName("*")
        if (nodes.length <= 0) return emptyList()
        val results = mutableListOf<Element>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                val name = node.localName ?: node.nodeName.substringAfterLast(':', node.nodeName)
                if (name.equals(childLocalName, ignoreCase = true)) {
                    results.add(node)
                }
            }
        }
        return results
    }

    fun findDirectChildByLocalName(
        parent: Element,
        childLocalName: String,
    ): Element? = findDirectChildrenByLocalName(parent, childLocalName).firstOrNull()

    fun findDirectChildrenByLocalName(
        parent: Element,
        childLocalName: String,
    ): List<Element> {
        val results = mutableListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                val name = localNameOf(node)
                if (name.equals(childLocalName, ignoreCase = true)) {
                    results.add(node)
                }
            }
        }
        return results
    }

    fun localNameOf(element: Element): String {
        return element.localName ?: element.nodeName.substringAfterLast(':', element.nodeName)
    }

    fun extractTextContents(nodes: NodeList): List<String> {
        if (nodes.length <= 0) return emptyList()
        val results = ArrayList<String>(nodes.length)
        for (i in 0 until nodes.length) {
            val text =
                nodes
                    .item(i)
                    ?.textContent
                    ?.let(EpubHtmlEntities::decode)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            if (text != null) {
                results.add(text)
            }
        }
        return results
    }
}
