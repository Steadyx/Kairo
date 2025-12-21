@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.kairo.ui.settings.RsvpSettingsContent
import com.example.kairo.ui.settings.SettingsNavRow
import com.example.kairo.ui.settings.SettingsSliderRow
import com.example.kairo.ui.settings.SettingsSwitchRow
import com.example.kairo.ui.settings.ThemeSelector

@Composable
internal fun BoxScope.RsvpQuickSettingsPanel(context: RsvpUiContext, estimatedWpm: Int) {
    val runtime = context.runtime

    AnimatedVisibility(
        visible = runtime.showQuickSettings,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(QUICK_SETTINGS_HEIGHT_FRACTION)
                .navigationBarsPadding()
                .background(
                    MaterialTheme.colorScheme.surface.copy(
                        alpha = QUICK_SETTINGS_BACKGROUND_ALPHA
                    ),
                    RoundedCornerShape(
                        topStart = QUICK_SETTINGS_CORNER,
                        topEnd = QUICK_SETTINGS_CORNER
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = QUICK_SETTINGS_HORIZONTAL_PADDING,
                    vertical = QUICK_SETTINGS_VERTICAL_PADDING
                ),
            verticalArrangement = Arrangement.spacedBy(QUICK_SETTINGS_SPACING)
        ) {
            var showRsvpSettings by remember { mutableStateOf(false) }
            if (showRsvpSettings) {
                RsvpQuickSettingsAdvanced(context) { showRsvpSettings = false }
            } else {
                RsvpQuickSettingsMain(
                    context = context,
                    estimatedWpm = estimatedWpm,
                    onOpenRsvpSettings = { showRsvpSettings = true }
                )
            }
        }
    }
}

@Composable
private fun RsvpQuickSettingsMain(
    context: RsvpUiContext,
    estimatedWpm: Int,
    onOpenRsvpSettings: () -> Unit
) {
    RsvpQuickSettingsHeader()
    RsvpQuickSettingsBookmarks(context, onOpenRsvpSettings)
    RsvpQuickSettingsThemeAndFocus(context)
    RsvpQuickSettingsPositioningToggle(context)
    RsvpQuickSettingsTempoControls(context, estimatedWpm)
    RsvpQuickSettingsTextSizeControls(context)
    RsvpQuickSettingsHints()
}

