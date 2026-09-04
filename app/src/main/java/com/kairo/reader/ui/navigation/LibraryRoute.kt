@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.LibrarySearchResult
import com.kairo.reader.core.model.LibrarySearchResultKind
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.model.withEdit
import com.kairo.reader.data.books.TextImportRequest
import com.kairo.reader.data.search.LibrarySearchController
import com.kairo.reader.data.sessions.buildReadingMomentum
import com.kairo.reader.ui.export.NoteExportSheet
import com.kairo.reader.ui.export.NoteExportUiBindings
import com.kairo.reader.ui.library.ImportUiState
import com.kairo.reader.ui.library.LibraryBookFilter
import com.kairo.reader.ui.library.LibraryBookProgress
import com.kairo.reader.ui.library.LibraryScreen
import com.kairo.reader.ui.library.LibraryTab
import com.kairo.reader.ui.library.buildLibraryEstimatedWpmByBookId
import com.kairo.reader.ui.library.buildLibraryProgress
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class LibraryRouteInput(
    val container: KairoApplication,
    val navController: NavHostController,
    val prefs: UserPreferences,
    val selectedWpm: Int,
    val importState: ImportUiState,
    val initialTabRouteValue: String? = null,
    val onImportFile: (Uri) -> Unit,
    val onImportUrl: (String) -> Unit,
    val onImportText: (TextImportRequest) -> Unit,
    val tutorialState: StartingTutorialOverlayState?,
    val noteExportUi: NoteExportUiBindings,
    val onTutorialNext: () -> Unit,
    val onTutorialPrevious: () -> Unit,
    val onTutorialSkip: () -> Unit,
)

