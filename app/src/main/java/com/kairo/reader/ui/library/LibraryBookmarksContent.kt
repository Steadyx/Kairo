package com.kairo.reader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookmarkItem
import kotlin.math.roundToInt

@Composable
internal fun BookmarksSummaryRow(
    bookmarkCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text =
                pluralStringResource(
                    R.plurals.library_bookmark_count,
                    bookmarkCount,
                    bookmarkCount,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.library_bookmarks_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun BookmarkBookHeader(
    book: Book,
    bookmarkCount: Int,
    onClearBookmarks: () -> Unit,
) {
    val authorSeparator = stringResource(R.string.list_separator)
    val clearBookmarksDescription =
        stringResource(R.string.content_desc_delete_book_bookmarks, book.title)
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(
            coverImage = book.coverImage,
            title = book.title,
            cacheKey = book.id.value,
            modifier = Modifier.size(width = 34.dp, height = 50.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val authors = book.authors.joinToString(authorSeparator)
            val count =
                pluralStringResource(
                    R.plurals.library_bookmark_count,
                    bookmarkCount,
                    bookmarkCount,
                )
            if (authors.isNotBlank()) {
                Text(
                    text = authors + stringResource(R.string.meta_separator) + count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = onClearBookmarks,
            modifier =
            Modifier.semantics {
                contentDescription = clearBookmarksDescription
            },
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.library_bookmark_clear_book))
        }
    }
}

@Composable
internal fun BookmarkRow(
    item: BookmarkItem,
    onOpenBookmark: (bookId: String, chapterIndex: Int, tokenIndex: Int) -> Unit,
    onDeleteBookmark: (bookmarkId: String) -> Unit,
) {
    val bookmark = item.bookmark
    val book = item.book
    val chapterCount = item.chapterCount.coerceAtLeast(1)
    val percent =
        remember(bookmark.chapterIndex, chapterCount) {
            (((bookmark.chapterIndex + 1).toFloat() / chapterCount.toFloat()) * PERCENT_SCALE)
                .roundToInt()
                .coerceIn(0, PERCENT_MAX)
        }

    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable {
                onOpenBookmark(book.id.value, bookmark.chapterIndex, bookmark.tokenIndex)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                stringResource(
                    R.string.library_bookmark_progress,
                    bookmark.chapterIndex + 1,
                    chapterCount,
                    percent,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = bookmark.previewText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = { onDeleteBookmark(bookmark.id) }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.content_desc_delete_bookmark),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val PERCENT_SCALE = 100f
private const val PERCENT_MAX = 100
