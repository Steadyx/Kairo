package com.example.kairo.ui.rsvp

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.shouldInsertSpaceBeforeToken

internal data class RsvpParagraph(val tokens: List<Token>, val startIndex: Int)

internal fun resolveRsvpParagraph(tokens: List<Token>, focusIndex: Int): RsvpParagraph? {
    if (tokens.isEmpty()) return null

    val safeIndex = focusIndex.coerceIn(0, tokens.lastIndex)
    var startIndex = safeIndex
    while (startIndex > 0) {
        val prevType = tokens[startIndex - 1].type
        if (prevType == TokenType.PARAGRAPH_BREAK || prevType == TokenType.PAGE_BREAK) break
        startIndex -= 1
    }

    var endExclusive = safeIndex
    while (endExclusive < tokens.size) {
        val type = tokens[endExclusive].type
        if (type == TokenType.PARAGRAPH_BREAK || type == TokenType.PAGE_BREAK) break
        endExclusive += 1
    }

    if (endExclusive <= startIndex) return null
    return RsvpParagraph(tokens.subList(startIndex, endExclusive), startIndex)
}

internal fun buildRsvpParagraphAnnotatedText(
    paragraph: RsvpParagraph,
    highlightIndex: Int,
    highlightStyle: SpanStyle,
): AnnotatedString =
    buildAnnotatedString {
        val tokens = paragraph.tokens
        if (tokens.isEmpty()) return@buildAnnotatedString

        val highlightLocalIndex =
            (highlightIndex - paragraph.startIndex).coerceIn(0, tokens.lastIndex)
        val window =
            resolveWordWindow(tokens, highlightLocalIndex, PARAGRAPH_PREVIEW_WINDOW_WORDS)
        val windowStart = window.first
        val windowEndExclusive = window.last + 1

        if (windowStart > 0) append("... ")

        var paragraphIndex = 0
        var prevToken: Token? = null
        val windowTokens = tokens.subList(windowStart, windowEndExclusive)
        val baseGlobalIndex = paragraph.startIndex + windowStart

        windowTokens.forEachIndexed { index, token ->
            if (token.type == TokenType.PARAGRAPH_BREAK || token.type == TokenType.PAGE_BREAK) {
                return@forEachIndexed
            }

            if (shouldInsertSpaceBeforeToken(token, prevToken, paragraphIndex)) {
                append(' ')
            }

            val start = length
            append(token.text)
            val end = length
            val globalIndex = baseGlobalIndex + index

            if (globalIndex == highlightIndex) {
                addStyle(highlightStyle, start, end)
            }

            prevToken = token
            paragraphIndex += 1
        }

        if (windowEndExclusive < tokens.size) append(" ...")
    }

private fun resolveWordWindow(
    tokens: List<Token>,
    highlightLocalIndex: Int,
    maxWords: Int,
): IntRange {
    val wordIndices = mutableListOf<Int>()
    tokens.forEachIndexed { index, token ->
        if (token.type == TokenType.WORD) wordIndices.add(index)
    }
    if (wordIndices.isEmpty()) return 0 until tokens.size

    val highlightWordOrdinal = wordIndices.indexOfFirst { it == highlightLocalIndex }
        .coerceAtLeast(0)
    val totalWords = wordIndices.size
    val windowWords = maxWords.coerceIn(1, totalWords)
    val progress =
        if (totalWords <= 1) {
            0f
        } else {
            highlightWordOrdinal.toFloat() / (totalWords - 1).toFloat()
        }
    val beforeWords = (windowWords * progress).toInt().coerceIn(0, windowWords - 1)
    val afterWords = windowWords - 1 - beforeWords
    val startWordOrdinal = (highlightWordOrdinal - beforeWords).coerceAtLeast(0)
    val endWordOrdinalExclusive =
        (highlightWordOrdinal + afterWords + 1).coerceAtMost(totalWords)
    val startTokenIndex = wordIndices[startWordOrdinal]
    val endTokenIndexExclusive =
        if (endWordOrdinalExclusive >= totalWords) {
            tokens.size
        } else {
            wordIndices[endWordOrdinalExclusive]
        }
    return startTokenIndex until endTokenIndexExclusive
}
