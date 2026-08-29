@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.R
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.model.nearestWordIndex
import com.kairo.reader.core.rsvp.RsvpConfigResolver
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.core.rsvp.RsvpSegmentationRolloutResolver
import com.kairo.reader.ui.reader.FileReaderImageBoundsResolver
import com.kairo.reader.ui.reader.ReaderScreen
import com.kairo.reader.ui.reader.ReaderUiState
import com.kairo.reader.ui.reader.ReaderViewModel
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState

internal data class ReaderRouteInput(
    val backStackEntry: NavBackStackEntry,
    val container: KairoApplication,
    val navController: NavHostController,
    val prefs: UserPreferences,
    val estimatedWpm: Int,
    val tutorialActive: Boolean,
    val tutorialState: StartingTutorialOverlayState?,
    val initialChapterIndex: Int? = null,
    val initialTokenIndex: Int? = null,
    val initialSearchCodePointOffset: Int? = null,
    val onShowUserMessage: (String) -> Unit,
    val onTutorialNext: () -> Unit,
    val onTutorialPrevious: () -> Unit,
    val onTutorialSkip: () -> Unit,
)

@Composable
internal fun ReaderRoute(input: ReaderRouteInput) {
    with(input) {
        val bookId = backStackEntry.arguments?.getString(KairoRoutes.ARG_BOOK_ID) ?: return
        val dispatcherProvider = container.dispatcherProvider
        val coroutineScope = rememberCoroutineScope()
        val resources = LocalResources.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val readerPositionSaver = rememberReaderPositionSaver(bookId, lifecycleOwner, container)

        val bookState =
            produceState<ReaderBookLoadState>(
                initialValue = ReaderBookLoadState.Loading,
                bookId,
            ) {
                value =
                    runCatching { container.bookRepository.getBook(BookId(bookId)) }
                        .fold(
                            onSuccess = { book -> ReaderBookLoadState.Loaded(book) },
                            onFailure = { ReaderBookLoadState.Missing },
                        )
            }
        val book =
            when (val state = bookState.value) {
                is ReaderBookLoadState.Loaded -> state.book
                ReaderBookLoadState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return
                }
                ReaderBookLoadState.Missing -> {
                    ReaderMissingBookState(
                        onOpenLibrary = {
                            navController.navigate(KairoRoutes.LIBRARY) {
                                popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                    return
                }
            }

        val readerViewModel = rememberReaderViewModel(container)
        val uiState by readerViewModel.uiState.collectAsState()
        val rsvpResult = rememberReaderRsvpResult(backStackEntry, bookId, uiState)
        ApplyReaderRsvpResult(
            result = rsvpResult,
            uiState = uiState,
            bookId = bookId,
            readerViewModel = readerViewModel,
            readerPositionSaver = readerPositionSaver,
            backStackEntry = backStackEntry,
        )

        val hasInitialized =
            rememberReaderInitialization(
                book = book,
                bookId = bookId,
                initialChapterIndex = initialChapterIndex,
                initialTokenIndex = initialTokenIndex,
                initialSearchCodePointOffset = initialSearchCodePointOffset,
                container = container,
                uiState = uiState,
                readerViewModel = readerViewModel,
            )
        ReaderPositionEffects(
            hasInitialized = hasInitialized,
            rsvpResult = rsvpResult,
            bookId = bookId,
            container = container,
            uiState = uiState,
            readerViewModel = readerViewModel,
            readerPositionSaver = readerPositionSaver,
            lifecycleOwner = lifecycleOwner,
        )

        val resolvedRsvpConfig = RsvpConfigResolver.resolve(prefs.rsvpConfig, book.languageTag)
        val rsvpGenerationOptions =
            rememberReaderRsvpGenerationOptions(container, book.languageTag, resolvedRsvpConfig)
        val readerEstimatedWpm =
            rememberReaderEstimatedWpm(
                baseConfig = resolvedRsvpConfig,
                fallbackEstimatedWpm = estimatedWpm,
                dispatcherProvider = dispatcherProvider,
                languageTag = book.languageTag,
                paceOptions = rsvpGenerationOptions.asPaceEstimationOptions(),
            )
        PrefetchReaderFrames(hasInitialized, uiState, resolvedRsvpConfig, rsvpGenerationOptions, container, bookId)
        val savedBindings =
            rememberReaderSavedBindings(
                container = container,
                bookId = BookId(bookId),
                chapterIndex = rsvpResult.effectiveUiState.chapterIndex,
                onShowUserMessage = onShowUserMessage,
            )

        var lastExplicitFocusIndex by remember(bookId) { mutableIntStateOf(-1) }
        val readerCallbacks =
            buildReaderRouteCallbacks(
                ReaderRouteCallbackDependencies(
                    container = container,
                    navController = navController,
                    prefs = prefs,
                    bookId = bookId,
                    bookIdValue = BookId(bookId),
                    languageTag = book.languageTag,
                    dispatcherProvider = dispatcherProvider,
                    coroutineScope = coroutineScope,
                    lifecycleScope = lifecycleOwner.lifecycleScope,
                    resources = resources,
                    uiState = uiState,
                    effectiveUiState = rsvpResult.effectiveUiState,
                    readerViewModel = readerViewModel,
                    readerPositionSaver = readerPositionSaver,
                    getLastExplicitFocusIndex = { lastExplicitFocusIndex },
                    setLastExplicitFocusIndex = { lastExplicitFocusIndex = it },
                    getPendingRsvpLaunchTempoMsPerWord = { rsvpResult.pendingLaunchTempoMs },
                    clearPendingRsvpLaunchTempoMsPerWord = rsvpResult.clearPendingLaunchTempo,
                    onShowUserMessage = onShowUserMessage,
                )
            )

        RecordReaderSessionEffect(
            container = container,
            bookId = BookId(bookId),
            hasInitialized = hasInitialized,
            readerState = rsvpResult.effectiveUiState,
            lifecycleOwner = lifecycleOwner,
        )

        BackHandler(enabled = !tutorialActive, onBack = readerCallbacks.onOpenLibrary)

        ReaderScreen(
            book = book,
            uiState = rsvpResult.effectiveUiState,
            fontSizeSp = prefs.readerFontSizeSp,
            invertedScroll = prefs.invertedScroll,
            readerTheme = prefs.readerTheme,
            textBrightness = prefs.readerTextBrightness,
            estimatedWpm = readerEstimatedWpm,
            onFontSizeChange = readerCallbacks.onFontSizeChange,
            onThemeChange = readerCallbacks.onThemeChange,
            onTextBrightnessChange = readerCallbacks.onTextBrightnessChange,
            onInvertedScrollChange = readerCallbacks.onInvertedScrollChange,
            focusModeEnabled = prefs.focusModeEnabled && prefs.focusApplyInReader,
            onFocusModeEnabledChange = readerCallbacks.onFocusModeEnabledChange,
            onAddBookmark = readerCallbacks.onAddBookmark,
            onOpenBookmarks = readerCallbacks.onOpenBookmarks,
            onOpenLibrary = readerCallbacks.onOpenLibrary,
            onFocusChange = readerCallbacks.onFocusChange,
            onPageChange = readerCallbacks.onPageChange,
            timedReadingMode = prefs.timedReadingMode,
            onStartTimedReading = readerCallbacks.onStartTimedReading,
            onSelectTimedReadingMode = readerCallbacks.onSelectTimedReadingMode,
            onChapterChange = readerCallbacks.onChapterChange,
            onTableOfContentsTargetSelected = readerCallbacks.onTableOfContentsTargetSelected,
            onViewportMetricsChanged = readerCallbacks.onViewportMetricsChanged,
            savedAnnotations = savedBindings.annotations,
            bookSearchState = savedBindings.searchState,
            onSearchBook = savedBindings.onSearch,
            onRetryBookSearch = savedBindings.onRetrySearch,
            onSearchResultSelected = { result ->
                container.readingSessionCoordinator.rebaseReader(BookId(bookId))
                readerViewModel.loadChapter(
                    chapterIndex = result.chapterIndex,
                    initialFocusIndex =
                    result.tokenIndex.takeIf { result.matchStartCodePointOffset == null },
                    initialSearchCodePointOffset = result.matchStartCodePointOffset,
                )
            },
            onSaveAnnotation = savedBindings.onSaveAnnotation,
            tutorialState = tutorialState,
            onTutorialNext = onTutorialNext,
            onTutorialPrevious = onTutorialPrevious,
            onTutorialSkip = onTutorialSkip,
        )
    }
}

@Composable
private fun rememberReaderRsvpGenerationOptions(
    container: KairoApplication,
    languageTag: String?,
    config: RsvpConfig,
): RsvpGenerationOptions =
    remember(languageTag, config) {
        RsvpSegmentationRolloutResolver.resolve(
            languageTag = languageTag,
            config = config,
            isDebugBuild = container.isDebuggableBuild(),
        )
    }

@Composable
private fun rememberReaderViewModel(container: KairoApplication): ReaderViewModel {
    val imageBoundsResolver =
        remember(container) {
            FileReaderImageBoundsResolver(
                filesDirectory = container.filesDir,
                ioDispatcher = container.dispatcherProvider.io,
            )
        }
    return viewModel(
        factory =
        ReaderViewModel.factory(
            container.bookRepository,
            container.tokenRepository,
            container.dispatcherProvider,
            imageBoundsResolver,
        ),
    )
}

@Composable
private fun rememberReaderPositionSaver(
    bookId: String,
    lifecycleOwner: LifecycleOwner,
    container: KairoApplication,
): ReaderPositionSaver =
    remember(bookId, lifecycleOwner) {
        ReaderPositionSaver(
            scope = lifecycleOwner.lifecycleScope,
            repository = container.readingPositionRepository,
            saveDispatcher = container.dispatcherProvider.io,
        )
    }

@Composable
private fun rememberReaderInitialization(
    book: Book,
    bookId: String,
    initialChapterIndex: Int?,
    initialTokenIndex: Int?,
    initialSearchCodePointOffset: Int?,
    container: KairoApplication,
    uiState: ReaderUiState,
    readerViewModel: ReaderViewModel,
): Boolean {
    var hasInitialized by rememberSaveable { mutableStateOf(false) }
    val restoresSavedPosition = initialChapterIndex == null || initialTokenIndex == null
    LaunchedEffect(book) {
        if (!hasInitialized || uiState.chapterData == null) {
            val savedPosition =
                if (
                    restoresSavedPosition ||
                    (hasInitialized && initialSearchCodePointOffset == null)
                ) {
                    container.readingPositionRepository.getPosition(BookId(bookId))
                } else {
                    null
                }
            readerViewModel.loadBook(
                book,
                savedPosition?.chapterIndex ?: initialChapterIndex ?: 0,
                savedPosition?.tokenIndex ?: initialTokenIndex ?: 0,
                initialSearchCodePointOffset =
                if (savedPosition == null) initialSearchCodePointOffset else null,
            )
            hasInitialized = true
        }
    }
    return hasInitialized
}

@Composable
private fun ReaderPositionEffects(
    hasInitialized: Boolean,
    rsvpResult: ReaderRsvpResult,
    bookId: String,
    container: KairoApplication,
    uiState: ReaderUiState,
    readerViewModel: ReaderViewModel,
    readerPositionSaver: ReaderPositionSaver,
    lifecycleOwner: LifecycleOwner,
) {
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState != Lifecycle.State.RESUMED || !hasInitialized) return@LaunchedEffect
        if (rsvpResult.chapterIndex >= 0 && rsvpResult.safeTokenIndex >= 0) return@LaunchedEffect
        val savedPosition = container.readingPositionRepository.getPosition(BookId(bookId))
        val tokens = uiState.chapterData?.tokens
        if (savedPosition?.chapterIndex == uiState.chapterIndex &&
            !tokens.isNullOrEmpty() &&
            savedPosition.tokenIndex != uiState.focusIndex
        ) {
            readerViewModel.applyFocusIndex(savedPosition.tokenIndex.coerceIn(0, tokens.lastIndex))
        }
    }
    LaunchedEffect(uiState.chapterIndex, uiState.chapterData) {
        if (!hasInitialized) return@LaunchedEffect
        val chapterData = uiState.chapterData ?: return@LaunchedEffect
        val tokens = chapterData.tokens.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val safeIndex = tokens.nearestWordIndex(uiState.focusIndex).coerceIn(0, tokens.lastIndex)
        readerPositionSaver.saveDebounced(
            ReadingPosition(
                BookId(bookId),
                uiState.chapterIndex,
                safeIndex,
                resolveWordIndex(chapterData.wordCountByToken, safeIndex),
            ),
        )
    }
}

