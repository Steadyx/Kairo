@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.rsvp

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.kairo.reader.core.model.BionicReadingPreferences
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.RsvpReadabilityMode
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.effectiveBlinkMode
import com.kairo.reader.core.model.nearestWordIndex
import com.kairo.reader.core.model.readabilityMode
import com.kairo.reader.core.rsvp.engine.frameTimingKey
import com.kairo.reader.data.rsvp.RsvpFrameIndexMap
import com.kairo.reader.data.rsvp.RsvpFrameRepository
import com.kairo.reader.data.rsvp.RsvpFrameSet
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import com.kairo.reader.ui.tutorial.StartingTutorialTargetIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun RsvpScreen(
    state: RsvpScreenState,
    callbacks: RsvpScreenCallbacks,
    dependencies: RsvpScreenDependencies,
    presentationMode: ReadingPresentationMode = ReadingPresentationMode.RSVP,
    bionicPreferences: BionicReadingPreferences = BionicReadingPreferences(),
    tutorialState: StartingTutorialOverlayState? = null,
    onTutorialNext: () -> Unit = {},
    onTutorialPrevious: () -> Unit = {},
    onTutorialSkip: () -> Unit = {},
) {
    val isTutorialMode = tutorialState != null
    val hapticFeedback = LocalHapticFeedback.current
    val minTempoMsPerWord =
        if (state.uiPrefs.extremeSpeedUnlocked) {
            EXTREME_MIN_TEMPO_MS_PER_WORD
        } else {
            SAFE_MIN_TEMPO_MS_PER_WORD
        }
    val sessionKey = buildSessionKey(state.book)
    val onPlaybackStateChanged by rememberUpdatedState(callbacks.playback.onPlaybackStateChanged)
    val runtime =
        rememberRsvpRuntimeState(
            profile = state.profile,
            launchTempoMsPerWord = state.launchTempoMsPerWord,
            initialIsPlaying = state.initialIsPlaying,
            textStyle = state.textStyle,
            layoutBias = state.layoutBias,
            startIndex = state.book.startIndex,
            startResumeCursor = state.book.startResumeCursor,
            sessionKey = sessionKey,
            onPlaybackStateChanged = onPlaybackStateChanged,
        )
    val frameState =
        rememberFrameLoadState(
            book = state.book,
            profile = state.profile,
            frameRepository = dependencies.frameRepository,
            previewStartIndex = runtime.currentTokenIndex,
            previewResumeCursor = runtime.currentResumeCursor,
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
            presentationMode = presentationMode,
            bionicPreferences = bionicPreferences,
            haptics =
            RsvpHapticCallbacks(
                onFrameStep = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onTempoStep = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
            ),
        )

    LaunchedEffect(tutorialState?.step?.targetId) {
        when (tutorialState?.step?.targetId) {
            StartingTutorialTargetIds.RSVP_PLAYBACK_CONTROLS -> {
                runtime.isPlaying = false
                runtime.completed = false
                runtime.showControls = true
                runtime.showQuickSettings = false
            }

            StartingTutorialTargetIds.RSVP_SURFACE,
            StartingTutorialTargetIds.RSVP_EXIT,
            StartingTutorialTargetIds.RSVP_TOP_SETTINGS -> {
                runtime.isPlaying = false
                runtime.completed = false
                runtime.showControls = false
                runtime.showQuickSettings = false
            }

            StartingTutorialTargetIds.RSVP_QUICK_SETTINGS,
            StartingTutorialTargetIds.RSVP_SETTINGS_ROW -> {
                runtime.isPlaying = false
                runtime.completed = false
                runtime.showControls = false
                runtime.showQuickSettings = true
            }

            else -> Unit
        }
    }

    RsvpBackHandler(context, enabled = !isTutorialMode)
    RsvpFrameLoadFailureEffect(context)
    RsvpPlaybackEffects(
        context = context,
        sessionKey = sessionKey,
        autoPlay = state.initialIsPlaying && !isTutorialMode,
        playbackEnabled =
        !isTutorialMode &&
            isFrameSetReadyForPlayback(
                frameState = frameState,
                presentationMode = presentationMode,
            ),
    )
    RsvpIndicatorEffects(runtime)

    if (frameState.loadFailed) {
        RsvpFrameLoadFailedState(presentationMode) {
            if (!isTutorialMode) {
                exitAndSavePosition(context)
            }
        }
        return
    }
    if (shouldShowLoading(frameState, presentationMode)) {
        RsvpLoadingState(presentationMode)
        return
    }
    if (frameState.frames.isEmpty()) {
        RsvpEmptyState(presentationMode) {
            if (!isTutorialMode) {
                exitAndSavePosition(context)
            }
        }
        return
    }

    RsvpPlaybackSurface(
        context = context,
        tutorialState = tutorialState,
        onTutorialNext = onTutorialNext,
        onTutorialPrevious = onTutorialPrevious,
        onTutorialSkip = onTutorialSkip,
    )
}

