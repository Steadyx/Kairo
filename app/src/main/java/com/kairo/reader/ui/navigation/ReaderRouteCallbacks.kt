package com.kairo.reader.ui.navigation

import android.content.res.Resources
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.R
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Bookmark
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.TableOfContentsTarget
import com.kairo.reader.core.model.TimedReadingMode
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.model.nearestWordIndex
import com.kairo.reader.ui.reader.ReaderUiState
import com.kairo.reader.ui.reader.ReaderViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ReaderRouteCallbacks(
    val onFontSizeChange: (Float) -> Unit,
    val onThemeChange: (ReaderTheme) -> Unit,
    val onTextBrightnessChange: (Float) -> Unit,
    val onInvertedScrollChange: (Boolean) -> Unit,
    val onFocusModeEnabledChange: (Boolean) -> Unit,
    val onAddBookmark: (chapterIndex: Int, tokenIndex: Int, previewText: String) -> Unit,
    val onOpenBookmarks: () -> Unit,
    val onOpenLibrary: () -> Unit,
    val onFocusChange: (Int) -> Unit,
    val onPageChange: (pageIndex: Int, focusTokenIndex: Int) -> Unit,
    val onStartTimedReading: (TimedReadingMode, Int) -> Unit,
    val onSelectTimedReadingMode: (TimedReadingMode, Int) -> Unit,
    val onChapterChange: (Int, Int?) -> Unit,
    val onTableOfContentsTargetSelected: (TableOfContentsTarget) -> Unit,
    val onViewportMetricsChanged: (fontSizeSp: Float, viewportHeightDp: Int) -> Unit,
)

internal data class ReaderRouteCallbackDependencies(
    val container: KairoApplication,
    val navController: NavHostController,
    val prefs: UserPreferences,
    val bookId: String,
    val bookIdValue: BookId,
    val languageTag: String?,
    val dispatcherProvider: DispatcherProvider,
    val coroutineScope: CoroutineScope,
    val lifecycleScope: CoroutineScope,
    val resources: Resources,
    val uiState: ReaderUiState,
    val effectiveUiState: ReaderUiState,
    val readerViewModel: ReaderViewModel,
    val readerPositionSaver: ReaderPositionSaver,
    val getPendingRsvpLaunchTempoMsPerWord: () -> Long,
    val clearPendingRsvpLaunchTempoMsPerWord: () -> Unit,
    val onShowUserMessage: (String) -> Unit,
)

