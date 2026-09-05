@file:Suppress("FunctionNaming", "LongParameterList")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairo.reader.R
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.RsvpFontWeight
import com.kairo.reader.core.model.UserPreferences

@Composable
fun RsvpSettingsScreen(
    preferences: UserPreferences,
    onSelectRsvpProfile: (String) -> Unit,
    onSaveRsvpProfile: (String, RsvpConfig) -> Unit,
    onDeleteRsvpProfile: (String) -> Unit,
    onRsvpTempoMsPerWordChange: (Long) -> Unit,
    onRsvpConfigChange: ((RsvpConfig) -> RsvpConfig) -> Unit,
    onUnlockExtremeSpeedChange: (Boolean) -> Unit,
    onRsvpFontSizeChange: (Float) -> Unit,
    onRsvpTextBrightnessChange: (Float) -> Unit,
    onRsvpFontWeightChange: (RsvpFontWeight) -> Unit,
    onRsvpFontFamilyChange: (RsvpFontFamily) -> Unit,
    onRsvpVerticalBiasChange: (Float) -> Unit,
    onRsvpHorizontalBiasChange: (Float) -> Unit,
    onBack: () -> Unit,
) {
    RsvpSettingsPage(
        readerTheme = preferences.readerTheme,
        onBack = onBack,

        state =
        RsvpSettingsState(
            selectedProfileId = preferences.rsvpSelectedProfileId,
            customProfiles = preferences.rsvpCustomProfiles,
            config = preferences.rsvpConfig,
            tempoMsPerWord = preferences.rsvpTempoMsPerWord,
            unlockExtremeSpeed = preferences.unlockExtremeSpeed,
            fontSizeSp = preferences.rsvpFontSizeSp,
            textBrightness = preferences.rsvpTextBrightness,
            fontFamily = preferences.rsvpFontFamily,
            fontWeight = preferences.rsvpFontWeight,
            verticalBias = preferences.rsvpVerticalBias,
            horizontalBias = preferences.rsvpHorizontalBias,
        ),
        actions =
        RsvpSettingsActions(
            onSelectProfile = onSelectRsvpProfile,
            onSaveCustomProfile = onSaveRsvpProfile,
            onDeleteCustomProfile = onDeleteRsvpProfile,
            onTempoMsPerWordChange = onRsvpTempoMsPerWordChange,
            onConfigChange = onRsvpConfigChange,
            onUnlockExtremeSpeedChange = onUnlockExtremeSpeedChange,
            onFontSizeChange = onRsvpFontSizeChange,
            onTextBrightnessChange = onRsvpTextBrightnessChange,
            onFontWeightChange = onRsvpFontWeightChange,
            onFontFamilyChange = onRsvpFontFamilyChange,
            onVerticalBiasChange = onRsvpVerticalBiasChange,
            onHorizontalBiasChange = onRsvpHorizontalBiasChange,
        ),
    )
}

/** One complete settings surface for Home and the in-reading settings entry point. */
@Composable
internal fun RsvpSettingsPage(
    state: RsvpSettingsState,
    actions: RsvpSettingsActions,
    readerTheme: com.kairo.reader.core.model.ReaderTheme,
    onBack: () -> Unit,
) {
    com.kairo.reader.ui.theme.KairoTheme(readerTheme = readerTheme) {
        SettingsScaffold(title = stringResource(R.string.rsvp_settings_title), onBack = onBack) { modifier ->
            Column(
                modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RsvpSettingsContent(state = state, actions = actions)
            }
        }
    }
}

@Composable
internal fun RsvpSettingsDialog(
    state: RsvpSettingsState,
    actions: RsvpSettingsActions,
    readerTheme: com.kairo.reader.core.model.ReaderTheme,
    onBack: () -> Unit,
) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        RsvpSettingsPage(state = state, actions = actions, readerTheme = readerTheme, onBack = onBack)
    }
}
