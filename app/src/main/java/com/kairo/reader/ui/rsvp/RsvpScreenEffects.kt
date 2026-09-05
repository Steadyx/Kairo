@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.rsvp

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.rsvp.frameFloorMs
import com.kairo.reader.core.rsvp.shouldSkipBlinkFrame
import com.kairo.reader.core.rsvp.timing.RsvpSessionTimingPolicy
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

@Composable
internal fun RsvpPositionSaveEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val book = context.state.book
    val positionSyncGate = remember { RsvpPositionSyncGate() }

    LaunchedEffect(
        runtime.frameIndex,
        context.frameState.isLoading,
        context.frameState.isComplete,
        frames,
    ) {
        // A replacement frame list must align to the saved source position before it can
        // update that position. Its old numeric frame index belongs to a different list.
        if (!positionSyncGate.shouldSync(context.frameState)) return@LaunchedEffect
        syncRuntimePositionFromVisibleFrame(runtime, frames, book)
        val now = SystemClock.elapsedRealtime()
        val shouldSave =
            now - runtime.lastPositionSaveMs >= POSITION_SAVE_INTERVAL_MS ||
                runtime.frameIndex == frames.lastIndex
        if (shouldSave) {
            runtime.lastPositionSaveMs = now
            context.callbacks.playback.onPositionChanged(currentResumePoint(context))
        }
    }
}

@Composable
internal fun RsvpLifecyclePositionSaveEffect(context: RsvpUiContext) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestContext by rememberUpdatedState(context)

    DisposableEffect(lifecycleOwner) {
        fun saveLatestVisiblePosition() {
            val current = latestContext
            if (current.runtime.isExiting || current.frameState.frames.isEmpty()) return
            current.callbacks.playback.onPlaybackStateChanged(current.runtime.isPlaying)
            current.callbacks.playback.onPositionChanged(currentResumePoint(current))
        }

        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    saveLatestVisiblePosition()
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            saveLatestVisiblePosition()
        }
    }
}

@Composable
internal fun RsvpFrameAlignmentEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val book = context.state.book

    LaunchedEffect(frames, context.frameState.isLoading, context.frameState.isComplete) {
        if (!shouldSyncPositionFromFrameState(context.frameState)) return@LaunchedEffect
        runtime.frameIndex =
            alignFrameIndex(
                frames = frames,
                tokenIndex = runtime.currentTokenIndex,
                resumeCursor = runtime.currentResumeCursor,
                frameIndexMap = context.frameState.frameIndexMap,
            )
        syncRuntimePositionFromVisibleFrame(runtime, frames, book)
    }
}

internal fun shouldSyncPositionFromFrameState(frameState: RsvpFrameLoadState): Boolean =
    frameState.frames.isNotEmpty() && !frameState.isLoading && frameState.isComplete

internal fun syncRuntimePositionFromVisibleFrame(
    runtime: RsvpRuntimeState,
    frames: List<RsvpFrame>,
    book: RsvpBookContext,
) {
    if (frames.isEmpty()) return
    val safeFrameIndex = runtime.frameIndex.coerceIn(0, frames.lastIndex)
    if (safeFrameIndex != runtime.frameIndex) {
        runtime.frameIndex = safeFrameIndex
    }
    runtime.currentTokenIndex =
        resolveCurrentTokenIndex(
            frames = frames,
            frameIndex = safeFrameIndex,
            fallbackIndex = book.startIndex,
        )
    runtime.currentResumeCursor =
        resolveCurrentResumeCursor(
            frames = frames,
            frameIndex = safeFrameIndex,
            fallbackCursor = book.startResumeCursor.takeIf { it >= 0 } ?: -1,
        )
}

@Composable
internal fun RsvpFrameLoadFailureEffect(context: RsvpUiContext) {
    val runtime = context.runtime

    LaunchedEffect(context.frameState.loadFailed) {
        if (!context.frameState.loadFailed) return@LaunchedEffect
        runtime.isPlaying = false
        runtime.scheduledFrameIndex = -1
        runtime.nextFrameAtMs = 0L
        runtime.showControls = true
    }
}

