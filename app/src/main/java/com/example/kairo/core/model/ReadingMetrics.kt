@file:Suppress("MagicNumber")

package com.example.kairo.core.model

import kotlin.math.ceil

private val WORD_REGEX =
    Regex(
        """[\p{L}\p{N}]+(?:['\u2019][\p{L}\p{N}]+)?""",
    )

fun countWords(text: String): Int {
    if (text.isBlank()) return 0
    return WORD_REGEX.findAll(text).count()
}

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

fun formatDurationMinutes(minutes: Int): String {
    if (minutes <= 0) return "<1m"
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours <= 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}
