package com.example.kairo.core.tokenization

import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.calculateOrpIndex
import com.example.kairo.core.model.normalizeWhitespace
import com.example.kairo.core.model.isMidSentencePunctuation
import com.example.kairo.core.model.isSentenceEndingPunctuation
import com.example.kairo.core.linguistics.WordAnalyzer
import com.example.kairo.core.linguistics.ClauseDetector
import com.example.kairo.core.linguistics.DialogueAnalyzer

class Tokenizer {
    // Track dialogue state across tokenization
    private var inDialogue = false

    fun tokenize(chapter: Chapter): List<Token> {
        val normalized = normalizeWhitespace(chapter.plainText)
        if (normalized.isEmpty()) return emptyList()

        val cleaned = normalizeEpubSymbols(normalized)

        // Reset dialogue state for each chapter
        inDialogue = false
        DialogueAnalyzer.reset()

        val paragraphs = cleaned.split(Regex("\\n\\s*\\n"))
        val tokens = mutableListOf<Token>()

        paragraphs.forEachIndexed { index, paragraph ->
            tokens += tokenizeParagraph(paragraph.trim())
            if (index < paragraphs.lastIndex) {
                tokens += Token(
                    text = "\n",
                    type = TokenType.PARAGRAPH_BREAK,
                    pauseAfterMs = PARAGRAPH_PAUSE
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
                part.length == 1 && (PUNCTUATION.contains(part[0]) ||
                    OPENING_QUOTES.contains(part[0]) || CLOSING_QUOTES.contains(part[0])) -> {
                    // Track dialogue state with quotes
                    val char = part[0]
                    if (OPENING_QUOTES.contains(char)) {
                        inDialogue = true
                    } else if (CLOSING_QUOTES.contains(char)) {
                        inDialogue = false
                    }

                    tokens += Token(
                        text = part,
                        type = TokenType.PUNCTUATION,
                        pauseAfterMs = punctuationPause(char),
                        isDialogue = inDialogue
                    )
                }
                else -> {
                    // This is a word (possibly with contractions like "don't" or hyphenation)
                    val syllables = WordAnalyzer.countSyllables(part)
                    val frequency = WordAnalyzer.getFrequencyScore(part)
                    val complexity = WordAnalyzer.getComplexityMultiplier(part)
                    val isClause = ClauseDetector.isClauseBoundary(part)

                    tokens += Token(
                        text = part,
                        type = TokenType.WORD,
                        orpIndex = calculateOrpIndex(part),
                        syllableCount = syllables,
                        frequencyScore = frequency,
                        complexityMultiplier = complexity,
                        isClauseBoundary = isClause,
                        isDialogue = inDialogue
                    )
                }
            }
        }
        return tokens
    }

    private fun punctuationPause(char: Char): Long = when {
        isSentenceEndingPunctuation(char) -> SENTENCE_PAUSE
        isMidSentencePunctuation(char) -> MID_SENTENCE_PAUSE
        else -> 0L
    }

    private fun normalizeEpubSymbols(input: String): String {
        var text = input

        // Normalize non-breaking/odd spaces to regular spaces for consistent regex handling.
        text = text.replace(Regex("[\\u00A0\\u2007\\u202F\\u2009\\u200A\\u200B]"), " ")

        // Fix common mojibake sequences from EPUB decoding (e.g., "Â°" for degree sign).
        text = text
            .replace("Â°", "°")
            .replace("Âº", "º")

        // Collapse a minus-like sign separated from a number:
        // " - 35c" / " – 35c" / " — 35c" / "‑35c" -> "-35c"
        text = text.replace(Regex("(^|[^\\w])[-−–—‐‑‒﹣－]\\s*(\\d)")) { match ->
            "${match.groupValues[1]}-${match.groupValues[2]}"
        }

        // Normalize temperature and percent spacing:
        // "20 ° C" -> "20°C", "20 ºF" -> "20°F"
        text = text.replace(Regex("(\\d)\\s*[°º]\\s*([cCfFkK])")) { match ->
            "${match.groupValues[1]}°${match.groupValues[2]}"
        }
        // "20 ℃" -> "20℃"
        text = text.replace(Regex("(\\d)\\s*([℃℉])"), "$1$2")
        // "50 %" -> "50%"
        text = text.replace(Regex("(\\d)\\s*%"), "$1%")

        return text
    }

    companion object {
        private const val SENTENCE_PAUSE = 260L
        private const val MID_SENTENCE_PAUSE = 140L
        private const val PARAGRAPH_PAUSE = 320L

        // Apostrophe characters used in contractions (straight and curly)
        private const val APOSTROPHES = "'\u2019\u2018"  // ' ' '

        // All punctuation characters we want to handle (NOT including apostrophes used in contractions)
        private val PUNCTUATION = setOf(
            '.', ',', ';', ':', '!', '?',  // Basic punctuation
            '"',                            // Straight double quote
            '\u201C', '\u201D',             // Curly double quotes " "
            '\u2014', '\u2013',             // Em-dash — and en-dash –
            '\u2026',                       // Ellipsis …
            '(', ')', '[', ']', '{', '}',  // Brackets
            '-', '\u2212',                  // Hyphen/minus (but not when inside words)
            '°', 'º', '℃', '℉',             // Temperature symbols
            '%',                            // Percent
            '$', '€', '£', '¥'              // Common currency symbols
        )

        // Opening quotes that start dialogue
        private val OPENING_QUOTES = setOf('"', '\u201C', '\u2018')
        // Closing quotes that end dialogue
        private val CLOSING_QUOTES = setOf('"', '\u201D', '\u2019')

        // Regex to match:
        // 1. Words with contractions: "don't", "he'd", "it's" - apostrophes (straight or curly) embedded in words
        // 2. Hyphenated words: "self-aware", "mother-in-law"
        // 3. Standalone punctuation marks
        private val TOKEN_REGEX = Regex(
            // Numeric + unit patterns: temperatures and percentages.
            // Examples: "20°C", "-35c", "–35c", "‑35c", "20°F", "20℃", "50%"
            """[-−–—‐‑‒﹣－]?\d+(?:[.,]\d+)?(?:[℃℉]|%|[°º]?[cCfFkK](?![a-zA-Z]))""" +
            "|" +
            // Word pattern: word chars, optionally followed by (apostrophe + word chars) or (hyphen + word chars)
            // This captures contractions like "don't" and hyphenated words like "self-aware"
            """[\w]+(?:[\u0027\u2019\u2018][\w]+|-[\w]+)*""" +
            "|" +
            // Punctuation pattern: single punctuation characters (quotes, periods, etc.)
            """[.,;:!?\u201C\u201D\u201E\u0022\u2018\u2019\u2014\u2013\u2026()\[\]{}\-\u2212°º%$€£¥℃℉]"""
        )
    }
}
