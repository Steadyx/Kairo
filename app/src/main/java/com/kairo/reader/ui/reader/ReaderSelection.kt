package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.SavedAnnotationLimits
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.joinTokensForDisplay

internal fun resolveReaderSelectionRange(
    anchor: Int?,
    end: Int?,
): IntRange? =
    anchor?.let { start ->
        val resolvedEnd = end ?: start
        minOf(start, resolvedEnd)..maxOf(start, resolvedEnd)
    }

internal fun buildReaderSelectionText(
    tokens: List<Token>,
    selectionRange: IntRange?,
): String {
    if (tokens.isEmpty() || selectionRange == null) return ""
    val start = selectionRange.first.coerceIn(tokens.indices)
    val end = selectionRange.last.coerceIn(start, tokens.lastIndex)
    return joinTokensForDisplay(tokens.subList(start, end + 1))
}

internal data class ReaderSelectionLimitState(val characterCount: Int, val tokenCount: Int, val canSave: Boolean,)

internal fun readerSelectionLimitState(
    selectedText: String,
    selectionRange: IntRange?,
): ReaderSelectionLimitState {
    val tokenCount =
        selectionRange
            ?.let { range ->
                (range.last.toLong() - range.first.toLong() + 1L)
                    .coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt()
            } ?: 0
    return ReaderSelectionLimitState(
        characterCount = selectedText.length,
        tokenCount = tokenCount,
        canSave =
        selectedText.isNotBlank() &&
            selectedText.length <= SavedAnnotationLimits.MAX_SELECTED_TEXT_CHARACTERS &&
            tokenCount <= SavedAnnotationLimits.MAX_SELECTED_TOKENS,
    )
}
