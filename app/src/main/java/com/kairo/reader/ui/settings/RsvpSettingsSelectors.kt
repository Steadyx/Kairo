package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpContextAssistMode

@Composable
internal fun BlinkModeSelector(
    config: RsvpConfig,
    onSelect: (BlinkMode) -> Unit,
) {
    SettingsSwitchRow(
        title = stringResource(R.string.blink_mode_title),
        subtitle = stringResource(R.string.blink_mode_subtle_description),
        checked = config.blinkMode != BlinkMode.OFF,
        onCheckedChange = { onSelect(if (it) BlinkMode.SUBTLE else BlinkMode.OFF) },
    )
}

@Composable
internal fun ContextAssistModeSelector(
    selected: RsvpContextAssistMode,
    onSelect: (RsvpContextAssistMode) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.rsvp_context_assist_title),
            modifier = Modifier.settingsSearchTarget(stringResource(R.string.rsvp_context_assist_title)),
            style = MaterialTheme.typography.bodyLarge,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                RsvpContextAssistMode.OFF,
                RsvpContextAssistMode.PREVIOUS_WORDS,
                RsvpContextAssistMode.SENTENCE_TICKER,
                RsvpContextAssistMode.FULL_CLAUSE,
            ).forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    label = { Text(stringResource(contextAssistModeLabelRes(mode))) },
                )
            }
        }
        Text(
            stringResource(contextAssistModeDescriptionRes(selected)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun contextAssistModeLabelRes(mode: RsvpContextAssistMode): Int = when (mode) {
    RsvpContextAssistMode.OFF -> R.string.rsvp_context_assist_off
    RsvpContextAssistMode.PREVIOUS_WORDS -> R.string.rsvp_context_assist_previous
    RsvpContextAssistMode.SENTENCE_TICKER -> R.string.rsvp_context_assist_ticker
    RsvpContextAssistMode.FULL_CLAUSE -> R.string.rsvp_context_assist_clause
}

private fun contextAssistModeDescriptionRes(mode: RsvpContextAssistMode): Int = when (mode) {
    RsvpContextAssistMode.OFF -> R.string.rsvp_context_assist_off_description
    RsvpContextAssistMode.PREVIOUS_WORDS -> R.string.rsvp_context_assist_previous_description
    RsvpContextAssistMode.SENTENCE_TICKER -> R.string.rsvp_context_assist_ticker_description
    RsvpContextAssistMode.FULL_CLAUSE -> R.string.rsvp_context_assist_clause_description
}
