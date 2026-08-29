package com.kairo.reader.data.books

import com.kairo.reader.data.books.epub.EpubHtmlEntities
import com.kairo.reader.data.books.epub.EpubNavigationReference
import java.util.Locale

internal class EpubNavigationParser(private val markupParser: EpubMarkupParser = EpubMarkupParser(),) {
    fun parse(
        document: String,
        isNcx: Boolean,
    ): EpubNavigationParseResult {
        if (document.isBlank()) return EpubNavigationParseResult(emptyList())
        val parseResult = markupParser.parseWithResult(document)
        if (!parseResult.complete) {
            return EpubNavigationParseResult(
                references = emptyList(),
                provenance = if (isNcx) EpubNavigationProvenance.NCX else EpubNavigationProvenance.NONE,
                complete = false,
            )
        }
        val parsed = parseResult.document
        val extractionStatus = NavigationExtractionStatus()
        return if (isNcx) {
            EpubNavigationParseResult(
                references = parseNcx(parsed, extractionStatus),
                provenance = EpubNavigationProvenance.NCX,
                complete = extractionStatus.complete,
            )
        } else {
            parseNavigationDocument(parsed, extractionStatus)
        }
    }

    private fun parseNavigationDocument(
        document: EpubMarkupDocument,
        extractionStatus: NavigationExtractionStatus,
    ): EpubNavigationParseResult {
        val navigationElements = findNavigationElements(document.children, extractionStatus)
        val explicitToc =
            navigationElements.firstOrNull { navigation ->
                isTocNavigation(navigation, extractionStatus)
            }
        val toc =
            explicitToc
                ?: navigationElements.firstOrNull { navigation ->
                    !isKnownNonTocNavigation(navigation, extractionStatus)
                }
                ?: return EpubNavigationParseResult(
                    references = emptyList(),
                    complete = extractionStatus.complete,
                )
        val list =
            directChildren(toc, ORDERED_LIST_TAG, extractionStatus).firstOrNull()
                ?: findDescendants(toc.children, ORDERED_LIST_TAG, extractionStatus).firstOrNull()
                ?: return EpubNavigationParseResult(
                    references = emptyList(),
                    complete = extractionStatus.complete,
                )
        val entries = mutableListOf<EpubNavigationReference>()
        appendNavigationList(
            list = list,
            depth = 0,
            structuralDepth = 0,
            entries = entries,
            budget = NavigationEntryBudget(extractionStatus),
            extractionStatus = extractionStatus,
        )
        val references = entries.toList()
        val ariaLabel = normalizedLabel(toc.attributes[ARIA_LABEL_ATTRIBUTE], extractionStatus)
        val heading =
            navigationHeading(toc, extractionStatus)
                ?: ariaLabel?.let { label -> EpubReaderNavigationHeading(level = 1, text = label) }
                ?: documentTitle(document, extractionStatus)?.let { title ->
                    EpubReaderNavigationHeading(level = 1, text = title)
                }
        val navigationOnly = isNavigationOnlyDocument(document, extractionStatus)
        val complete = extractionStatus.complete
        return EpubNavigationParseResult(
            references = references,
            readerHtml =
            if (complete) {
                EpubReaderNavigationContent.render(
                    references = references,
                    heading = heading,
                    ariaLabel = ariaLabel,
                )
            } else {
                null
            },
            provenance =
            if (explicitToc != null) {
                EpubNavigationProvenance.EXPLICIT_TOC
            } else {
                EpubNavigationProvenance.UNTYPED_FALLBACK
            },
            navigationOnly = navigationOnly,
            complete = complete,
            heading = heading,
            ariaLabel = ariaLabel,
        )
    }

