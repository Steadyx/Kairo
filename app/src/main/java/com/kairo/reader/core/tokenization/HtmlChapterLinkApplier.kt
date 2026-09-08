package com.kairo.reader.core.tokenization

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.text.HtmlEntities

internal object HtmlChapterLinkApplier {
    fun apply(
        tokens: MutableList<Token>,
        html: String,
        normalizeInlineText: (String) -> String,
        tokenizeInlineText: (String) -> List<String>,
        minimumRomanPageNumberLength: Int,
    ) {
        if (!html.contains(CHAPTER_LINK_PREFIX, ignoreCase = true)) return
        val matchable =
            tokens.mapIndexedNotNull { index, token ->
                token.takeIf { it.type == TokenType.WORD || it.type == TokenType.PUNCTUATION }
                    ?.let { index to it.text }
            }
        if (matchable.isEmpty()) return

        val tokenTexts = matchable.map { it.second }
        var scanIndex = 0
        var tokenCursor = 0
        var processedLinks = 0
        while (
            scanIndex < html.length &&
            tokenCursor < tokenTexts.size &&
            processedLinks < MAX_LINKS_PER_CHAPTER
        ) {
            val match = ANCHOR_OPEN_REGEX.find(html, scanIndex) ?: return
            val candidate =
                parseCandidate(
                    html = html,
                    match = match,
                    normalizeInlineText = normalizeInlineText,
                    tokenizeInlineText = tokenizeInlineText,
                    minimumRomanPageNumberLength = minimumRomanPageNumberLength,
                )
            scanIndex = candidate.nextScanIndex
            if (candidate.countsTowardBudget) processedLinks += 1
            candidate.link?.let { link ->
                val matchIndex = findTokenSequence(tokenTexts, link.tokens, tokenCursor)
                if (matchIndex >= 0) {
                    applyLink(tokens, matchable, matchIndex, link)
                    tokenCursor = matchIndex + link.tokens.size
                }
            }
        }
    }

    private fun parseCandidate(
        html: String,
        match: MatchResult,
        normalizeInlineText: (String) -> String,
        tokenizeInlineText: (String) -> List<String>,
        minimumRomanPageNumberLength: Int,
    ): LinkScanCandidate {
        val contentStart = match.range.last + 1
        val chapterIndex = match.groupValues[1].toIntOrNull()
        if (chapterIndex == null || contentStart >= html.length) {
            return LinkScanCandidate(contentStart.coerceAtMost(html.length))
        }
        val closeIndex = html.indexOf(ANCHOR_CLOSE_TAG, contentStart, ignoreCase = true)
        if (closeIndex < 0) return LinkScanCandidate(contentStart, countsTowardBudget = true)
        val nextScanIndex = closeIndex + ANCHOR_CLOSE_TAG.length
        val innerLength = closeIndex - contentStart
        if (innerLength !in 1..MAX_LINK_TEXT_HTML_CHARS) {
            return LinkScanCandidate(nextScanIndex, countsTowardBudget = true)
        }
        val linkText = extractLinkText(html.substring(contentStart, closeIndex))
        if (linkText.isBlank() || isPageNumberText(linkText, minimumRomanPageNumberLength)) {
            return LinkScanCandidate(nextScanIndex, countsTowardBudget = true)
        }
        val linkTokens = tokenizeInlineText(normalizeInlineText(linkText))
        return LinkScanCandidate(
            nextScanIndex = nextScanIndex,
            link = linkTokens.takeIf { it.isNotEmpty() }?.let { ChapterHtmlLink(chapterIndex, it) },
            countsTowardBudget = true,
        )
    }

    private fun applyLink(
        tokens: MutableList<Token>,
        matchable: List<Pair<Int, String>>,
        matchIndex: Int,
        link: ChapterHtmlLink,
    ) {
        for (offset in link.tokens.indices) {
            val tokenIndex = matchable[matchIndex + offset].first
            if (tokens[tokenIndex].linkChapterIndex == null) {
                tokens[tokenIndex] = tokens[tokenIndex].copy(linkChapterIndex = link.chapterIndex)
            }
        }
    }

    private fun findTokenSequence(
        tokens: List<String>,
        sequence: List<String>,
        startIndex: Int,
    ): Int {
        if (sequence.isEmpty() || tokens.isEmpty()) return -1
        val lastStart = tokens.size - sequence.size
        for (index in startIndex.coerceAtLeast(0)..lastStart) {
            if (sequence.indices.all { offset -> tokens[index + offset] == sequence[offset] }) {
                return index
            }
        }
        return -1
    }

    private fun extractLinkText(html: String): String =
        html
            .replace(HTML_TAG_REGEX, " ")
            .let(HtmlEntities::decode)
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun isPageNumberText(
        text: String,
        minimumRomanLength: Int,
    ): Boolean {
        val trimmed = text.trim()
        return trimmed.isNotEmpty() &&
            (
                trimmed.all { it.isDigit() } ||
                    (trimmed.length >= minimumRomanLength && ROMAN_NUMERAL_REGEX.matches(trimmed))
                )
    }

    private data class ChapterHtmlLink(val chapterIndex: Int, val tokens: List<String>)

    private data class LinkScanCandidate(
        val nextScanIndex: Int,
        val link: ChapterHtmlLink? = null,
        val countsTowardBudget: Boolean = false,
    )

    private const val CHAPTER_LINK_PREFIX = "kairo://chapter/"
    private const val ANCHOR_CLOSE_TAG = "</a>"
    private const val MAX_LINKS_PER_CHAPTER = 1000
    private const val MAX_LINK_TEXT_HTML_CHARS = 1200
    private val ANCHOR_OPEN_REGEX =
        Regex(
            "<a\\b[^>]*href\\s*=\\s*['\"]kairo://chapter/(\\d+)(?:#[^'\"]*)?['\"][^>]*>",
            RegexOption.IGNORE_CASE,
        )
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val ROMAN_NUMERAL_REGEX = Regex("^[ivxlcdm]+$", RegexOption.IGNORE_CASE)
}
