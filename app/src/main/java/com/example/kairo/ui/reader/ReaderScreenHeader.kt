package com.example.kairo.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.kairo.core.model.Book
import com.example.kairo.R

@Composable
internal fun ReaderHeader(
    book: Book,
    chapterIndex: Int,
    chapterTitle: String?,
    coverImage: ByteArray?,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onShowMenu: () -> Unit,
    compactMode: Boolean,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compactMode && coverImage != null && coverImage.isNotEmpty()) {
                AsyncImage(
                    model =
                    remember(coverImage, book.id.value) {
                        ImageRequest
                            .Builder(context)
                            .data(coverImage)
                            .memoryCacheKey("book_cover_thumb_${book.id.value}")
                            .crossfade(false)
                            .build()
                    },
                    contentDescription = null,
                    modifier =
                    Modifier
                        .size(width = 44.dp, height = 56.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column {
                val titleStyle =
                    if (compactMode) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    }
                Text(
                    text = book.title,
                    style = titleStyle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                    chapterTitle
                        ?: stringResource(
                            R.string.reader_chapter_title,
                            chapterIndex + 1,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compactMode) {
                    Text(
                        text =
                        stringResource(
                            R.string.reader_chapter_of_total,
                            chapterIndex + 1,
                            book.chapters.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onPrev, enabled = canGoPrev) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.content_desc_previous_page),
                    tint =
                    if (canGoPrev) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                )
            }
            IconButton(onClick = onNext, enabled = canGoNext) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.content_desc_next_page),
                    tint =
                    if (canGoNext) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                )
            }
            IconButton(onClick = onShowMenu) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.content_desc_reader_menu),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
internal fun ReaderProgressMeta(
    pageLabel: String?,
    progressPercent: Int?,
    etaLabel: String?,
) {
    if (pageLabel == null && progressPercent == null && etaLabel == null) return

    val percentLabel =
        if (progressPercent != null) {
            stringResource(R.string.format_percent, progressPercent)
        } else {
            null
        }
    val metaLine =
        listOfNotNull(
            pageLabel,
            percentLabel,
        ).joinToString(stringResource(R.string.meta_separator))

    Column(
        modifier = Modifier.padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (metaLine.isNotBlank()) {
            Text(
                text = metaLine,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (etaLabel != null) {
            Text(
                text = etaLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