internal fun buildReaderRouteCallbacks(
    dependencies: ReaderRouteCallbackDependencies,
): ReaderRouteCallbacks =
    ReaderRouteCallbacks(
        onFontSizeChange = { size ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateFontSize(size)
            }
        },
        onThemeChange = { theme ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateTheme(theme.name)
            }
        },
        onTextBrightnessChange = { brightness ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateReaderTextBrightness(brightness)
            }
        },
        onInvertedScrollChange = { enabled ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateInvertedScroll(enabled)
            }
        },
        onFocusModeEnabledChange = { enabled ->
            dependencies.coroutineScope.launch {
                if (enabled) {
                    if (!dependencies.prefs.focusModeEnabled) {
                        dependencies.container.preferencesRepository.updateFocusModeEnabled(true)
                    }
                    dependencies.container.preferencesRepository.updateFocusApplyInReader(true)
                } else {
                    dependencies.container.preferencesRepository.updateFocusApplyInReader(false)
                }
            }
        },
        onAddBookmark = { chapterIndex, tokenIndex, previewText ->
            dependencies.coroutineScope.launch {
                val id = "${dependencies.bookId}:$chapterIndex:$tokenIndex"
                dependencies.container.bookmarkRepository.add(
                    Bookmark(
                        id = id,
                        bookId = dependencies.bookIdValue,
                        chapterIndex = chapterIndex,
                        tokenIndex = tokenIndex,
                        previewText = previewText,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                dependencies.onShowUserMessage(
                    dependencies.resources.getString(R.string.toast_bookmark_added)
                )
            }
        },
        onOpenBookmarks = {
            dependencies.saveCurrentReaderPosition()
            dependencies.container.readingSessionCoordinator.finalizeReader(
                dependencies.bookIdValue
            )
            dependencies.navController.navigate(KairoRoutes.libraryBookmarks())
        },
        onOpenLibrary = {
            dependencies.navigateReaderToLibrary()
        },
        onFocusChange = { newFocusIndex ->
            dependencies.readerViewModel.setFocusIndex(newFocusIndex)
            val wordIndex =
                resolveWordIndex(
                    dependencies.uiState.chapterData?.wordCountByToken,
                    newFocusIndex,
                )
            dependencies.readerPositionSaver.saveDebounced(
                ReadingPosition(
                    dependencies.bookIdValue,
                    dependencies.uiState.chapterIndex,
                    newFocusIndex,
                    wordIndex,
                ),
            )
        },
        onPageChange = { pageIndex, focusTokenIndex ->
            val tokens = dependencies.uiState.chapterData?.tokens.orEmpty()
            val safeIndex =
                if (tokens.isEmpty()) {
                    focusTokenIndex.coerceAtLeast(0)
                } else {
                    tokens.nearestWordIndex(focusTokenIndex).coerceIn(0, tokens.lastIndex)
                }
            dependencies.readerViewModel.setPageIndex(pageIndex, safeIndex)
            val wordIndex =
                resolveWordIndex(
                    dependencies.uiState.chapterData?.wordCountByToken,
                    safeIndex,
                )
            dependencies.readerPositionSaver.saveDebounced(
                ReadingPosition(
                    dependencies.bookIdValue,
                    dependencies.uiState.chapterIndex,
                    safeIndex,
                    wordIndex,
                ),
            )
        },
        onStartTimedReading = { mode, start ->
            dependencies.startTimedReading(mode, start, rememberMode = false)
        },
        onSelectTimedReadingMode = { mode, start ->
            dependencies.startTimedReading(mode, start, rememberMode = true)
        },
        onChapterChange = { newIndex, focusIndex ->
            dependencies.readerViewModel.loadChapter(newIndex, focusIndex)
        },
        onTableOfContentsTargetSelected = { target ->
            dependencies.container.readingSessionCoordinator.rebaseReader(dependencies.bookIdValue)
            dependencies.readerViewModel.loadTableOfContentsTarget(target)
        },
        onViewportMetricsChanged = { resolvedFontSizeSp, viewportHeightDp ->
            dependencies.readerViewModel.updatePaginationMetrics(
                resolvedFontSizeSp,
                viewportHeightDp,
            )
        },
    )

private fun ReaderRouteCallbackDependencies.buildCurrentReaderPosition(): ReadingPosition? {
    // A tap or chapter change can precede recomposition. Read the complete current
    // location together, retaining a pending RSVP result only until the model catches up.
    val liveState = readerViewModel.uiState.value
    val currentState = if (liveState == uiState) effectiveUiState else liveState
    val chapterData = currentState.chapterData ?: return null
    val tokens = chapterData.tokens
    if (tokens.isEmpty()) return null
    val safeIndex = tokens.nearestWordIndex(currentState.focusIndex).coerceIn(0, tokens.lastIndex)
    val wordIndex = resolveWordIndex(chapterData.wordCountByToken, safeIndex)
    return ReadingPosition(
        bookIdValue,
        currentState.chapterIndex,
        safeIndex,
        wordIndex,
    )
}

private fun ReaderRouteCallbackDependencies.saveCurrentReaderPosition() {
    buildCurrentReaderPosition()?.let(readerPositionSaver::saveImmediate)
}

private fun ReaderRouteCallbackDependencies.navigateReaderToLibrary() {
    container.readingSessionCoordinator.finalizeReader(bookIdValue)
    val position = buildCurrentReaderPosition()
    lifecycleScope.launch(dispatcherProvider.io) {
        if (position != null) {
            readerPositionSaver.saveImmediateAndJoin(position)
        }
        withContext(Dispatchers.Main) {
            navController.navigate(KairoRoutes.LIBRARY) {
                popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
}

private fun ReaderRouteCallbackDependencies.startTimedReading(
    mode: TimedReadingMode,
    start: Int,
    rememberMode: Boolean,
) {
    container.readingSessionCoordinator.finalizeReader(bookIdValue)
    RsvpLaunchSnapshotStore.put(
        bookId = bookId,
        chapterIndex = uiState.chapterIndex,
        tokens = uiState.chapterData?.tokens.orEmpty(),
        languageTag = languageTag,
    )
    val wordIndex = resolveWordIndex(uiState.chapterData?.wordCountByToken, start)
    val launchTempoMsPerWord =
        getPendingRsvpLaunchTempoMsPerWord()
            .takeIf { it > 0L }
    lifecycleScope.launch(dispatcherProvider.io) {
        if (rememberMode) {
            container.preferencesRepository.updateTimedReadingMode(mode)
        }
        val existingPosition = container.readingPositionRepository.getPosition(bookIdValue)
        val resumeCursor =
            existingPosition
                ?.takeIf {
                    it.chapterIndex == uiState.chapterIndex &&
                        it.tokenIndex == start
                }?.rsvpResumeCursor ?: -1
        readerPositionSaver.saveImmediateAndJoin(
            ReadingPosition(
                bookIdValue,
                uiState.chapterIndex,
                start,
                wordIndex,
                rsvpResumeCursor = resumeCursor,
            ),
        )
        withContext(Dispatchers.Main) {
            navController.navigate(
                KairoRoutes.timedReading(
                    mode = mode,
                    bookId = bookId,
                    chapterIndex = uiState.chapterIndex,
                    tokenIndex = start,
                    tempoMsPerWord = launchTempoMsPerWord,
                )
            )
            clearPendingRsvpLaunchTempoMsPerWord()
        }
    }
}
