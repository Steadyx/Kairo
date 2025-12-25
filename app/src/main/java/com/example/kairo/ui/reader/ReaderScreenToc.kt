package com.example.kairo.ui.reader

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kairo.core.model.Book

@Composable
internal fun ChapterListOverlay(
    book: Book,
    currentChapterIndex: Int,
    onDismiss: () -> Unit,
    onChapterSelected: (Int) -> Unit,
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
                .background(Color.Black.copy(alpha = 0.45f))
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
                    onChapterSelected = onChapterSelected,
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
    onChapterSelected: (Int) -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxSize(),
    ) {
        Text(
            text = "Table of Contents",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        HorizontalDivider()

        LazyColumn(
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            itemsIndexed(
                items = book.chapters,
                key = { index, _ -> index },
            ) { index, chapter ->
                val isCurrentChapter = index == currentChapterIndex

                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChapterSelected(index) }
                        .background(
                            if (isCurrentChapter) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ).padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chapter.title ?: "Chapter ${index + 1}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrentChapter) FontWeight.Bold else FontWeight.Normal,
                            color =
                            if (isCurrentChapter) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Use pre-computed word count from Chapter model
                        // If not available, show nothing rather than computing on-the-fly
                        // chapter.wordCount?.let { count ->
                        //     Text(
                        //         text = "$count words",
                        //         style = MaterialTheme.typography.bodySmall,
                        //         color = MaterialTheme.colorScheme.onSurfaceVariant
                        //     )
                        // }
                    }

                    Box(
                        modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isCurrentChapter) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ).padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color =
                            if (isCurrentChapter) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                if (index < book.chapters.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
