package com.example.kairo.ui.rsvp

import com.example.kairo.core.model.RsvpFrame
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.nearestWordIndex

internal fun resolveCurrentTokenIndex(
    frames: List<RsvpFrame>,
    frameIndex: Int,
    fallbackIndex: Int,
): Int = frames.getOrNull(frameIndex)?.originalTokenIndex ?: fallbackIndex

internal fun resolveCurrentResumeCursor(
    frames: List<RsvpFrame>,
    frameIndex: Int,
    fallbackCursor: Int,
): Int = frames.getOrNull(frameIndex)?.resumeCursor ?: fallbackCursor

internal fun alignFrameIndex(
    frames: List<RsvpFrame>,
    tokenIndex: Int,
    resumeCursor: Int = -1,
): Int {
    if (frames.isEmpty()) return 0
    if (resumeCursor >= 0) {
        val exactResumeWordMatch =
            frames.indexOfLast { frame ->
                frame.resumeCursor == resumeCursor &&
                    frame.tokens.any { it.type == TokenType.WORD }
            }
        if (exactResumeWordMatch != -1) return exactResumeWordMatch.coerceIn(0, frames.lastIndex)

        val exactResumeMatch = frames.indexOfLast { it.resumeCursor == resumeCursor }
        if (exactResumeMatch != -1) return exactResumeMatch.coerceIn(0, frames.lastIndex)
    }

    val exactWordMatch =
        frames.indexOfLast { frame ->
            frame.originalTokenIndex == tokenIndex &&
                frame.tokens.any { it.type == TokenType.WORD }
        }
    if (exactWordMatch != -1) return exactWordMatch.coerceIn(0, frames.lastIndex)

    val exactMatch = frames.indexOfLast { it.originalTokenIndex == tokenIndex }
    if (exactMatch != -1) return exactMatch.coerceIn(0, frames.lastIndex)

    val priorWordMatch =
        frames.indexOfLast { frame ->
            frame.originalTokenIndex < tokenIndex &&
                frame.tokens.any { it.type == TokenType.WORD }
        }
    val safeIdx =
        when {
            priorWordMatch != -1 -> priorWordMatch
            else -> frames.indexOfLast { it.originalTokenIndex < tokenIndex }
        }.takeIf { it != -1 } ?: 0
    return safeIdx.coerceIn(0, frames.lastIndex)
}

internal fun currentResumePoint(context: RsvpUiContext): RsvpResumePoint {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val book = context.state.book
    val currentIndex = resolveCurrentTokenIndex(frames, runtime.frameIndex, book.startIndex)
    val safeIndex =
        if (book.tokens.isNotEmpty()) {
            book.tokens.nearestWordIndex(currentIndex)
        } else {
            currentIndex
        }
    val resumeCursor =
        resolveCurrentResumeCursor(
            frames = frames,
            frameIndex = runtime.frameIndex,
            fallbackCursor = book.startResumeCursor.takeIf { it >= 0 } ?: -1,
        )
    return RsvpResumePoint(tokenIndex = safeIndex, resumeCursor = resumeCursor)
}

internal fun exitAndSavePosition(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val book = context.state.book

    runtime.isExiting = true
    runtime.isPlaying = false
    runtime.completed = true

    val resumePoint = currentResumePoint(context)
    context.callbacks.playback.onPositionChanged(resumePoint)
    context.callbacks.playback.onExit(resumePoint)
}

internal fun advanceFrame(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    if (frames.isEmpty()) return

    if (runtime.frameIndex < frames.lastIndex) {
        runtime.frameIndex += 1
        return
    }
    completePlayback(context)
}

internal fun completePlayback(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val fallbackIndex = context.state.book.startIndex
    val completedFrame = frames.getOrNull(runtime.frameIndex)
    val rawNextIndex = ((completedFrame?.originalTokenIndex ?: fallbackIndex) + 1).coerceAtLeast(0)

    runtime.isPlaying = false
    runtime.completed = true
    runtime.currentResumeCursor = -1
    context.callbacks.playback.onFinished(
        RsvpResumePoint(
            tokenIndex = rawNextIndex,
            chapterIndex = context.state.book.chapterIndex,
        ),
    )
}
