package com.kairo.reader.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kairo.reader.R

@Composable
internal fun NoteExportProgressDialog(
    phase: NoteExportPhase,
    onCancelAwaiting: () -> Unit,
) {
    val message =
        when (phase) {
            NoteExportPhase.PREPARING -> R.string.note_export_preparing
            NoteExportPhase.WRITING -> R.string.note_export_writing
            NoteExportPhase.AWAITING_DESTINATION -> R.string.note_export_waiting_destination
            NoteExportPhase.IDLE -> return
        }
    val awaitingDestination = phase == NoteExportPhase.AWAITING_DESTINATION
    AlertDialog(
        onDismissRequest = if (awaitingDestination) onCancelAwaiting else ({}),
        properties =
        DialogProperties(
            dismissOnBackPress = awaitingDestination,
            dismissOnClickOutside = false,
        ),
        title = null,
        text = {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            if (awaitingDestination) {
                TextButton(onClick = onCancelAwaiting) {
                    Text(stringResource(R.string.note_export_cancel))
                }
            }
        },
    )
}
