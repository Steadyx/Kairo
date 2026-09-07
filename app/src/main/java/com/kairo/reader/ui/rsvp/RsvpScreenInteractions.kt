@file:Suppress("TooManyFunctions")

package com.kairo.reader.ui.rsvp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.nearestWordIndex
import com.kairo.reader.core.rsvp.RsvpSpeedControl
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.rsvpGestureModifier(
    context: RsvpUiContext,
    interactionSource: MutableInteractionSource,
): Modifier {
    val runtime = context.runtime
    val dragEnabled =
        shouldHandleGlobalRsvpDrag(
            showQuickSettings = runtime.showQuickSettings,
            isPositioningMode = runtime.isPositioningMode,
        )
    val dragModifier =
        if (dragEnabled) {
            pointerInput(
                context.state.profile.config.tempoMsPerWord,
                runtime.isPositioningMode,
                runtime.showQuickSettings,
            ) {
                detectDragGestures(
                    onDragStart = { handleDragStart(context) },
                    onDragEnd = { handleDragEnd(context) },
                    // A cancelled drag must release scrub/tempo state like a normal end,
                    // otherwise playback stays paused with isScrubbing stuck on.
                    onDragCancel = { handleDragEnd(context) },
                    onDrag = { change, dragAmount ->
                        handleDrag(context, dragAmount)
                        change.consume()
                    },
                )
            }
        } else {
            this
        }
    return dragModifier.combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = { handleTap(context) },
        onDoubleClick = {
            if (!runtime.showQuickSettings && !runtime.isPositioningMode) {
                replayPreviousPhrase(context)
            }
        },
        onLongClick = {
            // Long-press exits both while playing and while paused (controls visible) — pausing
            // sets showControls, so gating on it made the exit gesture dead exactly when paused.
            // Positioning mode keeps its own tap-to-confirm flow.
            if (!runtime.showQuickSettings && !runtime.isPositioningMode) {
                exitAndSavePosition(context)
            }
        },
    )
}

internal fun shouldHandleGlobalRsvpDrag(
    showQuickSettings: Boolean,
    isPositioningMode: Boolean,
): Boolean =
    isPositioningMode || !showQuickSettings

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

    val shouldResume =
        resumeIfWasPlaying && runtime.wasPlayingBeforePositioning && !runtime.completed
    if (shouldResume) {
        runtime.showControls = false
        resumePlayback(runtime)
    }
    runtime.isPositioningMode = false
    runtime.isAdjustingPosition = false
    context.callbacks.theme.onVerticalBiasChange(runtime.currentVerticalBias)
    context.callbacks.theme.onHorizontalBiasChange(runtime.currentHorizontalBias)
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

internal fun replayPreviousPhrase(context: RsvpUiContext) {
    val runtime = context.runtime
    val tokens = context.state.book.tokens
    val frames = context.frameState.frames
    if (tokens.isEmpty() || frames.isEmpty()) return

    val currentTokenIndex =
        resolveCurrentTokenIndex(
            frames = frames,
            frameIndex = runtime.frameIndex,
            fallbackIndex = context.state.book.startIndex,
        )
    val replayTokenIndex = findReplayPhraseStartTokenIndex(tokens, currentTokenIndex)
    val targetFrameIndex = findPlannedReplayFrameIndex(frames, runtime.frameIndex)
        ?: alignFrameIndex(
            frames = frames,
            tokenIndex = replayTokenIndex,
            frameIndexMap = context.frameState.frameIndexMap,
        )
    if (targetFrameIndex >= runtime.frameIndex && runtime.frameIndex > 0) {
        runtime.frameIndex = (runtime.frameIndex - 1).coerceAtLeast(0)
    } else {
        runtime.frameIndex = targetFrameIndex
    }
    runtime.completed = false
    runtime.rampStartFrameIndex = runtime.frameIndex
    runtime.resumePreparationScale = 1.0
    runtime.replayPreparationPending = !runtime.isPlaying
    runtime.scheduledFrameIndex = -1
    runtime.nextFrameAtMs = 0L
    registerRsvpRegression(runtime, context.state.profile.config.useRegressionAdaptivePacing)
    context.callbacks.playback.onPositionChanged(currentResumePoint(context))
    context.haptics.onFrameStep()
}

/** At a phrase entrance replay the preceding thought; inside it replay its first loaded word. */
internal fun findPlannedReplayFrameIndex(frames: List<RsvpFrame>, frameIndex: Int): Int? {
    val current = frames.getOrNull(frameIndex) ?: return null
    val phraseStart = current.phraseStartTokenIndex ?: return null
    var target = frameIndex
    while (target > 0 && frames[target - 1].phraseStartTokenIndex == phraseStart) target--
    if (target < frameIndex) return target
    var previous = frameIndex - 1
    while (previous >= 0 && frames[previous].phraseStartTokenIndex == null) previous--
    if (previous < 0) return target
    val previousStart = frames[previous].phraseStartTokenIndex
    while (previous > 0 && frames[previous - 1].phraseStartTokenIndex == previousStart) previous--
    return previous
}

