package com.kairo.reader.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.kairo.reader.R
import com.kairo.reader.core.rsvp.RsvpEffectivePace
import com.kairo.reader.core.rsvp.applyRsvpRhythmChoices

@Composable
fun RsvpSettingsContent(
    state: RsvpSettingsState,
    actions: RsvpSettingsActions,
) {
    val effectiveConfig = remember(state.config, state.tempoMsPerWord) {
        state.config.withLiveTempo(state.tempoMsPerWord)
    }
    RsvpProfileSelector(
        selectedProfileId = state.selectedProfileId,
        customProfiles = state.customProfiles,
        config = effectiveConfig,
        profileComparisonConfig = state.profileComparisonConfig,
        onSelectProfile = actions.onSelectProfile,
        onSaveCustomProfile = actions.onSaveCustomProfile,
        onDeleteCustomProfile = actions.onDeleteCustomProfile,
    )

    val estimatedWpm =
        remember(effectiveConfig, state.estimatedWpmOverride) {
            state.estimatedWpmOverride ?: RsvpEffectivePace.estimateWpm(effectiveConfig)
        }
    val estimatedText =
        if (estimatedWpm > 0) {
            stringResource(R.string.rsvp_estimated_pace, estimatedWpm)
        } else {
            stringResource(R.string.rsvp_estimating_pace)
        }
    RsvpReadingBasics(state, actions, estimatedText)
    var showRhythm by rememberSaveable { mutableStateOf(false) }
    SettingsNavRow(
        title = stringResource(R.string.rsvp_rhythm_calibration_title),
        subtitle = stringResource(R.string.rsvp_rhythm_subtitle),
        icon = Icons.Default.Tune,
        onClick = { showRhythm = true },
    )
    if (showRhythm) {
        RsvpRhythmDialog(
            state = state,
            onApply = { choices ->
                actions.onConfigChange { current -> applyRsvpRhythmChoices(current, choices) }
                showRhythm = false
            },
            onDismiss = { showRhythm = false },
        )
    }

    RsvpReadingAids(state.config, actions)
    RsvpDisplaySettings(state, actions)

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
