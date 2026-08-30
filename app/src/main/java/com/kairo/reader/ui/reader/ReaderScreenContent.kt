package com.kairo.reader.ui.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kairo.reader.R
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.SavedAnnotation
import com.kairo.reader.core.model.TimedReadingMode
import com.kairo.reader.ui.rememberWindowContainerMetrics
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow

internal data class ReaderContentState(
    val book: Book,
    val chapterIndex: Int,
    val coverImage: ByteArray?,
    val isLoading: Boolean,
    val loadErrorMessage: String?,
    val isCoverChapter: Boolean,
    val isPagedChapter: Boolean,
    val resolvedPageIndex: Int,
    val fullScreenTitlePageImagePath: String?,
    val headerCarouselImages: List<String>,
    val showHeaderCarousel: Boolean,
    val isBlankPage: Boolean,
    val displayBlocks: List<ReaderBlock>,
    val listState: LazyListState,
    val listStateKey: String,
    val invertedScroll: Boolean,
    val bottomInset: Dp,
    val overlayBottomPadding: Dp,
    val focusIndex: Int,
    val fontSizeSp: Float,
    val textBrightness: Float,
    val timedReadingMode: TimedReadingMode,
    val nonInteractiveChapterLinkTargets: Set<Int>,
    val savedAnnotations: List<SavedAnnotation>,
    val selectionRange: IntRange?,
    val searchMatchRange: IntRange?,
    val isPageGestureEnabled: () -> Boolean,
    val invertedScrollCommands: MutableSharedFlow<InvertedScrollCommand>,
)

internal data class ReaderContentActions(
    val onSafeFocusChange: (Int) -> Unit,
    val onStartTimedReadingForToken: (Int) -> Unit,
    val onPrevPage: () -> Unit,
    val onNextPage: () -> Unit,
    val onSwipePreviewChange: (ReaderSwipeDirection?, Float) -> Unit,
    val onOpenFullScreenImage: (String) -> Unit,
    val onSelectionStart: (Int) -> Unit,
    val onSelectionExtend: (Int) -> Unit,
    val onSelectionCancel: () -> Unit,
    val onChapterSelected: ((Int) -> Unit)? = null,
)

@Composable
internal fun ReaderContent(
    modifier: Modifier = Modifier,
    state: ReaderContentState,
    actions: ReaderContentActions,
) {
    when {
        state.isLoading ->
            ReaderLoadingState(
                modifier = modifier,
                book = state.book,
                coverImage = state.coverImage,
                isCoverChapter = state.isCoverChapter,
            )
        state.loadErrorMessage != null ->
            ReaderErrorState(modifier = modifier, message = state.loadErrorMessage)
        state.hasNoDisplayContent() -> ReaderEmptyState(modifier = modifier)
        else -> ReaderLoadedContent(modifier = modifier, state = state, actions = actions)
    }
}

private fun ReaderContentState.hasNoDisplayContent(): Boolean =
    displayBlocks.isEmpty() &&
        !isBlankPage &&
        !isCoverChapter &&
        fullScreenTitlePageImagePath == null &&
        headerCarouselImages.isEmpty()

@Composable
private fun ReaderLoadedContent(
    modifier: Modifier,
    state: ReaderContentState,
    actions: ReaderContentActions,
) {
    val windowMetrics = rememberWindowContainerMetrics()
    val compactLandscape = windowMetrics.isCompactLandscape(COMPACT_LANDSCAPE_MAX_HEIGHT_DP.dp)
    val paragraphSpacing =
        if (compactLandscape) {
            (state.fontSizeSp * COMPACT_PARAGRAPH_SPACING_FACTOR).dp.coerceIn(6.dp, 10.dp)
        } else {
            (state.fontSizeSp * STANDARD_PARAGRAPH_SPACING_FACTOR).dp.coerceIn(10.dp, 14.dp)
        }
    val gestureModifier =
        Modifier.readerPageGestures(
            state =
            ReaderGestureState(
                listStateKey = state.listStateKey,
                invertedScroll = state.invertedScroll,
                chapterIndex = state.chapterIndex,
                invertedScrollCommands = state.invertedScrollCommands,
                isPageGestureEnabled = state.isPageGestureEnabled,
            ),
            actions =
            ReaderGestureActions(
                onPreviousPage = actions.onPrevPage,
                onNextPage = actions.onNextPage,
                onSwipePreviewChange = actions.onSwipePreviewChange,
            ),
        )

    Box(modifier = modifier.fillMaxWidth().then(gestureModifier)) {
        LazyColumn(
            state = state.listState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !state.invertedScroll,
            verticalArrangement = Arrangement.spacedBy(paragraphSpacing),
            contentPadding = PaddingValues(bottom = state.bottomInset + state.overlayBottomPadding),
        ) {
            readerCoverItem(state)
            readerTitlePageItem(state, actions)
            readerHeaderCarouselItem(state, actions)
            readerBlockItems(state, actions)
        }
    }
}