    private fun appendNavigationList(
        list: EpubMarkupElementNode,
        depth: Int,
        structuralDepth: Int,
        entries: MutableList<EpubNavigationReference>,
        budget: NavigationEntryBudget,
        extractionStatus: NavigationExtractionStatus,
    ) {
        if (depth > MAX_NAVIGATION_DEPTH || structuralDepth > MAX_NAVIGATION_STRUCTURAL_DEPTH) {
            extractionStatus.markIncomplete()
            return
        }
        directChildren(list, LIST_ITEM_TAG, extractionStatus).forEach { item ->
            val labelNode =
                item.children
                    .filterIsInstance<EpubMarkupElementNode>()
                    .firstOrNull { child ->
                        child.localName() == ANCHOR_TAG || child.localName() == SPAN_TAG
                    }
            val label = labelNode?.let { node -> navigationLabel(node, extractionStatus) }
            if (!label.isNullOrBlank()) {
                if (entries.size >= MAX_NAVIGATION_ENTRIES) {
                    extractionStatus.markIncomplete()
                    return
                }
                val href = navigationHref(labelNode.attributes[HREF_ATTRIBUTE], extractionStatus)
                if (!budget.consume(label, href)) return
                entries +=
                    EpubNavigationReference(
                        label = label,
                        depth = depth,
                        href = href,
                    )
            }
            directChildren(item, ORDERED_LIST_TAG, extractionStatus).forEach { nested ->
                appendNavigationList(
                    list = nested,
                    depth = if (label.isNullOrBlank()) depth else depth + 1,
                    structuralDepth = structuralDepth + 1,
                    entries = entries,
                    budget = budget,
                    extractionStatus = extractionStatus,
                )
            }
        }
    }

    private fun parseNcx(
        document: EpubMarkupDocument,
        extractionStatus: NavigationExtractionStatus,
    ): List<EpubNavigationReference> {
        val navMap =
            findDescendants(document.children, NAV_MAP_TAG, extractionStatus).firstOrNull()
                ?: return emptyList()
        val entries = mutableListOf<EpubNavigationReference>()
        appendNcxPoints(
            parent = navMap,
            depth = 0,
            structuralDepth = 0,
            entries = entries,
            budget = NavigationEntryBudget(extractionStatus),
            extractionStatus = extractionStatus,
        )
        return entries
    }

    private fun appendNcxPoints(
        parent: EpubMarkupElementNode,
        depth: Int,
        structuralDepth: Int,
        entries: MutableList<EpubNavigationReference>,
        budget: NavigationEntryBudget,
        extractionStatus: NavigationExtractionStatus,
    ) {
        if (depth > MAX_NAVIGATION_DEPTH || structuralDepth > MAX_NAVIGATION_STRUCTURAL_DEPTH) {
            extractionStatus.markIncomplete()
            return
        }
        directChildren(parent, NAV_POINT_TAG, extractionStatus).forEach { point ->
            val navLabel = directChildren(point, NAV_LABEL_TAG, extractionStatus).firstOrNull()
            val label =
                navLabel
                    ?.let { findDescendants(it.children, TEXT_TAG, extractionStatus).firstOrNull() }
                    ?.let { node -> navigationLabel(node, extractionStatus) }
            val href =
                directChildren(point, CONTENT_TAG, extractionStatus)
                    .firstOrNull()
                    ?.attributes
                    ?.get("src")
                    .let { raw -> navigationHref(raw, extractionStatus) }
            if (!label.isNullOrBlank()) {
                if (entries.size >= MAX_NAVIGATION_ENTRIES) {
                    extractionStatus.markIncomplete()
                    return
                }
                if (!budget.consume(label, href)) return
                entries += EpubNavigationReference(label = label, depth = depth, href = href)
            }
            appendNcxPoints(
                parent = point,
                depth = if (label.isNullOrBlank()) depth else depth + 1,
                structuralDepth = structuralDepth + 1,
                entries = entries,
                budget = budget,
                extractionStatus = extractionStatus,
            )
        }
    }

    private fun isTocNavigation(
        node: EpubMarkupElementNode,
        extractionStatus: NavigationExtractionStatus,
    ): Boolean = TOC_SEMANTIC_TOKENS.any(semanticTokens(node, extractionStatus)::contains)

    private fun isKnownNonTocNavigation(
        node: EpubMarkupElementNode,
        extractionStatus: NavigationExtractionStatus,
    ): Boolean = KNOWN_NON_TOC_SEMANTIC_TOKENS.any(semanticTokens(node, extractionStatus)::contains)

    private fun semanticTokens(
        node: EpubMarkupElementNode,
        extractionStatus: NavigationExtractionStatus,
    ): Set<String> =
        sequenceOf(
            node.attributes["epub:type"],
            node.attributes["type"],
            node.attributes["role"],
        ).filterNotNull()
            .flatMap { value ->
                if (value.length > MAX_SEMANTIC_ATTRIBUTE_CHARACTERS) {
                    extractionStatus.markIncomplete()
                }
                value
                    .take(MAX_SEMANTIC_ATTRIBUTE_CHARACTERS)
                    .split(WHITESPACE_REGEX)
                    .asSequence()
            }
            .map { token -> token.lowercase(Locale.ROOT) }
            .toSet()