@Composable
private fun rememberRsvpRuntimeState(
    profile: RsvpProfileContext,
    launchTempoMsPerWord: Long?,
    initialIsPlaying: Boolean,
    textStyle: RsvpTextStyle,
    layoutBias: RsvpLayoutBias,
    startIndex: Int,
    startResumeCursor: Int,
    sessionKey: String,
    onPlaybackStateChanged: (Boolean) -> Unit,
): RsvpRuntimeState {
    var savedTokenIndex by rememberSaveable(sessionKey) { mutableIntStateOf(startIndex) }
    var savedResumeCursor by rememberSaveable(sessionKey) { mutableIntStateOf(startResumeCursor) }
    var savedIsPlaying by rememberSaveable(sessionKey) { mutableStateOf(initialIsPlaying) }
    var savedCompleted by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var savedTempoMsPerWord by rememberSaveable(sessionKey) {
        mutableLongStateOf(launchTempoMsPerWord ?: profile.config.tempoMsPerWord)
    }
    var savedFontSizeSp by rememberSaveable(sessionKey) { mutableFloatStateOf(textStyle.fontSizeSp) }
    var savedFontWeight by rememberSaveable(sessionKey) { mutableStateOf(textStyle.fontWeight) }
    var savedFontFamily by rememberSaveable(sessionKey) { mutableStateOf(textStyle.fontFamily) }
    var savedTextBrightness by rememberSaveable(sessionKey) {
        mutableFloatStateOf(textStyle.textBrightness)
    }
    var savedVerticalBias by rememberSaveable(sessionKey) {
        mutableFloatStateOf(layoutBias.verticalBias)
    }
    var savedHorizontalBias by rememberSaveable(sessionKey) {
        mutableFloatStateOf(layoutBias.horizontalBias)
    }
    val appearanceFingerprint =
        remember(textStyle, layoutBias) {
            buildAppearanceFingerprint(textStyle, layoutBias)
        }
    var lastAppearanceFingerprint by rememberSaveable(sessionKey) {
        mutableStateOf(appearanceFingerprint)
    }
    var lastPersistedProfileId by rememberSaveable(sessionKey) {
        mutableStateOf(profile.selectedProfileId)
    }
    val state =
        remember(sessionKey) {
            RsvpRuntimeState(
                onPlaybackStateChanged = { isPlaying, completed ->
                    savedIsPlaying = isPlaying
                    savedCompleted = completed
                    onPlaybackStateChanged(isPlaying)
                },
            ).apply {
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
                currentResumeCursor = savedResumeCursor.takeIf { it >= 0 } ?: -1
                isPlaying = savedIsPlaying
                completed = savedCompleted
            }
        }

    LaunchedEffect(state.currentTokenIndex, state.currentResumeCursor, state.isPlaying, state.completed) {
        if (savedTokenIndex != state.currentTokenIndex) {
            savedTokenIndex = state.currentTokenIndex
        }
        if (savedResumeCursor != state.currentResumeCursor) {
            savedResumeCursor = state.currentResumeCursor
        }
        if (savedIsPlaying != state.isPlaying) {
            savedIsPlaying = state.isPlaying
        }
        if (savedCompleted != state.completed) {
            savedCompleted = state.completed
        }
    }

    LaunchedEffect(appearanceFingerprint) {
        if (appearanceFingerprint != lastAppearanceFingerprint) {
            state.currentFontFamily = textStyle.fontFamily
            state.currentTextBrightness = textStyle.textBrightness
            state.currentFontSizeSp = textStyle.fontSizeSp
            state.currentFontWeight = textStyle.fontWeight
            state.currentVerticalBias = layoutBias.verticalBias
            state.currentHorizontalBias = layoutBias.horizontalBias
            state.dragStartBias = layoutBias.verticalBias
            state.dragStartHorizontalBias = layoutBias.horizontalBias
            savedFontSizeSp = state.currentFontSizeSp
            savedFontWeight = state.currentFontWeight
            savedFontFamily = state.currentFontFamily
            savedTextBrightness = state.currentTextBrightness
            savedVerticalBias = state.currentVerticalBias
            savedHorizontalBias = state.currentHorizontalBias
            lastAppearanceFingerprint = appearanceFingerprint
        }
    }

    LaunchedEffect(profile.selectedProfileId, profile.config.tempoMsPerWord) {
        val incomingTempoMsPerWord = profile.config.tempoMsPerWord
        if (
            shouldApplyPersistedTempo(
                currentTempoMsPerWord = state.currentTempoMsPerWord,
                incomingTempoMsPerWord = incomingTempoMsPerWord,
                lastSelectedProfileId = lastPersistedProfileId,
                incomingSelectedProfileId = profile.selectedProfileId,
            )
        ) {
            state.currentTempoMsPerWord = incomingTempoMsPerWord
            state.dragStartTempoMsPerWord = incomingTempoMsPerWord
            savedTempoMsPerWord = incomingTempoMsPerWord
        }
        lastPersistedProfileId = profile.selectedProfileId
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

internal fun shouldApplyPersistedTempo(
    currentTempoMsPerWord: Long,
    incomingTempoMsPerWord: Long,
    lastSelectedProfileId: String,
    incomingSelectedProfileId: String,
): Boolean {
    if (currentTempoMsPerWord <= 0L) return true
    if (currentTempoMsPerWord == incomingTempoMsPerWord) return true
    if (currentTempoMsPerWord < com.kairo.reader.core.rsvp.RsvpSpeedControl.SAFE_MIN_TEMPO_MS_PER_WORD) {
        return false
    }
    return incomingSelectedProfileId != lastSelectedProfileId
}

@Composable
private fun rememberFrameLoadState(
    book: RsvpBookContext,
    profile: RsvpProfileContext,
    frameRepository: RsvpFrameRepository,
    previewStartIndex: Int,
    previewResumeCursor: Int,
): RsvpFrameLoadState {
    val loadConfigKey = remember(profile.config) { frameLoadConfigKey(profile.config) }
    val instantFrameTokenCount = book.tokens.size
    val latestPreviewStartIndex by rememberUpdatedState(previewStartIndex)
    var frameSetState by remember(
        book.bookId,
        book.chapterIndex,
        book.startIndex,
        book.generationOptions,
        loadConfigKey,
        instantFrameTokenCount,
    ) {
        mutableStateOf(
            buildInstantFrameSet(
                book.copy(
                    startIndex = previewStartIndex,
                    startResumeCursor = previewResumeCursor,
                ),
                profile.config,
            )?.let { frameSet -> LoadedRsvpFrameSet(frameSet = frameSet, isComplete = false) },
        )
    }
    var isFramesLoading by remember(
        book.bookId,
        book.chapterIndex,
        book.startIndex,
        book.generationOptions,
        loadConfigKey,
    ) {
        mutableStateOf(true)
    }
    var frameLoadFailed by remember(
        book.bookId,
        book.chapterIndex,
        book.startIndex,
        book.generationOptions,
        loadConfigKey,
    ) {
        mutableStateOf(false)
    }
    var loadAttempt by remember(
        book.bookId,
        book.chapterIndex,
        book.startIndex,
        book.generationOptions,
        loadConfigKey,
    ) {
        mutableIntStateOf(0)
    }

    LaunchedEffect(
        book.bookId,
        book.chapterIndex,
        book.startIndex,
        book.generationOptions,
        loadConfigKey,
        instantFrameTokenCount,
        loadAttempt,
    ) {
        val loadStartIndex =
            resolveFrameLoadStartIndex(
                bookStartIndex = book.startIndex,
                previewStartIndex = latestPreviewStartIndex,
                tokenCount = instantFrameTokenCount,
            )
        val hadFrames = frameSetState?.frameSet?.frames?.isNotEmpty() == true
        val hadCompleteFrames =
            frameSetState?.isComplete == true &&
                frameSetState?.frameSet?.frames?.isNotEmpty() == true
        if (!hadFrames) isFramesLoading = true
        if (!hadFrames && book.tokens.isNotEmpty()) {
            val preview =
                runCatching {
                    frameRepository.getPreviewFrames(
                        tokens = book.tokens,
                        startIndex = loadStartIndex,
                        config = profile.config,
                        options = book.generationOptions,
                    )
                }.onFailure { error ->
                    if (error is CancellationException) {
                        throw error
                    }
                }.getOrNull()
            if (preview?.frames?.isNotEmpty() == true) {
                frameSetState = LoadedRsvpFrameSet(frameSet = preview, isComplete = false)
            }
        }

        val computed =
            runCatching {
                frameRepository.getFrames(
                    book.bookId,
                    book.chapterIndex,
                    profile.config,
                    startIndex = loadStartIndex,
                    options = book.generationOptions,
                )
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
            if (!hadCompleteFrames && !hadFrames) {
                frameSetState = null
            }
            frameLoadFailed = true
            isFramesLoading = false
            return@LaunchedEffect
        }

        frameSetState = LoadedRsvpFrameSet(frameSet = computed, isComplete = true)
        frameLoadFailed = false
        isFramesLoading = false
    }

    val activeFrameSet = frameSetState?.frameSet
    val frames = activeFrameSet?.frames.orEmpty()
    val baseTempoMs = activeFrameSet?.baseTempoMs ?: profile.config.tempoMsPerWord
    val isComplete = frameSetState?.isComplete == true && !frameLoadFailed
    val frameIndexMap = activeFrameSet?.frameIndexMap ?: RsvpFrameIndexMap.EMPTY
    return RsvpFrameLoadState(
        frames = frames,
        baseTempoMs = baseTempoMs,
        isLoading = isFramesLoading,
        isComplete = isComplete,
        loadFailed = frameLoadFailed,
        frameIndexMap = frameIndexMap,
    )
}

private data class LoadedRsvpFrameSet(val frameSet: RsvpFrameSet, val isComplete: Boolean,)

private fun buildInstantFrameSet(
    book: RsvpBookContext,
    config: RsvpConfig,
): RsvpFrameSet? {
    if (book.tokens.isEmpty()) return null
    val safeIndex = book.tokens.nearestWordIndex(book.startIndex).coerceIn(0, book.tokens.lastIndex)
    val token = book.tokens.getOrNull(safeIndex) ?: return null
    if (token.type != TokenType.WORD) return null
    val nextWordIndex =
        ((safeIndex + 1)..book.tokens.lastIndex)
            .firstOrNull { index -> book.tokens[index].type == TokenType.WORD }
            ?: book.tokens.size
    return RsvpFrameSet(
        frames =
        listOf(
            RsvpFrame(
                tokens = listOf(token),
                durationMs = config.tempoMsPerWord,
                originalTokenIndex = safeIndex,
                resumeCursor = book.startResumeCursor.takeIf { it >= 0 } ?: safeIndex,
                nextOriginalTokenIndex = nextWordIndex,
                displayOriginalStartIndex = safeIndex,
                displayOriginalEndExclusive = safeIndex + 1,
                displayOriginalStartCharacterOffset = 0,
                displayOriginalEndCharacterOffset = token.text.length,
            ),
        ),
        baseTempoMs = config.tempoMsPerWord,
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

internal fun shouldShowLoading(
    frameState: RsvpFrameLoadState,
    presentationMode: ReadingPresentationMode,
): Boolean =
    frameState.isLoading &&
        (frameState.frames.isEmpty() || presentationMode == ReadingPresentationMode.BIONIC)

internal fun isFrameSetReadyForPlayback(
    frameState: RsvpFrameLoadState,
    presentationMode: ReadingPresentationMode,
): Boolean =
    presentationMode == ReadingPresentationMode.RSVP || frameState.isComplete

internal fun buildSessionKey(book: RsvpBookContext): String =
    "${book.bookId.value}:${book.chapterIndex}:${book.sessionStartIndex}"

private fun buildAppearanceFingerprint(
    textStyle: RsvpTextStyle,
    layoutBias: RsvpLayoutBias,
): String =
    listOf(
        textStyle.fontSizeSp,
        textStyle.fontWeight.name,
        textStyle.fontFamily.name,
        textStyle.textBrightness,
        layoutBias.verticalBias,
        layoutBias.horizontalBias,
    ).joinToString("|")

internal fun frameLoadConfigKey(config: RsvpConfig): RsvpConfig =
    config.frameTimingKey().copy(tempoMsPerWord = blinkFrameLoadTempoKey(config))

private fun blinkFrameLoadTempoKey(config: RsvpConfig): Long =
    when {
        config.effectiveBlinkMode() == BlinkMode.OFF -> 0L
        config.readabilityMode() == RsvpReadabilityMode.STANDARD -> 0L
        config.readabilityMode() == RsvpReadabilityMode.HIGH_SPEED -> 1L
        else -> 2L
    }

internal fun resolveFrameLoadStartIndex(
    bookStartIndex: Int,
    previewStartIndex: Int,
    tokenCount: Int,
): Int {
    val requestedIndex = previewStartIndex.takeIf { it >= 0 } ?: bookStartIndex
    return if (tokenCount > 0) {
        requestedIndex.coerceIn(0, tokenCount - 1)
    } else {
        requestedIndex.coerceAtLeast(0)
    }
}

private const val FRAME_LOAD_RETRY_DELAY_MS = 200L
private const val MAX_FRAME_LOAD_RETRIES = 3

@Composable
private fun RsvpBackHandler(
    context: RsvpUiContext,
    enabled: Boolean,
) {
    val runtime = context.runtime
    BackHandler(enabled = enabled) {
        // Back dismisses the topmost overlay before exiting the screen, mirroring the
        // tap-to-dismiss behavior of each overlay.
        when {
            runtime.showQuickSettings -> runtime.showQuickSettings = false
            runtime.isPositioningMode -> finishPositioning(context, resumeIfWasPlaying = true)
            else -> exitAndSavePosition(context)
        }
    }
}

@Composable
private fun RsvpPlaybackEffects(
    context: RsvpUiContext,
    sessionKey: String,
    autoPlay: Boolean,
    playbackEnabled: Boolean,
) {
    RsvpPositionSaveEffect(context)
    RsvpLifecyclePositionSaveEffect(context)
    RsvpFrameAlignmentEffect(context)
    RsvpSessionResetEffect(context, sessionKey, autoPlay = autoPlay)
    RsvpPlaybackLoopEffect(context, enabled = playbackEnabled)
}

@Composable
private fun RsvpIndicatorEffects(runtime: RsvpRuntimeState) {
    RsvpAutoHideControlsEffect(runtime)
    RsvpAutoHideTempoIndicatorEffect(runtime)
    RsvpAutoHideFontSizeIndicatorEffect(runtime)
}
