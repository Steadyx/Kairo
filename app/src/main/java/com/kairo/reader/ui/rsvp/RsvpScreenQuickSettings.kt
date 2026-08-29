@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.rsvp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.rsvp.RsvpSpeedControl
import com.kairo.reader.ui.settings.BionicSettingsContent
import com.kairo.reader.ui.settings.RsvpSettingsActions
import com.kairo.reader.ui.settings.RsvpSettingsContent
import com.kairo.reader.ui.settings.RsvpSettingsState
import com.kairo.reader.ui.settings.SettingsNavRow
import com.kairo.reader.ui.settings.SettingsSliderRow
import com.kairo.reader.ui.settings.SettingsSwitchRow
import com.kairo.reader.ui.settings.ThemeSelector
import kotlin.math.roundToInt

/**
 * These modifiers target the tutorial-highlighted panel and settings row, not the visibility
 * container that is this composable's root.
 */
@Suppress("ModifierParameter")
@Composable
internal fun BoxScope.RsvpQuickSettingsPanel(
    context: RsvpUiContext,
    speedPercent: Int,
    panelModifier: Modifier = Modifier,
    settingsRowModifier: Modifier = Modifier,
) {
    val runtime = context.runtime

    AnimatedVisibility(
        visible = runtime.showQuickSettings,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Box(
            modifier =
            panelModifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(QUICK_SETTINGS_OUTER_PADDING),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier =
                Modifier
                    .widthIn(max = QUICK_SETTINGS_MAX_WIDTH)
                    .fillMaxWidth()
                    .fillMaxHeight(QUICK_SETTINGS_HEIGHT_FRACTION)
                    .clip(RoundedCornerShape(QUICK_SETTINGS_CORNER))
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = QUICK_SETTINGS_BACKGROUND_ALPHA),
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = QUICK_SETTINGS_BORDER_ALPHA),
                        shape = RoundedCornerShape(QUICK_SETTINGS_CORNER),
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = QUICK_SETTINGS_HORIZONTAL_PADDING,
                        vertical = QUICK_SETTINGS_VERTICAL_PADDING,
                    ),
                verticalArrangement = Arrangement.spacedBy(QUICK_SETTINGS_SPACING),
                horizontalAlignment = Alignment.Start,
            ) {
                RsvpQuickSettingsHandle()
                var showModeSettings by remember { mutableStateOf(false) }
                if (showModeSettings) {
                    RsvpQuickSettingsAdvanced(context) { showModeSettings = false }
                } else {
                    RsvpQuickSettingsMain(
                        context = context,
                        speedPercent = speedPercent,
                        onOpenModeSettings = { showModeSettings = true },
                        settingsRowModifier = settingsRowModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun RsvpQuickSettingsHandle() {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(bottom = QUICK_SETTINGS_HANDLE_BOTTOM_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
            Modifier
                .width(QUICK_SETTINGS_HANDLE_WIDTH)
                .height(QUICK_SETTINGS_HANDLE_HEIGHT)
                .clip(RoundedCornerShape(QUICK_SETTINGS_HANDLE_HEIGHT))
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = QUICK_SETTINGS_HANDLE_ALPHA),
                ),
        )
    }
}

/** [settingsRowModifier] is forwarded to the tutorial-highlighted settings row. */
@Suppress("ModifierParameter")
@Composable
private fun RsvpQuickSettingsMain(
    context: RsvpUiContext,
    speedPercent: Int,
    onOpenModeSettings: () -> Unit,
    settingsRowModifier: Modifier = Modifier,
) {
    RsvpQuickSettingsHeader(context)
    if (context.presentationMode == ReadingPresentationMode.BIONIC) {
        RsvpQuickSettingsTempoControls(context, speedPercent)
        RsvpQuickSettingsTextSizeControls(context)
        RsvpQuickSettingsThemeAndFocus(context)
        RsvpQuickSettingsBookmarks(
            context = context,
            onOpenModeSettings = onOpenModeSettings,
            settingsRowModifier = settingsRowModifier,
        )
    } else {
        RsvpQuickSettingsBookmarks(
            context = context,
            onOpenModeSettings = onOpenModeSettings,
            settingsRowModifier = settingsRowModifier,
        )
        RsvpQuickSettingsThemeAndFocus(context)
        RsvpQuickSettingsPositioningToggle(context)
        RsvpQuickSettingsContextAssist(context)
        RsvpQuickSettingsTempoControls(context, speedPercent)
        RsvpQuickSettingsTextSizeControls(context)
    }
    RsvpQuickSettingsHints()
}

