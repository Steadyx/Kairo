@file:Suppress("MaxLineLength")

package com.example.kairo.core.tokenization

import com.example.kairo.core.linguistics.ClauseDetector
import com.example.kairo.core.linguistics.DialogueAnalyzer
import com.example.kairo.core.linguistics.WordAnalyzer
import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.calculateOrpIndex
import com.example.kairo.core.model.normalizeWhitespace

class Tokenizer {
    // Track dialogue state across tokenization
    private var inDialogue = false

    fun tokenize(chapter: Chapter): List<Token> {
        val normalized = normalizeWhitespace(chapter.plainText)
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
        return tokens
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