@Composable
private fun PrefetchReaderFrames(
    hasInitialized: Boolean,
    uiState: ReaderUiState,
    config: RsvpConfig,
    generationOptions: RsvpGenerationOptions,
    container: KairoApplication,
    bookId: String,
) {
    LaunchedEffect(
        uiState.chapterIndex,
        uiState.chapterData,
        uiState.focusIndex,
        config,
        generationOptions,
    ) {
        if (!hasInitialized) return@LaunchedEffect
        val tokens = uiState.chapterData?.tokens?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val safeStartIndex = tokens.nearestWordIndex(uiState.focusIndex).coerceIn(0, tokens.lastIndex)
        container.rsvpFrameRepository.prefetchFrames(
            BookId(bookId),
            uiState.chapterIndex,
            config,
            startIndex = safeStartIndex,
            options = generationOptions,
        )
    }
}

private data class ReaderRsvpResult(
    val chapterIndex: Int,
    val safeTokenIndex: Int,
    val resumeCursor: Int,
    val pendingLaunchTempoMs: Long,
    val effectiveUiState: ReaderUiState,
    val clearPendingLaunchTempo: () -> Unit,
)

@Composable
private fun rememberReaderRsvpResult(
    backStackEntry: NavBackStackEntry,
    bookId: String,
    uiState: ReaderUiState,
): ReaderRsvpResult {
    val resultTokenIndex by
        remember(backStackEntry) {
            backStackEntry.savedStateHandle.getStateFlow(KairoSavedStateKeys.RSVP_RESULT_TOKEN_INDEX, -1)
        }.collectAsState(initial = -1)
    val resultChapterIndex by
        remember(backStackEntry) {
            backStackEntry.savedStateHandle.getStateFlow(KairoSavedStateKeys.RSVP_RESULT_CHAPTER_INDEX, -1)
        }.collectAsState(initial = -1)
    val resultResumeCursor by
        remember(backStackEntry) {
            backStackEntry.savedStateHandle.getStateFlow(KairoSavedStateKeys.RSVP_RESULT_RESUME_CURSOR, -1)
        }.collectAsState(initial = -1)
    val resultTempoMs by
        remember(backStackEntry) {
            backStackEntry.savedStateHandle.getStateFlow(KairoSavedStateKeys.RSVP_RESULT_TEMPO_MS, -1L)
        }.collectAsState(initial = -1L)
    var pendingLaunchTempoMs by rememberSaveable(bookId) { mutableLongStateOf(-1L) }
    LaunchedEffect(resultTempoMs) {
        if (resultTempoMs > 0L) {
            pendingLaunchTempoMs = resultTempoMs
            backStackEntry.savedStateHandle[KairoSavedStateKeys.RSVP_RESULT_TEMPO_MS] = -1L
        }
    }
    val safeTokenIndex = safeRsvpResultTokenIndex(resultTokenIndex, resultChapterIndex, uiState)
    val effectiveUiState =
        if (safeTokenIndex >= 0 && resultChapterIndex == uiState.chapterIndex) {
            uiState.copy(focusIndex = safeTokenIndex)
        } else {
            uiState
        }
    return ReaderRsvpResult(
        chapterIndex = resultChapterIndex,
        safeTokenIndex = safeTokenIndex,
        resumeCursor = resultResumeCursor,
        pendingLaunchTempoMs = pendingLaunchTempoMs,
        effectiveUiState = effectiveUiState,
        clearPendingLaunchTempo = { pendingLaunchTempoMs = -1L },
    )
}

