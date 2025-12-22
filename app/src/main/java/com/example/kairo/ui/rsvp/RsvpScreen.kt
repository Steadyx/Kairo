@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.kairo.data.rsvp.RsvpFrameRepository
import com.example.kairo.data.rsvp.RsvpFrameSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun RsvpScreen(
    state: RsvpScreenState,
    callbacks: RsvpScreenCallbacks,
    dependencies: RsvpScreenDependencies,
) {
    val minTempoMsPerWord =
        if (state.uiPrefs.extremeSpeedUnlocked) {
            EXTREME_MIN_TEMPO_MS_PER_WORD
        } else {
            SAFE_MIN_TEMPO_MS_PER_WORD
        }
    val sessionKey = buildSessionKey(state.book)
    val runtime =
        rememberRsvpRuntimeState(
            profile = state.profile,
            textStyle = state.textStyle,
            layoutBias = state.layoutBias,
            startIndex = state.book.startIndex,
            sessionKey = sessionKey,
        )
    val frameState =
        rememberFrameLoadState(
            book = state.book,
            profile = state.profile,
            frameRepository = dependencies.frameRepository,
        )
    val tempoScale =
        rememberTempoScale(
            currentTempoMsPerWord = runtime.currentTempoMsPerWord,
            baseTempoMs = frameState.baseTempoMs,
        )
    val timing =
        RsvpTimingInfo(
            minTempoMs = minTempoMsPerWord,
            maxTempoMs = MAX_TEMPO_MS_PER_WORD,
            tempoScale = tempoScale,
        )
    val context =
        RsvpUiContext(
            state = state,
            callbacks = callbacks,
            runtime = runtime,
            frameState = frameState,
            timing = timing,
        )

    RsvpBackHandler(context)
    RsvpPlaybackEffects(context, sessionKey = sessionKey)
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
    startIndex: Int,
    sessionKey: String,
): RsvpRuntimeState {
    var savedTokenIndex by rememberSaveable(sessionKey) { mutableStateOf(startIndex) }
    var savedIsPlaying by rememberSaveable(sessionKey) { mutableStateOf(true) }
    var savedCompleted by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var savedTempoMsPerWord by rememberSaveable(sessionKey) {
        mutableStateOf(profile.config.tempoMsPerWord)
    }
    var savedFontSizeSp by rememberSaveable(sessionKey) { mutableStateOf(textStyle.fontSizeSp) }
    var savedFontWeight by rememberSaveable(sessionKey) { mutableStateOf(textStyle.fontWeight) }
    var savedFontFamily by rememberSaveable(sessionKey) { mutableStateOf(textStyle.fontFamily) }
    var savedTextBrightness by rememberSaveable(sessionKey) {
        mutableStateOf(textStyle.textBrightness)
    }
    var savedVerticalBias by rememberSaveable(sessionKey) { mutableStateOf(layoutBias.verticalBias) }
    var savedHorizontalBias by rememberSaveable(sessionKey) {
        mutableStateOf(layoutBias.horizontalBias)
    }
    val prefsFingerprint =
        remember(profile, textStyle, layoutBias) {
            buildPrefsFingerprint(profile, textStyle, layoutBias)
        }
    var lastPrefsFingerprint by rememberSaveable(sessionKey) {
        mutableStateOf(prefsFingerprint)
    }
    val state =
        remember(sessionKey) {
            RsvpRuntimeState().apply {
                currentTempoMsPerWord = savedTempoMsPerWord
                dragStartTempoMsPerWord = savedTempoMsPerWord
                currentFontFamily = savedFontFamily
                currentTextBrightness = savedTextBrightness
                currentFontSizeSp = savedFontSizeSp
                currentFontWeight = savedFontWeight
                currentVerticalBias = savedVerticalBias
                currentHorizontalBias = savedHorizontalBias
                dragStartBias = savedVerticalBias
                dragStartHorizontalBias = savedHorizontalBias
                currentTokenIndex = savedTokenIndex.coerceAtLeast(0)
                isPlaying = savedIsPlaying
                completed = savedCompleted
            }
        }

    LaunchedEffect(state.currentTokenIndex, state.isPlaying, state.completed) {
        if (savedTokenIndex != state.currentTokenIndex) {
            savedTokenIndex = state.currentTokenIndex
        }
        if (savedIsPlaying != state.isPlaying) {
            savedIsPlaying = state.isPlaying
        }
        if (savedCompleted != state.completed) {
            savedCompleted = state.completed
        }
    }

    LaunchedEffect(prefsFingerprint) {
        if (prefsFingerprint != lastPrefsFingerprint) {
            state.currentTempoMsPerWord = profile.config.tempoMsPerWord
            state.dragStartTempoMsPerWord = profile.config.tempoMsPerWord
            state.currentFontFamily = textStyle.fontFamily
            state.currentTextBrightness = textStyle.textBrightness
            state.currentFontSizeSp = textStyle.fontSizeSp
            state.currentFontWeight = textStyle.fontWeight
            state.currentVerticalBias = layoutBias.verticalBias
            state.currentHorizontalBias = layoutBias.horizontalBias
            state.dragStartBias = layoutBias.verticalBias
            state.dragStartHorizontalBias = layoutBias.horizontalBias
            savedTempoMsPerWord = state.currentTempoMsPerWord
            savedFontSizeSp = state.currentFontSizeSp
            savedFontWeight = state.currentFontWeight
            savedFontFamily = state.currentFontFamily
            savedTextBrightness = state.currentTextBrightness
            savedVerticalBias = state.currentVerticalBias
            savedHorizontalBias = state.currentHorizontalBias
            lastPrefsFingerprint = prefsFingerprint
        }
    }

    LaunchedEffect(
        state.currentTempoMsPerWord,
        state.currentFontSizeSp,
        state.currentFontWeight,
        state.currentFontFamily,
        state.currentTextBrightness,
        state.currentVerticalBias,
        state.currentHorizontalBias,
    ) {
        if (savedTempoMsPerWord != state.currentTempoMsPerWord) {
            savedTempoMsPerWord = state.currentTempoMsPerWord
        }
        if (savedFontSizeSp != state.currentFontSizeSp) {
            savedFontSizeSp = state.currentFontSizeSp
        }
        if (savedFontWeight != state.currentFontWeight) {
            savedFontWeight = state.currentFontWeight
        }
        if (savedFontFamily != state.currentFontFamily) {
            savedFontFamily = state.currentFontFamily
        }
        if (savedTextBrightness != state.currentTextBrightness) {
            savedTextBrightness = state.currentTextBrightness
        }
        if (savedVerticalBias != state.currentVerticalBias) {
            savedVerticalBias = state.currentVerticalBias
        }
        if (savedHorizontalBias != state.currentHorizontalBias) {
            savedHorizontalBias = state.currentHorizontalBias
        }
    }

    return state
}

