package com.kairo.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.TableOfContentsEntry
import com.kairo.reader.core.model.TableOfContentsTarget

@Composable
internal fun ChapterListOverlay(
    book: Book,
    currentChapterIndex: Int,
    currentTableOfContentsEntry: TableOfContentsEntry?,
    onDismiss: () -> Unit,
    onTargetSelected: (TableOfContentsTarget) -> Unit,
) {
    Box(
        modifier =
        Modifier
            .fillMaxSize(),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                },
        )

        Surface(
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxSize(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Box(
                modifier =
                Modifier.windowInsetsPadding(
                    WindowInsets.displayCutout.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    ),
                ),
            ) {
                ChapterListSheet(
                    book = book,
                    currentChapterIndex = currentChapterIndex,
                    currentTableOfContentsEntry = currentTableOfContentsEntry,
                    onDismiss = onDismiss,
                    onTargetSelected = onTargetSelected,
                )
            }
        }
    }
}

/**
 * Bottom sheet displaying the table of contents / chapter list.
 * Now expects pre-computed word counts in Chapter model.
 */
@Composable
internal fun ChapterListSheet(
    book: Book,
    currentChapterIndex: Int,
    currentTableOfContentsEntry: TableOfContentsEntry?,
    onDismiss: () -> Unit,
    onTargetSelected: (TableOfContentsTarget) -> Unit,
) {
    val hasAuthoredTableOfContents = book.tableOfContents.isNotEmpty()
    val entries =
        book.tableOfContents.ifEmpty {
            book.chapters.mapIndexed { index, chapter ->
                TableOfContentsEntry(
                    label =
                    sanitizeChapterTitleForDisplay(chapter.title)
                        .orEmpty(),
                    depth = 0,
                    target = TableOfContentsTarget(chapterIndex = index),
                )
            }
        }
    Column(
        modifier =
        Modifier
            .fillMaxSize(),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reader_toc_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.content_desc_close),
                )
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            itemsIndexed(
                items = entries,
                key = { index, _ -> index },
            ) { index, entry ->
                val target = entry.target
                val isCurrentChapter =
                    if (hasAuthoredTableOfContents) {
                        entry === currentTableOfContentsEntry
                    } else {
                        target?.chapterIndex == currentChapterIndex
                    }

                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = target != null) {
                            target?.let(onTargetSelected)
                        }
                        .background(
                            if (isCurrentChapter) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ).padding(
                            start = 16.dp + (entry.depth.coerceAtMost(MAX_TOC_INDENT_DEPTH) * 16).dp,
                            end = 16.dp,
                            top = 14.dp,
                            bottom = 14.dp,
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                            entry.label.ifBlank {
                                stringResource(
                                    R.string.reader_chapter_title,
                                    (target?.chapterIndex ?: index) + 1,
                                )
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrentChapter) FontWeight.Bold else FontWeight.Normal,
                            color =
                            when {
                                isCurrentChapter -> MaterialTheme.colorScheme.primary
                                target == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (index < entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

private const val MAX_TOC_INDENT_DEPTH = 6