private fun safeRsvpResultTokenIndex(
    resultTokenIndex: Int,
    resultChapterIndex: Int,
    uiState: ReaderUiState,
): Int {
    if (resultTokenIndex < 0) return resultTokenIndex
    val tokens = uiState.chapterData?.tokens
    return if (resultChapterIndex == uiState.chapterIndex && !tokens.isNullOrEmpty()) {
        tokens.nearestWordIndex(resultTokenIndex)
    } else {
        resultTokenIndex.coerceAtLeast(0)
    }
}

@Composable
private fun ApplyReaderRsvpResult(
    result: ReaderRsvpResult,
    uiState: ReaderUiState,
    bookId: String,
    readerViewModel: ReaderViewModel,
    readerPositionSaver: ReaderPositionSaver,
    backStackEntry: NavBackStackEntry,
) {
    LaunchedEffect(
        result.chapterIndex,
        result.safeTokenIndex,
        result.resumeCursor,
        uiState.chapterIndex,
        uiState.chapterData,
    ) {
        if (result.chapterIndex < 0 || result.safeTokenIndex < 0) return@LaunchedEffect
        if (result.chapterIndex != uiState.chapterIndex) {
            readerViewModel.loadChapter(result.chapterIndex, result.safeTokenIndex)
            return@LaunchedEffect
        }
        val chapterData = uiState.chapterData ?: return@LaunchedEffect
        val safeTargetIndex =
            if (chapterData.tokens.isNotEmpty()) {
                chapterData.tokens.nearestWordIndex(result.safeTokenIndex).coerceIn(0, chapterData.tokens.lastIndex)
            } else {
                result.safeTokenIndex
            }
        if (safeTargetIndex != uiState.focusIndex) readerViewModel.applyFocusIndex(safeTargetIndex)
        readerPositionSaver.saveImmediate(
            ReadingPosition(
                BookId(bookId),
                result.chapterIndex,
                safeTargetIndex,
                resolveWordIndex(chapterData.wordCountByToken, safeTargetIndex),
                rsvpResumeCursor = result.resumeCursor,
            ),
        )
        backStackEntry.savedStateHandle[KairoSavedStateKeys.RSVP_RESULT_CHAPTER_INDEX] = -1
        backStackEntry.savedStateHandle[KairoSavedStateKeys.RSVP_RESULT_TOKEN_INDEX] = -1
        backStackEntry.savedStateHandle[KairoSavedStateKeys.RSVP_RESULT_RESUME_CURSOR] = -1
    }
}

private sealed interface ReaderBookLoadState {
    data object Loading : ReaderBookLoadState
    data class Loaded(val book: Book) : ReaderBookLoadState
    data object Missing : ReaderBookLoadState
}

@Composable
private fun ReaderMissingBookState(onOpenLibrary: () -> Unit) {
    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.reader_missing_book_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.reader_missing_book_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenLibrary) {
                Text(text = stringResource(R.string.action_return_to_library))
            }
        }
    }
}
