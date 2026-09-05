package com.kairo.reader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kairo.reader.R
import com.kairo.reader.core.model.Book

internal data class ReaderHeaderState(
    val book: Book,
    val chapterIndex: Int,
    val chapterTitle: String?,
    val tableOfContentsLabel: String?,
    val coverImage: ByteArray?,
    val canGoPrev: Boolean,
    val canGoNext: Boolean,
    val compactMode: Boolean,
    val landscapeCompact: Boolean,
    val detailsExpanded: Boolean,
    val pageLabel: String?,
    val progressPercent: Int?,
    val progressFraction: Float,
    val etaLabel: String?,
    val navigationModifier: Modifier = Modifier,
    val menuModifier: Modifier = Modifier,
)

internal data class ReaderHeaderActions(
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
    val onOpenLibrary: () -> Unit,
    val onShowMenu: () -> Unit,
    val onToggleDetails: () -> Unit,
)

@Composable
internal fun ReaderHeader(
    state: ReaderHeaderState,
    actions: ReaderHeaderActions,
) {
    val context = LocalContext.current
    val chapterProgress =
        remember(state.book.chapters, state.chapterIndex) {
            resolveReaderChapterProgress(state.book.chapters, state.chapterIndex)
        }
    Column(verticalArrangement = Arrangement.spacedBy(if (state.landscapeCompact) 6.dp else 10.dp)) {
        ReaderHeaderTopRow(state, actions)
        AnimatedVisibility(visible = state.detailsExpanded) {
            val chapterProgressLabel =
                state.tableOfContentsLabel
                    ?: stringResource(
                        R.string.reader_chapter_of_total,
                        chapterProgress.currentNumber,
                        chapterProgress.totalNumber,
                    )
            if (state.landscapeCompact) {
                ReaderHeaderDetailsCompact(
                    chapterProgressLabel = chapterProgressLabel,
                    pageLabel = state.pageLabel,
                    progressPercent = state.progressPercent,
                    progressFraction = state.progressFraction,
                    etaLabel = state.etaLabel,
                )
            } else {
                ReaderHeaderDetails(
                    book = state.book,
                    coverImage = state.coverImage,
                    chapterProgressLabel = chapterProgressLabel,
                    pageLabel = state.pageLabel,
                    progressPercent = state.progressPercent,
                    progressFraction = state.progressFraction,
                    etaLabel = state.etaLabel,
                    context = context,
                )
            }
        }
    }
}

@Composable
private fun ReaderHeaderTopRow(
    state: ReaderHeaderState,
    actions: ReaderHeaderActions,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 480.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReaderHeaderTitle(state)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ReaderHeaderActionButtons(state, actions, 48.dp)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) { ReaderHeaderTitle(state) }
                ReaderHeaderActionButtons(state, actions, 48.dp)
            }
        }
    }
}

@Composable
private fun ReaderHeaderTitle(state: ReaderHeaderState) {
    val compressedChrome = state.compactMode || state.landscapeCompact
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = state.book.title,
            style = if (compressedChrome) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = state.tableOfContentsLabel ?: state.chapterTitle
                ?: stringResource(R.string.reader_chapter_title, state.chapterIndex + 1),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReaderHeaderActionButtons(
    state: ReaderHeaderState,
    actions: ReaderHeaderActions,
    iconButtonSize: androidx.compose.ui.unit.Dp,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = state.navigationModifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = actions.onPrev, enabled = state.canGoPrev, modifier = Modifier.size(iconButtonSize)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.content_desc_previous_page),
                    tint = navigationIconTint(state.canGoPrev),
                )
            }
            IconButton(onClick = actions.onNext, enabled = state.canGoNext, modifier = Modifier.size(iconButtonSize)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.content_desc_next_page),
                    tint = navigationIconTint(state.canGoNext),
                )
            }
        }
        IconButton(onClick = actions.onToggleDetails, modifier = Modifier.size(iconButtonSize)) {
            Icon(
                imageVector = if (state.detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(R.string.content_desc_reader_details),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = actions.onOpenLibrary, modifier = Modifier.size(iconButtonSize)) {
            Icon(
                Icons.Default.Home,
                contentDescription = stringResource(R.string.content_desc_go_to_library),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        IconButton(onClick = actions.onShowMenu, modifier = state.menuModifier.size(iconButtonSize)) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.content_desc_reader_menu),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun navigationIconTint(enabled: Boolean) =
    if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

@Composable
private fun ReaderHeaderDetailsCompact(
    chapterProgressLabel: String,
    pageLabel: String?,
    progressPercent: Int?,
    progressFraction: Float,
    etaLabel: String?,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chapterProgressLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier =
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                )
                pageLabel?.let {
                    ReaderMetaPill(text = it)
                }
                progressPercent?.let {
                    ReaderMetaPill(text = stringResource(R.string.format_percent, it))
                }
            }
            etaLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReaderHeaderDetails(
    book: Book,
    coverImage: ByteArray?,
    chapterProgressLabel: String,
    pageLabel: String?,
    progressPercent: Int?,
    progressFraction: Float,
    etaLabel: String?,
    context: android.content.Context,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (coverImage != null && coverImage.isNotEmpty()) {
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
                        .size(width = 46.dp, height = 60.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = chapterProgressLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pageLabel?.let {
                        ReaderMetaPill(text = it)
                    }
                    progressPercent?.let {
                        ReaderMetaPill(text = stringResource(R.string.format_percent, it))
                    }
                }
                etaLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderMetaPill(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.64f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}
