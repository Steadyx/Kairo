package com.kairo.reader.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.export.NoteExportScope
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookmarkItem
import com.kairo.reader.core.model.EditSavedAnnotationRequest
import com.kairo.reader.core.model.LibrarySearchResult
import com.kairo.reader.core.model.ReadingMomentum
import com.kairo.reader.core.model.SavedAnnotationItem
import com.kairo.reader.data.books.BookImportFormats
import com.kairo.reader.data.books.TextImportRequest
import com.kairo.reader.data.search.LibrarySearchState
import com.kairo.reader.ui.rememberWindowContainerMetrics
import com.kairo.reader.ui.saved.SavedAnnotationEditorDialog
import com.kairo.reader.ui.search.LibrarySearchOverlay
import com.kairo.reader.ui.tutorial.StartingTutorialOverlay
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import com.kairo.reader.ui.tutorial.StartingTutorialTargetIds
import com.kairo.reader.ui.tutorial.startingTutorialTarget

@Suppress("LongMethod", "LongParameterList")
@Composable
fun LibraryScreen(
    books: List<Book>,
    bookmarks: List<BookmarkItem>,
    annotations: List<SavedAnnotationItem> = emptyList(),
    momentum: ReadingMomentum = ReadingMomentum(),
    weeklyGoalMinutes: Int = 120,
    searchState: LibrarySearchState = LibrarySearchState.Idle,
    bookProgress: Map<String, LibraryBookProgress>,
    initialTab: LibraryTab = LibraryTab.Books,
    initialBookFilter: LibraryBookFilter = LibraryBookFilter.READING,
    importState: ImportUiState = ImportUiState(),
    onOpen: (Book) -> Unit,
    onOpenBookmark: (bookId: String, chapterIndex: Int, tokenIndex: Int) -> Unit,
    onDeleteBookmark: (bookmarkId: String) -> Unit,
    onDeleteAnnotation: (annotationId: String) -> Unit = {},
    onEditAnnotation: (EditSavedAnnotationRequest) -> Unit = {},
    onRequestNoteExport: (NoteExportScope) -> Unit = {},
    onDeleteBookmarksForBook: (bookId: String) -> Unit,
    onSearchQuery: (String) -> Unit = {},
    onRetrySearch: () -> Unit = {},
    onOpenSearchResult: (LibrarySearchResult) -> Unit = {},
    onWeeklyGoalChange: (Int) -> Unit = {},
    onResetMomentum: () -> Unit = {},
    onImportFile: (Uri) -> Unit,
    onImportUrl: (String) -> Unit,
    onImportText: (TextImportRequest) -> Unit = {},
    onSettings: () -> Unit,
    onSetCompleted: (Book, Boolean) -> Unit,
    onDelete: (Book) -> Unit,
    tutorialState: StartingTutorialOverlayState? = null,
    onTutorialNext: () -> Unit = {},
    onTutorialPrevious: () -> Unit = {},
    onTutorialSkip: () -> Unit = {},
) {
    // File picker launcher for supported ebook and document files.
    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            uri?.let { onImportFile(it) }
        }
    val windowMetrics = rememberWindowContainerMetrics()
    val compactLandscape = windowMetrics.isCompactLandscape(COMPACT_LANDSCAPE_MAX_HEIGHT_DP.dp)
    var selectedTabName by rememberSaveable(initialTab) { mutableStateOf(initialTab.name) }
    val selectedTab =
        remember(selectedTabName) {
            LibraryTab.entries.firstOrNull { it.name == selectedTabName } ?: LibraryTab.Books
        }
    var bookFilterName by rememberSaveable(initialBookFilter) { mutableStateOf(initialBookFilter.name) }
    val bookFilter =
        remember(bookFilterName) {
            LibraryBookFilter.entries.firstOrNull { it.name == bookFilterName }
                ?: LibraryBookFilter.READING
        }
    var savedFilterName by rememberSaveable { mutableStateOf(SavedFilter.ALL.name) }
    val savedFilter =
        remember(savedFilterName) {
            SavedFilter.entries.firstOrNull { it.name == savedFilterName } ?: SavedFilter.ALL
        }
    var pendingDeleteBook by remember { mutableStateOf<Book?>(null) }
    var pendingClearBookmarkBook by remember { mutableStateOf<Book?>(null) }
    var pendingEditAnnotationId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteAnnotationId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSupportedFormats by rememberSaveable { mutableStateOf(false) }
    var showReadLinkDialog by rememberSaveable { mutableStateOf(false) }
    var linkInput by rememberSaveable { mutableStateOf("") }
    var showAddTextDialog by rememberSaveable { mutableStateOf(false) }
    var textImportTitle by rememberSaveable { mutableStateOf("") }
    var textImportContent by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val tutorialTargets = remember { mutableStateMapOf<String, Rect>() }
    val openSystemFilePicker = {
        showSupportedFormats = false
        filePickerLauncher.launch(BookImportFormats.pickerMimeTypes.toTypedArray())
    }
    val launchBookImport = { showSupportedFormats = true }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
                .padding(
                    horizontal = if (compactLandscape) 12.dp else 16.dp,
                    vertical = if (compactLandscape) 8.dp else 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 8.dp else 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.library_title),
                        style =
                        if (compactLandscape) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                    )
                    Text(
                        stringResource(R.string.library_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (compactLandscape && selectedTab == LibraryTab.Books) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ImportBookButton(
                            onClick = launchBookImport,
                            enabled = !importState.isImporting,
                            compact = true,
                            modifier =
                            Modifier.startingTutorialTarget(StartingTutorialTargetIds.LIBRARY_IMPORT) {
                                    targetId,
                                    bounds,
                                ->
                                tutorialTargets[targetId] = bounds
                            },
                        )
                        ReadFromLinkButton(
                            onClick = { showReadLinkDialog = true },
                            enabled = !importState.isImporting,
                            compact = true,
                        )
                        AddTextButton(
                            onClick = { showAddTextDialog = true },
                            enabled = !importState.isImporting,
                            compact = true,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(onClick = { showSearch = true }) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.content_desc_search),
                    )
                }
                IconButton(
                    onClick = onSettings,
                    modifier =
                    Modifier.startingTutorialTarget(StartingTutorialTargetIds.LIBRARY_SETTINGS) {
                            targetId,
                            bounds,
                        ->
                        tutorialTargets[targetId] = bounds
                    },
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.content_desc_settings),
                    )
                }
            }

            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier =
                Modifier.startingTutorialTarget(StartingTutorialTargetIds.LIBRARY_TABS) {
                        targetId,
                        bounds,
                    ->
                    tutorialTargets[targetId] = bounds
                },
            ) {
                Tab(
                    selected = selectedTab == LibraryTab.Books,
                    onClick = { selectedTabName = LibraryTab.Books.name },
                    text = { Text(stringResource(R.string.library_tab_books)) },
                )
                Tab(
                    selected = selectedTab == LibraryTab.Saved,
                    onClick = { selectedTabName = LibraryTab.Saved.name },
                    text = { Text(stringResource(R.string.library_tab_saved)) },
                )
                Tab(
                    selected = selectedTab == LibraryTab.Momentum,
                    onClick = { selectedTabName = LibraryTab.Momentum.name },
                    text = { Text(stringResource(R.string.library_tab_momentum)) },
                )
            }

            LibrarySelectedTabContent(
                state =
                LibraryTabContentState(
                    selectedTab = selectedTab,
                    books = books,
                    bookmarks = bookmarks,
                    annotations = annotations,
                    momentum = momentum,
                    weeklyGoalMinutes = weeklyGoalMinutes,
                    bookFilter = bookFilter,
                    savedFilter = savedFilter,
                    bookProgress = bookProgress,
                    compactLandscape = compactLandscape,
                    isImporting = importState.isImporting,
                ),
                actions =
                LibraryTabContentActions(
                    onOpen = onOpen,
                    onSetCompleted = onSetCompleted,
                    onRequestDelete = { pendingDeleteBook = it },
                    onOpenBookmark = onOpenBookmark,
                    onDeleteBookmark = onDeleteBookmark,
                    onDeleteAnnotation = { pendingDeleteAnnotationId = it },
                    onEditAnnotation = { pendingEditAnnotationId = it.annotation.id },
                    onRequestNoteExport = onRequestNoteExport,
                    onWeeklyGoalChange = onWeeklyGoalChange,
                    onResetMomentum = onResetMomentum,
                    onRequestClearBookmarks = { pendingClearBookmarkBook = it },
                    onLaunchBookImport = launchBookImport,
                    onShowReadLinkDialog = { showReadLinkDialog = true },
                    onShowAddTextDialog = { showAddTextDialog = true },
                ),
                tutorialTargets = tutorialTargets,
                onBookFilterChange = { bookFilterName = it.name },
                onSavedFilterChange = { savedFilterName = it.name },
            )
        }
        ImportProgressOverlay(state = importState)
        tutorialState?.let { overlayState ->
            StartingTutorialOverlay(
                state = overlayState,
                targetBounds = overlayState.step.targetId?.let(tutorialTargets::get),
                onNext = onTutorialNext,
                onPrevious = onTutorialPrevious,
                onSkip = onTutorialSkip,
            )
        }
    }

    if (showSupportedFormats) {
        SupportedFormatsSheet(
            onDismiss = { showSupportedFormats = false },
            onChooseFile = openSystemFilePicker,
        )
    }

    pendingDeleteBook?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDeleteBook = null },
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.library_delete_message, book.title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(book)
                        pendingDeleteBook = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBook = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingEditAnnotationId
        ?.let { id -> annotations.firstOrNull { it.annotation.id == id } }
        ?.let { item ->
            SavedAnnotationEditorDialog(
                annotation = item.annotation,
                onSave = { request ->
                    onEditAnnotation(request)
                    pendingEditAnnotationId = null
                },
                onDismiss = { pendingEditAnnotationId = null },
            )
        }

    pendingDeleteAnnotationId?.let { annotationId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteAnnotationId = null },
            title = { Text(stringResource(R.string.saved_delete_title)) },
            text = { Text(stringResource(R.string.saved_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAnnotation(annotationId)
                        pendingDeleteAnnotationId = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteAnnotationId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingClearBookmarkBook?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingClearBookmarkBook = null },
            title = { Text(stringResource(R.string.library_bookmark_clear_book_title)) },
            text = {
                Text(
                    stringResource(R.string.library_bookmark_clear_book_message, book.title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBookmarksForBook(book.id.value)
                        pendingClearBookmarkBook = null
                    },
                ) { Text(stringResource(R.string.library_bookmark_clear_book_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearBookmarkBook = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showReadLinkDialog) {
        ReadFromLinkDialog(
            value = linkInput,
            onValueChange = { linkInput = it },
            onDismiss = { showReadLinkDialog = false },
            onSubmit = {
                val submitted = linkInput.trim()
                if (submitted.isNotBlank()) {
                    onImportUrl(submitted)
                    linkInput = ""
                    showReadLinkDialog = false
                }
            },
        )
    }

    if (showAddTextDialog) {
        val defaultTitle = stringResource(R.string.library_text_default_title)
        AddTextDialog(
            title = textImportTitle,
            content = textImportContent,
            onTitleChange = { textImportTitle = it },
            onContentChange = { textImportContent = it },
            onDismiss = { showAddTextDialog = false },
            onSubmit = {
                val submittedContent = textImportContent.trim()
                if (submittedContent.isNotBlank()) {
                    onImportText(
                        TextImportRequest(
                            content = submittedContent,
                            title = textImportTitle.trim().ifBlank { defaultTitle },
                        )
                    )
                    textImportTitle = ""
                    textImportContent = ""
                    showAddTextDialog = false
                }
            },
        )
    }

    if (showSearch) {
        LibrarySearchOverlay(
            title = stringResource(R.string.search_kairo_title),
            hint = stringResource(R.string.search_hint),
            state = searchState,
            onQuery = onSearchQuery,
            onRetry = onRetrySearch,
            onOpenResult = { result ->
                showSearch = false
                onOpenSearchResult(result)
            },
            onDismiss = { showSearch = false },
        )
    }
}

private const val COMPACT_LANDSCAPE_MAX_HEIGHT_DP = 480

enum class LibraryTab { Books, Saved, Momentum }
