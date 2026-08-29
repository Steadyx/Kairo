package com.kairo.reader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.HighlightColor
import com.kairo.reader.core.model.SavedAnnotationLimits
import com.kairo.reader.ui.saved.displayColor
import com.kairo.reader.ui.saved.labelResource

@Composable
internal fun ReaderSelectionBar(
    selectedText: String,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    canSaveSelection: Boolean = true,
    selectionSupportingText: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = selectedText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.reader_selection_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            selectionSupportingText?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color =
                    if (canSaveSelection) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(onClick = onHighlight, enabled = canSaveSelection) {
                    Text(stringResource(R.string.action_highlight))
                }
                OutlinedButton(onClick = onNote, enabled = canSaveSelection) {
                    Text(stringResource(R.string.action_note))
                }
                OutlinedButton(onClick = onSearch) { Text(stringResource(R.string.action_search)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }
}

@Composable
internal fun ReaderNoteDialog(
    selectedText: String,
    onSave: (String, HighlightColor) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by rememberSaveable { mutableStateOf("") }
    var noteLimitAttempted by rememberSaveable { mutableStateOf(false) }
    var colorName by rememberSaveable { mutableStateOf(HighlightColor.YELLOW.name) }
    val color = HighlightColor.entries.firstOrNull { it.name == colorName } ?: HighlightColor.YELLOW
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_note_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { value ->
                        if (value.length <= SavedAnnotationLimits.MAX_NOTE_CHARACTERS) {
                            note = value
                            noteLimitAttempted = false
                        } else {
                            noteLimitAttempted = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.reader_note_hint)) },
                    placeholder = { Text(stringResource(R.string.reader_note_prompt)) },
                    minLines = 3,
                    maxLines = 6,
                    supportingText = {
                        Text(
                            text =
                            if (noteLimitAttempted) {
                                stringResource(
                                    R.string.saved_note_limit_error,
                                    SavedAnnotationLimits.MAX_NOTE_CHARACTERS,
                                )
                            } else {
                                stringResource(
                                    R.string.saved_note_character_count,
                                    note.length,
                                    SavedAnnotationLimits.MAX_NOTE_CHARACTERS,
                                )
                            },
                        )
                    },
                    isError = noteLimitAttempted,
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = color.displayColor().copy(alpha = NOTE_PASSAGE_TINT_ALPHA),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.saved_note_passage),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = selectedText,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.reader_highlight_color),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HighlightColor.entries.forEach { option ->
                        FilterChip(
                            selected = color == option,
                            onClick = { colorName = option.name },
                            label = { Text(stringResource(option.labelResource())) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(note.trim(), color) },
                enabled = note.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save_note))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
internal fun ReaderSearchMatchBar(
    currentIndex: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (total <= 0) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.content_desc_previous_search_match),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.reader_search_match_count, currentIndex + 1, total),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onNext) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.content_desc_next_search_match),
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.content_desc_close_search_matches),
                )
            }
        }
    }
}

private const val NOTE_PASSAGE_TINT_ALPHA = 0.10f
