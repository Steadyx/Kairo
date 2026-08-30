package com.kairo.reader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kairo.reader.R
import com.kairo.reader.core.model.Book

@Composable
internal fun BookCover(
    coverImage: ByteArray?,
    title: String,
    cacheKey: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (coverImage != null && coverImage.isNotEmpty()) {
        val coverDescription =
            stringResource(R.string.content_desc_cover_of_title, title)
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
            contentDescription = coverDescription,
            modifier = modifier.clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop,
        )
    } else {
        PlaceholderCover(modifier = modifier)
    }
}

@Composable
internal fun PlaceholderCover(modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .clip(MaterialTheme.shapes.small)
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
