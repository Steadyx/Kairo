@file:Suppress("MagicNumber")

package com.kairo.reader.core.model

import com.kairo.reader.core.linguistics.WordAnalyzer

fun isSentenceEndingPunctuation(char: Char): Boolean =
    char == '.' ||
        char == '!' ||
        char == '?' ||
        char == '\u2026' ||
        // Include ellipsis …
        char == '。' ||
        char == '！' ||
        char == '？' ||
        char == '｡' ||
        char == '؟' ||
        char == '۔' ||
        char == '׃'

fun isMidSentencePunctuation(char: Char): Boolean =
    char == ',' ||
        char == ';' ||
        char == ':' ||
        char == '\u2014' ||
        char == '\u2013' ||
        char == '、' ||
        char == '，' ||
        char == '：' ||
        char == '；' ||
        char == '·' ||
        char == '・' ||
        char == '‧' ||
        char == '،' ||
        char == '؛' ||
        char == '־'

fun isPhysicalPageBreakToken(token: Token): Boolean =
    token.type == TokenType.PAGE_BREAK && token.text == FORM_FEED_PAGE_BREAK_TEXT

fun shouldKeepPhysicalPageBreak(
    tokens: List<Token>,
    index: Int,
): Boolean =
    hasReadableContentAfter(tokens, index) && hasNaturalBoundaryBefore(tokens, index)

fun List<Token>.withoutInlinePhysicalPageBreaks(): List<Token> {
    if (none(::isPhysicalPageBreakToken)) return this
    return filterIndexed { index, token ->
        !isPhysicalPageBreakToken(token) || shouldKeepPhysicalPageBreak(this, index)
    }
}

private fun hasReadableContentAfter(
    tokens: List<Token>,
    index: Int,
): Boolean {
    for (i in index + 1 until tokens.size) {
        when (tokens[i].type) {
            TokenType.WORD -> return true
            TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> return false
            TokenType.PUNCTUATION -> Unit
        }
    }
    return false
}

private fun hasNaturalBoundaryBefore(
    tokens: List<Token>,
    index: Int,
): Boolean {
    var cursor = index - 1
    while (cursor >= 0) {
        val token = tokens[cursor]
        when (token.type) {
            TokenType.WORD -> return false
            TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> return true
            TokenType.PUNCTUATION -> {
                val char = token.text.singleOrNull() ?: return false
                when {
                    isSentenceEndingPunctuation(char) -> return true
                    char in CLOSING_BOUNDARY_PUNCTUATION -> cursor -= 1
                    else -> return false
                }
            }
        }
    }
    return false
}

fun normalizeWhitespace(input: String): String =
    input
        .split("\n")
        .joinToString("\n") { line ->
            line.trim().replace(Regex("\\s+"), " ")
        }.trim()

/**
 * Resolves a focus/position index to the nearest WORD token.
 *
 * If the token at [fromIndex] is already a word, returns it.
 * Otherwise searches outward, preferring the next word ahead of the index.
 * Falls back to 0 when no words are present.
 */
fun List<Token>.nearestWordIndex(fromIndex: Int): Int {
    if (isEmpty()) return 0
    val clamped = fromIndex.coerceIn(0, lastIndex)
    var resolved = if (this[clamped].type == TokenType.WORD) clamped else null

    if (resolved == null) {
        var offset = 1
        while (offset <= lastIndex && resolved == null) {
            val forward = clamped + offset
            if (forward <= lastIndex && this[forward].type == TokenType.WORD) {
                resolved = forward
            } else {
                val backward = clamped - offset
                if (backward >= 0 && this[backward].type == TokenType.WORD) {
                    resolved = backward
                }
            }
            offset += 1
        }
    }

    return resolved ?: 0
}

/**
 * Splits a hyphenated word token into multiple tokens for RSVP display.
 * For example, "over-telling" becomes ["over-", "telling"].
 * The hyphen stays attached to the preceding part for natural reading.
 *
 * Non-hyphenated tokens are returned as a single-element list.
 */
