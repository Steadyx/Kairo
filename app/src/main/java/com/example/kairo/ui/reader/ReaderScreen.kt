@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "MaxLineLength",
)

package com.example.kairo.ui.reader

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.text.TextUtilsCompat
import com.example.kairo.core.language.BookLanguageResolver
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.core.model.nearestWordIndex
import java.util.Locale

private val READER_MIN_BOTTOM_CONTENT_PADDING = 24.dp
private val READER_RSVP_LAUNCHER_CLEARANCE = 88.dp
private const val READER_CHROME_COLLAPSE_SCROLL_PX = 56

/**
 * Main reader screen - can be called directly with ViewModel state.
 *
 * Usage with ViewModel:
 * ```
 * val uiState by viewModel.uiState.collectAsState()
 * ReaderScreen(
 *     book = book,
 *     uiState = uiState,
 *     fontSizeSp = 18f,
 *     invertedScroll = false,
 *     onFocusChange = viewModel::setFocusIndex,
 *     onStartRsvp = { index -> navController.navigate("rsvp/$index") },
 *     onChapterChange = viewModel::loadChapter
 * )
 * ```
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: Book,
    uiState: ReaderUiState,
    fontSizeSp: Float,
    invertedScroll: Boolean,
    readerTheme: ReaderTheme,
    textBrightness: Float,
    estimatedWpm: Int,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onTextBrightnessChange: (Float) -> Unit,
    onInvertedScrollChange: (Boolean) -> Unit,
    focusModeEnabled: Boolean,
    onFocusModeEnabledChange: (Boolean) -> Unit,
    onAddBookmark: (chapterIndex: Int, tokenIndex: Int, previewText: String) -> Unit,
    onOpenBookmarks: () -> Unit,
    onFocusChange: (Int) -> Unit,
    onStartRsvp: (Int) -> Unit,
    onChapterChange: (Int, Int?) -> Unit,
    onViewportMetricsChanged: (fontSizeSp: Float, viewportHeightDp: Int) -> Unit,
) {
    val chapterIndex = uiState.chapterIndex
    val focusIndex = uiState.focusIndex
    val chapter = book.chapters.getOrNull(chapterIndex)
    val coverImage = book.coverImage

    val renderState =
        rememberReaderRenderState(
            chapterIndex = chapterIndex,
            focusIndex = focusIndex,
            coverImage = coverImage,
            chapterData = uiState.chapterData,
        )
    val contentLayoutDirection =
        remember(book) {
            val languageTag = BookLanguageResolver.resolve(book)
            val locale = languageTag?.let { Locale.forLanguageTag(it) }
            val layoutDirection =
                if (locale == null) {
                    View.LAYOUT_DIRECTION_LTR
                } else {
                    TextUtilsCompat.getLayoutDirectionFromLocale(locale)
                }
            if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }
        }
    val onSafeFocusChange =
        remember(renderState.tokens, onFocusChange) {
            { index: Int ->
                if (renderState.tokens.isNotEmpty()) {
                    onFocusChange(renderState.tokens.nearestWordIndex(index))
                }
            }
        }

    val listStateHolder =
        rememberReaderListState(
            listStateKey = renderState.listStateKey,
            focusListIndex = renderState.focusListIndex,
            listItemCount = renderState.listItemCount,
            displayBlocks = renderState.displayBlocks,
            invertedScroll = invertedScroll,
        )
    val chromeCollapsed by remember(listStateHolder.listState) {
        derivedStateOf {
            val state = listStateHolder.listState
            state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > READER_CHROME_COLLAPSE_SCROLL_PX
        }
    }
    val viewportHeightDp = LocalConfiguration.current.screenHeightDp
    LaunchedEffect(fontSizeSp, viewportHeightDp) {
        onViewportMetricsChanged(fontSizeSp, viewportHeightDp)
    }

    // Chapter list bottom sheet state
    val showChapterList = remember { mutableStateOf(false) }
    var showReaderMenu by remember { mutableStateOf(false) }
    var fullScreenImagePath by remember { mutableStateOf<String?>(null) }

    val isRsvpEnabled =
        !uiState.isLoading && renderState.firstWordIndex != -1 && renderState.tokens.isNotEmpty()
    val onStartRsvpForToken =
        remember(isRsvpEnabled, renderState.tokens, onStartRsvp) {
            { tokenIndex: Int ->
                if (isRsvpEnabled && renderState.tokens.isNotEmpty()) {
                    onStartRsvp(renderState.tokens.nearestWordIndex(tokenIndex))
                }
            }
        }
    val progressState =
        rememberReaderProgressState(
            safeFocusIndex = renderState.safeFocusIndex,
            totalChapterWords = renderState.totalChapterWords,
            wordCountByToken = renderState.wordCountByToken,
            resolvedPageIndex = renderState.resolvedPageIndex,
            pages = renderState.pages,
            currentPage = renderState.currentPage,
            estimatedWpm = estimatedWpm,
            bookWordCounts = uiState.bookWordCounts,
            chapterIndex = chapterIndex,
            chapterCount = book.chapters.size,
        )
    val navigationState =
        buildReaderNavigationState(
            pages = renderState.pages,
            isPagedChapter = renderState.isPagedChapter,
            resolvedPageIndex = renderState.resolvedPageIndex,
            chapterIndex = chapterIndex,
            lastChapterIndex = book.chapters.lastIndex,
            onFocusChange = onFocusChange,
            onChapterChange = onChapterChange,
        )
    val bottomInsetPadding = WindowInsets.safeDrawing.only(
        WindowInsetsSides.Bottom
    ).asPaddingValues()
    val bottomInset = bottomInsetPadding.calculateBottomPadding()
    val showRsvpLauncher = isRsvpEnabled && !showReaderMenu && !showChapterList.value
    val overlayBottomPadding =
        if (showRsvpLauncher) {
            READER_RSVP_LAUNCHER_CLEARANCE
        } else {
            READER_MIN_BOTTOM_CONTENT_PADDING
        }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    if (focusModeEnabled) {
                        // In focus mode, do not reserve space for the (hidden) status bar.
                        WindowInsets.displayCutout.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    } else {
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    },
                ).padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (focusModeEnabled) 8.dp else 16.dp
                ),
        ) {
            ReaderHeader(
                book = book,
                chapterIndex = chapterIndex,
                chapterTitle = sanitizeChapterTitleForDisplay(chapter?.title),
                coverImage = coverImage,
                canGoPrev = navigationState.canGoPrevPage,
                canGoNext = navigationState.canGoNextPage,
                onPrev = navigationState.onPrevPage,
                onNext = navigationState.onNextPage,
                onShowMenu = { showReaderMenu = !showReaderMenu },
                compactMode = chromeCollapsed,
            )

            AnimatedVisibility(visible = !chromeCollapsed && progressState.hasProgressMeta) {
                ReaderProgressMeta(
                    pageLabel = progressState.pageLabel,
                    progressPercent =
                        if (renderState.totalChapterWords > 0) progressState.progressPercent else null,
                    etaLabel = progressState.etaLabel,
                )
            }
            Spacer(modifier = Modifier.height(if (chromeCollapsed) 8.dp else 12.dp))

            CompositionLocalProvider(LocalLayoutDirection provides contentLayoutDirection) {
                ReaderContent(
                    modifier = Modifier.weight(1f),
                    book = book,
                    chapterIndex = chapterIndex,
                    coverImage = coverImage,
                    isLoading = uiState.isLoading,
                    isCoverChapter = renderState.isCoverChapter,
                    isPagedChapter = renderState.isPagedChapter,
                    resolvedPageIndex = renderState.resolvedPageIndex,
                    fullScreenTitlePageImagePath = renderState.fullScreenTitlePageImagePath,
                    headerCarouselImages = renderState.headerCarouselImages,
                    showHeaderCarousel = renderState.showHeaderCarousel,
                    displayBlocks = renderState.displayBlocks,
                    listState = listStateHolder.listState,
                    listStateKey = renderState.listStateKey,
                    invertedScroll = invertedScroll,
                    bottomInset = bottomInset,
                    overlayBottomPadding = overlayBottomPadding,
                    focusIndex = focusIndex,
                    fontSizeSp = fontSizeSp,
                    textBrightness = textBrightness,
                    onSafeFocusChange = onSafeFocusChange,
                    onStartRsvpForToken = onStartRsvpForToken,
                    onPrevPage = navigationState.onPrevPage,
                    onNextPage = navigationState.onNextPage,
                    onOpenFullScreenImage = { fullScreenImagePath = it },
                    invertedScrollCommands = listStateHolder.invertedScrollCommands,
                    onChapterSelected = { index -> onChapterChange(index, 0) },
                )
            }
        }

        if (showChapterList.value) {
            BackHandler { showChapterList.value = false }
            ChapterListOverlay(
                book = book,
                currentChapterIndex = chapterIndex,
                onDismiss = { showChapterList.value = false },
                onChapterSelected = { index ->
                    onChapterChange(index, 0)
                    showChapterList.value = false
                },
            )
        }

        if (showReaderMenu) {
            BackHandler { showReaderMenu = false }
            ReaderMenuOverlay(
                fontSizeSp = fontSizeSp,
                readerTheme = readerTheme,
                textBrightness = textBrightness,
                invertedScroll = invertedScroll,
                onFontSizeChange = onFontSizeChange,
                onThemeChange = onThemeChange,
                onTextBrightnessChange = onTextBrightnessChange,
                onInvertedScrollChange = onInvertedScrollChange,
                focusModeEnabled = focusModeEnabled,
                onFocusModeEnabledChange = onFocusModeEnabledChange,
                onAddBookmark = {
                    if (renderState.tokens.isEmpty()) return@ReaderMenuOverlay
                    val safeTokenIndex = renderState.tokens.nearestWordIndex(
                        focusIndex
                    ).coerceIn(0, renderState.tokens.lastIndex)
                    val preview = renderState.tokens.getOrNull(safeTokenIndex)?.text.orEmpty()
                    onAddBookmark(chapterIndex, safeTokenIndex, preview)
                    showReaderMenu = false
                },
                onOpenBookmarks = {
                    showReaderMenu = false
                    onOpenBookmarks()
                },
                onShowToc = {
                    showReaderMenu = false
                    showChapterList.value = true
                },
                onDismiss = { showReaderMenu = false },
            )
        }

        AnimatedVisibility(
            visible = showRsvpLauncher,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp + bottomInset),
        ) {
            ReaderRsvpLauncher(
                tokens = renderState.tokens,
                focusIndex = focusIndex,
                invertedScroll = invertedScroll,
                listState = listStateHolder.listState,
                focusListIndex = renderState.focusListIndex,
                progressFraction = progressState.progressFraction,
                onFocusChange = onFocusChange,
                onStartRsvp = onStartRsvp,
            )
        }

        fullScreenImagePath?.let { path ->
            BackHandler { fullScreenImagePath = null }
            FullScreenImageViewer(
                imagePath = path,
                onDismiss = { fullScreenImagePath = null },
            )
        }
    }
}
