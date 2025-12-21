@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.kairo.data.rsvp.RsvpFrameRepository
import com.example.kairo.data.rsvp.RsvpFrameSet

@Composable
fun RsvpScreen(
    state: RsvpScreenState,
    callbacks: RsvpScreenCallbacks,
    dependencies: RsvpScreenDependencies
) {
    val minTempoMsPerWord = if (state.uiPrefs.extremeSpeedUnlocked) {
        EXTREME_MIN_TEMPO_MS_PER_WORD
    } else {
        SAFE_MIN_TEMPO_MS_PER_WORD
    }
    val runtime = rememberRsvpRuntimeState(
        profile = state.profile,
        textStyle = state.textStyle,
        layoutBias = state.layoutBias,
        startIndex = state.book.startIndex
    )
    val frameState = rememberFrameLoadState(
        book = state.book,
        profile = state.profile,
        frameRepository = dependencies.frameRepository
    )
    val tempoScale = rememberTempoScale(
        currentTempoMsPerWord = runtime.currentTempoMsPerWord,
        baseTempoMs = frameState.baseTempoMs
    )
    val timing = RsvpTimingInfo(
        minTempoMs = minTempoMsPerWord,
        maxTempoMs = MAX_TEMPO_MS_PER_WORD,
        tempoScale = tempoScale
    )
    val context = RsvpUiContext(
        state = state,
        callbacks = callbacks,
        runtime = runtime,
        frameState = frameState,
        timing = timing
    )

    RsvpBackHandler(context)
    RsvpPlaybackEffects(context)
    RsvpIndicatorEffects(runtime)

    if (shouldShowLoading(frameState)) {
        RsvpLoadingState()
        return
    }
    if (frameState.frames.isEmpty()) {
        RsvpEmptyState { exitAndSavePosition(context) }
        return
    }

    RsvpPlaybackSurface(context)
}

@Composable
private fun rememberRsvpRuntimeState(
    profile: RsvpProfileContext,
    textStyle: RsvpTextStyle,
    layoutBias: RsvpLayoutBias,
    startIndex: Int
): RsvpRuntimeState {
    val state = remember {
        RsvpRuntimeState().apply {
            currentTempoMsPerWord = profile.config.tempoMsPerWord
            dragStartTempoMsPerWord = profile.config.tempoMsPerWord
            currentFontFamily = textStyle.fontFamily
            currentTextBrightness = textStyle.textBrightness
            currentFontSizeSp = textStyle.fontSizeSp
            currentFontWeight = textStyle.fontWeight
            currentVerticalBias = layoutBias.verticalBias
            currentHorizontalBias = layoutBias.horizontalBias
            dragStartBias = layoutBias.verticalBias
            dragStartHorizontalBias = layoutBias.horizontalBias
            currentTokenIndex = startIndex
        }
    }

    LaunchedEffect(profile.config.tempoMsPerWord) {
        state.currentTempoMsPerWord = profile.config.tempoMsPerWord
    }
    LaunchedEffect(textStyle.fontFamily) {
        state.currentFontFamily = textStyle.fontFamily
    }
    LaunchedEffect(textStyle.textBrightness) {
        state.currentTextBrightness = textStyle.textBrightness
    }

    return state
}

@Composable
private fun rememberFrameLoadState(
    book: RsvpBookContext,
    profile: RsvpProfileContext,
    frameRepository: RsvpFrameRepository
): RsvpFrameLoadState {
    var frameSet by remember { mutableStateOf<RsvpFrameSet?>(null) }
    var isFramesLoading by remember { mutableStateOf(true) }

    LaunchedEffect(book.bookId, book.chapterIndex, profile.config) {
        val hadFrames = frameSet?.frames?.isNotEmpty() == true
        if (!hadFrames) isFramesLoading = true

        val computed = runCatching {
            frameRepository.getFrames(book.bookId, book.chapterIndex, profile.config)
        }.getOrNull()

        frameSet = computed
        isFramesLoading = false
    }

    val frames = frameSet?.frames.orEmpty()
    val baseTempoMs = frameSet?.baseTempoMs ?: profile.config.tempoMsPerWord
    return RsvpFrameLoadState(
        frames = frames,
        baseTempoMs = baseTempoMs,
        isLoading = isFramesLoading
    )
}

@Composable
private fun rememberTempoScale(currentTempoMsPerWord: Long, baseTempoMs: Long): Double {
    return remember(currentTempoMsPerWord, baseTempoMs) {
        if (baseTempoMs <= 0L) {
            DEFAULT_TEMPO_SCALE
        } else {
            (currentTempoMsPerWord.toDouble() / baseTempoMs.toDouble())
                .coerceIn(TEMPO_SCALE_MIN, TEMPO_SCALE_MAX)
        }
    }
}

private fun shouldShowLoading(frameState: RsvpFrameLoadState): Boolean {
    return frameState.isLoading && frameState.frames.isEmpty()
}

@Composable
private fun RsvpBackHandler(context: RsvpUiContext) {
    BackHandler { exitAndSavePosition(context) }
}

@Composable
private fun RsvpPlaybackEffects(context: RsvpUiContext) {
    RsvpPositionSaveEffect(context)
    RsvpFrameAlignmentEffect(context)
    RsvpSessionResetEffect(context)
    RsvpPlaybackLoopEffect(context)
}

@Composable
private fun RsvpIndicatorEffects(runtime: RsvpRuntimeState) {
    RsvpAutoHideControlsEffect(runtime)
    RsvpAutoHideTempoIndicatorEffect(runtime)
    RsvpAutoHideFontSizeIndicatorEffect(runtime)
}
