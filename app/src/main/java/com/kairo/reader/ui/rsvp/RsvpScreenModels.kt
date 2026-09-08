package com.kairo.reader.ui.rsvp

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpCustomProfile
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.RsvpFontWeight
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.TimedReadingMode
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.data.rsvp.RsvpFrameRepository

data class RsvpScreenState(
    val book: RsvpBookContext,
    val profile: RsvpProfileContext,
    val launchTempoMsPerWord: Long? = null,
    val initialIsPlaying: Boolean = true,
    val uiPrefs: RsvpUiPreferences,
    val textStyle: RsvpTextStyle,
    val layoutBias: RsvpLayoutBias,
)

typealias ReadingPresentationMode = TimedReadingMode

data class RsvpBookContext(
    val bookId: BookId,
    val chapterIndex: Int,
    val tokens: List<Token>,
    val startIndex: Int,
    val startResumeCursor: Int = -1,
    val sessionStartIndex: Int = startIndex,
    val generationOptions: RsvpGenerationOptions = RsvpGenerationOptions.DEFAULT,
)

data class RsvpProfileContext(val config: RsvpConfig, val selectedProfileId: String, val customProfiles: List<RsvpCustomProfile>,)

data class RsvpUiPreferences(
    val extremeSpeedUnlocked: Boolean,
    val readerTheme: ReaderTheme,
    val focusModeEnabled: Boolean,
    val positioningGridEnabled: Boolean = true,
    val positioningGridSnap: Float = DEFAULT_POSITIONING_GRID_SNAP,
)

data class RsvpTextStyle(
    val fontSizeSp: Float = DEFAULT_FONT_SIZE_SP,
    val fontFamily: RsvpFontFamily = DEFAULT_FONT_FAMILY,
    val fontWeight: RsvpFontWeight = DEFAULT_FONT_WEIGHT,
    val textBrightness: Float = DEFAULT_TEXT_BRIGHTNESS,
)

data class RsvpLayoutBias(val verticalBias: Float = DEFAULT_VERTICAL_BIAS, val horizontalBias: Float = DEFAULT_HORIZONTAL_BIAS,)

data class RsvpScreenCallbacks(
    val bookmarks: RsvpBookmarkCallbacks,
    val playback: RsvpPlaybackCallbacks,
    val preferences: RsvpPreferenceCallbacks,
    val ui: RsvpUiCallbacks,
    val theme: RsvpThemeCallbacks,
    val bionic: BionicReadingCallbacks = BionicReadingCallbacks(),
)

data class BionicReadingCallbacks(
    val onFixationStrengthChange: (Float) -> Unit = {},
    val onHighlightStrengthChange: (Float) -> Unit = {},
    val onFontSizeChange: (Float) -> Unit = {},
    val onTextBrightnessChange: (Float) -> Unit = {},
)

data class RsvpBookmarkCallbacks(val onAddBookmark: (tokenIndex: Int, previewText: String) -> Unit, val onOpenBookmarks: () -> Unit,)

data class RsvpResumePoint(val tokenIndex: Int, val resumeCursor: Int = -1, val chapterIndex: Int? = null, val tempoMsPerWord: Long = -1L,)

data class RsvpPlaybackCallbacks(
    val onFinished: (RsvpResumePoint) -> Unit,
    val onPositionChanged: (RsvpResumePoint) -> Unit,
    val onTempoChange: (Long) -> Unit,
    val onExit: (RsvpResumePoint) -> Unit,
    val onOpenLibrary: (RsvpResumePoint) -> Unit = {},
    val onPlaybackStateChanged: (Boolean) -> Unit = {},
    val onFrameConsumed: (RsvpFrame) -> Unit = {},
)

data class RsvpPreferenceCallbacks(
    val onExtremeSpeedUnlockedChange: (Boolean) -> Unit,
    val onSelectProfile: (String) -> Unit,
    val onSaveCustomProfile: (String, RsvpConfig) -> Unit,
    val onDeleteCustomProfile: (String) -> Unit,
    val onRsvpConfigChange: ((RsvpConfig) -> RsvpConfig) -> Unit,
)

data class RsvpUiCallbacks(
    val onFocusModeEnabledChange: (Boolean) -> Unit,
    val onRsvpFontSizeChange: (Float) -> Unit,
    val onRsvpTextBrightnessChange: (Float) -> Unit,
    val onRsvpFontWeightChange: (RsvpFontWeight) -> Unit,
    val onRsvpFontFamilyChange: (RsvpFontFamily) -> Unit,
    val onPositioningGridEnabledChange: (Boolean) -> Unit = {},
    val onPositioningGridSnapChange: (Float) -> Unit = {},
)

data class RsvpThemeCallbacks(
    val onThemeChange: (ReaderTheme) -> Unit,
    val onVerticalBiasChange: (Float) -> Unit,
    val onHorizontalBiasChange: (Float) -> Unit,
)

data class RsvpScreenDependencies(val frameRepository: RsvpFrameRepository,)