private fun LazyListScope.readerCoverItem(
    state: ReaderContentState,
) {
    if (!state.isCoverChapter || (state.isPagedChapter && state.resolvedPageIndex > 0)) return
    item(key = "book_cover_full_${state.book.id.value}") {
        val context = LocalContext.current
        Surface(
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().fillParentMaxHeight().clip(RoundedCornerShape(14.dp)),
        ) {
            AsyncImage(
                model =
                remember(state.coverImage, state.book.id.value) {
                    ImageRequest.Builder(context)
                        .data(state.coverImage)
                        .memoryCacheKey("book_cover_full_${state.book.id.value}")
                        .crossfade(false)
                        .build()
                },
                contentDescription = stringResource(R.string.content_desc_cover_of_title, state.book.title),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun LazyListScope.readerTitlePageItem(
    state: ReaderContentState,
    actions: ReaderContentActions,
) {
    val imagePath = state.fullScreenTitlePageImagePath ?: return
    if (state.isPagedChapter && state.resolvedPageIndex > 0) return
    item(key = "title_page_full_${state.book.id.value}_$imagePath") {
        val context = LocalContext.current
        val file = remember(imagePath) { File(context.filesDir, imagePath) }
        if (file.exists()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().fillParentMaxHeight().clip(RoundedCornerShape(14.dp)),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = file,
                        contentDescription =
                        stringResource(R.string.content_desc_title_page_of_title, state.book.title),
                        modifier =
                        Modifier.fillMaxSize().openReaderImageOnLongPress(
                            imagePath = imagePath,
                            onOpen = actions.onOpenFullScreenImage,
                        ),
                        contentScale = ContentScale.Fit,
                    )
                    ReaderImageOpenHint(modifier = Modifier.align(Alignment.TopCenter).padding(12.dp))
                }
            }
        }
    }
}

private fun LazyListScope.readerHeaderCarouselItem(
    state: ReaderContentState,
    actions: ReaderContentActions,
) {
    if (!state.showHeaderCarousel) return
    item(key = "chapter_images_${state.chapterIndex}") {
        ChapterImages(
            imagePaths = state.headerCarouselImages,
            onImageOpen = actions.onOpenFullScreenImage,
        )
    }
}

private fun LazyListScope.readerBlockItems(
    state: ReaderContentState,
    actions: ReaderContentActions,
) {
    items(items = state.displayBlocks, key = { it.key }) { block ->
        when (block) {
            is ReaderParagraphBlock ->
                ParagraphText(
                    state =
                    ParagraphTextState(
                        paragraph = block.paragraph,
                        focusIndex = block.paragraph.focusIndexOrNone(state.focusIndex),
                        fontSizeSp = state.fontSizeSp,
                        textBrightness = state.textBrightness,
                        timedReadingMode = state.timedReadingMode,
                        nonInteractiveChapterLinkTargets = state.nonInteractiveChapterLinkTargets,
                        savedAnnotations = state.savedAnnotations,
                        selectionRange = state.selectionRange,
                        searchMatchRange = state.searchMatchRange,
                    ),
                    actions =
                    ParagraphTextActions(
                        onFocusChange = actions.onSafeFocusChange,
                        onStartTimedReading = actions.onStartTimedReadingForToken,
                        onChapterSelected = actions.onChapterSelected,
                        onSelectionStart = actions.onSelectionStart,
                        onSelectionExtend = actions.onSelectionExtend,
                        onSelectionCancel = actions.onSelectionCancel,
                    ),
                )
            is ReaderImageBlock ->
                InlineImageBlock(
                    imagePath = block.imagePath,
                    imageSize = block.imageSize,
                    onOpen = actions.onOpenFullScreenImage,
                )
        }
    }
}

@Composable
internal fun ReaderSwipePageChrome(
    direction: ReaderSwipeDirection?,
    progress: Float,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 120),
        label = "readerSwipePageChromeProgress",
    )
    val activeDirection = direction ?: return
    val style = readerSwipeChromeStyle(activeDirection, animatedProgress, canGoPrev, canGoNext)

    ReaderSwipeChromeBody(modifier, animatedProgress, style)
}

