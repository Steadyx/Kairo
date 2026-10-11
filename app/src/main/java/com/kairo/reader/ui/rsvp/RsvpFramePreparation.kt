package com.kairo.reader.ui.rsvp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.data.rsvp.RsvpFrameRepository
import com.kairo.reader.data.rsvp.RsvpFrameSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select

internal const val STARTUP_PREVIEW_TOKEN_COUNT = 64

internal suspend fun CoroutineScope.loadRsvpOpeningFrames(
    frameRepository: RsvpFrameRepository,
    book: RsvpBookContext,
    config: RsvpConfig,
    startIndex: Int,
    chapterFrames: Deferred<RsvpFrameSet?>,
): RsvpFrameSet? {
    if (chapterFrames.isCompleted) return null
    val previewConfig =
        if (book.tokens.size - startIndex <= STARTUP_PREVIEW_TOKEN_COUNT) {
            config
        } else {
            config.copy(endDelayMs = 0L, rampDownFrames = 0)
        }
    val openingFrames = async {
        runCatching {
            frameRepository.getPreviewFrames(
                tokens = book.tokens,
                startIndex = startIndex,
                config = previewConfig,
                maxTokenCount = STARTUP_PREVIEW_TOKEN_COUNT,
                options = book.generationOptions,
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }.getOrNull()
    }
    return select {
        chapterFrames.onAwait {
            openingFrames.cancel()
            null
        }
        openingFrames.onAwait { it }
    }
}

internal suspend fun awaitRsvpChapterHandoff(
    chapterFrames: RsvpFrameSet?,
    preview: RsvpFrameSet?,
    runtime: RsvpRuntimeState,
    book: RsvpBookContext,
    config: RsvpConfig,
    frameRepository: RsvpFrameRepository,
    publishChapter: (RsvpFrameSet) -> Unit = {},
): RsvpFrameSet? {
    if (chapterFrames == null || preview == null) return chapterFrames
    var activated by mutableStateOf(false)
    runtime.activatePreparedChapter = {
        if (!activated) {
            val scrubStart = preview.frames.getOrNull(runtime.dragStartFrameIndex).takeIf { runtime.isScrubbing }
            syncRuntimePositionFromVisibleFrame(runtime, preview.frames, book)
            alignRuntimeToFrameSet(runtime, chapterFrames, book)
            if (scrubStart != null) {
                runtime.dragStartFrameIndex = alignFrameIndex(
                    chapterFrames.frames,
                    scrubStart.originalTokenIndex,
                    scrubStart.resumeCursor,
                    chapterFrames.frameIndexMap,
                )
            }
            publishChapter(chapterFrames)
            activated = true
        }
        chapterFrames
    }
    try {
        // Navigation publishes synchronously; automatic handoff waits for a safe playback boundary.
        snapshotFlow {
            activated ||
                (
                    !runtime.isScrubbing &&
                        !runtime.isAdjustingPosition &&
                        (!runtime.isPlaying || hasConsumedLoadedFrames(runtime, preview.frames))
                    )
        }.first { it }
        if (activated || runtime.completed) return chapterFrames
        val continuingPlayback = hasConsumedLoadedFrames(runtime, preview.frames)
        if (!continuingPlayback) syncRuntimePositionFromVisibleFrame(runtime, preview.frames, book)
        val prepared = runCatching {
            frameRepository.getSeekableFrames(
                book.bookId,
                book.chapterIndex,
                if (continuingPlayback) config.copy(startDelayMs = 0L, rampUpFrames = 0) else config,
                startIndex = runtime.currentTokenIndex,
                options = book.generationOptions,
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }.getOrNull()
        return if (activated) chapterFrames else prepared
    } finally {
        runtime.activatePreparedChapter = null
    }
}

internal fun prepareRsvpNavigation(context: RsvpUiContext): RsvpUiContext {
    val chapter = context.runtime.activatePreparedChapter?.invoke() ?: return context
    return context.copy(
        frameState = context.frameState.copy(
            frames = chapter.frames,
            baseTempoMs = chapter.baseTempoMs,
            isLoading = false,
            isComplete = true,
            frameIndexMap = chapter.frameIndexMap,
            initialRampStartFrameIndex = chapter.initialRampStartFrameIndex,
        ),
    )
}
