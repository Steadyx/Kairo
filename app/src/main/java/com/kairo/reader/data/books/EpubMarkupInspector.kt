package com.kairo.reader.data.books

import java.util.Locale

internal data class EpubPlainTextAnchorMarker(val marker: String, val anchorId: String,)

internal data class EpubMarkedPlainText(val text: String, val markers: List<EpubPlainTextAnchorMarker>,)

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
    private val PLAIN_TEXT_SKIP_TAGS = SKIP_TEXT_TAGS + setOf("head", "title", "meta", "link")
    private val PAGE_BREAK_CLASS_TOKENS = setOf("pagebreak", "page-break")
    private val PAGE_BREAK_TYPES = setOf("pagebreak", "doc-pagebreak")
    private const val PAGE_BREAK_MARKER = '\u000C'

    fun renderPlainText(document: EpubMarkupDocument): String {
        val out = StringBuilder()
        document.children.forEach { node ->
            appendPlainText(node, out, onElementStart = null)
        }
        return out.toString()
    }

    fun renderPlainTextWithAnchorMarkers(document: EpubMarkupDocument): EpubMarkedPlainText {
        val out = StringBuilder()
        val markers = mutableListOf<EpubPlainTextAnchorMarker>()
        val seenAnchorIds = mutableSetOf<String>()
        document.children.forEach { node ->
            appendPlainText(node, out) { element ->
                anchorIds(element).forEach { anchorId ->
                    if (seenAnchorIds.add(anchorId)) {
                        val marker = "$ANCHOR_MARKER_START${markers.size.toString(ANCHOR_MARKER_RADIX)}$ANCHOR_MARKER_END"
                        markers += EpubPlainTextAnchorMarker(marker = marker, anchorId = anchorId)
                        out.append(marker)
                    }
                }
            }
        }
        return EpubMarkedPlainText(text = out.toString(), markers = markers)
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
        onElementStart: ((EpubMarkupElementNode) -> Unit)?,
    ) {
        when (node) {
            is EpubMarkupTextNode -> out.append(node.text)
            is EpubMarkupElementNode -> {
                if (PLAIN_TEXT_SKIP_TAGS.contains(node.name)) return
                onElementStart?.invoke(node)
                if (isPageBreakNode(node)) {
                    appendPageBreak(out)
                    return
                }

                if (node.name == "br") {
                    appendLineBreak(out)
                    return
                }

                val isBlock = BLOCK_ELEMENTS.contains(node.name)
                if (isBlock) appendLineBreak(out)
                node.children.forEach { child ->
                    appendPlainText(child, out, onElementStart)
                }
                if (isBlock) appendLineBreak(out)
            }
        }
    }

    private fun anchorIds(node: EpubMarkupElementNode): List<String> =
        buildList {
            node.attributes["id"]?.trim()?.takeIf(String::isNotBlank)?.let(::add)
            node.attributes["xml:id"]?.trim()?.takeIf(String::isNotBlank)?.let(::add)
            if (node.name == "a") {
                node.attributes["name"]?.trim()?.takeIf(String::isNotBlank)?.let(::add)
            }
        }.distinct()

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

    private fun appendPageBreak(out: StringBuilder) {
        if (out.isNotEmpty() && out.last() == PAGE_BREAK_MARKER) return
        out.append(PAGE_BREAK_MARKER)
    }

    private fun isPageBreakNode(node: EpubMarkupElementNode): Boolean {
        val epubType = node.attributes["epub:type"].orEmpty()
        val role = node.attributes["role"].orEmpty()
        val className = node.attributes["class"].orEmpty()

        return hasPageBreakType(epubType) ||
            hasPageBreakType(role) ||
            hasPageBreakClass(className)
    }

    private fun hasPageBreakType(value: String): Boolean {
        if (value.isBlank()) return false
        return value
            .split(Regex("\\s+"))
            .any { token -> token.lowercase(Locale.ROOT) in PAGE_BREAK_TYPES }
    }

    private fun hasPageBreakClass(value: String): Boolean {
        if (value.isBlank()) return false
        return value
            .split(Regex("\\s+"))
            .any { token -> token.lowercase(Locale.ROOT) in PAGE_BREAK_CLASS_TOKENS }
    }

    private const val ANCHOR_MARKER_START = '\uE000'
    private const val ANCHOR_MARKER_END = '\uE001'
    private const val ANCHOR_MARKER_RADIX = 36
}
