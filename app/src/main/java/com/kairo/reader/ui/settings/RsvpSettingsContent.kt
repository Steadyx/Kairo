package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.rsvp.RsvpEffectivePace

@Composable
fun RsvpSettingsContent(
    state: RsvpSettingsState,
    actions: RsvpSettingsActions,
) {
    val selectedProfileId = state.selectedProfileId
    val customProfiles = state.customProfiles
    val config = state.config
    val tempoMsPerWord = state.tempoMsPerWord
    val profileComparisonConfig = state.profileComparisonConfig
    val estimatedWpmOverride = state.estimatedWpmOverride
    val onSelectProfile = actions.onSelectProfile
    val onSaveCustomProfile = actions.onSaveCustomProfile
    val onDeleteCustomProfile = actions.onDeleteCustomProfile
    val effectiveConfig =
        remember(config, tempoMsPerWord) {
            config.withLiveTempo(tempoMsPerWord)
        }

    RsvpProfileSelector(
        selectedProfileId = selectedProfileId,
        customProfiles = customProfiles,
        config = effectiveConfig,
        profileComparisonConfig = profileComparisonConfig,
        onSelectProfile = onSelectProfile,
        onSaveCustomProfile = onSaveCustomProfile,
        onDeleteCustomProfile = onDeleteCustomProfile,
    )

    val estimatedWpm =
        remember(effectiveConfig, estimatedWpmOverride) {
            estimatedWpmOverride ?: RsvpEffectivePace.estimateWpm(effectiveConfig)
        }
    val estimatedText =
        if (estimatedWpm > 0) {
            stringResource(R.string.rsvp_estimated_pace, estimatedWpm)
        } else {
            stringResource(R.string.rsvp_estimating_pace)
        }
    Text(estimatedText, style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(10.dp))

    RsvpEssentialSettingsContent(state, actions)

    val searchTarget = LocalSettingsSearchTarget.current
    var showAdvanced by rememberSaveable(searchTarget?.id) { mutableStateOf(searchTarget?.advanced == true) }
    AdvancedSettingsToggle(
        expanded = showAdvanced,
        onToggle = { showAdvanced = !showAdvanced },
    )

    CompositionLocalProvider(LocalAdvancedSettingsScope provides true) {
        RsvpAdvancedSettingsContent(showAdvanced, state, actions)
    }
}