    private fun navigationLabel(
        node: EpubMarkupElementNode,
        extractionStatus: NavigationExtractionStatus,
    ): String = normalizedLabel(extractText(node, extractionStatus), extractionStatus).orEmpty()

    private fun navigationHref(
        raw: String?,
        extractionStatus: NavigationExtractionStatus,
    ): String? {
        if (raw == null) return null
        if (raw.length > MAX_NAVIGATION_HREF_SOURCE_CHARACTERS) extractionStatus.markIncomplete()
        val trimmed = raw.take(MAX_NAVIGATION_HREF_SOURCE_CHARACTERS).trim()
        if (trimmed.length > MAX_NAVIGATION_HREF_LENGTH) extractionStatus.markIncomplete()
        return trimmed.takeIf(String::isNotBlank)?.take(MAX_NAVIGATION_HREF_LENGTH)
    }

    private fun navigationHeading(
        node: EpubMarkupElementNode,
        extractionStatus: NavigationExtractionStatus,
    ): EpubReaderNavigationHeading? =
        node.children
            .filterIsInstance<EpubMarkupElementNode>()
            .firstOrNull { child -> child.localName() in HEADING_TAGS }
            ?.let { heading ->
                normalizedLabel(
                    raw = extractText(heading, extractionStatus),
                    extractionStatus = extractionStatus,
                )?.let { text ->
                    EpubReaderNavigationHeading(
                        level = heading.localName().removePrefix("h").toInt(),
                        text = text,
                    )
                }
            }

    private fun documentTitle(
        document: EpubMarkupDocument,
        extractionStatus: NavigationExtractionStatus,
    ): String? =
        findDescendants(document.children, TITLE_TAG, extractionStatus)
            .firstNotNullOfOrNull { title ->
                normalizedLabel(extractText(title, extractionStatus), extractionStatus)
            }

    private fun normalizedLabel(
        raw: String?,
        extractionStatus: NavigationExtractionStatus,
    ): String? {
        if (raw == null) return null
        if (raw.length > MAX_EXTRACTED_TEXT_CHARACTERS) extractionStatus.markIncomplete()
        val normalized =
            EpubHtmlEntities
                .decode(raw.take(MAX_EXTRACTED_TEXT_CHARACTERS))
                .replace(WHITESPACE_REGEX, " ")
                .trim()
        if (normalized.length > MAX_NAVIGATION_LABEL_LENGTH) extractionStatus.markIncomplete()
        return normalized.take(MAX_NAVIGATION_LABEL_LENGTH).takeIf(String::isNotBlank)
    }

    private fun extractText(
        node: EpubMarkupElementNode,
        extractionStatus: NavigationExtractionStatus,
    ): String {
        val out = StringBuilder()
        val stack = ArrayDeque<EpubMarkupNode>()
        val budget = MarkupTraversalBudget(extractionStatus)
        stack.addLast(node)
        while (stack.isNotEmpty()) {
            if (out.length >= MAX_EXTRACTED_TEXT_CHARACTERS) {
                extractionStatus.markIncomplete()
                break
            }
            val current = stack.removeLast()
            if (!budget.visit(current)) break
            when (current) {
                is EpubMarkupTextNode -> {
                    val remaining = MAX_EXTRACTED_TEXT_CHARACTERS - out.length
                    if (current.text.length > remaining) extractionStatus.markIncomplete()
                    out.append(current.text.take(remaining))
                }
                is EpubMarkupElementNode -> {
                    if (current.localName() == ORDERED_LIST_TAG ||
                        current.localName() in INERT_TAGS
                    ) {
                        continue
                    }
                    pushNodesInDocumentOrder(current.children, stack, extractionStatus)
                }
            }
        }
        return out.toString()
    }

    private fun directChildren(
        parent: EpubMarkupElementNode,
        localName: String,
        extractionStatus: NavigationExtractionStatus,
    ): List<EpubMarkupElementNode> {
        val matches = mutableListOf<EpubMarkupElementNode>()
        for (child in parent.children) {
            if (child !is EpubMarkupElementNode || child.localName() != localName) continue
            if (matches.size >= MAX_DIRECT_CHILD_MATCHES) {
                extractionStatus.markIncomplete()
                return matches
            }
            matches += child
        }
        return matches
    }