internal fun findReplayPhraseStartTokenIndex(
    tokens: List<Token>,
    currentTokenIndex: Int,
): Int {
    if (tokens.isEmpty()) return 0
    val currentWord = findWordAtOrBefore(tokens, currentTokenIndex.coerceIn(0, tokens.lastIndex))
    if (currentWord < 0) return 0
    val currentPhraseStart = findPhraseStart(tokens, currentWord)
    if (currentPhraseStart < currentWord) return currentPhraseStart

    val previousWord = findWordAtOrBefore(tokens, currentPhraseStart - 1)
    return if (previousWord >= 0) findPhraseStart(tokens, previousWord) else currentPhraseStart
}

private fun findPhraseStart(tokens: List<Token>, wordIndex: Int): Int {
    if (tokens[wordIndex].isClauseBoundary) return wordIndex
    var earliestWord = wordIndex
    var wordsSeen = 1
    for (index in wordIndex - 1 downTo 0) {
        val token = tokens[index]
        if (token.isReplayPunctuationBoundary()) break
        if (token.type == TokenType.WORD) {
            earliestWord = index
            wordsSeen += 1
            if (token.isClauseBoundary) break
            if (wordsSeen >= REPLAY_PHRASE_MAX_WORDS) break
        }
    }
    return earliestWord
}

private fun findWordAtOrBefore(tokens: List<Token>, startIndex: Int): Int {
    for (index in startIndex.coerceAtMost(tokens.lastIndex) downTo 0) {
        if (tokens[index].type == TokenType.WORD) return index
    }
    return -1
}

private fun Token.isReplayPunctuationBoundary(): Boolean =
    type == TokenType.PARAGRAPH_BREAK ||
        type == TokenType.PAGE_BREAK ||
        (type == TokenType.PUNCTUATION && text.any { it in REPLAY_BOUNDARY_PUNCTUATION })

internal fun registerRsvpRegression(
    runtime: RsvpRuntimeState,
    enabled: Boolean,
) {
    if (!enabled) return
    runtime.comprehensionPaceScale =
        (runtime.comprehensionPaceScale + REGRESSION_PACE_STEP)
            .coerceAtMost(REGRESSION_PACE_MAX_SCALE)
    runtime.stablePhrasesSinceRegression = 0
}

internal fun recoverRsvpRegressionPace(
    runtime: RsvpRuntimeState,
    enabled: Boolean,
    endsPhrase: Boolean,
) {
    if (!enabled) {
        runtime.comprehensionPaceScale = 1f
        runtime.stablePhrasesSinceRegression = 0
        return
    }
    if (runtime.comprehensionPaceScale <= 1f) return
    if (!endsPhrase) return
    runtime.stablePhrasesSinceRegression += 1
    if (runtime.stablePhrasesSinceRegression <= REGRESSION_RECOVERY_START_PHRASES) return
    runtime.comprehensionPaceScale =
        (runtime.comprehensionPaceScale - REGRESSION_RECOVERY_STEP).coerceAtLeast(1f)
}

