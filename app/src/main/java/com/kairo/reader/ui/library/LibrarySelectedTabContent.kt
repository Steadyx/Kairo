package com.kairo.reader.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect

@Composable
internal fun LibrarySelectedTabContent(
    state: LibraryTabContentState,
    actions: LibraryTabContentActions,
    tutorialTargets: MutableMap<String, Rect>,
    onBookFilterChange: (LibraryBookFilter) -> Unit,
    onSavedFilterChange: (SavedFilter) -> Unit,
) {
    when (state.selectedTab) {
        LibraryTab.Books ->
            LibraryBooksContent(
                books = state.books,
                filter = state.bookFilter,
                bookProgress = state.bookProgress,
                compactLandscape = state.compactLandscape,
                horizontalImportActionVisible = state.horizontalImportActionVisible,
                isImporting = state.isImporting,
                actions = actions,
                tutorialTargets = tutorialTargets,
                onFilterChange = onBookFilterChange,
            )
        LibraryTab.Saved ->
            LibrarySavedContent(
                bookmarks = state.bookmarks,
                annotations = state.annotations,
                filter = state.savedFilter,
                onFilterChange = onSavedFilterChange,
                onOpenSaved = actions.onOpenBookmark,
                onDeleteBookmark = actions.onDeleteBookmark,
                onDeleteAnnotation = actions.onDeleteAnnotation,
                onEditAnnotation = actions.onEditAnnotation,
                onRequestNoteExport = actions.onRequestNoteExport,
                onClearBookmarksForBook = actions.onRequestClearBookmarks,
            )
        LibraryTab.Momentum ->
            LibraryMomentumContent(
                momentum = state.momentum,
                weeklyGoalMinutes = state.weeklyGoalMinutes,
                onWeeklyGoalChange = actions.onWeeklyGoalChange,
                onResetMomentum = actions.onResetMomentum,
            )
    }
}
