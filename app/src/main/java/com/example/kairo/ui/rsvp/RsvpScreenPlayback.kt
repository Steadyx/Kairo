package com.example.kairo.ui.rsvp

import com.example.kairo.core.model.RsvpFrame
import com.example.kairo.core.model.nearestWordIndex

internal fun resolveCurrentTokenIndex(
    frames: List<RsvpFrame>,
    frameIndex: Int,
    fallbackIndex: Int,
): Int = frames.getOrNull(frameIndex)?.originalTokenIndex ?: fallbackIndex

internal fun alignFrameIndex(
    frames: List<RsvpFrame>,
    tokenIndex: Int,
): Int {
    if (frames.isEmpty()) return 0
    val idx = frames.indexOfLast { it.originalTokenIndex <= tokenIndex }
    val safeIdx = if (idx == -1) 0 else idx
    return safeIdx.coerceIn(0, frames.lastIndex)
}

internal fun exitAndSavePosition(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val book = context.state.book

    runtime.isPlaying = false
    runtime.completed = true

    val currentIndex = resolveCurrentTokenIndex(frames, runtime.frameIndex, book.startIndex)
    val safeIndex =
        if (book.tokens.isNotEmpty()) {
            book.tokens.nearestWordIndex(currentIndex)
        } else {
            currentIndex
        }
    context.callbacks.playback.onPositionChanged(safeIndex)
    context.callbacks.playback.onExit(safeIndex)
}

internal fun advanceFrame(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    if (frames.isEmpty()) return

    if (runtime.frameIndex < frames.lastIndex) {
        runtime.frameIndex += 1
        return
    }
    val lastFrame = frames.getOrNull(runtime.frameIndex)
    val rawNextIndex = (lastFrame?.originalTokenIndex ?: context.state.book.startIndex) + 1
    val safeNextIndex =
        if (context.state.book.tokens
                .isNotEmpty()
        ) {
            context.state.book.tokens
                .nearestWordIndex(rawNextIndex)
        } else {
            rawNextIndex
        }
    context.callbacks.playback.onFinished(safeNextIndex)
    runtime.completed = true
}
