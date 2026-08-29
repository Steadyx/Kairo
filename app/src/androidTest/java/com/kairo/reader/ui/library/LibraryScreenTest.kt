package com.kairo.reader.ui.library

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Bookmark
import com.kairo.reader.core.model.BookmarkItem
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.EditSavedAnnotationRequest
import com.kairo.reader.core.model.HighlightColor
import com.kairo.reader.core.model.SavedAnnotation
import com.kairo.reader.core.model.SavedAnnotationItem
import com.kairo.reader.core.model.SavedAnnotationKind
import com.kairo.reader.data.books.TextImportRequest
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    private val sampleBook =
        Book(
            id = BookId("book-1"),
            title = "Test Book",
            authors = listOf("Author One"),
            chapters = listOf(Chapter(index = 0, title = "Chapter 1", htmlContent = "", plainText = "")),
            coverImage = null,
        )

    @Test
    fun deleteDialog_showsAndCancels() {
        var deleteCalls = 0

        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook),
                    bookmarks = emptyList(),
                    bookProgress = emptyMap(),
                    initialTab = LibraryTab.Books,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onDeleteBookmarksForBook = {},
                    onImportFile = {},
                    onImportUrl = {},
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = { deleteCalls++ },
                )
            }
        }

        val actionsDesc = composeRule.activity.getString(R.string.content_desc_book_actions)
        composeRule.onNodeWithContentDescription(actionsDesc).performClick()

        val deleteDesc = composeRule.activity.getString(R.string.content_desc_delete_book)
        composeRule.onNodeWithContentDescription(deleteDesc).performClick()

        val dialogTitle = composeRule.activity.getString(R.string.library_delete_title)
        composeRule.onNodeWithText(dialogTitle).assertIsDisplayed()

        val cancelText = composeRule.activity.getString(R.string.action_cancel)
        composeRule.onNodeWithText(cancelText).performClick()
        composeRule.onAllNodesWithText(dialogTitle).assertCountEquals(0)

        composeRule.runOnIdle { assertEquals(0, deleteCalls) }
    }

    @Test
    fun deleteDialog_confirmsAndDeletes() {
        var deleteCalls = 0

        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook),
                    bookmarks = emptyList(),
                    bookProgress = emptyMap(),
                    initialTab = LibraryTab.Books,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onDeleteBookmarksForBook = {},
                    onImportFile = {},
                    onImportUrl = {},
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = { deleteCalls++ },
                )
            }
        }

        val actionsDesc = composeRule.activity.getString(R.string.content_desc_book_actions)
        composeRule.onNodeWithContentDescription(actionsDesc).performClick()

        val deleteDesc = composeRule.activity.getString(R.string.content_desc_delete_book)
        composeRule.onNodeWithContentDescription(deleteDesc).performClick()

        val deleteText = composeRule.activity.getString(R.string.action_delete)
        composeRule.onNodeWithText(deleteText).performClick()

        composeRule.runOnIdle { assertEquals(1, deleteCalls) }
    }

    @Test
    fun completedTab_showsCompletedBooks() {
        val completedBook =
            sampleBook.copy(
                id = BookId("book-2"),
                title = "Finished Book",
                isCompleted = true,
            )

        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook, completedBook),
                    bookmarks = emptyList(),
                    bookProgress = emptyMap(),
                    initialTab = LibraryTab.Books,
                    initialBookFilter = LibraryBookFilter.COMPLETED,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onDeleteBookmarksForBook = {},
                    onImportFile = {},
                    onImportUrl = {},
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("Finished Book").assertIsDisplayed()
        composeRule.onAllNodesWithText("Test Book").assertCountEquals(0)
    }

    @Test
    fun markCompletedAction_invokesCallback() {
        var completedBookId = ""
        var completedValue = false

        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook),
                    bookmarks = emptyList(),
                    bookProgress = emptyMap(),
                    initialTab = LibraryTab.Books,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onDeleteBookmarksForBook = {},
                    onImportFile = {},
                    onImportUrl = {},
                    onSettings = {},
                    onSetCompleted = { book, isCompleted ->
                        completedBookId = book.id.value
                        completedValue = isCompleted
                    },
                    onDelete = {},
                )
            }
        }

        val actionsDesc = composeRule.activity.getString(R.string.content_desc_book_actions)
        composeRule.onNodeWithContentDescription(actionsDesc).performClick()

        val completeDesc = composeRule.activity.getString(R.string.content_desc_mark_book_completed)
        composeRule.onNodeWithContentDescription(completeDesc).performClick()

        composeRule.runOnIdle {
            assertEquals("book-1", completedBookId)
            assertEquals(true, completedValue)
        }
    }

    @Test
    fun clearBookBookmarks_confirmsAndDeletesForBook() {
        var clearedBookId = ""
        val bookmarkItem =
            BookmarkItem(
                bookmark =
                Bookmark(
                    id = "bookmark-1",
                    bookId = sampleBook.id,
                    chapterIndex = 0,
                    tokenIndex = 12,
                    previewText = "A saved passage from the chapter",
                    createdAt = 100L,
                ),
                book = sampleBook,
                chapterCount = sampleBook.chapters.size,
            )

        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook),
                    bookmarks = listOf(bookmarkItem),
                    bookProgress = emptyMap(),
                    initialTab = LibraryTab.Saved,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onDeleteBookmarksForBook = { clearedBookId = it },
                    onImportFile = {},
                    onImportUrl = {},
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        val clearBookDesc =
            composeRule.activity.getString(
                R.string.content_desc_delete_book_bookmarks,
                sampleBook.title,
            )
        composeRule.onNodeWithContentDescription(clearBookDesc).performClick()

        val dialogTitle = composeRule.activity.getString(R.string.library_bookmark_clear_book_title)
        composeRule.onNodeWithText(dialogTitle).assertIsDisplayed()

        val deleteText = composeRule.activity.getString(R.string.action_delete)
        composeRule.onNodeWithText(deleteText).performClick()

        composeRule.runOnIdle { assertEquals("book-1", clearedBookId) }
    }

    @Test
    fun readFromLinkDialog_submitsUrl() {
        var importedUrl = ""

        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook),
                    bookmarks = emptyList(),
                    bookProgress = emptyMap(),
                    initialTab = LibraryTab.Books,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onDeleteBookmarksForBook = {},
                    onImportFile = {},
                    onImportUrl = { importedUrl = it },
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.library_source_link)
        ).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("example.com/story")
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.library_read_from_link_submit)
        ).performClick()

        composeRule.runOnIdle { assertEquals("example.com/story", importedUrl) }
    }

    @Test
    fun addTextDialog_submitsPastedReading() {
        var importedText: TextImportRequest? = null

        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook),
                    bookmarks = emptyList(),
                    bookProgress = emptyMap(),
                    initialTab = LibraryTab.Books,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onDeleteBookmarksForBook = {},
                    onImportFile = {},
                    onImportUrl = {},
                    onImportText = { importedText = it },
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.library_source_text)
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.library_text_import_content_placeholder)
        ).performTextInput("# Shared note\n\nThis is enough shared text to read in Kairo.")
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.library_text_import_submit)
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(
                "# Shared note\n\nThis is enough shared text to read in Kairo.",
                importedText?.content,
            )
        }
    }

    @Test
    fun tabsOpenSavedAndMomentumViews() {
        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook),
                    bookmarks = emptyList(),
                    bookProgress = emptyMap(),
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onDeleteBookmarksForBook = {},
                    onImportFile = {},
                    onImportUrl = {},
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.library_tab_saved),
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.saved_title),
        ).assertIsDisplayed()

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.library_tab_momentum),
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_this_week),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_stored_locally),
        ).assertIsDisplayed()
    }

    @Test
    fun savedViewShowsHighlightAndNote() {
        val annotation =
            SavedAnnotationItem(
                annotation =
                SavedAnnotation(
                    id = "note-1",
                    bookId = sampleBook.id,
                    chapterIndex = 0,
                    startTokenIndex = 2,
                    endTokenIndex = 5,
                    selectedText = "A highlighted passage",
                    note = "A useful note",
                    color = HighlightColor.BLUE,
                    kind = SavedAnnotationKind.NOTE,
                    createdAt = 100L,
                    updatedAt = 100L,
                ),
                book = sampleBook,
                chapterCount = 1,
            )
        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook),
                    bookmarks = emptyList(),
                    annotations = listOf(annotation),
                    bookProgress = emptyMap(),
                    initialTab = LibraryTab.Saved,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onDeleteBookmarksForBook = {},
                    onImportFile = {},
                    onImportUrl = {},
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("A highlighted passage").assertIsDisplayed()
        composeRule.onNodeWithText("A useful note").assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.reader_note_hint),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.saved_note_passage),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.reader_chapter_of_total, 1, 1),
        ).assertIsDisplayed()
    }

    @Test
    fun savedNoteCanBeEdited() {
        var editRequest: EditSavedAnnotationRequest? = null
        val annotation = savedAnnotation(kind = SavedAnnotationKind.NOTE, note = "A useful note")
        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook),
                    bookmarks = emptyList(),
                    annotations = listOf(annotation),
                    bookProgress = emptyMap(),
                    initialTab = LibraryTab.Saved,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onEditAnnotation = { editRequest = it },
                    onDeleteBookmarksForBook = {},
                    onImportFile = {},
                    onImportUrl = {},
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.content_desc_edit_saved),
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.saved_edit_note_title),
        ).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction())
            .assertTextEquals("A useful note")
            .performTextReplacement("Updated thought")
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.highlight_pink),
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.action_save),
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(
                EditSavedAnnotationRequest(
                    annotationId = "note-1",
                    note = "Updated thought",
                    color = HighlightColor.PINK,
                ),
                editRequest,
            )
        }
    }

    @Test
    fun savedHighlightCanChangeColour() {
        var editRequest: EditSavedAnnotationRequest? = null
        val annotation = savedAnnotation(kind = SavedAnnotationKind.HIGHLIGHT, note = "")
        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook),
                    bookmarks = emptyList(),
                    annotations = listOf(annotation),
                    bookProgress = emptyMap(),
                    initialTab = LibraryTab.Saved,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onEditAnnotation = { editRequest = it },
                    onDeleteBookmarksForBook = {},
                    onImportFile = {},
                    onImportUrl = {},
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.content_desc_edit_saved),
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.saved_edit_highlight_title),
        ).assertIsDisplayed()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.highlight_blue),
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.action_save),
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(
                EditSavedAnnotationRequest(
                    annotationId = "note-1",
                    note = "",
                    color = HighlightColor.BLUE,
                ),
                editRequest,
            )
        }
    }

    private fun savedAnnotation(
        kind: SavedAnnotationKind,
        note: String,
    ): SavedAnnotationItem =
        SavedAnnotationItem(
            annotation =
            SavedAnnotation(
                id = "note-1",
                bookId = sampleBook.id,
                chapterIndex = 0,
                startTokenIndex = 2,
                endTokenIndex = 5,
                selectedText = "A highlighted passage",
                note = note,
                color = HighlightColor.YELLOW,
                kind = kind,
                createdAt = 100L,
                updatedAt = 100L,
            ),
            book = sampleBook,
            chapterCount = 1,
        )

    @Test
    fun searchOverlayDebouncesQuery() {
        var submittedQuery = ""
        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook),
                    bookmarks = emptyList(),
                    bookProgress = emptyMap(),
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onDeleteBookmarksForBook = {},
                    onSearchQuery = { submittedQuery = it },
                    onImportFile = {},
                    onImportUrl = {},
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.content_desc_search),
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.search_kairo_title),
        ).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput("flow")

        composeRule.waitUntil(timeoutMillis = 2_000L) { submittedQuery == "flow" }
    }

    @Test
    fun savedPassageDeletionRequiresConfirmation() {
        var deletedAnnotationId: String? = null
        val annotation = savedAnnotation(kind = SavedAnnotationKind.NOTE, note = "A useful note")
        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook),
                    bookmarks = emptyList(),
                    annotations = listOf(annotation),
                    bookProgress = emptyMap(),
                    initialTab = LibraryTab.Saved,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onDeleteAnnotation = { deletedAnnotationId = it },
                    onDeleteBookmarksForBook = {},
                    onImportFile = {},
                    onImportUrl = {},
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        val deleteDescription = composeRule.activity.getString(R.string.content_desc_delete_saved)
        val dialogTitle = composeRule.activity.getString(R.string.saved_delete_title)
        composeRule.onNodeWithContentDescription(deleteDescription).performClick()
        composeRule.onNodeWithText(dialogTitle).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.action_cancel)
        ).performClick()
        composeRule.runOnIdle { assertEquals(null, deletedAnnotationId) }

        composeRule.onNodeWithContentDescription(deleteDescription).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.action_delete)
        ).performClick()
        composeRule.runOnIdle { assertEquals("note-1", deletedAnnotationId) }
    }

    @Test
    fun clearBookFilterRevealsUnderlyingBooks() {
        val completedBook = sampleBook.copy(isCompleted = true)
        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(completedBook),
                    bookmarks = emptyList(),
                    bookProgress = emptyMap(),
                    initialTab = LibraryTab.Books,
                    initialBookFilter = LibraryBookFilter.READING,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onDeleteBookmarksForBook = {},
                    onImportFile = {},
                    onImportUrl = {},
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.library_books_filter_empty)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.action_clear_filter)
        ).performClick()
        composeRule.onNodeWithText(sampleBook.title).assertIsDisplayed()
    }

    @Test
    fun clearSavedFilterRevealsUnderlyingSavedItems() {
        val annotation = savedAnnotation(kind = SavedAnnotationKind.NOTE, note = "A useful note")
        composeRule.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook),
                    bookmarks = emptyList(),
                    annotations = listOf(annotation),
                    bookProgress = emptyMap(),
                    initialTab = LibraryTab.Saved,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onDeleteBookmarksForBook = {},
                    onImportFile = {},
                    onImportUrl = {},
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.saved_filter_highlights)
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.saved_filter_empty)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.action_clear_filter)
        ).performClick()
        composeRule.onNodeWithText("A useful note").assertIsDisplayed()
    }

    @Test
    fun savedEditorAndDraftSurviveSavedStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        val annotation = savedAnnotation(kind = SavedAnnotationKind.NOTE, note = "A useful note")
        restorationTester.setContent {
            KairoTheme {
                LibraryScreen(
                    books = listOf(sampleBook),
                    bookmarks = emptyList(),
                    annotations = listOf(annotation),
                    bookProgress = emptyMap(),
                    initialTab = LibraryTab.Saved,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onDeleteBookmarksForBook = {},
                    onImportFile = {},
                    onImportUrl = {},
                    onSettings = {},
                    onSetCompleted = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.content_desc_edit_saved)
        ).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Unsaved draft")
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNode(hasSetTextAction()).assertTextEquals("Unsaved draft")
    }
}
