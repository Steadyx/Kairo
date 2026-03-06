package com.example.kairo.core.tokenization.rtl

import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.ChapterLink
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.tokenization.LinkPositionMapper

internal object RtlLinkApplier {
    fun apply(
        tokens: MutableList<Token>,
        chapter: Chapter,
        tokenizeInlineText: (String) -> List<String>,
    ): List<Token> {
        if (chapter.links.isNotEmpty()) {
            applyLinksByCharPositions(tokens, chapter.links, chapter.plainText)
        }
        applyLinksFromHtml(tokens, chapter.htmlContent, tokenizeInlineText)
        return tokens
    }

    private fun applyLinksByCharPositions(
        tokens: MutableList<Token>,
        links: List<ChapterLink>,
        plainText: String,
    ) {
        LinkPositionMapper.apply(tokens, links, plainText)
    }

    private fun applyLinksFromHtml(
        tokens: MutableList<Token>,
        html: String,
        tokenizeInlineText: (String) -> List<String>,
    ) {
        if (!html.contains("kairo://chapter/", ignoreCase = true)) return

        val anchorOpenRegex = Regex(
            "<a\\b[^>]*href\\s*=\\s*['\"]kairo://chapter/(\\d+)['\"][^>]*>",
            RegexOption.IGNORE_CASE,
        )
        val matchable =
            tokens.mapIndexedNotNull { index, token ->
                if (token.type == TokenType.WORD || token.type == TokenType.PUNCTUATION) {
                    index to token.text
                } else {
                    null
                }
            }
        if (matchable.isEmpty()) return
        val tokenTexts = matchable.map { it.second }

        var scanIndex = 0
        var tokenCursor = 0
        var processedLinks = 0
        while (scanIndex < html.length) {
            if (processedLinks >= MAX_LINKS_PER_CHAPTER) break
            if (tokenCursor >= tokenTexts.size) break
            val match = anchorOpenRegex.find(html, scanIndex) ?: break
            val chapterIndex = match.groupValues[1].toIntOrNull()
            val contentStart = match.range.last + 1
            if (chapterIndex == null || contentStart >= html.length) {
                scanIndex = contentStart.coerceAtMost(html.length)
                continue
            }
            processedLinks += 1

            val closeIndex = html.indexOf("</a>", contentStart, ignoreCase = true)
            if (closeIndex == -1) {
                scanIndex = contentStart
                continue
            }

            val innerLength = closeIndex - contentStart
            if (innerLength <= 0 || innerLength > MAX_LINK_TEXT_HTML_CHARS) {
                scanIndex = closeIndex + 4
                continue
            }

            val innerHtml = html.substring(contentStart, closeIndex)
            val linkText = extractLinkText(innerHtml)
            if (linkText.isBlank() || isPageNumberText(linkText)) {
                scanIndex = closeIndex + 4
                continue
            }

            val normalizedLinkText = RtlTextNormalizer.normalizeInlineText(linkText)
            val linkTokens = tokenizeInlineText(normalizedLinkText)
            if (linkTokens.isEmpty()) {
                scanIndex = closeIndex + 4
                continue
            }

            val matchIndex = findTokenSequence(tokenTexts, linkTokens, tokenCursor)
            if (matchIndex >= 0) {
                for (offset in linkTokens.indices) {
                    val tokenIndex = matchable[matchIndex + offset].first
                    val token = tokens[tokenIndex]
                    if (token.linkChapterIndex == null) {
                        tokens[tokenIndex] = token.copy(linkChapterIndex = chapterIndex)
                    }
                }
                tokenCursor = matchIndex + linkTokens.size
            }
            scanIndex = closeIndex + 4
        }
    }

    private fun findTokenSequence(
        tokens: List<String>,
        sequence: List<String>,
        startIndex: Int,
    ): Int {
        if (sequence.isEmpty() || tokens.isEmpty()) return -1
        val lastStart = tokens.size - sequence.size
        var i = startIndex.coerceAtLeast(0)
        while (i <= lastStart) {
            var j = 0
            while (j < sequence.size && tokens[i + j] == sequence[j]) {
                j += 1
            }
            if (j == sequence.size) return i
            i += 1
        }
        return -1
    }

    private fun extractLinkText(html: String): String =
        html
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
                m.groupValues[1].toIntOrNull(16)?.toChar()?.toString().orEmpty()
            }
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isPageNumberText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.all { it.isDigit() }) return true
        val romanNumeralPattern = Regex("^[ivxlcdm]+$", RegexOption.IGNORE_CASE)
        return romanNumeralPattern.matches(trimmed)
    }

    private const val MAX_LINKS_PER_CHAPTER = 1000
    private const val MAX_LINK_TEXT_HTML_CHARS = 1200
}
