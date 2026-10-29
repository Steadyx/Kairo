package com.kairo.reader.ui.rsvp

import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.nearestWordIndex
import com.kairo.reader.data.rsvp.RsvpFrameIndexMap
import com.kairo.reader.data.rsvp.RsvpFrameSet

internal fun alignRuntimeToFrameSet(
    runtime: RsvpRuntimeState,
    frameSet: RsvpFrameSet,
    book: RsvpBookContext,
) {
    if (frameSet.frames.isEmpty()) return
    runtime.frameIndex =
        alignFrameIndex(
            frames = frameSet.frames,
            tokenIndex = runtime.currentTokenIndex,
            resumeCursor = runtime.currentResumeCursor,
            frameIndexMap = frameSet.frameIndexMap,
        )
    runtime.rampStartFrameIndex = runtime.frameIndex
    syncRuntimePositionFromVisibleFrame(runtime, frameSet.frames, book)
}

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
    frameIndexMap: RsvpFrameIndexMap = RsvpFrameIndexMap.from(frames),
): Int =
    frameIndexMap.alignFrameIndex(
        tokenIndex = tokenIndex,
        resumeCursor = resumeCursor,
        frameCount = frames.size,
    )

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
    return RsvpResumePoint(
        tokenIndex = safeIndex,
        resumeCursor = resumeCursor,
        tempoMsPerWord = runtime.currentTempoMsPerWord,
    )
}

internal fun exitAndSavePosition(context: RsvpUiContext) {
    val runtime = context.runtime
    if (runtime.completed) return
    val frames = context.frameState.frames
    val book = context.state.book

    runtime.isExiting = true
    runtime.isPlaying = false

    val resumePoint = currentResumePoint(context)
    context.callbacks.playback.onPositionChanged(resumePoint)
    context.callbacks.playback.onExit(resumePoint)
}

internal fun openLibraryAndSavePosition(context: RsvpUiContext) {
    val runtime = context.runtime
    if (runtime.completed) return

    runtime.isExiting = true
    runtime.isPlaying = false

    val resumePoint = currentResumePoint(context)
    context.callbacks.playback.onPositionChanged(resumePoint)
    context.callbacks.playback.onOpenLibrary(resumePoint)
}

internal fun advanceFrame(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    if (frames.isEmpty()) return

    if (runtime.frameIndex < frames.lastIndex) {
        runtime.frameIndex += 1
        return
    }
    if (shouldCompleteAtLoadedFrameBoundary(context)) {
        completePlayback(context)
    } else {
        holdAtLoadingFrameBoundary(context)
    }
}

internal fun completePlayback(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val fallbackIndex = context.state.book.startIndex
    val completedFrame = frames.getOrNull(runtime.frameIndex)
    val rawNextIndex = (completedFrame?.nextOriginalTokenIndex ?: (fallbackIndex + 1)).coerceAtLeast(0)

    runtime.isPlaying = false
    runtime.completed = true
    runtime.currentResumeCursor = -1
    context.callbacks.playback.onFinished(
        RsvpResumePoint(
            tokenIndex = rawNextIndex,
            chapterIndex = context.state.book.chapterIndex,
            tempoMsPerWord = runtime.currentTempoMsPerWord,
        ),
    )
}