@Composable
internal fun LibraryRoute(input: LibraryRouteInput) =
    with(input) {
        val coroutineScope = rememberCoroutineScope()
        val dispatcherProvider = container.dispatcherProvider
        val books by container.libraryRepository.observeLibrary().collectAsState(initial = emptyList())
        val bookmarks by container.bookmarkRepository.observeBookmarks().collectAsState(
            initial = emptyList()
        )
        val annotations by container.savedAnnotationRepository.observeAnnotations().collectAsState(
            initial = emptyList(),
        )
        val sessions by container.readingSessionRepository.observeSessions().collectAsState(
            initial = emptyList(),
        )
        val currentLocalDayKey = rememberCurrentLocalDayKey()
        val momentum =
            remember(sessions, currentLocalDayKey, prefs.momentumResetCutoffAt) {
                buildReadingMomentum(
                    sessions = sessions,
                    resetCutoffAt = prefs.momentumResetCutoffAt,
                )
            }
        val searchController = remember(container.searchRepository, coroutineScope) {
            LibrarySearchController(container.searchRepository, coroutineScope)
        }
        val searchState by searchController.state.collectAsState()
        val positions by container.readingPositionRepository.observePositions().collectAsState(
            initial = emptyList()
        )
        val libraryEstimatedWpmByBook by produceState<Map<String, Int>>(
            initialValue = emptyMap(),
            books,
            prefs.rsvpConfig,
            selectedWpm,
        ) {
            value =
                withContext(dispatcherProvider.default) {
                    buildLibraryEstimatedWpmByBookId(
                        books = books,
                        config = prefs.rsvpConfig,
                        fallbackEstimatedWpm = selectedWpm,
                    )
                }
        }
        val bookProgress by produceState<Map<String, LibraryBookProgress>>(
            initialValue = emptyMap(),
            books,
            positions,
            libraryEstimatedWpmByBook,
        ) {
            value =
                withContext(dispatcherProvider.io) {
                    buildLibraryProgress(
                        books = books,
                        positions = positions,
                        estimatedWpmByBookId = libraryEstimatedWpmByBook,
                    )
                }
        }
        val initialTab =
            when (initialTabRouteValue?.lowercase()) {
                KairoRoutes.TAB_BOOKMARKS -> LibraryTab.Saved
                else -> LibraryTab.Books
            }
        val initialBookFilter =
            if (initialTabRouteValue?.lowercase() == KairoRoutes.TAB_COMPLETED) {
                LibraryBookFilter.COMPLETED
            } else {
                LibraryBookFilter.READING
            }

        fun openSearchResult(result: LibrarySearchResult) {
            if (
                result.kind != LibrarySearchResultKind.BOOK &&
                result.matchStartCodePointOffset == null
            ) {
                coroutineScope.launch(dispatcherProvider.io) {
                    container.readingPositionRepository.savePosition(
                        ReadingPosition(
                            result.bookId,
                            result.chapterIndex,
                            result.tokenIndex,
                        ),
                    )
                }
            }
            navController.navigate(
                if (result.kind == LibrarySearchResultKind.BOOK) {
                    KairoRoutes.reader(result.bookId.value)
                } else {
                    KairoRoutes.reader(
                        result.bookId.value,
                        result.chapterIndex,
                        result.tokenIndex,
                        searchCodePointOffset = result.matchStartCodePointOffset,
                    )
                },
            )
        }

        LibraryScreen(
            books = books,
            bookmarks = bookmarks,
            annotations = annotations,
            momentum = momentum,
            weeklyGoalMinutes = prefs.weeklyReadingGoalMinutes,
            searchState = searchState,
            bookProgress = bookProgress,
            initialTab = initialTab,
            initialBookFilter = initialBookFilter,
            importState = importState,
            onOpen = { book ->
                navController.navigate(KairoRoutes.reader(book.id.value))
            },
            onOpenBookmark = { bookId, chapterIndex, tokenIndex ->
                coroutineScope.launch(dispatcherProvider.io) {
                    container.readingPositionRepository.savePosition(
                        ReadingPosition(BookId(bookId), chapterIndex, tokenIndex),
                    )
                }
                navController.navigate(KairoRoutes.reader(bookId, chapterIndex, tokenIndex))
            },
            onDeleteBookmark = { bookmarkId ->
                coroutineScope.launch { container.bookmarkRepository.delete(bookmarkId) }
            },
            onDeleteAnnotation = { annotationId ->
                coroutineScope.launch { container.savedAnnotationRepository.delete(annotationId) }
            },
            onEditAnnotation = { request ->
                val annotation =
                    annotations.firstOrNull { it.annotation.id == request.annotationId }?.annotation
                        ?: return@LibraryScreen
                coroutineScope.launch {
                    container.savedAnnotationRepository.save(
                        annotation.withEdit(request, System.currentTimeMillis()),
                    )
                }
            },
            onRequestNoteExport = noteExportUi.requestExport,
            onDeleteBookmarksForBook = { bookId ->
                coroutineScope.launch {
                    container.bookmarkRepository.deleteForBook(BookId(bookId))
                }
            },
            onSearchQuery = searchController::search,
            onRetrySearch = searchController::retry,
            onOpenSearchResult = ::openSearchResult,
            onWeeklyGoalChange = { minutes ->
                coroutineScope.launch {
                    container.preferencesRepository.updateWeeklyReadingGoalMinutes(minutes)
                }
            },
            onResetMomentum = {
                coroutineScope.launch {
                    container.preferencesRepository.updateMomentumResetCutoffAt(
                        System.currentTimeMillis(),
                    )
                }
            },
            onImportFile = onImportFile,
            onImportUrl = onImportUrl,
            onImportText = onImportText,
            onSettings = { navController.navigate(KairoRoutes.SETTINGS) },
            onSetCompleted = { book, isCompleted ->
                coroutineScope.launch {
                    container.libraryRepository.setCompleted(book.id.value, isCompleted)
                }
            },
            onDelete = { book ->
                coroutineScope.launch { container.libraryRepository.delete(book.id.value) }
            },
            tutorialState = tutorialState,
            onTutorialNext = onTutorialNext,
            onTutorialPrevious = onTutorialPrevious,
            onTutorialSkip = onTutorialSkip,
        )
        NoteExportSheet(
            state = noteExportUi.state,
            annotations = annotations,
            onSelectScope = noteExportUi.selectScope,
            onSelectFormat = noteExportUi.selectFormat,
            onDismiss = noteExportUi.dismissSheet,
            onSave = noteExportUi.save,
        )
    }
