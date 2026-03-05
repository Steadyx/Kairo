package com.example.kairo.ui.rsvp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.example.kairo.core.model.nearestWordIndex
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.rsvpGestureModifier(
    context: RsvpUiContext,
    interactionSource: MutableInteractionSource,
): Modifier {
    val runtime = context.runtime
    return this
        .pointerInput(context.state.profile.config.tempoMsPerWord, runtime.isPositioningMode) {
            detectDragGestures(
                onDragStart = { handleDragStart(context) },
                onDragEnd = { handleDragEnd(context) },
                onDrag = { change, dragAmount ->
                    handleDrag(context, dragAmount)
                    change.consume()
                },
            )
        }.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { handleTap(context) },
            onLongClick = { exitAndSavePosition(context) },
        )
}

internal fun enterPositioningMode(runtime: RsvpRuntimeState) {
    runtime.wasPlayingBeforePositioning = runtime.isPlaying
    runtime.isPlaying = false
    runtime.showControls = false
    runtime.showQuickSettings = false
    runtime.showTempoIndicator = false
    runtime.isPositioningMode = true
    runtime.isAdjustingPosition = true
}

internal fun finishPositioning(
    context: RsvpUiContext,
    resumeIfWasPlaying: Boolean,
) {
    val runtime = context.runtime
    if (!runtime.isPositioningMode) return

    runtime.isPositioningMode = false
    runtime.isAdjustingPosition = false
    context.callbacks.theme.onVerticalBiasChange(runtime.currentVerticalBias)
    context.callbacks.theme.onHorizontalBiasChange(runtime.currentHorizontalBias)
    if (resumeIfWasPlaying && runtime.wasPlayingBeforePositioning && !runtime.completed) {
        resumePlayback(runtime)
    }
}

internal fun addBookmarkNow(context: RsvpUiContext) {
    val runtime = context.runtime
    val book = context.state.book
    if (book.tokens.isEmpty()) return

    val currentIndex =
        resolveCurrentTokenIndex(
            context.frameState.frames,
            runtime.frameIndex,
            book.startIndex,
        )
    val safeIndex =
        book.tokens
            .nearestWordIndex(currentIndex)
            .coerceIn(0, book.tokens.lastIndex)
    val preview =
        book.tokens
            .getOrNull(safeIndex)
            ?.text
            .orEmpty()
    context.callbacks.bookmarks.onAddBookmark(safeIndex, preview)
    runtime.showQuickSettings = false
}

internal fun handleTap(context: RsvpUiContext) {
    val runtime = context.runtime
    if (runtime.isPositioningMode) {
        finishPositioning(context, resumeIfWasPlaying = true)
    } else if (runtime.showQuickSettings) {
        runtime.showQuickSettings = false
    } else if (!runtime.completed) {
        val willPlay = !runtime.isPlaying
        runtime.isPlaying = willPlay
        if (willPlay) {
            resumePlayback(runtime)
        }
        runtime.showTempoIndicator = false
        runtime.showFontSizeIndicator = false
        runtime.showControls = !willPlay
    }
}

private fun handleDragStart(context: RsvpUiContext) {
    val runtime = context.runtime
    runtime.dragAccumulator = ZERO_FLOAT
    runtime.dragAccumulatorX = ZERO_FLOAT
    runtime.dragAxis = RsvpDragAxis.NONE
    runtime.dragStartTempoMsPerWord = runtime.currentTempoMsPerWord
    runtime.dragStartFrameIndex = runtime.frameIndex
    runtime.dragStartBias = runtime.currentVerticalBias
    runtime.dragStartHorizontalBias = runtime.currentHorizontalBias
    runtime.wasPlayingBeforeScrub = runtime.isPlaying
    runtime.isScrubbing = false
}

