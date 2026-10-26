package com.kairo.reader.ui.rsvp

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpPlaybackStateTest {
    @Test
    fun frameLoadConfigKeyIgnoresTempoAndTracksFrameAffectingChanges() {
        val baseConfig = RsvpConfig(tempoMsPerWord = 120L, baseWpm = 500, commaPauseMs = 95L)

        assertEquals(
            frameLoadConfigKey(baseConfig),
            frameLoadConfigKey(baseConfig.copy(baseWpm = 333)),
        )
        assertEquals(
            frameLoadConfigKey(baseConfig),
            frameLoadConfigKey(baseConfig.copy(tempoMsPerWord = 180L)),
        )
        assertNotEquals(
            frameLoadConfigKey(baseConfig),
            frameLoadConfigKey(baseConfig.copy(commaPauseMs = 140L)),
        )
    }

    @Test
    fun ambientProgressOnlyShowsForPausedUncoveredSurface() {
        assertFalse(
            shouldShowAmbientProgressBar(
                isPlaying = true,
                showControls = false,
                showQuickSettings = false,
            ),
        )
        assertTrue(
            shouldShowAmbientProgressBar(
                isPlaying = false,
                showControls = false,
                showQuickSettings = false,
            ),
        )
        assertFalse(
            shouldShowAmbientProgressBar(
                isPlaying = false,
                showControls = true,
                showQuickSettings = false,
            ),
        )
        assertFalse(
            shouldShowAmbientProgressBar(
                isPlaying = false,
                showControls = false,
                showQuickSettings = true,
            ),
        )
    }

    @Test
    fun globalDragStaysAvailableUnlessQuickSettingsAreOpen() {
        assertTrue(
            shouldHandleGlobalRsvpDrag(
                showQuickSettings = false,
                isPositioningMode = false,
            ),
        )
        assertFalse(
            shouldHandleGlobalRsvpDrag(
                showQuickSettings = true,
                isPositioningMode = false,
            ),
        )
        assertTrue(
            shouldHandleGlobalRsvpDrag(
                showQuickSettings = true,
                isPositioningMode = true,
            ),
        )
    }

    @Test
    fun completePlaybackMarksCompletedAndCallsOnFinished() {
        var finishedPoint = RsvpResumePoint(tokenIndex = -1, resumeCursor = -1)
        val context =
            createContext(
                frames = listOf(
                    RsvpFrame(
                        tokens = listOf(Token(text = "Hello", type = TokenType.WORD)),
                        durationMs = 120L,
                        originalTokenIndex = 0,
                    ),
                    RsvpFrame(
                        tokens = listOf(Token(text = " ", type = TokenType.PUNCTUATION)),
                        durationMs = 30L,
                        originalTokenIndex = 0,
                    ),
                ),
                tokens = listOf(
                    Token(text = "Hello", type = TokenType.WORD),
                    Token(text = "World", type = TokenType.WORD),
                ),
                onFinished = { finishedPoint = it },
            )
        context.runtime.frameIndex = 1
        context.runtime.isPlaying = true
        context.runtime.currentTempoMsPerWord = 18L

        completePlayback(context)

        assertTrue(context.runtime.completed)
        assertFalse(context.runtime.isPlaying)
        assertEquals(1, finishedPoint.tokenIndex)
        assertEquals(-1, finishedPoint.resumeCursor)
        assertEquals(0, finishedPoint.chapterIndex)
        assertEquals(18L, finishedPoint.tempoMsPerWord)
    }

    @Test
    fun lifecycleAndExitDoNotSaveLastFrameAfterCompletion() {
        val saved = mutableListOf<RsvpResumePoint>()
        var finished: RsvpResumePoint? = null
        val base = createContext(
            frames = listOf(RsvpFrame(tokens = listOf(Token("Hello", TokenType.WORD)), durationMs = 120L, originalTokenIndex = 0)),
            onFinished = { finished = it },
        )
        val context = base.copy(
            callbacks = base.callbacks.copy(
                playback = base.callbacks.playback.copy(onPositionChanged = { saved += it }),
            ),
        )
        saveRsvpLifecyclePosition(context)
        assertEquals(1, saved.size)
        completePlayback(context)
        assertEquals(1, finished?.tokenIndex)
        saveRsvpLifecyclePosition(context)
        exitAndSavePosition(context)
        openLibraryAndSavePosition(context)
        assertEquals(1, saved.size)
    }

    @Test
    fun completePlaybackReturnsOverflowIndexAtChapterEnd() {
        var finishedPoint = RsvpResumePoint(tokenIndex = -1, resumeCursor = -1)
        val context =
            createContext(
                frames = listOf(
                    RsvpFrame(
                        tokens = listOf(Token(text = "World", type = TokenType.WORD)),
                        durationMs = 120L,
                        originalTokenIndex = 1,
                    ),
                ),
                tokens = listOf(
                    Token(text = "Hello", type = TokenType.WORD),
                    Token(text = "World", type = TokenType.WORD),
                ),
                onFinished = { finishedPoint = it },
            )

        completePlayback(context)

        assertEquals(2, finishedPoint.tokenIndex)
        assertEquals(-1, finishedPoint.resumeCursor)
        assertEquals(0, finishedPoint.chapterIndex)
    }

    @Test
    fun completePlaybackUsesNextOriginalIndexForPhraseFrames() {
        var finishedPoint = RsvpResumePoint(tokenIndex = -1, resumeCursor = -1)
        val context =
            createContext(
                frames = listOf(
                    RsvpFrame(
                        tokens =
                        listOf(
                            Token(text = "in", type = TokenType.WORD),
                            Token(text = "the", type = TokenType.WORD),
                            Token(text = "house", type = TokenType.WORD),
                        ),
                        durationMs = 240L,
                        originalTokenIndex = 2,
                        nextOriginalTokenIndex = 5,
                    ),
                ),
                tokens = listOf(
                    Token(text = "before", type = TokenType.WORD),
                    Token(text = "then", type = TokenType.WORD),
                    Token(text = "in", type = TokenType.WORD),
                    Token(text = "the", type = TokenType.WORD),
                    Token(text = "house", type = TokenType.WORD),
                    Token(text = "after", type = TokenType.WORD),
                ),
                onFinished = { finishedPoint = it },
            )

        completePlayback(context)

        assertEquals(5, finishedPoint.tokenIndex)
    }

    @Test
    fun previewFrameBoundaryDoesNotCompleteWhileFullFramesLoad() {
        val context =
            createContext(
                frames = listOf(
                    RsvpFrame(
                        tokens = listOf(Token(text = "Hello", type = TokenType.WORD)),
                        durationMs = 120L,
                        originalTokenIndex = 0,
                    ),
                ),
                isLoading = true,
            )

        assertFalse(shouldCompleteAtLoadedFrameBoundary(context))
    }

    @Test
    fun manualAdvanceDoesNotCompletePreviewFrames() {
        var finished = false
        val context =
            createContext(
                frames =
                listOf(
                    RsvpFrame(
                        tokens = listOf(Token(text = "Hello", type = TokenType.WORD)),
                        durationMs = 120L,
                        originalTokenIndex = 0,
                        nextOriginalTokenIndex = 3,
                    ),
                ),
                isLoading = true,
                isComplete = false,
                onFinished = { finished = true },
            )

        advanceFrame(context)

        assertFalse(finished)
        assertFalse(context.runtime.completed)
        assertEquals(3, context.runtime.currentTokenIndex)
    }

    @Test
    fun manualAdvanceCompletesReadyFrames() {
        var finished = false
        val context =
            createContext(
                frames =
                listOf(
                    RsvpFrame(
                        tokens = listOf(Token(text = "Hello", type = TokenType.WORD)),
                        durationMs = 120L,
                        originalTokenIndex = 0,
                        nextOriginalTokenIndex = 1,
                    ),
                ),
                isLoading = false,
                isComplete = true,
                onFinished = { finished = true },
            )

        advanceFrame(context)

        assertTrue(finished)
        assertTrue(context.runtime.completed)
    }

    @Test
    fun loadingBoundaryKeepsPlaybackPositionMovingForward() {
        val context =
            createContext(
                frames = listOf(
                    RsvpFrame(
                        tokens = listOf(Token(text = "Hello", type = TokenType.WORD)),
                        durationMs = 120L,
                        originalTokenIndex = 0,
                        nextOriginalTokenIndex = 3,
                    ),
                ),
                isLoading = true,
            )
        context.runtime.scheduledFrameIndex = 0
        context.runtime.nextFrameAtMs = 123L

        holdAtLoadingFrameBoundary(context)

        assertEquals(3, context.runtime.currentTokenIndex)
        assertEquals(-1, context.runtime.currentResumeCursor)
        assertEquals(-1, context.runtime.scheduledFrameIndex)
        assertEquals(0L, context.runtime.nextFrameAtMs)
    }

    @Test
    fun finalFrameBoundaryCompletesAfterFullFramesLoad() {
        val context =
            createContext(
                frames = listOf(
                    RsvpFrame(
                        tokens = listOf(Token(text = "Hello", type = TokenType.WORD)),
                        durationMs = 120L,
                        originalTokenIndex = 0,
                    ),
                ),
                isLoading = false,
            )

        assertTrue(shouldCompleteAtLoadedFrameBoundary(context))
    }

    @Test
    fun effectivePlaybackTempoUsesLoadedFrameBaseTempo() {
        assertEquals(60L, effectivePlaybackTempoMs(baseTempoMs = 120L, tempoScale = 0.5))
        assertEquals(180L, effectivePlaybackTempoMs(baseTempoMs = 120L, tempoScale = 1.5))
    }

    @Test
    fun loadingPreviewFramesDoNotPersistOverRestoredRotationPosition() {
        val previewFrameState =
            RsvpFrameLoadState(
                frames =
                listOf(
                    RsvpFrame(
                        tokens = listOf(Token(text = "Start", type = TokenType.WORD)),
                        durationMs = 120L,
                        originalTokenIndex = 4,
                    ),
                ),
                baseTempoMs = 120L,
                isLoading = true,
            )
        val loadedFrameState = previewFrameState.copy(isLoading = false)

        assertFalse(shouldSyncPositionFromFrameState(previewFrameState))
        assertTrue(shouldSyncPositionFromFrameState(loadedFrameState))
    }

    @Test
    fun visiblePreviewFramesStillUpdateRuntimeResumePoint() {
        val runtime =
            RsvpRuntimeState().apply {
                frameIndex = 1
                currentTokenIndex = 4
                currentResumeCursor = 40
            }
        val frames =
            listOf(
                RsvpFrame(
                    tokens = listOf(Token(text = "First", type = TokenType.WORD)),
                    durationMs = 120L,
                    originalTokenIndex = 4,
                    resumeCursor = 40,
                ),
                RsvpFrame(
                    tokens = listOf(Token(text = "Second", type = TokenType.WORD)),
                    durationMs = 120L,
                    originalTokenIndex = 8,
                    resumeCursor = 80,
                ),
            )

        syncRuntimePositionFromVisibleFrame(
            runtime = runtime,
            frames = frames,
            book = rsvpBookContext(startIndex = 4, startResumeCursor = 40),
        )

        assertEquals(8, runtime.currentTokenIndex)
        assertEquals(80, runtime.currentResumeCursor)
    }

    @Test
    fun visibleFrameSyncClampsRuntimeFrameIndexToLoadedFrames() {
        val runtime =
            RsvpRuntimeState().apply {
                frameIndex = 10
                currentTokenIndex = 0
                currentResumeCursor = 0
            }
        val frames =
            listOf(
                RsvpFrame(
                    tokens = listOf(Token(text = "Only", type = TokenType.WORD)),
                    durationMs = 120L,
                    originalTokenIndex = 12,
                    resumeCursor = 120,
                ),
            )

        syncRuntimePositionFromVisibleFrame(
            runtime = runtime,
            frames = frames,
            book = rsvpBookContext(startIndex = 4, startResumeCursor = 40),
        )

        assertEquals(0, runtime.frameIndex)
        assertEquals(12, runtime.currentTokenIndex)
        assertEquals(120, runtime.currentResumeCursor)
    }

    @Test
    fun readyFrameSetsCanSaveTheirAlreadyAlignedPositionImmediately() {
        val initialFrames = frameListAt(12)
        val reloadedFrames = frameListAt(12)

        assertTrue(shouldSyncPositionFromFrameState(loadedFrameState(initialFrames)))
        assertTrue(shouldSyncPositionFromFrameState(loadedFrameState(reloadedFrames)))
    }

    @Test
    fun positionSyncWaitsForLoadingPreviewToBeReplaced() {
        val frames = frameListAt(7)

        assertFalse(shouldSyncPositionFromFrameState(loadedFrameState(frames, isLoading = true)))
        assertTrue(shouldSyncPositionFromFrameState(loadedFrameState(frames)))
    }

    @Test
    fun sessionKeyDoesNotChangeWhenResumeCursorArrivesLater() {
        val base =
            RsvpBookContext(
                bookId = BookId("book"),
                chapterIndex = 2,
                tokens = emptyList(),
                startIndex = 42,
                startResumeCursor = -1,
            )

        assertEquals(
            buildSessionKey(base),
            buildSessionKey(base.copy(startResumeCursor = 99)),
        )
    }

    @Test
    fun sessionKeyStaysOnLaunchStartWhenLiveStartRestoresAfterRotation() {
        val base =
            RsvpBookContext(
                bookId = BookId("book"),
                chapterIndex = 2,
                tokens = emptyList(),
                startIndex = 42,
                startResumeCursor = -1,
                sessionStartIndex = 5,
            )

        assertEquals(
            buildSessionKey(base),
            buildSessionKey(base.copy(startIndex = 99)),
        )
        assertNotEquals(
            buildSessionKey(base),
            buildSessionKey(base.copy(sessionStartIndex = 6)),
        )
    }

    @Test
    fun frameReloadStartsFromLivePreviewPosition() {
        assertEquals(
            42,
            resolveFrameLoadStartIndex(
                bookStartIndex = 5,
                previewStartIndex = 42,
                tokenCount = 100,
            ),
        )
    }

    @Test
    fun frameReloadFallsBackToRouteStartWhenLivePositionIsMissing() {
        assertEquals(
            5,
            resolveFrameLoadStartIndex(
                bookStartIndex = 5,
                previewStartIndex = -1,
                tokenCount = 100,
            ),
        )
    }

    @Test
    fun frameReloadClampsLivePreviewPositionToLoadedTokens() {
        assertEquals(
            99,
            resolveFrameLoadStartIndex(
                bookStartIndex = 5,
                previewStartIndex = 140,
                tokenCount = 100,
            ),
        )
    }

    @Test
    fun finishPositioningPersistsBothBiasAxes() {
        var savedVerticalBias = 0f
        var savedHorizontalBias = 0f
        val context =
            createContext(
                onVerticalBiasChange = { savedVerticalBias = it },
                onHorizontalBiasChange = { savedHorizontalBias = it },
            )
        context.runtime.isPositioningMode = true
        context.runtime.currentVerticalBias = 0.24f
        context.runtime.currentHorizontalBias = -0.18f

        finishPositioning(context, resumeIfWasPlaying = false)

        assertFalse(context.runtime.isPositioningMode)
        assertEquals(0.24f, savedVerticalBias, 0f)
        assertEquals(-0.18f, savedHorizontalBias, 0f)
    }

    @Test
    fun resumePlaybackResetsSchedulerState() {
        val runtime =
            RsvpRuntimeState().apply {
                frameIndex = 4
                scheduledFrameIndex = 9
                nextFrameAtMs = 1234L
                isPlaying = false
            }

        resumePlayback(runtime)

        assertEquals(4, runtime.rampStartFrameIndex)
        assertEquals(-1, runtime.scheduledFrameIndex)
        assertEquals(0L, runtime.nextFrameAtMs)
        assertTrue(runtime.isPlaying)
    }

    @Test
    fun pausePlaybackNotifiesSaveableStateSynchronously() {
        var savedIsPlaying = true
        var savedCompleted = false
        val runtime =
            RsvpRuntimeState(
                onPlaybackStateChanged = { isPlaying, completed ->
                    savedIsPlaying = isPlaying
                    savedCompleted = completed
                },
            )

        runtime.isPlaying = false

        assertFalse(savedIsPlaying)
        assertFalse(savedCompleted)
    }

    @Test
    fun pausingClearsPreparationCreditBeforeAnExplicitResume() {
        val runtime = RsvpRuntimeState().apply { startupPreparationMs = 500L }

        runtime.isPlaying = false

        assertEquals(0L, runtime.startupPreparationMs)
    }

    @Test
    fun persistedTempoDoesNotOverrideLocalTempoOverride() {
        val shouldApply =
            shouldApplyPersistedTempo(
                currentTempoMsPerWord = 10L,
                incomingTempoMsPerWord = 115L,
                lastSelectedProfileId = "builtin:BALANCED",
                incomingSelectedProfileId = "builtin:SPRINT",
            )

        assertFalse(shouldApply)
    }

    @Test
    fun persistedTempoAppliesWhenSessionStillMatchesPersistedTempo() {
        val shouldApply =
            shouldApplyPersistedTempo(
                currentTempoMsPerWord = 115L,
                incomingTempoMsPerWord = 90L,
                lastSelectedProfileId = "builtin:BALANCED",
                incomingSelectedProfileId = "builtin:SPRINT",
            )

        assertTrue(shouldApply)
    }

    @Test
    fun persistedTempoAcknowledgesMatchingIncomingTempo() {
        val shouldApply =
            shouldApplyPersistedTempo(
                currentTempoMsPerWord = 10L,
                incomingTempoMsPerWord = 10L,
                lastSelectedProfileId = "builtin:SPRINT",
                incomingSelectedProfileId = "custom:unsaved",
            )

        assertTrue(shouldApply)
    }

    @Test
    fun persistedTempoDoesNotReapplyForSameProfileWhenLiveTempoDiffers() {
        val shouldApply =
            shouldApplyPersistedTempo(
                currentTempoMsPerWord = 80L,
                incomingTempoMsPerWord = 115L,
                lastSelectedProfileId = "custom:unsaved",
                incomingSelectedProfileId = "custom:unsaved",
            )

        assertFalse(shouldApply)
    }

    private fun createContext(
        frames: List<RsvpFrame> = emptyList(),
        tokens: List<Token> = listOf(Token(text = "Hello", type = TokenType.WORD)),
        isLoading: Boolean = false,
        isComplete: Boolean = !isLoading,
        onFinished: (RsvpResumePoint) -> Unit = {},
        onVerticalBiasChange: (Float) -> Unit = {},
        onHorizontalBiasChange: (Float) -> Unit = {},
    ): RsvpUiContext {
        val runtime = RsvpRuntimeState()
        val state =
            RsvpScreenState(
                book = RsvpBookContext(BookId("book"), chapterIndex = 0, tokens = tokens, startIndex = 0),
                profile =
                RsvpProfileContext(
                    config = RsvpConfig(),
                    selectedProfileId = "builtin",
                    customProfiles = emptyList(),
                ),
                uiPrefs =
                RsvpUiPreferences(
                    extremeSpeedUnlocked = false,
                    readerTheme = ReaderTheme.LIGHT,
                    focusModeEnabled = false,
                ),
                textStyle = RsvpTextStyle(),
                layoutBias = RsvpLayoutBias(),
            )
        val callbacks =
            RsvpScreenCallbacks(
                bookmarks =
                RsvpBookmarkCallbacks(
                    onAddBookmark = { _, _ -> },
                    onOpenBookmarks = {},
                ),
                playback =
                RsvpPlaybackCallbacks(
                    onFinished = onFinished,
                    onPositionChanged = {},
                    onTempoChange = {},
                    onExit = {},
                ),
                preferences =
                RsvpPreferenceCallbacks(
                    onExtremeSpeedUnlockedChange = {},
                    onSelectProfile = {},
                    onSaveCustomProfile = { _, _ -> },
                    onDeleteCustomProfile = {},
                    onRsvpConfigChange = {},
                ),
                ui =
                RsvpUiCallbacks(
                    onFocusModeEnabledChange = {},
                    onRsvpFontSizeChange = {},
                    onRsvpTextBrightnessChange = {},
                    onRsvpFontWeightChange = {},
                    onRsvpFontFamilyChange = {},
                ),
                theme =
                RsvpThemeCallbacks(
                    onThemeChange = {},
                    onVerticalBiasChange = onVerticalBiasChange,
                    onHorizontalBiasChange = onHorizontalBiasChange,
                ),
            )

        return RsvpUiContext(
            state = state,
            callbacks = callbacks,
            runtime = runtime,
            frameState =
            RsvpFrameLoadState(
                frames = frames,
                baseTempoMs = RsvpConfig().tempoMsPerWord,
                isLoading = isLoading,
                isComplete = isComplete,
            ),
            timing = RsvpTimingInfo(minTempoMs = 1L, maxTempoMs = 1000L, tempoScale = 1.0),
        )
    }

    private fun frameListAt(originalTokenIndex: Int): List<RsvpFrame> =
        listOf(
            RsvpFrame(
                tokens = listOf(Token(text = "w$originalTokenIndex", type = TokenType.WORD)),
                durationMs = 120L,
                originalTokenIndex = originalTokenIndex,
            )
        )

    private fun loadedFrameState(
        frames: List<RsvpFrame>,
        isLoading: Boolean = false,
    ): RsvpFrameLoadState =
        RsvpFrameLoadState(
            frames = frames,
            baseTempoMs = 120L,
            isLoading = isLoading,
        )

    private fun rsvpBookContext(
        startIndex: Int,
        startResumeCursor: Int,
    ): RsvpBookContext =
        RsvpBookContext(
            bookId = BookId("book"),
            chapterIndex = 0,
            tokens = emptyList(),
            startIndex = startIndex,
            startResumeCursor = startResumeCursor,
        )
}
