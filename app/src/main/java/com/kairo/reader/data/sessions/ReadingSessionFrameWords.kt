package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType

fun countCompletedWordsInFrame(
    frame: RsvpFrame,
    sourceTokens: List<Token>,
): Int = completedFrameProgress(frame, sourceTokens).words

data class CompletedFrameProgress(val words: Int, val lastFullyConsumedOriginalTokenIndex: Int?,)

fun completedFrameProgress(
    frame: RsvpFrame,
    sourceTokens: List<Token>,
): CompletedFrameProgress {
    if (sourceTokens.isEmpty()) return CompletedFrameProgress(0, null)
    val start = frame.displayOriginalStartIndex.coerceIn(0, sourceTokens.size)
    val endExclusive = frame.displayOriginalEndExclusive.coerceIn(start, sourceTokens.size)
    if (start == endExclusive) return CompletedFrameProgress(0, null)
    val lastIndex = endExclusive - 1
    val fullyConsumed =
        (start until endExclusive).filter { index ->
            if (index != lastIndex) return@filter true
            val endOffset = frame.displayOriginalEndCharacterOffset ?: return@filter true
            endOffset >= sourceTokens[index].text.length
        }
    val words =
        if (frame.tokens.any { it.type == TokenType.WORD }) {
            fullyConsumed.count { sourceTokens[it].type == TokenType.WORD }
        } else {
            0
        }
    return CompletedFrameProgress(
        words = words,
        lastFullyConsumedOriginalTokenIndex = fullyConsumed.lastOrNull(),
    )
}
