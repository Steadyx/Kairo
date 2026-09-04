package com.kairo.reader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.export.NoteExportScope
import com.kairo.reader.core.model.BookmarkItem
import com.kairo.reader.core.model.SavedAnnotationItem
import com.kairo.reader.core.model.SavedAnnotationKind
import com.kairo.reader.ui.saved.displayColor

@Composable
internal fun LibrarySavedContent(
    bookmarks: List<BookmarkItem>,
    annotations: List<SavedAnnotationItem>,
    filter: SavedFilter,
    onFilterChange: (SavedFilter) -> Unit,
    onOpenSaved: (String, Int, Int) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onEditAnnotation: (SavedAnnotationItem) -> Unit,
    onRequestNoteExport: (NoteExportScope) -> Unit,
    onClearBookmarksForBook: (com.kairo.reader.core.model.Book) -> Unit,
) {
    val visibleAnnotations =
        when (filter) {
            SavedFilter.ALL -> annotations
            SavedFilter.HIGHLIGHTS -> annotations.filter { it.annotation.kind == SavedAnnotationKind.HIGHLIGHT }
            SavedFilter.NOTES -> annotations.filter { it.annotation.kind == SavedAnnotationKind.NOTE }
            SavedFilter.BOOKMARKS -> emptyList()
        }
    val visibleBookmarks = if (filter == SavedFilter.ALL || filter == SavedFilter.BOOKMARKS) bookmarks else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.saved_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.saved_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(
            onClick = { onRequestNoteExport(NoteExportScope.All) },
            modifier = Modifier.align(Alignment.End),
            enabled = annotations.any { it.annotation.kind == SavedAnnotationKind.NOTE },
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Text(
                text = stringResource(R.string.note_export_action),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SavedFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(stringResource(option.labelResource())) },
                )
            }
        }
        if (visibleAnnotations.isEmpty() && visibleBookmarks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text =
                        stringResource(
                            if (bookmarks.isNotEmpty() || annotations.isNotEmpty()) {
                                R.string.saved_filter_empty
                            } else {
                                R.string.saved_empty
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (bookmarks.isNotEmpty() || annotations.isNotEmpty()) {
                        TextButton(onClick = { onFilterChange(SavedFilter.ALL) }) {
                            Text(stringResource(R.string.action_clear_filter))
                        }
                    }
                }
            }
        } else {
            val groupedBookmarks = visibleBookmarks.groupBy { it.book.id.value }.values
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleAnnotations, key = { "annotation:${it.annotation.id}" }) { item ->
                    SavedAnnotationRow(
                        item,
                        onOpenSaved,
                        onDeleteAnnotation,
                        onEditAnnotation,
                        onRequestNoteExport,
                    )
                }
                groupedBookmarks.forEach { group ->
                    val first = group.first()
                    item(key = "bookmark-header:${first.book.id.value}") {
                        BookmarkBookHeader(
                            book = first.book,
                            bookmarkCount = group.size,
                            onClearBookmarks = { onClearBookmarksForBook(first.book) },
                        )
                    }
                    items(group, key = { "bookmark:${it.bookmark.id}" }) { item ->
                        BookmarkRow(item, onOpenSaved, onDeleteBookmark)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedAnnotationRow(
    item: SavedAnnotationItem,
    onOpenSaved: (String, Int, Int) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (SavedAnnotationItem) -> Unit,
    onRequestNoteExport: (NoteExportScope) -> Unit,
) {
    val annotation = item.annotation
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable {
                onOpenSaved(item.book.id.value, annotation.chapterIndex, annotation.startTokenIndex)
            },
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(annotation.color.displayColor().copy(alpha = SAVED_ACCENT_ALPHA)),
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SavedAnnotationHeader(item)
            when (annotation.kind) {
                SavedAnnotationKind.NOTE -> SavedNoteContent(item)
                SavedAnnotationKind.HIGHLIGHT -> SavedPassageCard(item, prominent = true)
            }
        }
        SavedAnnotationActions(
            item = item,
            onEdit = onEdit,
            onDelete = onDelete,
            onRequestNoteExport = onRequestNoteExport,
        )
    }
}

@Composable
private fun SavedAnnotationActions(
    item: SavedAnnotationItem,
    onEdit: (SavedAnnotationItem) -> Unit,
    onDelete: (String) -> Unit,
    onRequestNoteExport: (NoteExportScope) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(top = 2.dp, end = 2.dp)) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.content_desc_saved_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (item.annotation.kind == SavedAnnotationKind.NOTE) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.note_export_menu_single)) },
                    onClick = {
                        expanded = false
                        onRequestNoteExport(NoteExportScope.Single(item.annotation.id))
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Description, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.note_export_menu_book)) },
                    onClick = {
                        expanded = false
                        onRequestNoteExport(NoteExportScope.Book(item.annotation.bookId.value))
                    },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                )
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                onClick = {
                    expanded = false
                    onEdit(item)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    expanded = false
                    onDelete(item.annotation.id)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@Composable
private fun SavedAnnotationHeader(item: SavedAnnotationItem) {
    val annotation = item.annotation
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                stringResource(
                    R.string.reader_chapter_of_total,
                    annotation.chapterIndex + 1,
                    item.chapterCount.coerceAtLeast(1),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = annotation.color.displayColor().copy(alpha = SAVED_KIND_TINT_ALPHA),
        ) {
            Text(
                text =
                stringResource(
                    if (annotation.kind == SavedAnnotationKind.NOTE) {
                        R.string.saved_note_label
                    } else {
                        R.string.saved_highlight_label
                    },
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SavedNoteContent(item: SavedAnnotationItem) {
    val note = item.annotation.note
    val noteText = if (note.isBlank()) stringResource(R.string.saved_note_empty) else note
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.reader_note_hint),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = noteText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        SavedPassageCard(item, prominent = false)
    }
}

@Composable
private fun SavedPassageCard(
    item: SavedAnnotationItem,
    prominent: Boolean,
) {
    val annotation = item.annotation
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = annotation.color.displayColor().copy(alpha = SAVED_PASSAGE_TINT_ALPHA),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(R.string.saved_note_passage),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = annotation.selectedText,
                style =
                if (prominent) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodySmall
                },
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (prominent) 4 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun SavedFilter.labelResource(): Int =
    when (this) {
        SavedFilter.ALL -> R.string.saved_filter_all
        SavedFilter.HIGHLIGHTS -> R.string.saved_filter_highlights
        SavedFilter.NOTES -> R.string.saved_filter_notes
        SavedFilter.BOOKMARKS -> R.string.saved_filter_bookmarks
    }

private const val SAVED_ACCENT_ALPHA = 0.78f
private const val SAVED_KIND_TINT_ALPHA = 0.18f
private const val SAVED_PASSAGE_TINT_ALPHA = 0.10f
