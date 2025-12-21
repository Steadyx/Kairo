package com.example.kairo.ui.rsvp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.example.kairo.core.model.nearestWordIndex

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.rsvpGestureModifier(
    context: RsvpUiContext,
    interactionSource: MutableInteractionSource
): Modifier {
    val runtime = context.runtime
    return this
        .pointerInput(context.state.profile.config.tempoMsPerWord, runtime.isPositioningMode) {
            detectDragGestures(
                onDragStart = { handleDragStart(runtime) },
                onDragEnd = { handleDragEnd(context) },
                onDrag = { change, dragAmount ->
                    handleDrag(context, dragAmount)
                    change.consume()
                }
            )
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { handleTap(context) },
            onLongClick = { exitAndSavePosition(context) }
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

internal fun finishPositioning(context: RsvpUiContext, resumeIfWasPlaying: Boolean) {
    val runtime = context.runtime
    if (!runtime.isPositioningMode) return

    runtime.isPositioningMode = false
    runtime.isAdjustingPosition = false
    context.callbacks.theme.onVerticalBiasChange(runtime.currentVerticalBias)
    if (resumeIfWasPlaying && runtime.wasPlayingBeforePositioning && !runtime.completed) {
        runtime.isPlaying = true
    }
}

internal fun addBookmarkNow(context: RsvpUiContext) {
    val runtime = context.runtime
    val book = context.state.book
    if (book.tokens.isEmpty()) return

    val currentIndex = resolveCurrentTokenIndex(
        context.frameState.frames,
        runtime.frameIndex,
        book.startIndex
    )
    val safeIndex = book.tokens.nearestWordIndex(currentIndex)
        .coerceIn(0, book.tokens.lastIndex)
    val preview = book.tokens.getOrNull(safeIndex)?.text.orEmpty()
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
        runtime.isPlaying = !runtime.isPlaying
        runtime.showTempoIndicator = false
        runtime.showFontSizeIndicator = false
        runtime.showControls = !runtime.isPlaying
    }
}

private fun handleDragStart(runtime: RsvpRuntimeState) {
    runtime.dragAccumulator = ZERO_FLOAT
    runtime.dragStartTempoMsPerWord = runtime.currentTempoMsPerWord
    runtime.dragStartBias = runtime.currentVerticalBias
    runtime.dragStartHorizontalBias = runtime.currentHorizontalBias
}

private fun handleDrag(context: RsvpUiContext, dragAmount: Offset) {
    val runtime = context.runtime
    if (runtime.isPositioningMode) {
        val biasPerPx = POSITIONING_BIAS_PER_PX
        runtime.currentVerticalBias =
            (runtime.currentVerticalBias + dragAmount.y * biasPerPx).coerceIn(
                VERTICAL_BIAS_MIN,
                VERTICAL_BIAS_MAX
            )
        runtime.currentHorizontalBias =
            (runtime.currentHorizontalBias + dragAmount.x * biasPerPx).coerceIn(
                HORIZONTAL_BIAS_MIN,
                HORIZONTAL_BIAS_MAX
            )
        runtime.isAdjustingPosition = true
    } else {
        runtime.dragAccumulator += dragAmount.y
        val tempoDeltaMs =
            (runtime.dragAccumulator / TEMPO_SWIPE_THRESHOLD_PX).toInt() * TEMPO_STEP_MS
        if (tempoDeltaMs != 0L) {
            val newTempo =
                (runtime.dragStartTempoMsPerWord + tempoDeltaMs)
                    .coerceIn(context.timing.minTempoMs, context.timing.maxTempoMs)
            if (newTempo != runtime.currentTempoMsPerWord) {
                runtime.currentTempoMsPerWord = newTempo
                runtime.showTempoIndicator = true
            }
        }
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
    } else if (runtime.currentTempoMsPerWord != runtime.dragStartTempoMsPerWord) {
        context.callbacks.playback.onTempoChange(runtime.currentTempoMsPerWord)
        runtime.showTempoIndicator = false
    }
    runtime.dragAccumulator = ZERO_FLOAT
}
