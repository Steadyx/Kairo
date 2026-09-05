package com.kairo.reader.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpContextAssistMode

@Composable
internal fun BlinkModeSelector(
    selected: BlinkMode,
    onSelect: (BlinkMode) -> Unit,
) {
    val options = listOf(BlinkMode.OFF, BlinkMode.SUBTLE, BlinkMode.ADAPTIVE)
    val subtitle = stringResource(blinkModeDescriptionRes(selected))

    Text(
        stringResource(R.string.blink_mode_title),
        modifier = Modifier.settingsSearchTarget(stringResource(R.string.blink_mode_title)),
        style = MaterialTheme.typography.bodyLarge
    )
    Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(6.dp))

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.name }) { mode ->
            val isSelected = mode == selected
            val label = stringResource(blinkModeLabelRes(mode))

            Surface(
                modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(mode) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (isSelected) 0.7f else 0.4f
                ),
                border =
                BorderStroke(
                    1.dp,
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = 0.45f
                        )
                    },
                ),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun ContextAssistModeSelector(
    selected: RsvpContextAssistMode,
    onSelect: (RsvpContextAssistMode) -> Unit,
) {
    Text(
        stringResource(R.string.rsvp_context_assist_title),
        modifier = Modifier.settingsSearchTarget(stringResource(R.string.rsvp_context_assist_title)),
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        stringResource(contextAssistModeDescriptionRes(selected)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(RsvpContextAssistMode.entries, key = { it.name }) { mode ->
            val isSelected = mode == selected
            Surface(
                modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(mode) },
                shape = RoundedCornerShape(12.dp),
                color =
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (isSelected) 0.7f else 0.4f,
                ),
                border =
                BorderStroke(
                    1.dp,
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    },
                ),
            ) {
                Text(
                    stringResource(contextAssistModeLabelRes(mode)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

internal fun contextAssistModeLabelRes(mode: RsvpContextAssistMode): Int =
    when (mode) {
        RsvpContextAssistMode.OFF -> R.string.rsvp_context_assist_off
        RsvpContextAssistMode.PREVIOUS_WORDS -> R.string.rsvp_context_assist_previous
        RsvpContextAssistMode.FULL_CLAUSE -> R.string.rsvp_context_assist_clause
        RsvpContextAssistMode.SENTENCE_TICKER -> R.string.rsvp_context_assist_ticker
    }

internal fun contextAssistModeDescriptionRes(mode: RsvpContextAssistMode): Int =
    when (mode) {
        RsvpContextAssistMode.OFF -> R.string.rsvp_context_assist_off_description
        RsvpContextAssistMode.PREVIOUS_WORDS ->
            R.string.rsvp_context_assist_previous_description
        RsvpContextAssistMode.FULL_CLAUSE -> R.string.rsvp_context_assist_clause_description
        RsvpContextAssistMode.SENTENCE_TICKER ->
            R.string.rsvp_context_assist_ticker_description
    }
