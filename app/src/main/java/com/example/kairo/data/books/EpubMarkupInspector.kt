package com.example.kairo.data.books

import java.util.Locale

internal object EpubMarkupInspector {
    private val BLOCK_ELEMENTS =
        setOf(
            "article",
            "blockquote",
            "div",
            "figcaption",
            "figure",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "hr",
            "li",
            "ol",
            "p",
            "pre",
            "section",
            "table",
            "tbody",
            "td",
            "tfoot",
            "th",
            "thead",
            "tr",
            "ul",
        )

    private val SKIP_TEXT_TAGS = setOf("script", "style", "noscript")

    fun renderPlainText(document: EpubMarkupDocument): String {
        val out = StringBuilder()
        document.children.forEach { node ->
            appendPlainText(node, out)
        }
        return out.toString()
    }

    fun countTagOccurrences(
        document: EpubMarkupDocument,
        tagName: String,
        limit: Int = Int.MAX_VALUE,
    ): Int {
        if (limit <= 0) return 0
        val normalizedTag = tagName.lowercase(Locale.ROOT)
        var count = 0

        fun visit(node: EpubMarkupNode): Boolean {
            if (count >= limit) return true
            if (node is EpubMarkupElementNode) {
                if (node.name == normalizedTag) {
                    count += 1
                    if (count >= limit) return true
                }
                node.children.forEach { child ->
                    if (visit(child)) return true
                }
            }
            return false
        }

        document.children.forEach { node ->
            if (visit(node)) return count
        }
        return count
    }

    fun firstTextInTags(
        document: EpubMarkupDocument,
        tagNames: Set<String>,
    ): String? {
        if (tagNames.isEmpty()) return null
        val normalizedTags = tagNames.map { it.lowercase(Locale.ROOT) }.toSet()
        var found: String? = null

        fun visit(node: EpubMarkupNode) {
            if (found != null) return
            if (node !is EpubMarkupElementNode) return

            if (normalizedTags.contains(node.name)) {
                val text = extractText(node).trim()
                if (text.isNotBlank()) {
                    found = text
                    return
                }
            }
            node.children.forEach(::visit)
        }

        document.children.forEach(::visit)
        return found
    }

    fun extractImageSources(document: EpubMarkupDocument): List<String> {
        val sources = mutableListOf<String>()

        fun visit(node: EpubMarkupNode) {
            if (node !is EpubMarkupElementNode) return

            when (node.name) {
                "img" -> {
                    val src = node.attributes["src"]?.trim()
                    if (!src.isNullOrBlank()) {
                        sources.add(src)
                    }
                    val srcset = node.attributes["srcset"]?.trim()
                    if (!srcset.isNullOrBlank()) {
                        sources.addAll(extractSrcsetUrls(srcset))
                    }
                }
                "source" -> {
                    val src = node.attributes["src"]?.trim()
                    if (!src.isNullOrBlank()) {
                        sources.add(src)
                    }
                    val srcset = node.attributes["srcset"]?.trim()
                    if (!srcset.isNullOrBlank()) {
                        sources.addAll(extractSrcsetUrls(srcset))
                    }
                }
                "image" -> {
                    val href =
                        node.attributes["xlink:href"]?.trim()
                            ?: node.attributes["href"]?.trim()
                    if (!href.isNullOrBlank()) {
                        sources.add(href)
                    }
                }
            }

            node.children.forEach(::visit)
        }

        document.children.forEach(::visit)
        return sources
    }

    private fun extractSrcsetUrls(srcset: String): List<String> {
        if (srcset.isBlank()) return emptyList()
        return srcset
            .split(',')
            .mapNotNull { candidate ->
                val trimmed = candidate.trim()
                if (trimmed.isBlank()) return@mapNotNull null
                val url = trimmed.split(Regex("\\s+"), limit = 2).firstOrNull().orEmpty()
                url.takeIf { it.isNotBlank() }
            }
    }

    private fun appendPlainText(
        node: EpubMarkupNode,
        out: StringBuilder,
    ) {
        when (node) {
            is EpubMarkupTextNode -> out.append(node.text)
            is EpubMarkupElementNode -> {
                if (SKIP_TEXT_TAGS.contains(node.name)) return

                if (node.name == "br") {
                    appendLineBreak(out)
                    return
                }

                val isBlock = BLOCK_ELEMENTS.contains(node.name)
                if (isBlock) appendLineBreak(out)
                node.children.forEach { child ->
                    appendPlainText(child, out)
                }
                if (isBlock) appendLineBreak(out)
            }
        }
    }

    private fun extractText(node: EpubMarkupNode): String {
        val out = StringBuilder()

        fun visit(current: EpubMarkupNode) {
            when (current) {
                is EpubMarkupTextNode -> out.append(current.text)
                is EpubMarkupElementNode -> {
                    if (SKIP_TEXT_TAGS.contains(current.name)) return
                    current.children.forEach(::visit)
                }
            }
        }

        visit(node)
        return out.toString()
    }

    private fun appendLineBreak(out: StringBuilder) {
        out.append('\n')
    }
}
