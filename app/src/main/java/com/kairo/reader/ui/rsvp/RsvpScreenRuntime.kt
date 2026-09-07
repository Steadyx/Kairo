package com.kairo.reader.ui.rsvp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kairo.reader.core.model.BionicReadingPreferences
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.rsvp.timing.RsvpSessionTimingPolicy
import com.kairo.reader.data.rsvp.RsvpFrameIndexMap

internal data class RsvpFrameLoadState(
    val frames: List<RsvpFrame>,
    val baseTempoMs: Long,
    val isLoading: Boolean,
    val isComplete: Boolean = true,
    val loadFailed: Boolean = false,
    val frameIndexMap: RsvpFrameIndexMap = RsvpFrameIndexMap.from(frames),
)

internal data class RsvpTimingInfo(val minTempoMs: Long, val maxTempoMs: Long, val tempoScale: Double,)

internal data class RsvpHapticCallbacks(val onFrameStep: () -> Unit = {}, val onTempoStep: () -> Unit = {},)

internal data class RsvpUiContext(
    val state: RsvpScreenState,
    val callbacks: RsvpScreenCallbacks,
    val runtime: RsvpRuntimeState,
    val frameState: RsvpFrameLoadState,
    val timing: RsvpTimingInfo,
    val presentationMode: ReadingPresentationMode = ReadingPresentationMode.RSVP,
    val bionicPreferences: BionicReadingPreferences = BionicReadingPreferences(),
    val haptics: RsvpHapticCallbacks = RsvpHapticCallbacks(),
)

internal enum class RsvpDragAxis { NONE, HORIZONTAL, VERTICAL }

internal class RsvpRuntimeState(
    private val onPlaybackStateChanged: (isPlaying: Boolean, completed: Boolean) -> Unit = { _, _ -> },
    private val monotonicTimeMs: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
) {
    var currentTempoMsPerWord by mutableLongStateOf(0L)
    var showTempoIndicator by mutableStateOf(false)
    var showFontSizeIndicator by mutableStateOf(false)
    var dragAccumulator by mutableFloatStateOf(ZERO_FLOAT)
    var dragAccumulatorX by mutableFloatStateOf(ZERO_FLOAT)
    var dragStartTempoMsPerWord by mutableLongStateOf(0L)
    var dragStartFrameIndex by mutableIntStateOf(0)
    var dragAxis by mutableStateOf(RsvpDragAxis.NONE)
    var currentVerticalBias by mutableFloatStateOf(ZERO_FLOAT)
    var currentHorizontalBias by mutableFloatStateOf(ZERO_FLOAT)
    var currentFontSizeSp by mutableFloatStateOf(ZERO_FLOAT)
    var currentFontWeight by mutableStateOf(DEFAULT_FONT_WEIGHT)
    var currentFontFamily by mutableStateOf(DEFAULT_FONT_FAMILY)
    var currentTextBrightness by mutableFloatStateOf(DEFAULT_TEXT_BRIGHTNESS)
    var comprehensionPaceScale by mutableFloatStateOf(1f)
    var stablePhrasesSinceRegression by mutableIntStateOf(0)
    var resumePreparationScale by mutableDoubleStateOf(1.0)
    var replayPreparationPending = false
    private var pausedAtMs: Long? = null
    var frameIndex by mutableIntStateOf(0)
    var currentTokenIndex by mutableIntStateOf(0)
    var currentResumeCursor by mutableIntStateOf(0)
    var rampStartFrameIndex by mutableIntStateOf(0)
    var scheduledFrameIndex by mutableIntStateOf(-1)
    var nextFrameAtMs by mutableLongStateOf(0L)
    private var playbackIsPlaying by mutableStateOf(true)
    var isPlaying: Boolean
        get() = playbackIsPlaying
        set(value) {
            if (playbackIsPlaying == value) return
            if (value) {
                resumePreparationScale = pausedAtMs?.let {
                    RsvpSessionTimingPolicy.resumePreparationScale(
                        (monotonicTimeMs() - it).coerceAtLeast(0L),
                    )
                } ?: 1.0
                pausedAtMs = null
            } else {
                pausedAtMs = monotonicTimeMs()
            }
            playbackIsPlaying = value
            onPlaybackStateChanged(playbackIsPlaying, playbackCompleted)
        }
    var isScrubbing by mutableStateOf(false)
    var isExiting by mutableStateOf(false)
    private var playbackCompleted by mutableStateOf(false)
    var completed: Boolean
        get() = playbackCompleted
        set(value) {
            if (playbackCompleted == value) return
            playbackCompleted = value
            onPlaybackStateChanged(playbackIsPlaying, playbackCompleted)
        }
    var showControls by mutableStateOf(false)
    var showQuickSettings by mutableStateOf(false)
    var isAdjustingPosition by mutableStateOf(false)
    var isPositioningMode by mutableStateOf(false)
    var dragStartBias by mutableFloatStateOf(ZERO_FLOAT)
    var dragStartHorizontalBias by mutableFloatStateOf(ZERO_FLOAT)

    // Grid line the position is currently snapped to per axis (POSITIONING_GRID_LINE_NONE when
    // free). Only read/written inside gesture callbacks, so plain vars are fine.
    var positioningSnapLineV: Int = POSITIONING_GRID_LINE_NONE
    var positioningSnapLineH: Int = POSITIONING_GRID_LINE_NONE
    var wasPlayingBeforePositioning by mutableStateOf(true)
    var wasPlayingBeforeScrub by mutableStateOf(true)
    var lastPositionSaveMs by mutableLongStateOf(0L)
}

private const val NANOS_PER_MILLISECOND = 1_000_000L
