package com.example.kairo.ui.rsvp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kairo.core.model.RsvpFrame

internal data class RsvpFrameLoadState(
    val frames: List<RsvpFrame>,
    val baseTempoMs: Long,
    val isLoading: Boolean
)

internal data class RsvpTimingInfo(
    val minTempoMs: Long,
    val maxTempoMs: Long,
    val tempoScale: Double
)

internal data class RsvpUiContext(
    val state: RsvpScreenState,
    val callbacks: RsvpScreenCallbacks,
    val runtime: RsvpRuntimeState,
    val frameState: RsvpFrameLoadState,
    val timing: RsvpTimingInfo
)

internal class RsvpRuntimeState {
    var currentTempoMsPerWord by mutableLongStateOf(0L)
    var showTempoIndicator by mutableStateOf(false)
    var showFontSizeIndicator by mutableStateOf(false)
    var dragAccumulator by mutableFloatStateOf(ZERO_FLOAT)
    var dragStartTempoMsPerWord by mutableLongStateOf(0L)
    var currentVerticalBias by mutableFloatStateOf(ZERO_FLOAT)
    var currentHorizontalBias by mutableFloatStateOf(ZERO_FLOAT)
    var currentFontSizeSp by mutableFloatStateOf(ZERO_FLOAT)
    var currentFontWeight by mutableStateOf(DEFAULT_FONT_WEIGHT)
    var currentFontFamily by mutableStateOf(DEFAULT_FONT_FAMILY)
    var currentTextBrightness by mutableFloatStateOf(DEFAULT_TEXT_BRIGHTNESS)
    var frameIndex by mutableIntStateOf(0)
    var currentTokenIndex by mutableIntStateOf(0)
    var isPlaying by mutableStateOf(true)
    var completed by mutableStateOf(false)
    var showControls by mutableStateOf(false)
    var showQuickSettings by mutableStateOf(false)
    var isAdjustingPosition by mutableStateOf(false)
    var isPositioningMode by mutableStateOf(false)
    var dragStartBias by mutableFloatStateOf(ZERO_FLOAT)
    var dragStartHorizontalBias by mutableFloatStateOf(ZERO_FLOAT)
    var wasPlayingBeforePositioning by mutableStateOf(true)
    var lastPositionSaveMs by mutableLongStateOf(0L)
}
