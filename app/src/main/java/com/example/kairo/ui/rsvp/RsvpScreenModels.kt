package com.example.kairo.ui.rsvp

import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpCustomProfile
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.Token
import com.example.kairo.data.rsvp.RsvpFrameRepository

data class RsvpScreenState(
    val book: RsvpBookContext,
    val profile: RsvpProfileContext,
    val uiPrefs: RsvpUiPreferences,
    val textStyle: RsvpTextStyle,
    val layoutBias: RsvpLayoutBias,
)

data class RsvpBookContext(val bookId: BookId, val chapterIndex: Int, val tokens: List<Token>, val startIndex: Int,)

data class RsvpProfileContext(val config: RsvpConfig, val selectedProfileId: String, val customProfiles: List<RsvpCustomProfile>,)

data class RsvpUiPreferences(val extremeSpeedUnlocked: Boolean, val readerTheme: ReaderTheme, val focusModeEnabled: Boolean,)

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
)

data class RsvpBookmarkCallbacks(val onAddBookmark: (tokenIndex: Int, previewText: String) -> Unit, val onOpenBookmarks: () -> Unit,)

data class RsvpPlaybackCallbacks(
    val onFinished: (Int) -> Unit,
    val onPositionChanged: (Int) -> Unit,
    val onTempoChange: (Long) -> Unit,
    val onExit: (Int) -> Unit,
)

data class RsvpPreferenceCallbacks(
    val onExtremeSpeedUnlockedChange: (Boolean) -> Unit,
    val onSelectProfile: (String) -> Unit,
    val onSaveCustomProfile: (String, RsvpConfig) -> Unit,
    val onDeleteCustomProfile: (String) -> Unit,
    val onRsvpConfigChange: (RsvpConfig) -> Unit,
)

data class RsvpUiCallbacks(
    val onFocusModeEnabledChange: (Boolean) -> Unit,
    val onRsvpFontSizeChange: (Float) -> Unit,
    val onRsvpTextBrightnessChange: (Float) -> Unit,
    val onRsvpFontWeightChange: (RsvpFontWeight) -> Unit,
    val onRsvpFontFamilyChange: (RsvpFontFamily) -> Unit,
)

data class RsvpThemeCallbacks(
    val onThemeChange: (ReaderTheme) -> Unit,
    val onVerticalBiasChange: (Float) -> Unit,
    val onHorizontalBiasChange: (Float) -> Unit,
)

data class RsvpScreenDependencies(val frameRepository: RsvpFrameRepository,)