internal class RsvpPositionSyncGate {
    private var lastReadyFrames: List<RsvpFrame>? = null

    fun shouldSync(frameState: RsvpFrameLoadState): Boolean {
        if (!shouldSyncPositionFromFrameState(frameState)) return false
        if (lastReadyFrames !== frameState.frames) {
            lastReadyFrames = frameState.frames
            return false
        }
        return true
    }
}

@Composable
internal fun RsvpSessionResetEffect(
    context: RsvpUiContext,
    sessionKey: String,
    autoPlay: Boolean,
) {
    val runtime = context.runtime
    val book = context.state.book
    val profile = context.state.profile
    val textStyle = context.state.textStyle
    val layoutBias = context.state.layoutBias
    val frames = context.frameState.frames

    var lastSessionKey by rememberSaveable { mutableStateOf(sessionKey) }

    LaunchedEffect(sessionKey) {
        if (lastSessionKey == sessionKey) return@LaunchedEffect
        lastSessionKey = sessionKey
        runtime.currentTokenIndex = book.startIndex
        runtime.currentResumeCursor = book.startResumeCursor.takeIf { it >= 0 } ?: -1
        runtime.frameIndex =
            alignFrameIndex(
                frames = frames,
                tokenIndex = book.startIndex,
                resumeCursor = runtime.currentResumeCursor,
                frameIndexMap = context.frameState.frameIndexMap,
            )
        runtime.rampStartFrameIndex = runtime.frameIndex
        runtime.scheduledFrameIndex = -1
        runtime.nextFrameAtMs = 0L
        runtime.isPlaying = autoPlay
        runtime.completed = false
        runtime.currentTempoMsPerWord =
            context.state.launchTempoMsPerWord ?: profile.config.tempoMsPerWord
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
        runtime.isScrubbing = false
        runtime.isExiting = false
        runtime.comprehensionPaceScale = 1f
        runtime.stableFramesSinceRegression = 0
        runtime.dragAxis = RsvpDragAxis.NONE
        runtime.dragAccumulator = ZERO_FLOAT
        runtime.dragAccumulatorX = ZERO_FLOAT
        runtime.showQuickSettings = false
        runtime.showControls = false
    }
}