fun splitHyphenatedToken(token: Token): List<Token> {
    // Only split WORD tokens that contain hyphens
    val shouldSplit = token.type == TokenType.WORD && token.text.contains('-')

    // Don't split leading hyphens used as numeric signs (e.g., "-35c", "-10", "-3.14").
    // Those should be treated as a single unit for RSVP.
    val startsWithNumericDash =
        token.text.length > 1 &&
            token.text[0] == '-' &&
            token.text[1].isDigit()

    // Leading/trailing/double hyphens ("well-", "-ish", "well--done") would produce empty parts,
    // which become blank word frames. Keep those tokens whole.
    val parts = token.text.split('-')
    val canSplit =
        shouldSplit && !startsWithNumericDash && parts.size > 1 && parts.none { it.isEmpty() }

    if (!canSplit) return listOf(token)

    return parts.mapIndexed { index, part ->
        val isLast = index == parts.lastIndex
        // Attach hyphen to each part except the last
        val text = if (isLast) part else "$part-"
        val analysis = WordAnalyzer.analyze(part)

        token.copy(
            text = text,
            orpIndex = analysis.orpIndex,
            syllableCount = analysis.syllableCount,
            frequencyScore = analysis.frequencyScore,
            complexityMultiplier = analysis.complexityMultiplier,
            isClauseBoundary = if (isLast) token.isClauseBoundary else false,
            isDialogue = token.isDialogue,
            pauseAfterMs = if (isLast) token.pauseAfterMs else 0L,
            highlightStart = null,
            highlightEndExclusive = null,
        )
    }
}

private const val FORM_FEED_PAGE_BREAK_TEXT = "\u000C"
private val CLOSING_BOUNDARY_PUNCTUATION =
    setOf(
        '"',
        '\'',
        '\u201D',
        '\u2019',
        ')',
        ']',
        '}',
        '\u3009',
        '\u300B',
        '\u300D',
        '\u300F',
        '\u3011',
        '\u3015',
        '\u3017',
        '\u3019',
        '\u301B',
        '\uFF09',
        '\uFF3D',
        '\uFF5D',
    )

/**
 * Splits a token for RSVP display, handling hyphenated words and long words.
 * Long words are chunked into minimal parts based on [maxChunkLength].
 */
fun splitTokenForRsvp(
    token: Token,
    maxChunkLength: Int,
    subwordChunkPauseMs: Long,
): List<Token> =
    splitHyphenatedToken(token).flatMap { splitToken ->
        splitLongWordToken(splitToken, maxChunkLength, subwordChunkPauseMs)
    }

private fun splitLongWordToken(
    token: Token,
    maxChunkLength: Int,
    subwordChunkPauseMs: Long,
): List<Token> {
    if (token.type != TokenType.WORD) return listOf(token)
    if (maxChunkLength <= 0) return listOf(token)

    val text = token.text
    val trailingHyphen = text.endsWith("-")
    val baseText = if (trailingHyphen) text.dropLast(1) else text

    if (baseText.length <= maxChunkLength || baseText.isEmpty()) return listOf(token)

    val ranges = splitWordIntoChunkRanges(baseText, maxChunkLength)
    return ranges.mapIndexed { index, range ->
        val isLast = index == ranges.lastIndex
        val extraPause = if (isLast) 0L else subwordChunkPauseMs.coerceAtLeast(0L)

        token.copy(
            text = text,
            // Phrase chunking across subword splits is blocked via isSubwordChunk in
            // the scored segmenter; isClauseBoundary must stay truthful because the timing
            // model reads it (clause holds would otherwise fire on every chunk of a long word).
            isClauseBoundary = if (isLast) token.isClauseBoundary else false,
            isDialogue = token.isDialogue,
            pauseAfterMs = if (isLast) token.pauseAfterMs else extraPause,
            isSubwordChunk = true,
            highlightStart = range.start,
            highlightEndExclusive = range.endExclusive,
        )
    }
}

private data class ChunkRange(val start: Int, val endExclusive: Int,)

private fun splitWordIntoChunkRanges(
    word: String,
    maxChunkLength: Int,
): List<ChunkRange> {
    if (word.length <= maxChunkLength) return listOf(ChunkRange(0, word.length))

    val chunkCount = (word.length + maxChunkLength - 1) / maxChunkLength
    val baseSize = word.length / chunkCount
    val remainder = word.length % chunkCount
    val parts = mutableListOf<ChunkRange>()
    var index = 0

    repeat(chunkCount) { chunkIndex ->
        val size = baseSize + if (chunkIndex < remainder) 1 else 0
        parts += ChunkRange(index, index + size)
        index += size
    }

    return parts
}
