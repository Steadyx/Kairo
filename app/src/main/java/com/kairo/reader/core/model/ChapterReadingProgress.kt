package com.kairo.reader.core.model

import kotlin.math.roundToInt

/** Progress through source words in the current chapter, shared by Reader and timed reading. */
data class ChapterReadingProgress(val currentWord: Int, val totalWords: Int) {
    val fraction: Float
        get() = if (totalWords > 0) (currentWord.toFloat() / totalWords).coerceIn(0f, 1f) else 0f
    val percent: Int get() = (fraction * PROGRESS_PERCENT_SCALE).roundToInt()
}

fun chapterReadingProgress(
    wordCountByToken: IntArray?,
    tokenIndex: Int,
    totalWords: Int,
): ChapterReadingProgress {
    val safeTotal = totalWords.coerceAtLeast(0)
    val currentWord = when {
        wordCountByToken == null || wordCountByToken.isEmpty() || tokenIndex < 0 -> 0
        tokenIndex >= wordCountByToken.size -> safeTotal
        else -> wordCountByToken[tokenIndex].coerceIn(0, safeTotal)
    }
    return ChapterReadingProgress(currentWord, safeTotal)
}

private const val PROGRESS_PERCENT_SCALE = 100f
