@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.kairo.core.model.nearestWordIndex
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

@Composable
internal fun RsvpPositionSaveEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val book = context.state.book

    LaunchedEffect(runtime.frameIndex) {
        if (frames.isEmpty()) return@LaunchedEffect
        val currentIndex = resolveCurrentTokenIndex(frames, runtime.frameIndex, book.startIndex)
        runtime.currentTokenIndex = currentIndex
        val safeIndex = if (book.tokens.isNotEmpty()) {
            book.tokens.nearestWordIndex(currentIndex)
        } else {
            currentIndex
        }
        val now = SystemClock.elapsedRealtime()
        val shouldSave = now - runtime.lastPositionSaveMs >= POSITION_SAVE_INTERVAL_MS ||
            runtime.frameIndex == frames.lastIndex
        if (shouldSave) {
            runtime.lastPositionSaveMs = now
            context.callbacks.playback.onPositionChanged(safeIndex)
        }
    }
}

@Composable
internal fun RsvpFrameAlignmentEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames

    LaunchedEffect(frames) {
        if (frames.isEmpty()) return@LaunchedEffect
        runtime.frameIndex = alignFrameIndex(frames, runtime.currentTokenIndex)
    }
}

@Composable
internal fun RsvpSessionResetEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val book = context.state.book
    val profile = context.state.profile
    val textStyle = context.state.textStyle
    val layoutBias = context.state.layoutBias
    val frames = context.frameState.frames

    LaunchedEffect(book.tokens, book.startIndex) {
        runtime.currentTokenIndex = book.startIndex
        runtime.frameIndex = alignFrameIndex(frames, book.startIndex)
        runtime.isPlaying = true
        runtime.completed = false
        runtime.currentTempoMsPerWord = profile.config.tempoMsPerWord
        runtime.currentFontSizeSp = textStyle.fontSizeSp
        runtime.currentFontWeight = textStyle.fontWeight
        runtime.currentFontFamily = textStyle.fontFamily
        runtime.currentTextBrightness = textStyle.textBrightness
        runtime.currentVerticalBias = layoutBias.verticalBias
        runtime.currentHorizontalBias = layoutBias.horizontalBias
        runtime.dragStartBias = layoutBias.verticalBias
        runtime.dragStartHorizontalBias = layoutBias.horizontalBias
        runtime.isPositioningMode = false
        runtime.isAdjustingPosition = false
        runtime.showQuickSettings = false
        runtime.showControls = false
    }
}

@Composable
internal fun RsvpPlaybackLoopEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val tokens = context.state.book.tokens
    val tempoScale = context.timing.tempoScale

    LaunchedEffect(runtime.isPlaying, runtime.frameIndex, runtime.completed, frames, tempoScale) {
        if (!runtime.isPlaying || runtime.completed) return@LaunchedEffect
        if (runtime.frameIndex >= frames.size) return@LaunchedEffect
        val frame = frames[runtime.frameIndex]
        val scaledMs = (frame.durationMs * tempoScale)
            .roundToLong()
            .coerceAtLeast(MIN_FRAME_DELAY_MS)
        delay(scaledMs)
        if (runtime.frameIndex == frames.lastIndex) {
            runtime.completed = true
            val rawNextIndex = frame.originalTokenIndex + 1
            val safeNextIndex = if (tokens.isNotEmpty()) {
                tokens.nearestWordIndex(rawNextIndex)
            } else {
                rawNextIndex
            }
            context.callbacks.playback.onFinished(safeNextIndex)
        } else {
            runtime.frameIndex += 1
        }
    }
}

@Composable
internal fun RsvpAutoHideControlsEffect(runtime: RsvpRuntimeState) {
    LaunchedEffect(runtime.showControls, runtime.isPlaying) {
        if (runtime.showControls && runtime.isPlaying) {
            delay(CONTROLS_HIDE_DELAY_MS)
            runtime.showControls = false
        }
    }
}

@Composable
internal fun RsvpAutoHideTempoIndicatorEffect(runtime: RsvpRuntimeState) {
    LaunchedEffect(runtime.showTempoIndicator) {
        if (runtime.showTempoIndicator) {
            delay(TEMPO_INDICATOR_HIDE_DELAY_MS)
            runtime.showTempoIndicator = false
        }
    }
}

@Composable
internal fun RsvpAutoHideFontSizeIndicatorEffect(runtime: RsvpRuntimeState) {
    LaunchedEffect(runtime.showFontSizeIndicator) {
        if (runtime.showFontSizeIndicator) {
            delay(FONT_SIZE_INDICATOR_HIDE_DELAY_MS)
            runtime.showFontSizeIndicator = false
        }
    }
}