@Composable
private fun RsvpQuickSettingsHeader() {
    Text(
        "Quick Settings",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun RsvpQuickSettingsBookmarks(
    context: RsvpUiContext,
    onOpenRsvpSettings: () -> Unit
) {
    val runtime = context.runtime

    SettingsNavRow(
        title = "Bookmarks",
        subtitle = "Open saved bookmarks",
        icon = Icons.Default.Bookmark,
        onClick = {
            runtime.showQuickSettings = false
            context.callbacks.bookmarks.onOpenBookmarks()
        }
    )
    SettingsNavRow(
        title = "Add bookmark",
        subtitle = "Save this position",
        icon = Icons.Default.Bookmark,
        showChevron = false,
        onClick = { addBookmarkNow(context) }
    )
    SettingsNavRow(
        title = "RSVP settings",
        subtitle = "Timing profile, readability, display",
        icon = Icons.Default.Settings,
        onClick = onOpenRsvpSettings
    )
}

@Composable
private fun RsvpQuickSettingsThemeAndFocus(context: RsvpUiContext) {
    ThemeSelector(
        selected = context.state.uiPrefs.readerTheme,
        onThemeChange = context.callbacks.theme.onThemeChange
    )
    SettingsSwitchRow(
        title = "Focus mode",
        subtitle = "Hide system chrome while reading.",
        checked = context.state.uiPrefs.focusModeEnabled,
        onCheckedChange = context.callbacks.ui.onFocusModeEnabledChange
    )
}

@Composable
private fun RsvpQuickSettingsPositioningToggle(context: RsvpUiContext) {
    val runtime = context.runtime

    SettingsSwitchRow(
        title = "Positioning mode",
        subtitle = "Swipe to adjust text position.",
        checked = runtime.isPositioningMode,
        onCheckedChange = { enabled ->
            if (enabled) {
                enterPositioningMode(runtime)
            } else {
                finishPositioning(context, resumeIfWasPlaying = false)
            }
        }
    )
}

@Composable
private fun RsvpQuickSettingsTempoControls(context: RsvpUiContext, estimatedWpm: Int) {
    val runtime = context.runtime
    val minTempoMs = context.timing.minTempoMs
    val maxTempoMs = context.timing.maxTempoMs

    Text(
        "~$estimatedWpm WPM",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    SettingsSliderRow(
        title = "Tempo",
        subtitle = "Lower = faster.",
        valueLabel = "${runtime.currentTempoMsPerWord}ms",
        value = runtime.currentTempoMsPerWord.toFloat(),
        onValueChange = { newValue ->
            runtime.currentTempoMsPerWord = newValue.toLong().coerceIn(minTempoMs, maxTempoMs)
        },
        onValueChangeFinished = {
            context.callbacks.playback.onTempoChange(runtime.currentTempoMsPerWord)
        },
        valueRange = minTempoMs.toFloat()..maxTempoMs.toFloat()
    )
    SettingsSwitchRow(
        title = "Unlock extreme speeds",
        subtitle = "Allows down to ${EXTREME_MIN_TEMPO_MS_PER_WORD}ms (can become unreadable).",
        checked = context.state.uiPrefs.extremeSpeedUnlocked,
        onCheckedChange = { enabled ->
            context.callbacks.preferences.onExtremeSpeedUnlockedChange(enabled)
            if (!enabled && runtime.currentTempoMsPerWord < SAFE_MIN_TEMPO_MS_PER_WORD) {
                runtime.currentTempoMsPerWord = SAFE_MIN_TEMPO_MS_PER_WORD
                context.callbacks.playback.onTempoChange(runtime.currentTempoMsPerWord)
            }
        }
    )
}

@Composable
private fun RsvpQuickSettingsTextSizeControls(context: RsvpUiContext) {
    val runtime = context.runtime

    SettingsSliderRow(
        title = "Text size",
        valueLabel = "${runtime.currentFontSizeSp.toInt()}sp",
        value = runtime.currentFontSizeSp,
        onValueChange = { newValue ->
            runtime.currentFontSizeSp = newValue.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
            runtime.showFontSizeIndicator = true
        },
        onValueChangeFinished = {
            context.callbacks.ui.onRsvpFontSizeChange(runtime.currentFontSizeSp)
        },
        valueRange = MIN_FONT_SIZE_SP..MAX_FONT_SIZE_SP
    )
}

@Composable
private fun RsvpQuickSettingsHints() {
    Text(
        "Swipe up/down to adjust speed\nUse sliders to preview changes\nLong press to exit",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RsvpQuickSettingsAdvanced(
    context: RsvpUiContext,
    onBack: () -> Unit
) {
    SettingsNavRow(
        title = "Back",
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        showChevron = false,
        onClick = onBack
    )
    Text(
        "RSVP settings",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
    RsvpQuickSettingsAdvancedContent(context)
}

@Composable
private fun RsvpQuickSettingsAdvancedContent(context: RsvpUiContext) {
    val runtime = context.runtime
    val profile = context.state.profile

    val configForSettings = remember(profile.config, runtime.currentTempoMsPerWord) {
        profile.config.copy(tempoMsPerWord = runtime.currentTempoMsPerWord)
    }
    RsvpSettingsContent(
        selectedProfileId = profile.selectedProfileId,
        customProfiles = profile.customProfiles,
        config = configForSettings,
        unlockExtremeSpeed = context.state.uiPrefs.extremeSpeedUnlocked,
        rsvpFontSizeSp = runtime.currentFontSizeSp,
        rsvpTextBrightness = runtime.currentTextBrightness,
        rsvpFontFamily = runtime.currentFontFamily,
        rsvpFontWeight = runtime.currentFontWeight,
        rsvpVerticalBias = runtime.currentVerticalBias,
        rsvpHorizontalBias = runtime.currentHorizontalBias,
        onSelectProfile = context.callbacks.preferences.onSelectProfile,
        onSaveCustomProfile = context.callbacks.preferences.onSaveCustomProfile,
        onDeleteCustomProfile = context.callbacks.preferences.onDeleteCustomProfile,
        onConfigChange = { updated ->
            runtime.currentTempoMsPerWord = updated.tempoMsPerWord
            context.callbacks.preferences.onRsvpConfigChange(updated)
        },
        onUnlockExtremeSpeedChange = context.callbacks.preferences.onExtremeSpeedUnlockedChange,
        onRsvpFontSizeChange = { size ->
            runtime.currentFontSizeSp = size
            runtime.showFontSizeIndicator = true
            context.callbacks.ui.onRsvpFontSizeChange(size)
        },
        onRsvpTextBrightnessChange = { brightness ->
            runtime.currentTextBrightness = brightness
            context.callbacks.ui.onRsvpTextBrightnessChange(brightness)
        },
        onRsvpFontWeightChange = { weight ->
            runtime.currentFontWeight = weight
            context.callbacks.ui.onRsvpFontWeightChange(weight)
        },
        onRsvpFontFamilyChange = { family ->
            runtime.currentFontFamily = family
            context.callbacks.ui.onRsvpFontFamilyChange(family)
        },
        onRsvpVerticalBiasChange = { bias ->
            runtime.currentVerticalBias = bias
            context.callbacks.theme.onVerticalBiasChange(bias)
        },
        onRsvpHorizontalBiasChange = { bias ->
            runtime.currentHorizontalBias = bias
            context.callbacks.theme.onHorizontalBiasChange(bias)
        }
    )
}