private data class ReaderSwipeChromeStyle(
    val canNavigate: Boolean,
    val alignment: Alignment,
    val railShape: RoundedCornerShape,
    val railBrush: Brush,
    val accentColor: Color,
    val icon: ImageVector,
    val iconDescription: String,
    val iconOffset: Dp,
)

@Composable
private fun readerSwipeChromeStyle(
    direction: ReaderSwipeDirection,
    progress: Float,
    canGoPrev: Boolean,
    canGoNext: Boolean,
): ReaderSwipeChromeStyle {
    val canNavigate =
        when (direction) {
            ReaderSwipeDirection.Previous -> canGoPrev
            ReaderSwipeDirection.Next -> canGoNext
        }
    val accentColor =
        if (canNavigate) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    return when (direction) {
        ReaderSwipeDirection.Previous ->
            ReaderSwipeChromeStyle(
                canNavigate = canNavigate,
                alignment = Alignment.CenterStart,
                railShape = RoundedCornerShape(topEnd = 48.dp, bottomEnd = 48.dp),
                railBrush =
                Brush.horizontalGradient(
                    listOf(
                        accentColor.copy(alpha = 0.24f),
                        accentColor.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                ),
                accentColor = accentColor,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                iconDescription = stringResource(R.string.content_desc_reader_swipe_previous_page),
                iconOffset = (-18).dp + (14.dp * progress),
            )
        ReaderSwipeDirection.Next ->
            ReaderSwipeChromeStyle(
                canNavigate = canNavigate,
                alignment = Alignment.CenterEnd,
                railShape = RoundedCornerShape(topStart = 48.dp, bottomStart = 48.dp),
                railBrush =
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        accentColor.copy(alpha = 0.08f),
                        accentColor.copy(alpha = 0.24f),
                    ),
                ),
                accentColor = accentColor,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                iconDescription = stringResource(R.string.content_desc_reader_swipe_next_page),
                iconOffset = 18.dp - (14.dp * progress),
            )
    }
}

@Composable
private fun ReaderSwipeChromeBody(
    modifier: Modifier,
    progress: Float,
    style: ReaderSwipeChromeStyle,
) {
    Box(modifier = modifier.alpha(progress)) {
        Box(
            modifier =
            Modifier
                .align(style.alignment)
                .fillMaxHeight()
                .width(86.dp)
                .background(brush = style.railBrush, shape = style.railShape),
        )
        Surface(
            modifier =
            Modifier
                .align(style.alignment)
                .padding(horizontal = 8.dp)
                .offset(x = style.iconOffset),
            shape = CircleShape,
            color =
            style.accentColor.copy(
                alpha = if (style.canNavigate) 0.92f else 0.44f,
            ),
            contentColor = MaterialTheme.colorScheme.onPrimary,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = style.iconDescription,
                modifier = Modifier.size(48.dp).padding(12.dp),
            )
        }
    }
}

internal enum class ReaderSwipeDirection {
    Previous,
    Next,
}

private fun Paragraph.focusIndexOrNone(focusIndex: Int): Int {
    val endIndex = startIndex + tokens.size - 1
    return if (focusIndex in startIndex..endIndex) focusIndex else NO_PARAGRAPH_FOCUS_INDEX
}

private const val NO_PARAGRAPH_FOCUS_INDEX = -1
private const val COMPACT_LANDSCAPE_MAX_HEIGHT_DP = 480
private const val COMPACT_PARAGRAPH_SPACING_FACTOR = 0.32f
private const val STANDARD_PARAGRAPH_SPACING_FACTOR = 0.45f

@Composable
private fun ReaderLoadingState(
    modifier: Modifier,
    book: Book,
    coverImage: ByteArray?,
    isCoverChapter: Boolean,
) {
    if (isCoverChapter) {
        Box(
            modifier =
            modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val context = LocalContext.current
            Surface(
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxSize(),
            ) {
                AsyncImage(
                    model =
                    remember(coverImage, book.id.value) {
                        ImageRequest
                            .Builder(context)
                            .data(coverImage)
                            .memoryCacheKey("book_cover_full_${book.id.value}")
                            .crossfade(false)
                            .build()
                    },
                    contentDescription =
                    stringResource(R.string.content_desc_cover_of_title, book.title),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            CircularProgressIndicator()
        }
    } else {
        Box(
            modifier =
            modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ReaderEmptyState(
    modifier: Modifier,
) {
    Box(
        modifier =
        modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.reader_empty_chapter),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReaderErrorState(
    modifier: Modifier,
    message: String,
) {
    Box(
        modifier =
        modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.reader_chapter_load_failed, message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
