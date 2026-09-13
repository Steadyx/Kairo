package com.kairo.reader.ui.rsvp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.kairo.reader.core.model.ChapterReadingProgress
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.buildWordCountByToken
import com.kairo.reader.core.model.chapterReadingProgress

internal typealias RsvpProgress = ChapterReadingProgress

/** Match Reader's chapter progress, independently of the remaining playback frame list. */
internal class RsvpProgressBasis(tokens: List<Token>) {
    private val wordCountByToken = buildWordCountByToken(tokens)

    fun at(tokenIndex: Int, completed: Boolean = false): RsvpProgress =
        chapterReadingProgress(
            wordCountByToken = wordCountByToken,
            tokenIndex = if (completed) wordCountByToken.size else tokenIndex,
            totalWords = wordCountByToken.lastOrNull() ?: 0,
        )
}

@Composable
internal fun rememberRsvpProgress(context: RsvpUiContext): RsvpProgress {
    val book = context.state.book
    val basis = remember(book.tokens) {
        RsvpProgressBasis(book.tokens)
    }
    val runtime = context.runtime
    val tokenIndex = resolveCurrentTokenIndex(
        frames = context.frameState.frames,
        frameIndex = runtime.frameIndex,
        fallbackIndex = runtime.currentTokenIndex,
    )
    return basis.at(tokenIndex, completed = runtime.completed && !runtime.isExiting)
}
