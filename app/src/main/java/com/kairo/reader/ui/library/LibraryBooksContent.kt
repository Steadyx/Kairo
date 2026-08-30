package com.kairo.reader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.Book
import com.kairo.reader.ui.tutorial.StartingTutorialTargetIds
import com.kairo.reader.ui.tutorial.startingTutorialTarget

@Composable
internal fun LibraryBooksContent(
    books: List<Book>,
    filter: LibraryBookFilter,
    bookProgress: Map<String, LibraryBookProgress>,
    compactLandscape: Boolean,
    isImporting: Boolean,
    actions: LibraryTabContentActions,
    tutorialTargets: MutableMap<String, Rect>,
    onFilterChange: (LibraryBookFilter) -> Unit,
) {
    val visibleBooks =
        when (filter) {
            LibraryBookFilter.READING -> books.filterNot { it.isCompleted }
            LibraryBookFilter.COMPLETED -> books.filter { it.isCompleted }
            LibraryBookFilter.ALL -> books
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!compactLandscape) {
            LibraryImportSources(isImporting, actions, tutorialTargets)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LibraryBookFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(stringResource(option.labelResource())) },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 6.dp else 8.dp),
        ) {
            if (books.isNotEmpty() && visibleBooks.isEmpty()) {
                item(key = "filtered-empty") {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.library_books_filter_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { onFilterChange(LibraryBookFilter.ALL) }) {
                                Text(stringResource(R.string.action_clear_filter))
                            }
                        }
                    }
                }
            }
            items(visibleBooks, key = { it.id.value }) { book ->
                LibraryCard(
                    book = book,
                    progress = bookProgress[book.id.value],
                    onOpen = actions.onOpen,
                    onSetCompleted = actions.onSetCompleted,
                    onRequestDelete = actions.onRequestDelete,
                    compactLandscape = compactLandscape,
                )
            }
        }
    }
}

@Composable
private fun LibraryImportSources(
    isImporting: Boolean,
    actions: LibraryTabContentActions,
    tutorialTargets: MutableMap<String, Rect>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.library_add_content_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ImportSourceCard(
                icon = Icons.Default.Book,
                label = stringResource(R.string.library_source_book),
                supportingText = stringResource(R.string.library_source_book_hint),
                onClick = actions.onLaunchBookImport,
                enabled = !isImporting,
                modifier =
                Modifier
                    .weight(1f)
                    .startingTutorialTarget(StartingTutorialTargetIds.LIBRARY_IMPORT) { targetId, bounds ->
                        tutorialTargets[targetId] = bounds
                    },
            )
            ImportSourceCard(
                icon = Icons.Default.Link,
                label = stringResource(R.string.library_source_link),
                supportingText = stringResource(R.string.library_source_link_hint),
                onClick = actions.onShowReadLinkDialog,
                enabled = !isImporting,
                modifier = Modifier.weight(1f),
            )
            ImportSourceCard(
                icon = Icons.AutoMirrored.Filled.TextSnippet,
                label = stringResource(R.string.library_source_text),
                supportingText = stringResource(R.string.library_source_text_hint),
                onClick = actions.onShowAddTextDialog,
                enabled = !isImporting,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun LibraryBookFilter.labelResource(): Int =
    when (this) {
        LibraryBookFilter.READING -> R.string.library_filter_reading
        LibraryBookFilter.COMPLETED -> R.string.library_filter_completed
        LibraryBookFilter.ALL -> R.string.library_filter_all
    }
