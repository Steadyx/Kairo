package com.example.kairo.ui.rsvp

import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFrame
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpPlaybackStateTest {
    @Test
    fun frameLoadConfigKeyIgnoresTempoButKeepsOtherConfigChanges() {
        val baseConfig = RsvpConfig(tempoMsPerWord = 120L, baseWpm = 500, commaPauseMs = 95L)

        assertEquals(
            frameLoadConfigKey(baseConfig),
            frameLoadConfigKey(baseConfig.copy(tempoMsPerWord = 180L, baseWpm = 333)),
        )
        assertNotEquals(
            frameLoadConfigKey(baseConfig),
            frameLoadConfigKey(baseConfig.copy(commaPauseMs = 140L)),
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

        completePlayback(context)

        assertTrue(context.runtime.completed)
        assertFalse(context.runtime.isPlaying)
        assertEquals(1, finishedPoint.tokenIndex)
        assertEquals(-1, finishedPoint.resumeCursor)
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

    private fun createContext(
        frames: List<RsvpFrame> = emptyList(),
        tokens: List<Token> = listOf(Token(text = "Hello", type = TokenType.WORD)),
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
            frameState = RsvpFrameLoadState(frames = frames, baseTempoMs = RsvpConfig().tempoMsPerWord, isLoading = false),
            timing = RsvpTimingInfo(minTempoMs = 1L, maxTempoMs = 1000L, tempoScale = 1.0),
        )
    }
}
