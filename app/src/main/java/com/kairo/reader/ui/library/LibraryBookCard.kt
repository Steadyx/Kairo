package com.kairo.reader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.Book
import com.kairo.reader.ui.format.formatShortDurationMinutes

@Composable
internal fun LibraryCard(
    book: Book,
    progress: LibraryBookProgress?,
    onOpen: (Book) -> Unit,
    onSetCompleted: (Book, Boolean) -> Unit,
    onRequestDelete: (Book) -> Unit,
    compactLandscape: Boolean = false,
) {
    val resources = LocalResources.current
    val authorSeparator = stringResource(R.string.list_separator)
    var actionsExpanded by remember { mutableStateOf(false) }
    val deleteActionDescription = stringResource(R.string.content_desc_delete_book)
    val completedActionDescription =
        stringResource(
            if (book.isCompleted) {
                R.string.content_desc_move_book_to_library
            } else {
                R.string.content_desc_mark_book_completed
            },
        )
    Card(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(book) },
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(if (compactLandscape) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compactLandscape) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Book cover or placeholder
            BookCover(
                coverImage = book.coverImage,
                title = book.title,
                cacheKey = book.id.value,
                modifier =
                Modifier.size(
                    width = if (compactLandscape) 48.dp else 60.dp,
                    height = if (compactLandscape) 72.dp else 90.dp,
                ),
            )

            // Book info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 2.dp else 4.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (compactLandscape) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.authors.isNotEmpty()) {
                    Text(
                        text = book.authors.joinToString(authorSeparator),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text =
                    pluralStringResource(
                        R.plurals.library_chapter_count,
                        book.chapters.size,
                        book.chapters.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                progress?.let { stats ->
                    val eta =
                        if (stats.remainingMinutes != null) {
                            stringResource(
                                R.string.library_time_left,
                                formatShortDurationMinutes(resources, stats.remainingMinutes),
                            )
                        } else {
                            null
                        }
                    val percentCompleteLabel =
                        stringResource(
                            R.string.library_percent_complete,
                            stats.percentComplete,
                        )
                    val label =
                        if (eta != null) {
                            percentCompleteLabel + stringResource(R.string.meta_separator) + eta
                        } else {
                            percentCompleteLabel
                        }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (book.isCompleted) {
                    CompletedStatusPill()
                }
            }

            Box {
                IconButton(onClick = { actionsExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.content_desc_book_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false },
                ) {
                    DropdownMenuItem(
                        modifier = Modifier.semantics {
                            contentDescription = completedActionDescription
                        },
                        text = { Text(completedActionDescription) },
                        leadingIcon = {
                            Icon(
                                imageVector =
                                if (book.isCompleted) {
                                    Icons.Default.Refresh
                                } else {
                                    Icons.Default.Done
                                },
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            actionsExpanded = false
                            onSetCompleted(book, !book.isCompleted)
                        },
                    )
                    DropdownMenuItem(
                        modifier = Modifier.semantics {
                            contentDescription = deleteActionDescription
                        },
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            actionsExpanded = false
                            onRequestDelete(book)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun CompletedStatusPill() {
    Row(
        modifier =
        Modifier
            .clip(RoundedCornerShape(BOOK_PROGRESS_CORNER_PERCENT))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Done,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = stringResource(R.string.library_completed_status),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private const val BOOK_PROGRESS_CORNER_PERCENT = 50