    private fun findDescendants(
        nodes: List<EpubMarkupNode>,
        localName: String,
        extractionStatus: NavigationExtractionStatus,
    ): List<EpubMarkupElementNode> {
        val matches = mutableListOf<EpubMarkupElementNode>()
        val stack = ArrayDeque<EpubMarkupNode>()
        val budget = MarkupTraversalBudget(extractionStatus)
        pushNodesInDocumentOrder(nodes, stack, extractionStatus)
        while (stack.isNotEmpty()) {
            if (matches.size >= MAX_DESCENDANT_MATCHES) {
                extractionStatus.markIncomplete()
                break
            }
            val node = stack.removeLast()
            if (!budget.visit(node)) break
            if (node is EpubMarkupElementNode) {
                val nodeLocalName = node.localName()
                if (nodeLocalName !in INERT_TAGS) {
                    if (nodeLocalName == localName) matches += node
                    pushNodesInDocumentOrder(node.children, stack, extractionStatus)
                }
            }
        }
        return matches
    }

    private fun findNavigationElements(
        nodes: List<EpubMarkupNode>,
        extractionStatus: NavigationExtractionStatus,
    ): List<EpubMarkupElementNode> {
        val matches = mutableListOf<EpubMarkupElementNode>()
        val stack = ArrayDeque<EpubMarkupNode>()
        val budget = MarkupTraversalBudget(extractionStatus)
        pushNodesInDocumentOrder(nodes, stack, extractionStatus)
        while (stack.isNotEmpty()) {
            if (matches.size >= MAX_DESCENDANT_MATCHES) {
                extractionStatus.markIncomplete()
                break
            }
            val node = stack.removeLast()
            if (!budget.visit(node)) break
            if (node is EpubMarkupElementNode) {
                val localName = node.localName()
                if (localName !in INERT_TAGS) {
                    if (localName == NAV_TAG) matches += node
                    pushNodesInDocumentOrder(node.children, stack, extractionStatus)
                }
            }
        }
        return matches
    }

    private fun isNavigationOnlyDocument(
        document: EpubMarkupDocument,
        extractionStatus: NavigationExtractionStatus,
    ): Boolean {
        data class PendingNode(val node: EpubMarkupNode, val insideNavigation: Boolean, val inert: Boolean,)

        val stack = ArrayDeque<PendingNode>()
        val budget = MarkupTraversalBudget(extractionStatus)
        if (document.children.size > MAX_PENDING_TRAVERSAL_NODES) {
            extractionStatus.markIncomplete()
            return false
        }
        for (index in document.children.lastIndex downTo 0) {
            stack.addLast(PendingNode(document.children[index], insideNavigation = false, inert = false))
        }
        while (stack.isNotEmpty()) {
            val pending = stack.removeLast()
            if (!budget.visit(pending.node)) return false
            when (val node = pending.node) {
                is EpubMarkupTextNode -> {
                    if (!pending.insideNavigation && !pending.inert && node.text.isNotBlank()) {
                        return false
                    }
                }
                is EpubMarkupElementNode -> {
                    val localName = node.localName()
                    val insideNavigation = pending.insideNavigation || localName == NAV_TAG
                    val inert = pending.inert || localName in INERT_TAGS || localName == HEAD_TAG
                    if (!insideNavigation && !inert && localName !in NAVIGATION_DOCUMENT_WRAPPERS) {
                        return false
                    }
                    if (stack.size.toLong() + node.children.size > MAX_PENDING_TRAVERSAL_NODES) {
                        extractionStatus.markIncomplete()
                        return false
                    }
                    for (index in node.children.lastIndex downTo 0) {
                        stack.addLast(PendingNode(node.children[index], insideNavigation, inert))
                    }
                }
            }
        }
        return true
    }

    private fun pushNodesInDocumentOrder(
        nodes: List<EpubMarkupNode>,
        stack: ArrayDeque<EpubMarkupNode>,
        extractionStatus: NavigationExtractionStatus,
    ) {
        val available = (MAX_PENDING_TRAVERSAL_NODES - stack.size).coerceAtLeast(0)
        val accepted = minOf(nodes.size, available)
        if (accepted < nodes.size) extractionStatus.markIncomplete()
        if (accepted == 0) return
        for (index in accepted - 1 downTo 0) {
            stack.addLast(nodes[index])
        }
    }

