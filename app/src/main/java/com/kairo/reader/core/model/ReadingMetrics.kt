@file:Suppress("MagicNumber")

package com.kairo.reader.core.model

import kotlin.math.ceil

fun countWords(text: String): Int {
    if (text.isBlank()) return 0
    var count = 0
    var inWord = false
    var index = 0
    while (index < text.length) {
        val codePoint = Character.codePointAt(text, index)
        val charCount = Character.charCount(codePoint)
        when {
            Character.isLetterOrDigit(codePoint) -> {
                if (!inWord) count += 1
                inWord = true
            }
            isWordApostrophe(codePoint) && inWord -> {
                val nextIndex = index + charCount
                inWord =
                    nextIndex < text.length &&
                    Character.isLetterOrDigit(Character.codePointAt(text, nextIndex))
            }
            else -> inWord = false
        }
        index += charCount
    }
    return count
}

private fun isWordApostrophe(codePoint: Int): Boolean =
    codePoint == '\''.code || codePoint == '\u2019'.code

fun countWords(tokens: List<Token>): Int = tokens.count { it.type == TokenType.WORD }

fun countWordsThroughToken(
    tokens: List<Token>,
    tokenIndex: Int,
): Int {
    if (tokens.isEmpty()) return 0
    val clamped = tokenIndex.coerceIn(0, tokens.lastIndex)
    var count = 0
    for (i in 0..clamped) {
        if (tokens[i].type == TokenType.WORD) count += 1
    }
    return count
}

fun buildWordCountByToken(tokens: List<Token>): IntArray {
    if (tokens.isEmpty()) return IntArray(0)
    val counts = IntArray(tokens.size)
    var total = 0
    tokens.forEachIndexed { index, token ->
        if (token.type == TokenType.WORD) total += 1
        counts[index] = total
    }
    return counts
}

fun wordIndexForToken(
    wordCountByToken: IntArray,
    tokenIndex: Int,
): Int {
    if (wordCountByToken.isEmpty()) return 0
    val clamped = tokenIndex.coerceIn(0, wordCountByToken.lastIndex)
    return wordCountByToken[clamped]
}

fun estimateMinutesForWords(
    wordsRemaining: Int,
    wpm: Int,
): Int {
    if (wordsRemaining <= 0 || wpm <= 0) return 0
    return ceil(wordsRemaining / wpm.toDouble()).toInt().coerceAtLeast(1)
}
