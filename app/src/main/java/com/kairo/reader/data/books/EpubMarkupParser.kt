package com.kairo.reader.data.books

internal data class EpubMarkupParseResult(val document: EpubMarkupDocument, val complete: Boolean, val limitExceeded: Boolean,)

internal class EpubMarkupParser(private val tokenizer: EpubMarkupTokenizer = EpubMarkupTokenizer(),) {
    companion object {
        private val VOID_ELEMENTS =
            setOf(
                "area",
                "base",
                "br",
                "col",
                "embed",
                "hr",
                "img",
                "input",
                "link",
                "meta",
                "param",
                "source",
                "track",
                "wbr",
            )
        private const val MAX_AST_NODES = 100_000
        private const val MAX_OPEN_ELEMENTS = 256
    }

    fun parse(input: String): EpubMarkupDocument = parseWithResult(input).document

    fun parseWithResult(input: String): EpubMarkupParseResult {
        val document = EpubMarkupDocument()
        val openElements = mutableListOf<EpubMarkupElementNode>()
        val openIndexesByTag = mutableMapOf<String, ArrayDeque<Int>>()
        val tokenization = tokenizer.tokenize(input)
        var nodeCount = 0
        var limitExceeded = false
        var unmatchedEndTag = false

        fun appendNode(node: EpubMarkupNode) {
            if (openElements.isEmpty()) {
                document.children.add(node)
            } else {
                openElements.last().children.add(node)
            }
        }

        fun popThrough(targetIndex: Int) {
            while (openElements.size > targetIndex) {
                val popped = openElements.removeAt(openElements.lastIndex)
                openIndexesByTag[popped.name]?.let { indexes ->
                    indexes.removeLast()
                    if (indexes.isEmpty()) openIndexesByTag.remove(popped.name)
                }
            }
        }

        for (token in tokenization.tokens) {
            when (token) {
                is EpubTextToken -> {
                    if (token.text.isEmpty()) continue
                    if (nodeCount >= MAX_AST_NODES) {
                        limitExceeded = true
                        break
                    }
                    appendNode(EpubMarkupTextNode(token.text))
                    nodeCount += 1
                }
                is EpubStartTagToken -> {
                    val opensElement = !token.selfClosing && token.name !in VOID_ELEMENTS
                    if (nodeCount >= MAX_AST_NODES ||
                        (opensElement && openElements.size >= MAX_OPEN_ELEMENTS)
                    ) {
                        limitExceeded = true
                        break
                    }
                    val element =
                        EpubMarkupElementNode(
                            name = token.name,
                            attributes = token.attributes,
                        )
                    appendNode(element)
                    nodeCount += 1
                    if (opensElement) {
                        val index = openElements.size
                        openElements.add(element)
                        openIndexesByTag.getOrPut(token.name, ::ArrayDeque).addLast(index)
                    }
                }
                is EpubEndTagToken -> {
                    val matchIndex = openIndexesByTag[token.name]?.lastOrNull()
                    if (matchIndex == null) {
                        unmatchedEndTag = true
                    } else {
                        popThrough(matchIndex)
                    }
                }
            }
        }

        val exceeded = limitExceeded || tokenization.limitExceeded
        return EpubMarkupParseResult(
            document = document,
            complete = tokenization.complete && !exceeded && !unmatchedEndTag && openElements.isEmpty(),
            limitExceeded = exceeded,
        )
    }
}
