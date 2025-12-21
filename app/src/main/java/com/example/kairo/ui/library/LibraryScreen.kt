@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "MagicNumber")

package com.example.kairo.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookmarkItem
import kotlin.math.roundToInt

@Composable
fun LibraryScreen(
    books: List<Book>,
    bookmarks: List<BookmarkItem>,
    initialTab: LibraryTab = LibraryTab.Library,
    onOpen: (Book) -> Unit,
    onOpenBookmark: (bookId: String, chapterIndex: Int, tokenIndex: Int) -> Unit,
    onDeleteBookmark: (bookmarkId: String) -> Unit,
    onImportFile: (Uri) -> Unit,
    onSettings: () -> Unit,
    onDelete: (Book) -> Unit,
) {
    // File picker launcher for EPUB/MOBI files
    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            uri?.let { onImportFile(it) }
        }
    var selectedTab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab.ordinal) }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Your Library", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Import EPUB or MOBI files to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == LibraryTab.Library.ordinal,
                onClick = { selectedTab = LibraryTab.Library.ordinal },
                text = { Text("Library") },
            )
            Tab(
                selected = selectedTab == LibraryTab.Bookmarks.ordinal,
                onClick = { selectedTab = LibraryTab.Bookmarks.ordinal },
                text = { Text("Bookmarks") },
            )
        }

        if (selectedTab == LibraryTab.Library.ordinal) {
            // Import button
            Button(
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "application/epub+zip",
                            "application/x-mobipocket-ebook",
                            "application/octet-stream",
                            "*/*",
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Book")
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(books, key = { it.id.value }) { book ->
                    LibraryCard(book = book, onOpen = onOpen, onDelete = onDelete)
                }
            }
        } else {
            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No bookmarks yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val grouped =
                    remember(bookmarks) {
                        bookmarks
                            .groupBy { it.book.id.value }
                            .values
                            .map { group ->
                                val firstItem = group.first()
                                group.sortedByDescending { it.bookmark.createdAt } to firstItem
                            }.sortedBy { (_, firstItem) -> firstItem.book.title.lowercase() }
                    }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    grouped.forEach { (group, firstItem) ->
                        item(key = "header_${firstItem.book.id.value}") {
                            BookmarkBookHeader(
                                book = firstItem.book,
                                bookmarkCount = group.size,
                            )
                        }
                        items(
                            items = group,
                            key = { it.bookmark.id },
                        ) { item ->
                            BookmarkRow(
                                item = item,
                                onOpenBookmark = onOpenBookmark,
                                onDeleteBookmark = onDeleteBookmark,
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class LibraryTab { Library, Bookmarks }

@Composable
private fun LibraryCard(
    book: Book,
    onOpen: (Book) -> Unit,
    onDelete: (Book) -> Unit,
) {
    Card(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(book) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Book cover or placeholder
            BookCover(
                coverImage = book.coverImage,
                title = book.title,
                cacheKey = book.id.value,
                modifier = Modifier.size(width = 60.dp, height = 90.dp),
            )

            // Book info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.authors.isNotEmpty()) {
                    Text(
                        text = book.authors.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${book.chapters.size} chapters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            // Delete button
            IconButton(onClick = { onDelete(book) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun BookmarkBookHeader(
    book: Book,
    bookmarkCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                coverImage = book.coverImage,
                title = book.title,
                cacheKey = book.id.value,
                modifier = Modifier.size(width = 44.dp, height = 44.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.authors.isNotEmpty()) {
                    Text(
                        text = book.authors.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier =
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "$bookmarkCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    item: BookmarkItem,
    onOpenBookmark: (bookId: String, chapterIndex: Int, tokenIndex: Int) -> Unit,
    onDeleteBookmark: (bookmarkId: String) -> Unit,
) {
    val bookmark = item.bookmark
    val book = item.book
    val chapterCount = item.chapterCount.coerceAtLeast(1)
    val percent =
        remember(bookmark.chapterIndex, chapterCount) {
            (((bookmark.chapterIndex + 1).toFloat() / chapterCount.toFloat()) * 100f)
                .roundToInt()
                .coerceIn(0, 100)
        }

    Card(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable {
                onOpenBookmark(book.id.value, bookmark.chapterIndex, bookmark.tokenIndex)
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chapter ${bookmark.chapterIndex + 1} / $chapterCount • $percent%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = bookmark.previewText,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = { onDeleteBookmark(bookmark.id) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete bookmark",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BookCover(
    coverImage: ByteArray?,
    title: String,
    cacheKey: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (coverImage != null && coverImage.isNotEmpty()) {
        AsyncImage(
            model =
            remember(coverImage, cacheKey) {
                ImageRequest
                    .Builder(context)
                    .data(coverImage)
                    .memoryCacheKey("book_cover_$cacheKey")
                    .crossfade(false)
                    .build()
            },
            contentDescription = "Cover of $title",
            modifier = modifier.clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        PlaceholderCover(modifier = modifier)
    }
}

@Composable
private fun PlaceholderCover(modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Book,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(32.dp),
        )
    }
}
