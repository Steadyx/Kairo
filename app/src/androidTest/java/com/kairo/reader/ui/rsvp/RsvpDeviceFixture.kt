package com.kairo.reader.ui.rsvp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kairo.reader.core.dispatchers.DefaultDispatcherProvider
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.rsvp.ComprehensionRsvpEngine
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.data.rsvp.RsvpFrameRepository
import com.kairo.reader.data.rsvp.RsvpFrameRepositoryImpl
import com.kairo.reader.data.rsvp.RsvpFrameSet
import com.kairo.reader.data.token.TokenRepository
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Real playback and frame generation with no personal library or preference writes. */
internal class RsvpDeviceFixture(tokens: List<Token>, config: RsvpConfig, startIndex: Int = 0, resumeCursor: Int = -1) {
    var session by mutableIntStateOf(0)
    var state by mutableStateOf(
        RsvpScreenState(
            book = RsvpBookContext(BookId("rsvp-device-fixture"), 0, tokens, startIndex, resumeCursor),
            profile = RsvpProfileContext(config, "device-fixture", emptyList()),
            initialIsPlaying = true,
            uiPrefs = RsvpUiPreferences(false, ReaderTheme.LIGHT, false),
            textStyle = RsvpTextStyle(),
            layoutBias = RsvpLayoutBias(),
        ),
    )
    val consumed = CopyOnWriteArrayList<RsvpFrame>()

    @Volatile var saved: RsvpResumePoint? = null

    @Volatile var finished = false
    val loads = AtomicInteger()
    private val repository = RsvpFrameRepositoryImpl(
        tokenRepository = object : TokenRepository {
            override suspend fun getTokens(bookId: BookId, chapterIndex: Int, chapter: Chapter?): List<Token> = tokens
        },
        engine = ComprehensionRsvpEngine(),
        dispatcherProvider = DefaultDispatcherProvider(),
    )
    val dependencies = RsvpScreenDependencies(
        object : RsvpFrameRepository by repository {
            override suspend fun getFrames(
                bookId: BookId,
                chapterIndex: Int,
                config: RsvpConfig,
                startIndex: Int,
                options: RsvpGenerationOptions,
            ): RsvpFrameSet = repository.getFrames(bookId, chapterIndex, config, startIndex, options).also { loads.incrementAndGet() }
        },
    )
    val callbacks = RsvpScreenCallbacks(
        bookmarks = RsvpBookmarkCallbacks({ _, _ -> }, {}),
        playback = RsvpPlaybackCallbacks(
            onFinished = { finished = true },
            onPositionChanged = {},
            onTempoChange = {},
            onExit = { saved = it },
            onFrameConsumed = { consumed += it },
        ),
        preferences = RsvpPreferenceCallbacks(
            onExtremeSpeedUnlockedChange = {},
            onSelectProfile = {},
            onSaveCustomProfile = { _, _ -> },
            onDeleteCustomProfile = {},
            onRsvpConfigChange = { update ->
                state = state.copy(profile = state.profile.copy(config = update(state.profile.config)))
            },
        ),
        ui = RsvpUiCallbacks(
            onFocusModeEnabledChange = {},
            onRsvpFontSizeChange = {},
            onRsvpTextBrightnessChange = {},
            onRsvpFontWeightChange = {},
            onRsvpFontFamilyChange = {},
        ),
        theme = RsvpThemeCallbacks({}, {}, {}),
    )
}