    private fun EpubMarkupElementNode.localName(): String = name.substringAfterLast(':')

    private class NavigationExtractionStatus {
        var complete: Boolean = true
            private set

        fun markIncomplete() {
            complete = false
        }
    }

    private class NavigationEntryBudget(private val extractionStatus: NavigationExtractionStatus,) {
        private var remainingCharacters = MAX_NAVIGATION_TOTAL_CHARACTERS

        fun consume(
            label: String,
            href: String?,
        ): Boolean {
            val characterCount = label.length + href.orEmpty().length
            if (characterCount > remainingCharacters) {
                remainingCharacters = 0
                extractionStatus.markIncomplete()
                return false
            }
            remainingCharacters -= characterCount
            return true
        }
    }

    private class MarkupTraversalBudget(private val extractionStatus: NavigationExtractionStatus,) {
        private var remainingNodes = MAX_TRAVERSAL_NODES
        private var remainingSourceCharacters = MAX_TRAVERSAL_SOURCE_CHARACTERS

        fun visit(node: EpubMarkupNode): Boolean {
            if (remainingNodes <= 0 || remainingSourceCharacters <= 0) {
                extractionStatus.markIncomplete()
                return false
            }
            remainingNodes -= 1
            val sourceCharacters =
                when (node) {
                    is EpubMarkupTextNode -> node.text.length
                    is EpubMarkupElementNode -> {
                        var total = node.name.length
                        node.attributes.forEach { (name, value) ->
                            total =
                                (total.toLong() + name.length + value.length)
                                    .coerceAtMost(Int.MAX_VALUE.toLong())
                                    .toInt()
                        }
                        total
                    }
                }
            if (sourceCharacters > remainingSourceCharacters) {
                remainingSourceCharacters = 0
                extractionStatus.markIncomplete()
                return false
            }
            remainingSourceCharacters -= sourceCharacters
            return true
        }
    }

    private companion object {
        const val MAX_NAVIGATION_DEPTH = 12
        const val MAX_NAVIGATION_STRUCTURAL_DEPTH = 64
        const val MAX_NAVIGATION_ENTRIES = 4_000
        const val MAX_NAVIGATION_LABEL_LENGTH = 240
        const val MAX_NAVIGATION_HREF_LENGTH = 2_048
        const val MAX_NAVIGATION_HREF_SOURCE_CHARACTERS = MAX_NAVIGATION_HREF_LENGTH * 2
        const val MAX_NAVIGATION_TOTAL_CHARACTERS = 500_000
        const val MAX_TRAVERSAL_NODES = 50_000
        const val MAX_PENDING_TRAVERSAL_NODES = MAX_TRAVERSAL_NODES
        const val MAX_TRAVERSAL_SOURCE_CHARACTERS = 2_000_000
        const val MAX_DESCENDANT_MATCHES = 4_000
        const val MAX_DIRECT_CHILD_MATCHES = MAX_NAVIGATION_ENTRIES
        const val MAX_EXTRACTED_TEXT_CHARACTERS = 4_096
        const val MAX_SEMANTIC_ATTRIBUTE_CHARACTERS = 512
        const val NAV_TAG = "nav"
        const val HEAD_TAG = "head"
        const val ARIA_LABEL_ATTRIBUTE = "aria-label"
        const val HREF_ATTRIBUTE = "href"
        const val ORDERED_LIST_TAG = "ol"
        const val LIST_ITEM_TAG = "li"
        const val ANCHOR_TAG = "a"
        const val SPAN_TAG = "span"
        const val NAV_MAP_TAG = "navmap"
        const val NAV_POINT_TAG = "navpoint"
        const val NAV_LABEL_TAG = "navlabel"
        const val TEXT_TAG = "text"
        const val CONTENT_TAG = "content"
        const val TITLE_TAG = "title"
        val HEADING_TAGS = (1..6).mapTo(mutableSetOf()) { level -> "h$level" }
        val TOC_SEMANTIC_TOKENS = setOf("toc", "doc-toc")
        val KNOWN_NON_TOC_SEMANTIC_TOKENS =
            setOf("page-list", "doc-pagelist", "landmarks", "doc-landmarks")
        val INERT_TAGS = setOf("script", "style", "noscript")
        val NAVIGATION_DOCUMENT_WRAPPERS =
            setOf("html", "body", "main", "section", "article", "div", "header", "footer")
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
