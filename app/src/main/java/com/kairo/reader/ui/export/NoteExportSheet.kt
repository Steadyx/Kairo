package com.kairo.reader.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.export.NoteExportFormat
import com.kairo.reader.core.export.NoteExportScope
import com.kairo.reader.core.model.SavedAnnotationItem
import com.kairo.reader.core.model.SavedAnnotationKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteExportSheet(
    state: NoteExportUiState,
    annotations: List<SavedAnnotationItem>,
    onSelectScope: (NoteExportScope) -> Unit,
    onSelectFormat: (NoteExportFormat) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val selectedScope = state.sheetScope ?: return
    val origin = state.sheetOrigin ?: selectedScope
    val notes = annotations.filter { it.annotation.kind == SavedAnnotationKind.NOTE }
    val originNote =
        (origin as? NoteExportScope.Single)?.let { single ->
            notes.firstOrNull { it.annotation.id == single.annotationId }
        }
    val originBookId =
        when (origin) {
            NoteExportScope.All -> null
            is NoteExportScope.Book -> origin.bookId
            is NoteExportScope.Single -> originNote?.annotation?.bookId?.value
        }
    val originBookTitle =
        originBookId?.let { bookId -> notes.firstOrNull { it.annotation.bookId.value == bookId }?.book?.title }
    val availableScopes =
        buildList {
            if (origin is NoteExportScope.Single) add(origin)
            originBookId?.let { add(NoteExportScope.Book(it)) }
            add(NoteExportScope.All)
        }.distinct()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(NOTE_EXPORT_SHEET_TAG),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.note_export_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.note_export_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.note_export_scope_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                availableScopes.forEach { scope ->
                    ExportChoice(
                        title = scopeTitle(scope),
                        description = scopeDescription(scope, notes, originBookTitle),
                        selected = scope == selectedScope,
                        onClick = { onSelectScope(scope) },
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = stringResource(R.string.note_export_format_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExportChoice(
                    title = stringResource(R.string.note_export_format_pdf),
                    description = stringResource(R.string.note_export_format_pdf_description),
                    icon = Icons.Outlined.PictureAsPdf,
                    selected = state.selectedFormat == NoteExportFormat.PDF,
                    onClick = { onSelectFormat(NoteExportFormat.PDF) },
                )
                ExportChoice(
                    title = stringResource(R.string.note_export_format_markdown),
                    description = stringResource(R.string.note_export_format_markdown_description),
                    icon = Icons.Outlined.Description,
                    selected = state.selectedFormat == NoteExportFormat.MARKDOWN,
                    onClick = { onSelectFormat(NoteExportFormat.MARKDOWN) },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.phase == NoteExportPhase.IDLE,
            ) {
                Text(stringResource(R.string.note_export_choose_destination))
            }
        }
    }
}

@Composable
private fun scopeTitle(scope: NoteExportScope): String =
    when (scope) {
        NoteExportScope.All -> stringResource(R.string.note_export_scope_all)
        is NoteExportScope.Book -> stringResource(R.string.note_export_scope_book)
        is NoteExportScope.Single -> stringResource(R.string.note_export_scope_single)
    }

@Composable
private fun scopeDescription(
    scope: NoteExportScope,
    notes: List<SavedAnnotationItem>,
    originBookTitle: String?,
): String {
    val matchingNotes =
        when (scope) {
            NoteExportScope.All -> notes
            is NoteExportScope.Book -> notes.filter { it.annotation.bookId.value == scope.bookId }
            is NoteExportScope.Single -> notes.filter { it.annotation.id == scope.annotationId }
        }
    val count = matchingNotes.size
    val countLabel = pluralStringResource(R.plurals.note_export_note_count, count, count)
    return when (scope) {
        NoteExportScope.All -> stringResource(R.string.note_export_scope_all_description, countLabel)
        is NoteExportScope.Book ->
            stringResource(
                R.string.note_export_scope_book_description,
                countLabel,
                originBookTitle ?: matchingNotes.firstOrNull()?.book?.title.orEmpty(),
            )
        is NoteExportScope.Single ->
            stringResource(
                R.string.note_export_scope_single_description,
                originBookTitle ?: matchingNotes.firstOrNull()?.book?.title.orEmpty(),
            )
    }
}

@Composable
private fun ExportChoice(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color =
        if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

internal const val NOTE_EXPORT_SHEET_TAG = "note-export-sheet"
