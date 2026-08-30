package com.kairo.reader.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.ReadingMomentumWeek
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@Composable
internal fun MomentumHistoryHeader(
    previousWeekCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
) {
    val expansionState =
        stringResource(
            if (expanded) R.string.momentum_previous_weeks_expanded else R.string.momentum_previous_weeks_collapsed,
        )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { stateDescription = expansionState }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.momentum_previous_weeks),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                        if (previousWeekCount == 0) {
                            stringResource(R.string.momentum_no_previous_weeks)
                        } else {
                            pluralStringResource(
                                R.plurals.momentum_previous_weeks_count,
                                previousWeekCount,
                                previousWeekCount,
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = expansionState,
                )
            }
            HorizontalDivider()
            TextButton(
                onClick = onReset,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(stringResource(R.string.momentum_reset_this_week))
            }
        }
    }
}

@Composable
internal fun MomentumPreviousWeekRow(week: ReadingMomentumWeek) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = momentumWeekDateRangeLabel(week),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = momentumDurationText(week.activeDurationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                    stringResource(
                        R.string.momentum_words_read,
                        NumberFormat.getIntegerInstance().format(week.wordsRead),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                    pluralStringResource(
                        R.plurals.momentum_active_days_count,
                        week.activeDays,
                        week.activeDays,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun MomentumResetConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.momentum_reset_title)) },
        text = { Text(stringResource(R.string.momentum_reset_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.momentum_reset_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

internal fun momentumWeekDateRangeLabel(week: ReadingMomentumWeek): String {
    val formatter = DateFormat.getDateInstance(DateFormat.MEDIUM)
    return "${formatter.format(Date(week.startedAt))} – " +
        formatter.format(Date(week.endedAt - 1L))
}