private fun handleDrag(
    context: RsvpUiContext,
    dragAmount: Offset,
) {
    val runtime = context.runtime
    if (runtime.isPositioningMode) {
        val biasPerPx = POSITIONING_BIAS_PER_PX
        runtime.currentVerticalBias =
            (runtime.currentVerticalBias + dragAmount.y * biasPerPx).coerceIn(
                VERTICAL_BIAS_MIN,
                VERTICAL_BIAS_MAX,
            )
        runtime.currentHorizontalBias =
            (runtime.currentHorizontalBias + dragAmount.x * biasPerPx).coerceIn(
                HORIZONTAL_BIAS_MIN,
                HORIZONTAL_BIAS_MAX,
            )
        runtime.isAdjustingPosition = true
        return
    }

    runtime.dragAccumulator += dragAmount.y
    runtime.dragAccumulatorX += dragAmount.x

    if (runtime.dragAxis == RsvpDragAxis.NONE) {
        val absX = abs(runtime.dragAccumulatorX)
        val absY = abs(runtime.dragAccumulator)
        if (absX < DRAG_AXIS_LOCK_THRESHOLD_PX && absY < DRAG_AXIS_LOCK_THRESHOLD_PX) {
            return
        }
        runtime.dragAxis =
            if (absX > absY) {
                startScrubbing(context)
                RsvpDragAxis.HORIZONTAL
            } else {
                RsvpDragAxis.VERTICAL
            }
    }

    when (runtime.dragAxis) {
        RsvpDragAxis.HORIZONTAL -> handleSweep(context)
        RsvpDragAxis.VERTICAL -> handleTempoDrag(context)
        RsvpDragAxis.NONE -> Unit
    }
}

private fun handleDragEnd(context: RsvpUiContext) {
    val runtime = context.runtime
    if (runtime.isPositioningMode) {
        if (runtime.currentVerticalBias != runtime.dragStartBias) {
            context.callbacks.theme.onVerticalBiasChange(runtime.currentVerticalBias)
        }
        if (runtime.currentHorizontalBias != runtime.dragStartHorizontalBias) {
            context.callbacks.theme.onHorizontalBiasChange(runtime.currentHorizontalBias)
        }
        runtime.dragAxis = RsvpDragAxis.NONE
        runtime.dragAccumulator = ZERO_FLOAT
        runtime.dragAccumulatorX = ZERO_FLOAT
        return
    }

    when (runtime.dragAxis) {
        RsvpDragAxis.HORIZONTAL -> finishScrubbing(context)
        RsvpDragAxis.VERTICAL -> {
            if (runtime.currentTempoMsPerWord != runtime.dragStartTempoMsPerWord) {
                context.callbacks.playback.onTempoChange(runtime.currentTempoMsPerWord)
                runtime.showTempoIndicator = false
            }
        }
        RsvpDragAxis.NONE -> Unit
    }

    runtime.dragAxis = RsvpDragAxis.NONE
    runtime.dragAccumulator = ZERO_FLOAT
    runtime.dragAccumulatorX = ZERO_FLOAT
}

private fun handleTempoDrag(context: RsvpUiContext) {
    val runtime = context.runtime
    val tempoDeltaMs =
        (runtime.dragAccumulator / TEMPO_SWIPE_THRESHOLD_PX).toInt() * TEMPO_STEP_MS
    if (tempoDeltaMs == 0L) return

    val newTempo =
        (runtime.dragStartTempoMsPerWord + tempoDeltaMs)
            .coerceIn(context.timing.minTempoMs, context.timing.maxTempoMs)
    if (newTempo != runtime.currentTempoMsPerWord) {
        runtime.currentTempoMsPerWord = newTempo
        runtime.showTempoIndicator = true
        context.haptics.onTempoStep()
    }
}

private fun startScrubbing(context: RsvpUiContext) {
    val runtime = context.runtime
    runtime.wasPlayingBeforeScrub = runtime.isPlaying
    runtime.isPlaying = false
    runtime.isScrubbing = true
    runtime.completed = false
    runtime.dragStartFrameIndex = runtime.frameIndex
}

private fun finishScrubbing(context: RsvpUiContext) {
    val runtime = context.runtime
    if (!runtime.isScrubbing) return

    context.callbacks.playback.onPositionChanged(currentResumePoint(context))
    runtime.isScrubbing = false
    if (runtime.wasPlayingBeforeScrub && !runtime.completed) {
        resumePlayback(runtime)
    }
}

private fun handleSweep(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    if (frames.isEmpty()) return

    val step = (runtime.dragAccumulatorX / SWEEP_SWIPE_THRESHOLD_PX).toInt()
    if (step == 0) return

    val frameDelta = step * SWEEP_FRAME_STEP
    val targetIndex = (runtime.frameIndex + frameDelta).coerceIn(0, frames.lastIndex)
    if (targetIndex != runtime.frameIndex) {
        runtime.frameIndex = targetIndex
        runtime.completed = false
        context.haptics.onFrameStep()
    }
    runtime.dragAccumulatorX -= step * SWEEP_SWIPE_THRESHOLD_PX
}

internal fun resumePlayback(runtime: RsvpRuntimeState) {
    runtime.rampStartFrameIndex = runtime.frameIndex
    runtime.scheduledFrameIndex = -1
    runtime.nextFrameAtMs = 0L
    runtime.isPlaying = true
}
