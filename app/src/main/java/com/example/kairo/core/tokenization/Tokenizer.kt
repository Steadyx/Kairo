@file:Suppress("MaxLineLength")

package com.example.kairo.core.tokenization

import com.example.kairo.core.linguistics.ClauseDetector
import com.example.kairo.core.linguistics.DialogueAnalyzer
import com.example.kairo.core.linguistics.WordAnalyzer
import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.ChapterLink
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.calculateOrpIndex
import com.example.kairo.core.model.normalizeWhitespace

class Tokenizer {
    // Track dialogue state across tokenization
    private var inDialogue = false

    fun tokenize(chapter: Chapter): List<Token> {
        val cleanedText =
            if (shouldStripPageNumbers(chapter.htmlContent)) {
                stripStandalonePageNumbers(chapter.plainText)
            } else {
                chapter.plainText
            }
        val normalized = normalizeWhitespace(cleanedText)
        if (normalized.isEmpty()) return emptyList()

        val cleaned = normalizeEpubSymbols(normalized)
        val withPageBreaks = normalizePageBreakMarkers(cleaned)
        val blockCues = extractBlockCues(chapter.htmlContent)

        // Reset dialogue state for each chapter
        inDialogue = false
        DialogueAnalyzer.reset()

        val paragraphs =
            withPageBreaks
                .split(Regex("\\n\\s*\\n"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        val tokens = mutableListOf<Token>()

        paragraphs.forEachIndexed { index, paragraph ->
            val isPageBreak = isPageBreakParagraph(paragraph)
            if (isPageBreak) {
                tokens +=
                    Token(
                        text = "\u000C",
                        type = TokenType.PAGE_BREAK,
                        pauseAfterMs = 0L,
                    )
            } else {
                tokens += tokenizeParagraph(paragraph)
            }

            val nextParagraph = paragraphs.getOrNull(index + 1)
            val nextIsPageBreak = nextParagraph?.let(::isPageBreakParagraph) == true
            if (index < paragraphs.lastIndex && !isPageBreak && !nextIsPageBreak) {
                val currentCue = blockCues.getOrNull(index) ?: BlockCue(BlockType.PARAGRAPH, false)
                val nextCue = blockCues.getOrNull(index + 1)
                val extraPause = structuralPauseMs(currentCue, nextCue)
                tokens +=
                    Token(
                        text = "\n",
                        type = TokenType.PARAGRAPH_BREAK,
                        pauseAfterMs = extraPause,
                    )
            }
        }

        // Apply links - try multiple strategies
        return applyLinks(tokens, chapter)
    }

    /**
     * Applies links to tokens using pre-computed link positions from the Chapter.
     * Uses character position tracking to efficiently map links to tokens.
     */
    private fun applyLinks(tokens: MutableList<Token>, chapter: Chapter): List<Token> {
        if (chapter.links.isNotEmpty()) {
            applyLinksByCharPositions(tokens, chapter.links)
        }
        applyLinksFromHtml(tokens, chapter.htmlContent)
        return tokens
    }

    private fun applyLinksByCharPositions(
        tokens: MutableList<Token>,
        links: List<ChapterLink>,
    ) {
        if (links.isEmpty()) return
        val sortedLinks =
            if (links.size <= 1) {
                links
            } else {
                links.sortedBy { it.startChar }
            }
        var linkIndex = 0
        var currentLink: ChapterLink? = sortedLinks.getOrNull(linkIndex) ?: return

        // Track character position as we iterate through tokens
        var charPos = 0
        tokens.forEachIndexed { index, token ->
            val tokenStart = charPos
            val tokenEnd = charPos + token.text.length

            while (currentLink != null && tokenStart >= currentLink.endChar) {
                linkIndex += 1
                currentLink = sortedLinks.getOrNull(linkIndex)
            }

            val linkChapterIndex =
                if (currentLink != null &&
                    tokenStart >= currentLink.startChar &&
                    tokenStart < currentLink.endChar
                ) {
                    currentLink.targetChapterIndex
                } else {
                    null
                }

            // Update character position (account for spaces between word tokens)
            charPos = tokenEnd
            if (index < tokens.lastIndex) {
                val nextToken = tokens[index + 1]
                if (token.type == TokenType.WORD && nextToken.type == TokenType.WORD) {
                    charPos++
                }
            }

            if (linkChapterIndex != null && token.linkChapterIndex == null) {
                tokens[index] = token.copy(linkChapterIndex = linkChapterIndex)
            }
        }
    }

    private fun applyLinksFromHtml(
        tokens: MutableList<Token>,
        html: String,
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

            val normalizedLinkText = normalizeEpubSymbols(normalizeWhitespace(linkText))
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

    private fun tokenizeInlineText(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val matcher = TOKEN_REGEX.toPattern().matcher(text)
        val parts = mutableListOf<String>()
        while (matcher.find()) {
            val part = matcher.group()
            if (part.isNotBlank()) parts.add(part)
        }
        return parts
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

    private fun tokenizeParagraph(paragraph: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = TOKEN_REGEX.toPattern().matcher(paragraph)

        while (matcher.find()) {
            val part = matcher.group()
            when {
                part.isEmpty() -> continue
                part.length == 1 &&
                    (
                        PUNCTUATION.contains(part[0]) ||
                            OPENING_QUOTES.contains(part[0]) ||
                            CLOSING_QUOTES.contains(part[0])
                        ) -> {
                    // Track dialogue state with quotes
                    val char = part[0]
                    inDialogue =
                        when {
                            // Straight quotes are ambiguous; toggle on each occurrence.
                            char == '"' -> !inDialogue
                            OPENING_QUOTES.contains(char) -> true
                            CLOSING_QUOTES.contains(char) -> false
                            else -> inDialogue
                        }

                    tokens +=
                        Token(
                            text = part,
                            type = TokenType.PUNCTUATION,
                            pauseAfterMs = 0L,
                            isDialogue = inDialogue,
                        )
                }
                else -> {
                    // This is a word (possibly with contractions like "don't" or hyphenation)
                    val syllables = WordAnalyzer.countSyllables(part)
                    val frequency = WordAnalyzer.getFrequencyScore(part)
                    val complexity = WordAnalyzer.getComplexityMultiplier(part)
                    val isClause = ClauseDetector.isClauseBoundary(part)

                    tokens +=
                        Token(
                            text = part,
                            type = TokenType.WORD,
                            orpIndex = calculateOrpIndex(part),
                            syllableCount = syllables,
                            frequencyScore = frequency,
                            complexityMultiplier = complexity,
                            isClauseBoundary = isClause,
                            isDialogue = inDialogue,
                        )
                }
            }
        }
        return tokens
    }

    private fun shouldStripPageNumbers(html: String): Boolean =
        html.contains("kairo://chapter/", ignoreCase = true)

    private fun stripStandalonePageNumbers(text: String): String {
        return text.lineSequence()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && isPageNumberText(trimmed)
            }
            .joinToString("\n")
    }

    private fun structuralPauseMs(
        current: BlockCue,
        next: BlockCue?,
    ): Long {
        var extra = 0L

        when (current.type) {
            BlockType.HEADING -> extra += HEADING_AFTER_MS
            BlockType.BLOCKQUOTE -> extra += BLOCKQUOTE_AFTER_MS
            BlockType.PREFORMATTED -> extra += PREFORMATTED_AFTER_MS
            BlockType.LIST_ITEM -> if (next?.type != BlockType.LIST_ITEM) extra += LIST_END_AFTER_MS
            BlockType.PARAGRAPH -> Unit
        }

        if (current.hasEmphasis) extra += EMPHASIS_AFTER_MS

        when (next?.type) {
            BlockType.HEADING -> extra += HEADING_BEFORE_MS
            BlockType.BLOCKQUOTE -> extra += BLOCKQUOTE_BEFORE_MS
            BlockType.PREFORMATTED -> extra += PREFORMATTED_BEFORE_MS
            else -> Unit
        }

        return extra.coerceAtLeast(0L)
    }

    private fun extractBlockCues(html: String): List<BlockCue> {
        if (html.isBlank()) return emptyList()

        val cleaned =
            html
                .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")

        val tagRegex =
            Regex("<\\s*(h[1-6]|li|blockquote|pre|p|div|br)\\b[^>]*>", RegexOption.IGNORE_CASE)
        val emphasisRegex = Regex("<\\s*(em|i)\\b", RegexOption.IGNORE_CASE)
        val matches = tagRegex.findAll(cleaned).toList()
        val cues = mutableListOf<BlockCue>()
        if (matches.isNotEmpty()) {
            for (i in matches.indices) {
                val match = matches[i]
                val tag = match.groupValues[1].lowercase()
                val start = match.range.first
                val end = if (i < matches.lastIndex) matches[i + 1].range.first else cleaned.length
                val content = cleaned.substring(start, end)
                val hasEmphasis = emphasisRegex.containsMatchIn(content)

                val type =
                    when {
                        tag.startsWith("h") -> BlockType.HEADING
                        tag == "li" -> BlockType.LIST_ITEM
                        tag == "blockquote" -> BlockType.BLOCKQUOTE
                        tag == "pre" -> BlockType.PREFORMATTED
                        else -> BlockType.PARAGRAPH
                    }
                cues += BlockCue(type, hasEmphasis)
            }
        }

        return cues
    }

    private fun normalizeEpubSymbols(input: String): String {
        var text = input

        // Normalize non-breaking/odd spaces to regular spaces for consistent regex handling.
        text = text.replace(Regex("[\\u00A0\\u2007\\u202F\\u2009\\u200A\\u200B]"), " ")

        // Normalize common ASCII punctuation variants.
        // "..." -> "…" so ellipses don't act like three sentence-ending dots.
        text = text.replace(Regex("\\.{3,}"), "\u2026")
        // "--" -> "—" so em-dashes don't act like two separate hyphens.
        text = text.replace(Regex("(?<!-)--(?!-)"), "\u2014")

        // Fix common mojibake sequences from EPUB decoding (e.g., "Â°" for degree sign).
        text =
            text
                .replace("Â°", "°")
                .replace("Âº", "º")

        // Collapse a minus-like sign separated from a number:
        // " - 35c" / " – 35c" / " — 35c" / "‑35c" -> "-35c"
        text =
            text.replace(Regex("(^|[^\\w])[-−–—‐‑‒﹣－]\\s*(\\d)")) { match ->
                "${match.groupValues[1]}-${match.groupValues[2]}"
            }

        // Normalize temperature and percent spacing:
        // "20 ° C" -> "20°C", "20 ºF" -> "20°F"
        text =
            text.replace(Regex("(\\d)\\s*[°º]\\s*([cCfFkK])")) { match ->
                "${match.groupValues[1]}°${match.groupValues[2]}"
            }
        // "20 ℃" -> "20℃"
        text = text.replace(Regex("(\\d)\\s*([℃℉])"), "$1$2")
        // "50 %" -> "50%"
        text = text.replace(Regex("(\\d)\\s*%"), "$1%")

        return text
    }

    private fun normalizePageBreakMarkers(input: String): String {
        var text = input

        // Make form-feed page breaks visible to the paragraph splitter.
        // Many ebook conversions use \u000C for page/scene breaks.
        text = text.replace("\u000C", "\n\n\u000C\n\n")
        return text
    }

    private fun isPageBreakParagraph(paragraph: String): Boolean {
        if (paragraph.isBlank()) return false
        val isFormFeed = paragraph == "\u000C"
        return isFormFeed || PAGE_BREAK_REGEX.matches(paragraph)
    }

    companion object {
        private const val MAX_LINKS_PER_CHAPTER = 1000
        private const val MAX_LINK_TEXT_HTML_CHARS = 1200

        private const val HEADING_BEFORE_MS = 140L
        private const val HEADING_AFTER_MS = 220L
        private const val BLOCKQUOTE_BEFORE_MS = 90L
        private const val BLOCKQUOTE_AFTER_MS = 140L
        private const val PREFORMATTED_BEFORE_MS = 110L
        private const val PREFORMATTED_AFTER_MS = 160L
        private const val LIST_END_AFTER_MS = 120L
        private const val EMPHASIS_AFTER_MS = 60L

        // All punctuation characters we want to handle (NOT including apostrophes used in contractions)
        private val PUNCTUATION =
            setOf(
                '.',
                ',',
                ';',
                ':',
                '!',
                '?', // Basic punctuation
                '"', // Straight double quote
                '\u201C',
                '\u201D', // Curly double quotes " "
                '\u2014',
                '\u2013', // Em-dash — and en-dash –
                '\u2026', // Ellipsis …
                '(',
                ')',
                '[',
                ']',
                '{',
                '}', // Brackets
                '-',
                '\u2212', // Hyphen/minus (but not when inside words)
                '°',
                'º',
                '℃',
                '℉', // Temperature symbols
                '%', // Percent
                '$',
                '€',
                '£',
                '¥', // Common currency symbols
            )

        // Opening quotes that start dialogue
        private val OPENING_QUOTES = setOf('"', '\u201C', '\u2018')

        // Closing quotes that end dialogue
        private val CLOSING_QUOTES = setOf('"', '\u201D', '\u2019')

        // Common "scene break" markers: "***", "* * *", "---", "— — —", "• • •", "___", etc.
        // These frequently represent page breaks or scene breaks in ebooks.
        private val PAGE_BREAK_REGEX =
            Regex(
                """^\s*(?:(?:\*\s*){3,}|(?:-\s*){3,}|(?:_\s*){3,}|(?:~\s*){3,}|(?:\u2014\s*){2,}|(?:\u2013\s*){2,}|(?:\u2022\s*){3,}|(?:\u00B7\s*){3,})\s*$""",
            )

        // Regex to match:
        // 1. Words with contractions: "don't", "he'd", "it's" - apostrophes (straight or curly) embedded in words
        // 2. Hyphenated words: "self-aware", "mother-in-law"
        // 3. Standalone punctuation marks
        private val TOKEN_REGEX =
            Regex(
                // Numeric + unit patterns: temperatures and percentages.
                // Examples: "20°C", "-35c", "–35c", "‑35c", "20°F", "20℃", "50%"
                """[-−–—‐‑‒﹣－]?\d+(?:[.,]\d+)?(?:[℃℉]|%|[°º]?[cCfFkK](?![a-zA-Z]))""" +
                    "|" +
                    // Numeric patterns with separators (decimals / thousands).
                    // Examples: "3.14", "1,000", "1,000,000", "-2.5"
                    """[-−–—‐‑‒﹣－]?\d+(?:[.,]\d+)+""" +
                    "|" +
                    // Word pattern: word chars, optionally followed by (apostrophe + word chars) or (hyphen + word chars)
                    // This captures contractions like "don't" and hyphenated words like "self-aware"
                    """[\w]+(?:[\u0027\u2019\u2018][\w]+|-[\w]+)*""" +
                    "|" +
                    // Punctuation pattern: single punctuation characters (quotes, periods, etc.)
                    """[.,;:!?\u201C\u201D\u201E\u0022\u2018\u2019\u2014\u2013\u2026()\[\]{}\-\u2212°º%$€£¥℃℉]""",
            )
    }

    private enum class BlockType {
        PARAGRAPH,
        HEADING,
        LIST_ITEM,
        BLOCKQUOTE,
        PREFORMATTED,
    }

    private data class BlockCue(val type: BlockType, val hasEmphasis: Boolean,)
}
