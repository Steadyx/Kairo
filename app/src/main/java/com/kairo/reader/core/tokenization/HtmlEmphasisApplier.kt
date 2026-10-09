package com.kairo.reader.core.tokenization

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** Optional source metadata. Never guess an occurrence when HTML and reading text disagree. */
internal object HtmlEmphasisApplier {
    fun apply(
        tokens: List<Token>,
        html: String,
        normalizeText: (String) -> String,
        tokenizeInlineText: (String) -> List<String>,
    ): List<Token> {
        if (tokens.isEmpty() || !EMPHASIS_TAG.containsMatchIn(html) || html.any { it == START || it == END }) return tokens
        val body = Jsoup.parse(html).body()
        body.select("script, style, template, [hidden], [aria-hidden=true]").remove()
        body.getAllElements().filter { element ->
            isPageBreak(element) ||
                (
                    element.isBlock &&
                        element.children().none { it.isBlock } &&
                        ParagraphBreakPatterns.sceneBreak.matches(normalizeText(element.text()))
                    )
        }.forEach { it.replaceWith(TextNode(" ")) }
        if (body.text().any { it == START || it == END }) return tokens
        val blockTexts = mutableMapOf<Element, String>()
        val accents = body.select("em, strong").filter { isLocalEmphasis(it, blockTexts) }
        if (accents.isEmpty()) return tokens
        accents.forEach { element ->
            element.before(TextNode(START.toString()))
            element.after(TextNode(END.toString()))
        }
        val source = markedText(normalizeText(body.text()))
        val visible = tokens.indices.filter { tokens[it].type == TokenType.WORD || tokens[it].type == TokenType.PUNCTUATION }
        val sourceTokens = tokenizeInlineText(normalizeText(source.text)).map(::canonicalText)
        // Full alignment disambiguates repeated words, nested markup and inline word fragments.
        // A mismatch simply drops optional expression; tokens, links and positions stay untouched.
        if (visible.map { canonicalText(tokens[it].text) } != sourceTokens) return tokens
        val ranges = sourceRanges(source.text, sourceTokens, tokenizeInlineText, normalizeText) ?: return tokens
        val tokenRanges = MutableList(tokens.size) { IntRange.EMPTY }
        visible.forEachIndexed { index, tokenIndex -> tokenRanges[tokenIndex] = ranges[index] }
        val emphasized = emphasizedIndices(tokens, tokenRanges, source.ranges)
        return tokens.mapIndexed { index, token ->
            if (index in emphasized) token.copy(authorEmphasis = true) else token
        }
    }

    private fun isPageBreak(element: Element): Boolean =
        listOf(element.attr("epub:type"), element.attr("role"), element.className()).any { value ->
            value.lowercase().split(Regex("\\s+")).any { it in PAGE_BREAK_MARKERS }
        }

    private fun sourceRanges(
        text: String,
        normalizedTokens: List<String>,
        tokenizeInlineText: (String) -> List<String>,
        normalizeText: (String) -> String,
    ): List<IntRange>? {
        var offset = 0
        val parts = tokenizeInlineText(text).map { part ->
            val start = text.indexOf(canonicalText(part), offset)
            if (start < 0) return null
            offset = start + part.length
            start until offset
        }
        var cursor = 0
        val ranges = mutableListOf<IntRange>()
        while (cursor < parts.size) {
            val start = parts[cursor].first
            var end: Int
            var candidateTokens: List<String>
            // Normalization can join pieces across markup, such as <em>50</em> %.
            do {
                end = parts.getOrNull(cursor++)?.last?.plus(1) ?: return null
                val candidate = text.substring(start, end)
                candidateTokens = if (candidate == normalizedTokens.getOrNull(ranges.size)) {
                    listOf(candidate)
                } else {
                    tokenizeInlineText(normalizeText(candidate)).map(::canonicalText)
                }
            } while (candidateTokens.isEmpty() ||
                candidateTokens.indices.any {
                    candidateTokens[it] != normalizedTokens.getOrNull(ranges.size + it)
                }
            )
            repeat(candidateTokens.size) { ranges += start until end }
        }
        return ranges.takeIf { it.size == normalizedTokens.size }
    }

    private fun emphasizedIndices(tokens: List<Token>, ranges: List<IntRange>, accents: List<IntRange>): Set<Int> = buildSet {
        var cursor = 0
        accents.forEach { accent ->
            while (cursor < ranges.size && ranges[cursor].last < accent.first) cursor++
            val covered = mutableListOf<Int>()
            while (cursor < ranges.size && ranges[cursor].first <= accent.last) {
                val range = ranges[cursor]
                if (tokens[cursor].type == TokenType.WORD &&
                    !range.isEmpty() &&
                    range.first >= accent.first &&
                    range.last <= accent.last
                ) {
                    covered += cursor
                }
                cursor++
            }
            if (covered.size in 1..MAX_EMPHASIS_WORDS) addAll(covered)
        }
    }

    private fun isLocalEmphasis(element: Element, blockTexts: MutableMap<Element, String>): Boolean {
        val parents = element.parents()
        if (parents.any { it.normalName() in EXCLUDED_ANCESTORS }) return false
        val text = element.text().trim()
        if (text.isEmpty() || text.length > MAX_EMPHASIS_CHARACTERS) return false
        val block = parents.firstOrNull { it.isBlock }
        return block != null && blockTexts.getOrPut(block) { block.text().trim() } != text
    }

    private fun markedText(marked: String): EmphasisText {
        val text = StringBuilder(marked.length)
        val ranges = mutableListOf<IntRange>()
        var start = -1
        marked.forEach { character ->
            when (character) {
                START -> start = text.length
                END -> {
                    if (start >= 0) ranges += start until text.length
                    start = -1
                }
                else -> text.append(canonical(character))
            }
        }
        return EmphasisText(text.toString(), ranges)
    }

    private fun canonicalText(text: String): String = text.map(::canonical).joinToString("")

    private fun canonical(character: Char): Char = when (character) {
        '“', '”' -> '"'
        '‘', '’' -> '\''
        else -> character
    }

    private data class EmphasisText(val text: String, val ranges: List<IntRange>)

    private const val START = '\uE000'
    private const val END = '\uE001'
    private const val MAX_EMPHASIS_WORDS = 4
    private const val MAX_EMPHASIS_CHARACTERS = 48
    private val PAGE_BREAK_MARKERS = setOf("pagebreak", "doc-pagebreak", "page-break")
    private val EMPHASIS_TAG = Regex("<\\s*(em|strong)\\b", RegexOption.IGNORE_CASE)
    private val EXCLUDED_ANCESTORS = setOf("em", "strong", "i", "cite", "code", "pre", "h1", "h2", "h3", "h4", "h5", "h6")
}