@Composable
internal fun RsvpPlaybackLoopEffect(
    context: RsvpUiContext,
    enabled: Boolean,
) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val latestTempoScale by rememberUpdatedState(context.timing.tempoScale)
    val latestBaseTempoMs by rememberUpdatedState(context.frameState.baseTempoMs)
    val latestConfig by rememberUpdatedState(context.state.profile.config)

    LaunchedEffect(enabled, runtime.isPlaying, runtime.frameIndex, runtime.completed, frames) {
        if (!enabled) return@LaunchedEffect
        if (!runtime.isPlaying || runtime.completed) return@LaunchedEffect
        if (runtime.frameIndex >= frames.size) return@LaunchedEffect
        val frame = frames[runtime.frameIndex]
        val tempoScale = latestTempoScale
        val config = latestConfig
        val effectiveTempoMs =
            effectivePlaybackTempoMs(
                baseTempoMs = latestBaseTempoMs,
                tempoScale = tempoScale,
            )
        if (shouldSkipBlinkFrame(frame, config, effectiveTempoMs, tempoScale)) {
            if (runtime.frameIndex >= frames.lastIndex) {
                if (shouldCompleteAtLoadedFrameBoundary(context)) {
                    completePlayback(context)
                } else {
                    holdAtLoadingFrameBoundary(context)
                }
            } else {
                runtime.frameIndex += 1
            }
            return@LaunchedEffect
        }
        val rampMultiplier =
            RsvpSessionTimingPolicy.resumeRampMultiplier(
                config,
                runtime.frameIndex,
                runtime.rampStartFrameIndex,
            )
        val resumeDelayMs =
            RsvpSessionTimingPolicy.resumeDelayMs(
                config,
                runtime.frameIndex,
                runtime.rampStartFrameIndex,
            )
        val frameMs =
            (frame.durationMs * rampMultiplier)
                .roundToLong()
                .coerceAtLeast(MIN_FRAME_DELAY_MS)
        var scaledMs =
            ((frameMs + resumeDelayMs) * tempoScale * runtime.comprehensionPaceScale)
                .roundToLong()
                .coerceAtLeast(MIN_FRAME_DELAY_MS)
        val floorMs = frameFloorMs(frame, config, effectiveTempoMs)
        if (scaledMs < floorMs) {
            scaledMs = floorMs
        }
        val now = SystemClock.elapsedRealtime()
        val chained = runtime.scheduledFrameIndex == runtime.frameIndex - 1
        val overshootMs =
            if (chained && runtime.nextFrameAtMs > 0L) {
                (now - runtime.nextFrameAtMs).coerceAtLeast(0L)
            } else {
                0L
            }
        val candidateTarget =
            if (chained && runtime.nextFrameAtMs > 0L) {
                runtime.nextFrameAtMs + scaledMs
            } else {
                now + scaledMs
            }
        val softenedCatchUp =
            if (overshootMs > 0L) {
                (overshootMs * (1.0 - CATCH_UP_FACTOR)).roundToLong()
            } else {
                0L
            }
        val targetMs =
            if (candidateTarget < now) {
                now + scaledMs
            } else {
                candidateTarget + softenedCatchUp
            }
        runtime.scheduledFrameIndex = runtime.frameIndex
        runtime.nextFrameAtMs = targetMs
        val delayMs = (targetMs - now).coerceAtLeast(MIN_FRAME_DELAY_MS)
        delay(delayMs)
        context.callbacks.playback.onFrameConsumed(frame)
        if (runtime.frameIndex == frames.lastIndex) {
            if (shouldCompleteAtLoadedFrameBoundary(context)) {
                completePlayback(context)
            } else {
                holdAtLoadingFrameBoundary(context)
            }
        } else {
            recoverRsvpRegressionPace(
                runtime = runtime,
                enabled = config.useRegressionAdaptivePacing,
            )
            runtime.frameIndex += 1
        }
    }
}

internal fun shouldCompleteAtLoadedFrameBoundary(context: RsvpUiContext): Boolean =
    context.frameState.frames.isNotEmpty() &&
        context.runtime.frameIndex >= context.frameState.frames.lastIndex &&
        !context.frameState.isLoading &&
        context.frameState.isComplete

internal fun effectivePlaybackTempoMs(
    baseTempoMs: Long,
    tempoScale: Double,
): Long =
    (baseTempoMs.coerceAtLeast(1L) * tempoScale)
        .roundToLong()
        .coerceAtLeast(1L)

internal fun holdAtLoadingFrameBoundary(context: RsvpUiContext) {
    val runtime = context.runtime
    val frame = context.frameState.frames.getOrNull(runtime.frameIndex)
    if (frame != null) {
        runtime.currentTokenIndex = frame.nextOriginalTokenIndex
        runtime.currentResumeCursor = -1
    }
    runtime.scheduledFrameIndex = -1
    runtime.nextFrameAtMs = 0L
}

private const val CATCH_UP_FACTOR = 0.25

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
    // Keyed on the tempo as well so every adjustment step restarts the timer — otherwise the
    // indicator vanishes mid-drag once the delay from the *first* step expires.
    LaunchedEffect(runtime.showTempoIndicator, runtime.currentTempoMsPerWord) {
        if (runtime.showTempoIndicator) {
            delay(TEMPO_INDICATOR_HIDE_DELAY_MS)
            runtime.showTempoIndicator = false
        }
    }
}

@Composable
internal fun RsvpAutoHideFontSizeIndicatorEffect(runtime: RsvpRuntimeState) {
    // Keyed on the size as well so every adjustment step restarts the timer (see tempo above).
    LaunchedEffect(runtime.showFontSizeIndicator, runtime.currentFontSizeSp) {
        if (runtime.showFontSizeIndicator) {
            delay(FONT_SIZE_INDICATOR_HIDE_DELAY_MS)
            runtime.showFontSizeIndicator = false
        }
    }
}