internal fun handleTap(context: RsvpUiContext) {
    val runtime = context.runtime
    if (runtime.isPositioningMode) {
        finishPositioning(context, resumeIfWasPlaying = true)
    } else if (runtime.showQuickSettings) {
        runtime.showQuickSettings = false
    } else if (!runtime.completed) {
        val willPlay = !runtime.isPlaying
        if (willPlay) {
            runtime.showControls = false
            resumePlayback(runtime)
        } else {
            runtime.isPlaying = false
            runtime.showControls = true
        }
        runtime.showTempoIndicator = false
        runtime.showFontSizeIndicator = false
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
    val snapRadius = positioningSnapRadius(context.state.uiPrefs)
    runtime.positioningSnapLineV = snappedGridLineIndex(runtime.currentVerticalBias, snapRadius)
    runtime.positioningSnapLineH = snappedGridLineIndex(runtime.currentHorizontalBias, snapRadius)
}

private fun handleDrag(
    context: RsvpUiContext,
    dragAmount: Offset,
) {
    val runtime = context.runtime
    if (runtime.isPositioningMode) {
        handlePositioningDrag(context, dragAmount)
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

/**
 * Positioning-mode drag: the position is derived from the drag-start bias plus the raw
 * accumulated drag (not the possibly-snapped current bias), so grid snapping never traps the
 * gesture — the finger keeps authority and the position releases from a line as soon as the
 * raw drag moves outside the snap radius.
 */
private fun handlePositioningDrag(
    context: RsvpUiContext,
    dragAmount: Offset,
) {
    val runtime = context.runtime
    runtime.dragAccumulator += dragAmount.y
    runtime.dragAccumulatorX += dragAmount.x

    val rawVertical =
        (runtime.dragStartBias + (runtime.dragAccumulator * POSITIONING_BIAS_PER_PX))
            .coerceIn(VERTICAL_BIAS_MIN, VERTICAL_BIAS_MAX)
    val rawHorizontal =
        (runtime.dragStartHorizontalBias + (runtime.dragAccumulatorX * POSITIONING_BIAS_PER_PX))
            .coerceIn(HORIZONTAL_BIAS_MIN, HORIZONTAL_BIAS_MAX)

    val snapRadius = positioningSnapRadius(context.state.uiPrefs)
    val lineV = snappedGridLineIndex(rawVertical, snapRadius)
    val lineH = snappedGridLineIndex(rawHorizontal, snapRadius)
    val landedOnNewLine =
        (lineV != POSITIONING_GRID_LINE_NONE && lineV != runtime.positioningSnapLineV) ||
            (lineH != POSITIONING_GRID_LINE_NONE && lineH != runtime.positioningSnapLineH)
    if (landedOnNewLine) {
        context.haptics.onFrameStep()
    }
    runtime.positioningSnapLineV = lineV
    runtime.positioningSnapLineH = lineH

    runtime.currentVerticalBias = snapBiasToGrid(rawVertical, snapRadius)
    runtime.currentHorizontalBias = snapBiasToGrid(rawHorizontal, snapRadius)
    runtime.isAdjustingPosition = true
}

/** Capture radius (in bias units) around each grid line; 0 disables snapping entirely. */
internal fun positioningSnapRadius(uiPrefs: RsvpUiPreferences): Float =
    if (uiPrefs.positioningGridEnabled) {
        uiPrefs.positioningGridSnap.coerceIn(0f, 1f) * (POSITIONING_GRID_SPACING_BIAS / 2f)
    } else {
        0f
    }

/** Index of the grid line [bias] is captured by, or [POSITIONING_GRID_LINE_NONE]. */
internal fun snappedGridLineIndex(
    bias: Float,
    snapRadius: Float,
): Int {
    if (snapRadius <= 0f) return POSITIONING_GRID_LINE_NONE
    val index = (bias / POSITIONING_GRID_SPACING_BIAS).roundToInt()
    return if (abs(bias - (index * POSITIONING_GRID_SPACING_BIAS)) <= snapRadius) {
        index
    } else {
        POSITIONING_GRID_LINE_NONE
    }
}

internal fun snapBiasToGrid(
    bias: Float,
    snapRadius: Float,
): Float {
    val index = snappedGridLineIndex(bias, snapRadius)
    return if (index == POSITIONING_GRID_LINE_NONE) {
        bias
    } else {
        index * POSITIONING_GRID_SPACING_BIAS
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
                // Leave the indicator visible so the final speed can be read; the auto-hide
                // timer fades it out shortly after the last adjustment.
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
    val speedDelta =
        (runtime.dragAccumulator / TEMPO_SWIPE_THRESHOLD_PX).toInt() * SPEED_STEP_PERCENT
    if (speedDelta == ZERO_FLOAT) return

    val dragStartSpeed =
        RsvpSpeedControl.speedForTempoMs(
            tempoMsPerWord = runtime.dragStartTempoMsPerWord,
            minTempoMsPerWord = context.timing.minTempoMs,
            maxTempoMsPerWord = context.timing.maxTempoMs,
        )
    val newSpeed =
        (dragStartSpeed - speedDelta)
            .coerceIn(RsvpSpeedControl.MIN_SPEED, RsvpSpeedControl.MAX_SPEED)

    val newTempo =
        RsvpSpeedControl.tempoForSpeed(
            speed = newSpeed,
            minTempoMsPerWord = context.timing.minTempoMs,
            maxTempoMsPerWord = context.timing.maxTempoMs,
        )
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

    if (runtime.frameIndex < runtime.dragStartFrameIndex) {
        registerRsvpRegression(runtime, context.state.profile.config.useRegressionAdaptivePacing)
    }
    context.callbacks.playback.onPositionChanged(currentResumePoint(context))
    val shouldResume = runtime.wasPlayingBeforeScrub && !runtime.completed
    if (shouldResume) {
        resumePlayback(runtime)
    }
    runtime.isScrubbing = false
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
    if (runtime.replayPreparationPending) {
        runtime.resumePreparationScale = 1.0
        runtime.replayPreparationPending = false
    }
}

private val REPLAY_BOUNDARY_PUNCTUATION =
    setOf('.', ',', ';', ':', '!', '?', '\u2026', '\u2014', '\u2013')
