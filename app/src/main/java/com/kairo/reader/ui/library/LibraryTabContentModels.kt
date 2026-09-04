package com.kairo.reader.ui.library

import com.kairo.reader.core.export.NoteExportScope
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookmarkItem
import com.kairo.reader.core.model.ReadingMomentum
import com.kairo.reader.core.model.SavedAnnotationItem

data class LibraryTabContentState(
    val selectedTab: LibraryTab,
    val books: List<Book>,
    val bookmarks: List<BookmarkItem>,
    val annotations: List<SavedAnnotationItem>,
    val momentum: ReadingMomentum,
    val weeklyGoalMinutes: Int,
    val bookFilter: LibraryBookFilter,
    val savedFilter: SavedFilter,
    val bookProgress: Map<String, LibraryBookProgress>,
    val compactLandscape: Boolean,
    val horizontalImportActionVisible: Boolean,
    val isImporting: Boolean,
)

data class LibraryTabContentActions(
    val onOpen: (Book) -> Unit,
    val onSetCompleted: (Book, Boolean) -> Unit,
    val onRequestDelete: (Book) -> Unit,
    val onOpenBookmark: (String, Int, Int) -> Unit,
    val onDeleteBookmark: (String) -> Unit,
    val onDeleteAnnotation: (String) -> Unit,
    val onEditAnnotation: (SavedAnnotationItem) -> Unit,
    val onRequestNoteExport: (NoteExportScope) -> Unit,
    val onWeeklyGoalChange: (Int) -> Unit,
    val onResetMomentum: () -> Unit,
    val onRequestClearBookmarks: (Book) -> Unit,
    val onLaunchBookImport: () -> Unit,
    val onShowReadLinkDialog: () -> Unit,
    val onShowAddTextDialog: () -> Unit,
)