@Composable
private fun RsvpQuickSettingsContextAssist(context: RsvpUiContext) {
    val enabled = context.state.profile.config.contextAssistMode != RsvpContextAssistMode.OFF
    SettingsSwitchRow(
        title = stringResource(R.string.rsvp_context_assist_quick_title),
        subtitle = stringResource(R.string.rsvp_context_assist_quick_subtitle),
        checked = enabled,
        onCheckedChange = { shouldEnable ->
            context.callbacks.preferences.onRsvpConfigChange { config ->
                config.copy(
                    contextAssistMode =
                    if (shouldEnable) {
                        RsvpContextAssistMode.PREVIOUS_WORDS
                    } else {
                        RsvpContextAssistMode.OFF
                    },
                )
            }
        },
    )
}

@Composable
private fun RsvpQuickSettingsHeader(context: RsvpUiContext) {
    Text(
        stringResource(
            if (context.presentationMode == ReadingPresentationMode.BIONIC) {
                R.string.bionic_quick_settings_title
            } else {
                R.string.rsvp_quick_settings_title
            }
        ),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** [settingsRowModifier] targets only the mode-settings navigation row. */
@Suppress("ModifierParameter")
@Composable
private fun RsvpQuickSettingsBookmarks(
    context: RsvpUiContext,
    onOpenModeSettings: () -> Unit,
    settingsRowModifier: Modifier = Modifier,
) {
    val runtime = context.runtime

    SettingsNavRow(
        title = stringResource(R.string.library_tab_saved),
        subtitle = stringResource(R.string.saved_subtitle),
        icon = Icons.Default.Bookmark,
        onClick = {
            runtime.showQuickSettings = false
            context.callbacks.playback.onPositionChanged(currentResumePoint(context))
            context.callbacks.bookmarks.onOpenBookmarks()
        },
    )
    SettingsNavRow(
        title = stringResource(R.string.reader_add_bookmark),
        subtitle = stringResource(R.string.reader_add_bookmark_subtitle),
        icon = Icons.Default.Bookmark,
        showChevron = false,
        onClick = { addBookmarkNow(context) },
    )
    SettingsNavRow(
        modifier = settingsRowModifier,
        title =
        stringResource(
            if (context.presentationMode == ReadingPresentationMode.BIONIC) {
                R.string.bionic_settings_title
            } else {
                R.string.rsvp_settings_title
            }
        ),
        subtitle =
        stringResource(
            if (context.presentationMode == ReadingPresentationMode.BIONIC) {
                R.string.settings_bionic_subtitle
            } else {
                R.string.rsvp_settings_subtitle
            }
        ),
        icon = Icons.Default.Settings,
        onClick = onOpenModeSettings,
    )
}

@Composable
private fun RsvpQuickSettingsThemeAndFocus(context: RsvpUiContext) {
    ThemeSelector(
        selected = context.state.uiPrefs.readerTheme,
        onThemeChange = context.callbacks.theme.onThemeChange,
    )
    SettingsSwitchRow(
        title = stringResource(R.string.focus_mode_title),
        subtitle = stringResource(R.string.focus_mode_subtitle),
        checked = context.state.uiPrefs.focusModeEnabled,
        onCheckedChange = context.callbacks.ui.onFocusModeEnabledChange,
    )
}

@Composable
private fun RsvpQuickSettingsPositioningToggle(context: RsvpUiContext) {
    val runtime = context.runtime

    SettingsSwitchRow(
        title = stringResource(R.string.rsvp_positioning_mode_title),
        subtitle = stringResource(R.string.rsvp_positioning_mode_subtitle),
        checked = runtime.isPositioningMode,
        onCheckedChange = { enabled ->
            if (enabled) {
                enterPositioningMode(runtime)
            } else {
                finishPositioning(context, resumeIfWasPlaying = false)
            }
        },
    )
    RsvpQuickSettingsPositioningGrid(context)
}

@Composable
private fun RsvpQuickSettingsPositioningGrid(context: RsvpUiContext) {
    val uiPrefs = context.state.uiPrefs

    SettingsSwitchRow(
        title = stringResource(R.string.rsvp_positioning_grid_title),
        subtitle = stringResource(R.string.rsvp_positioning_grid_subtitle),
        checked = uiPrefs.positioningGridEnabled,
        onCheckedChange = context.callbacks.ui.onPositioningGridEnabledChange,
    )
    if (uiPrefs.positioningGridEnabled) {
        var snapStrength by remember(uiPrefs.positioningGridSnap) {
            mutableFloatStateOf(uiPrefs.positioningGridSnap)
        }
        SettingsSliderRow(
            title = stringResource(R.string.rsvp_positioning_grid_snap_title),
            subtitle = stringResource(R.string.rsvp_positioning_grid_snap_subtitle),
            valueLabel =
            stringResource(
                R.string.rsvp_positioning_grid_snap_value,
                (snapStrength * PERCENT_SCALE).roundToInt(),
            ),
            value = snapStrength,
            onValueChange = { snapStrength = it.coerceIn(0f, 1f) },
            onValueChangeFinished = {
                context.callbacks.ui.onPositioningGridSnapChange(snapStrength)
            },
            valueRange = 0f..1f,
        )
    }
}

@Composable
private fun RsvpQuickSettingsTempoControls(
    context: RsvpUiContext,
    speedPercent: Int,
) {
    val runtime = context.runtime
    val minTempoMs = context.timing.minTempoMs
    val maxTempoMs = context.timing.maxTempoMs
    val speedLabel =
        stringResource(
            rsvpSpeedBandLabelRes(
                tempoMsPerWord = runtime.currentTempoMsPerWord,
                extremeUnlocked = context.state.uiPrefs.extremeSpeedUnlocked,
            ),
        )
    val speedValueLabel =
        stringResource(R.string.rsvp_reading_speed_indicator, speedLabel, speedPercent)

    Text(
        stringResource(R.string.rsvp_reading_speed_summary, speedLabel, speedPercent),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SettingsSliderRow(
        title = stringResource(R.string.rsvp_reading_speed_title),
        subtitle = stringResource(R.string.rsvp_reading_speed_subtitle),
        valueLabel = speedValueLabel,
        value = speedPercent.toFloat(),
        onValueChange = { newValue ->
            runtime.currentTempoMsPerWord =
                RsvpSpeedControl.tempoForSpeed(
                    speed = newValue,
                    minTempoMsPerWord = minTempoMs,
                    maxTempoMsPerWord = maxTempoMs,
                )
        },
        onValueChangeFinished = {
            context.callbacks.playback.onTempoChange(runtime.currentTempoMsPerWord)
        },
        valueRange = RsvpSpeedControl.MIN_SPEED..RsvpSpeedControl.MAX_SPEED,
    )
    SettingsSwitchRow(
        title = stringResource(R.string.rsvp_unlock_extreme_speeds_title),
        subtitle =
        stringResource(
            R.string.rsvp_unlock_extreme_speeds_subtitle,
            EXTREME_MIN_TEMPO_MS_PER_WORD,
        ),
        checked = context.state.uiPrefs.extremeSpeedUnlocked,
        onCheckedChange = { enabled ->
            context.callbacks.preferences.onExtremeSpeedUnlockedChange(enabled)
            if (!enabled && runtime.currentTempoMsPerWord < SAFE_MIN_TEMPO_MS_PER_WORD) {
                runtime.currentTempoMsPerWord = SAFE_MIN_TEMPO_MS_PER_WORD
                context.callbacks.playback.onTempoChange(runtime.currentTempoMsPerWord)
            }
        },
    )
}

@Composable
private fun RsvpQuickSettingsTextSizeControls(context: RsvpUiContext) {
    val runtime = context.runtime

    SettingsSliderRow(
        title =
        stringResource(
            if (context.presentationMode == ReadingPresentationMode.BIONIC) {
                R.string.bionic_text_size_title
            } else {
                R.string.rsvp_text_size_title
            }
        ),
        valueLabel =
        stringResource(
            R.string.format_sp,
            runtime.currentFontSizeSp.toInt(),
        ),
        value = runtime.currentFontSizeSp,
        onValueChange = { newValue ->
            runtime.currentFontSizeSp = newValue.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
            runtime.showFontSizeIndicator = true
        },
        onValueChangeFinished = {
            if (context.presentationMode == ReadingPresentationMode.BIONIC) {
                context.callbacks.bionic.onFontSizeChange(runtime.currentFontSizeSp)
            } else {
                context.callbacks.ui.onRsvpFontSizeChange(runtime.currentFontSizeSp)
            }
        },
        valueRange =
        if (context.presentationMode == ReadingPresentationMode.BIONIC) {
            com.kairo.reader.ui.bionic.BIONIC_MIN_FONT_SIZE_SP..com.kairo.reader.ui.bionic.BIONIC_MAX_FONT_SIZE_SP
        } else {
            MIN_FONT_SIZE_SP..MAX_FONT_SIZE_SP
        },
    )
}

@Composable
private fun RsvpQuickSettingsHints() {
    Text(
        stringResource(R.string.rsvp_quick_settings_hints),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RsvpQuickSettingsAdvanced(
    context: RsvpUiContext,
    onBack: () -> Unit,
) {
    SettingsNavRow(
        title = stringResource(R.string.action_back),
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        showChevron = false,
        onClick = onBack,
    )
    Text(
        stringResource(
            if (context.presentationMode == ReadingPresentationMode.BIONIC) {
                R.string.bionic_settings_title
            } else {
                R.string.rsvp_settings_title
            }
        ),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    RsvpQuickSettingsAdvancedContent(context)
}

@Composable
private fun RsvpQuickSettingsAdvancedContent(context: RsvpUiContext) {
    if (context.presentationMode == ReadingPresentationMode.BIONIC) {
        BionicQuickSettingsAdvancedContent(context)
        return
    }
    val runtime = context.runtime
    val profile = context.state.profile
    RsvpSettingsContent(
        state =
        RsvpSettingsState(
            selectedProfileId = profile.selectedProfileId,
            customProfiles = profile.customProfiles,
            config = profile.config,
            tempoMsPerWord = runtime.currentTempoMsPerWord,
            profileComparisonConfig = profile.config,
            unlockExtremeSpeed = context.state.uiPrefs.extremeSpeedUnlocked,
            fontSizeSp = runtime.currentFontSizeSp,
            textBrightness = runtime.currentTextBrightness,
            fontFamily = runtime.currentFontFamily,
            fontWeight = runtime.currentFontWeight,
            verticalBias = runtime.currentVerticalBias,
            horizontalBias = runtime.currentHorizontalBias,
        ),
        actions =
        RsvpSettingsActions(
            onSelectProfile = context.callbacks.preferences.onSelectProfile,
            onSaveCustomProfile = context.callbacks.preferences.onSaveCustomProfile,
            onDeleteCustomProfile = context.callbacks.preferences.onDeleteCustomProfile,
            onTempoMsPerWordChange = { updatedTempoMsPerWord ->
                runtime.currentTempoMsPerWord = updatedTempoMsPerWord
                context.callbacks.playback.onTempoChange(updatedTempoMsPerWord)
            },
            onConfigChange = context.callbacks.preferences.onRsvpConfigChange,
            onUnlockExtremeSpeedChange = context.callbacks.preferences.onExtremeSpeedUnlockedChange,
            onFontSizeChange = { size ->
                runtime.currentFontSizeSp = size
                runtime.showFontSizeIndicator = true
                context.callbacks.ui.onRsvpFontSizeChange(size)
            },
            onTextBrightnessChange = { brightness ->
                runtime.currentTextBrightness = brightness
                context.callbacks.ui.onRsvpTextBrightnessChange(brightness)
            },
            onFontWeightChange = { weight ->
                runtime.currentFontWeight = weight
                context.callbacks.ui.onRsvpFontWeightChange(weight)
            },
            onFontFamilyChange = { family ->
                runtime.currentFontFamily = family
                context.callbacks.ui.onRsvpFontFamilyChange(family)
            },
            onVerticalBiasChange = { bias ->
                runtime.currentVerticalBias = bias
                context.callbacks.theme.onVerticalBiasChange(bias)
            },
            onHorizontalBiasChange = { bias ->
                runtime.currentHorizontalBias = bias
                context.callbacks.theme.onHorizontalBiasChange(bias)
            },
        ),
    )
}

@Composable
private fun BionicQuickSettingsAdvancedContent(context: RsvpUiContext) {
    val runtime = context.runtime
    BionicSettingsContent(
        preferences =
        context.bionicPreferences.copy(
            fontSizeSp = runtime.currentFontSizeSp,
            textBrightness = runtime.currentTextBrightness,
        ),
        onFixationStrengthChange = context.callbacks.bionic.onFixationStrengthChange,
        onHighlightStrengthChange = context.callbacks.bionic.onHighlightStrengthChange,
        onFontSizeChange = { size ->
            runtime.currentFontSizeSp = size
            runtime.showFontSizeIndicator = true
            context.callbacks.bionic.onFontSizeChange(size)
        },
        onTextBrightnessChange = { brightness ->
            runtime.currentTextBrightness = brightness
            context.callbacks.bionic.onTextBrightnessChange(brightness)
        },
    )
}