@Composable
private fun rememberFrameLoadState(
    book: RsvpBookContext,
    profile: RsvpProfileContext,
    frameRepository: RsvpFrameRepository,
): RsvpFrameLoadState {
    var frameSet by remember { mutableStateOf<RsvpFrameSet?>(null) }
    var isFramesLoading by remember { mutableStateOf(true) }
    var loadAttempt by remember(book.bookId, book.chapterIndex, profile.config) {
        mutableIntStateOf(0)
    }

    LaunchedEffect(book.bookId, book.chapterIndex, profile.config, loadAttempt) {
        val hadFrames = frameSet?.frames?.isNotEmpty() == true
        if (!hadFrames) isFramesLoading = true

        val computed =
            runCatching {
                frameRepository.getFrames(book.bookId, book.chapterIndex, profile.config)
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
            }.getOrNull()

        if (computed == null) {
            if (loadAttempt < MAX_FRAME_LOAD_RETRIES) {
                delay(FRAME_LOAD_RETRY_DELAY_MS)
                loadAttempt += 1
                return@LaunchedEffect
            }
            isFramesLoading = false
            return@LaunchedEffect
        }

        frameSet = computed
        isFramesLoading = false
    }

    val frames = frameSet?.frames.orEmpty()
    val baseTempoMs = frameSet?.baseTempoMs ?: profile.config.tempoMsPerWord
    return RsvpFrameLoadState(
        frames = frames,
        baseTempoMs = baseTempoMs,
        isLoading = isFramesLoading,
    )
}

@Composable
private fun rememberTempoScale(
    currentTempoMsPerWord: Long,
    baseTempoMs: Long,
): Double =
    remember(currentTempoMsPerWord, baseTempoMs) {
        if (baseTempoMs <= 0L) {
            DEFAULT_TEMPO_SCALE
        } else {
            (currentTempoMsPerWord.toDouble() / baseTempoMs.toDouble())
                .coerceIn(TEMPO_SCALE_MIN, TEMPO_SCALE_MAX)
        }
    }

private fun shouldShowLoading(frameState: RsvpFrameLoadState): Boolean =
    frameState.isLoading && frameState.frames.isEmpty()

private fun buildSessionKey(book: RsvpBookContext): String =
    "${book.bookId.value}:${book.chapterIndex}:${book.startIndex}"

private fun buildPrefsFingerprint(
    profile: RsvpProfileContext,
    textStyle: RsvpTextStyle,
    layoutBias: RsvpLayoutBias,
): String =
    listOf(
        profile.config.tempoMsPerWord,
        textStyle.fontSizeSp,
        textStyle.fontWeight.name,
        textStyle.fontFamily.name,
        textStyle.textBrightness,
        layoutBias.verticalBias,
        layoutBias.horizontalBias,
    ).joinToString("|")

private const val FRAME_LOAD_RETRY_DELAY_MS = 200L
private const val MAX_FRAME_LOAD_RETRIES = 3

@Composable
private fun RsvpBackHandler(context: RsvpUiContext) {
    BackHandler { exitAndSavePosition(context) }
}

@Composable
private fun RsvpPlaybackEffects(context: RsvpUiContext, sessionKey: String) {
    RsvpPositionSaveEffect(context)
    RsvpFrameAlignmentEffect(context)
    RsvpSessionResetEffect(context, sessionKey)
    RsvpPlaybackLoopEffect(context)
}

@Composable
private fun RsvpIndicatorEffects(runtime: RsvpRuntimeState) {
    RsvpAutoHideControlsEffect(runtime)
    RsvpAutoHideTempoIndicatorEffect(runtime)
    RsvpAutoHideFontSizeIndicatorEffect(runtime)
}
