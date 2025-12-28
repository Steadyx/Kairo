package com.example.kairo.ui.reader

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.kairo.core.model.Book
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableSharedFlow

@Composable
internal fun ReaderContent(
    modifier: Modifier = Modifier,
    book: Book,
    chapterIndex: Int,
    coverImage: ByteArray?,
    isLoading: Boolean,
    isCoverChapter: Boolean,
    isPagedChapter: Boolean,
    resolvedPageIndex: Int,
    fullScreenTitlePageImagePath: String?,
    headerCarouselImages: List<String>,
    showHeaderCarousel: Boolean,
    displayBlocks: List<ReaderBlock>,
    listState: LazyListState,
    listStateKey: String,
    invertedScroll: Boolean,
    bottomInset: Dp,
    focusIndex: Int,
    fontSizeSp: Float,
    textBrightness: Float,
    onSafeFocusChange: (Int) -> Unit,
    onStartRsvpForToken: (Int) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpenFullScreenImage: (String) -> Unit,
    invertedScrollCommands: MutableSharedFlow<InvertedScrollCommand>,
    onChapterSelected: ((Int) -> Unit)? = null,
) {
    if (isLoading) {
        ReaderLoadingState(
            modifier = modifier,
            book = book,
            coverImage = coverImage,
            isCoverChapter = isCoverChapter,
        )
        return
    }

    if (displayBlocks.isEmpty() &&
        !isCoverChapter &&
        fullScreenTitlePageImagePath == null &&
        headerCarouselImages.isEmpty()
    ) {
        ReaderEmptyState(modifier = modifier)
        return
    }

    val gestureModifier =
        Modifier.pointerInput(listStateKey, invertedScroll, chapterIndex) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pointerId = down.id

                val touchSlop = viewConfiguration.touchSlop
                val swipeThreshold = touchSlop * 4f

                var totalX = 0f
                var totalY = 0f
                var axis = Axis.Horizontal
                var axisResolved = false

                val tracker = VelocityTracker()
                tracker.addPosition(SystemClock.uptimeMillis(), down.position)

                while (true) {
                    val event = awaitPointerEvent()
                    val change =
                        event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) break

                    val dx = change.position.x - change.previousPosition.x
                    val dy = change.position.y - change.previousPosition.y
                    totalX += dx
                    totalY += dy

                    if (!axisResolved) {
                        val absX = abs(totalX)
                        val absY = abs(totalY)
                        if (absX > touchSlop || absY > touchSlop) {
                            axis = if (absX > absY) Axis.Horizontal else Axis.Vertical
                            axisResolved = true
                        } else {
                            continue
                        }
                    }

                    when (axis) {
                        Axis.Horizontal -> Unit // wait for drag end to switch chapter
                        Axis.Vertical -> {
                            if (!invertedScroll) {
                                // Let LazyColumn handle normal vertical scrolling.
                                break
                            }
                            tracker.addPosition(
                                SystemClock.uptimeMillis(),
                                change.position
                            )
                            invertedScrollCommands.tryEmit(
                                InvertedScrollCommand.Drag(dy)
                            )
                        }
                    }
                }

                if (axisResolved) {
                    when (axis) {
                        Axis.Horizontal -> {
                            when {
                                totalX <= -swipeThreshold -> onNextPage()
                                totalX >= swipeThreshold -> onPrevPage()
                            }
                        }
                        Axis.Vertical -> {
                            if (invertedScroll) {
                                val velocity = tracker.calculateVelocity().y
                                if (abs(velocity) > 200f) {
                                    invertedScrollCommands.tryEmit(
                                        InvertedScrollCommand.Fling(velocity)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .then(gestureModifier),
    ) {
        val viewportHeight = LocalConfiguration.current.screenHeightDp.dp

        // LAZY block-based rendering (text + images)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !invertedScroll,
            verticalArrangement = Arrangement.spacedBy(18.dp), // Paragraph spacing
            contentPadding = PaddingValues(bottom = bottomInset + 96.dp),
        ) {
            if (isCoverChapter && (!isPagedChapter || resolvedPageIndex <= 0)) {
                item(key = "book_cover_full_${book.id.value}") {
                    val context = LocalContext.current
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 2.dp,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(viewportHeight)
                            .clip(RoundedCornerShape(14.dp)),
                    ) {
                        AsyncImage(
                            model =
                            remember(coverImage, book.id.value) {
                                ImageRequest
                                    .Builder(context)
                                    .data(coverImage)
                                    .memoryCacheKey(
                                        "book_cover_full_${book.id.value}"
                                    )
                                    .crossfade(false)
                                    .build()
                            },
                            contentDescription = "Cover of ${book.title}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
            if (fullScreenTitlePageImagePath != null &&
                (!isPagedChapter || resolvedPageIndex <= 0)
            ) {
                item(
                    key = "title_page_full_${book.id.value}_$fullScreenTitlePageImagePath"
                ) {
                    val context = LocalContext.current
                    val file =
                        remember(fullScreenTitlePageImagePath) {
                            File(context.filesDir, fullScreenTitlePageImagePath)
                        }
                    if (file.exists()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 2.dp,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(viewportHeight)
                                .clip(RoundedCornerShape(14.dp)),
                        ) {
                            AsyncImage(
                                model = file,
                                contentDescription = "Title page of ${book.title}",
                                modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        onOpenFullScreenImage(fullScreenTitlePageImagePath)
                                    },
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }
            if (showHeaderCarousel) {
                item(key = "chapter_images_$chapterIndex") {
                    ChapterImages(
                        imagePaths = headerCarouselImages,
                        onImageClick = onOpenFullScreenImage,
                    )
                }
            }
            items(
                items = displayBlocks,
                key = { it.key },
            ) { block ->
                when (block) {
                    is ReaderParagraphBlock -> {
                        ParagraphText(
                            paragraph = block.paragraph,
                            focusIndex = focusIndex,
                            fontSizeSp = fontSizeSp,
                            textBrightness = textBrightness,
                            onFocusChange = onSafeFocusChange,
                            onStartRsvp = onStartRsvpForToken,
                            onChapterSelected = onChapterSelected,
                        )
                    }
                    is ReaderImageBlock -> {
                        InlineImageBlock(
                            imagePath = block.imagePath,
                            onOpen = onOpenFullScreenImage,
                        )
                    }
                }
            }
        }
    }
}

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
                    contentDescription = "Cover of ${book.title}",
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
            text = "No content in this chapter",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
