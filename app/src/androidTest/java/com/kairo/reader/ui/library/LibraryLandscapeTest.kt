package com.kairo.reader.ui.library

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Bookmark
import com.kairo.reader.core.model.BookmarkItem
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.HighlightColor
import com.kairo.reader.core.model.SavedAnnotation
import com.kairo.reader.core.model.SavedAnnotationItem
import com.kairo.reader.core.model.SavedAnnotationKind
import com.kairo.reader.ui.theme.KairoTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryLandscapeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    private val books = List(12) { index ->
        Book(
            id = BookId("landscape-$index"),
            title = "Landscape book $index",
            authors = listOf("A. Reader"),
            chapters = listOf(Chapter(0, "Chapter one", "", "")),
            coverImage = null,
        )
    }
    private var openedBook: Book? = null
    private var openedToken: Int? = null

    @Before
    fun useLandscape() {
        composeRule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
    }

    @After
    fun restoreOrientation() {
        composeRule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    @Test
    fun booksUseTwoColumnsAndLastBookRemainsReachable() {
        showLibrary(LibraryTab.Books)
        val first = composeRule.onNodeWithText("Landscape book 0").assertIsDisplayed().getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithText("Landscape book 1").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(first.top, second.top)
        assertTrue(second.left > first.right)
        capture("library-landscape")
        composeRule.onNodeWithTag(LIBRARY_BOOKS_LIST_TEST_TAG).performScrollToKey("landscape-11")
        composeRule.onNodeWithText("Landscape book 11").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(books.last(), openedBook) }
    }

    @Test
    fun savedFiltersAndAllThreeKindsRemainUsableInLandscape() {
        showLibrary(LibraryTab.Saved)
        capture("saved-landscape-all")
        for ((filter, token, preview) in listOf(
            Triple(R.string.saved_filter_notes, 2, "A note worth returning to"),
            Triple(R.string.saved_filter_highlights, 4, "A passage worth remembering"),
            Triple(R.string.saved_filter_bookmarks, 8, "Continue reading here"),
        )) {
            composeRule.onNodeWithTag(LIBRARY_SAVED_LIST_TEST_TAG).performScrollToKey("saved-filters")
            composeRule.onNodeWithText(composeRule.activity.getString(filter)).assertIsDisplayed().performClick()
            composeRule.onNodeWithText(preview).assertIsDisplayed().performClick()
            composeRule.runOnIdle { assertEquals(token, openedToken) }
            capture("saved-landscape-$token")
        }
    }

    @Test
    fun largeTextSavedItemsScrollPastControls() {
        showLibrary(LibraryTab.Saved, fontScale = 2f)
        composeRule.onNodeWithTag(LIBRARY_SAVED_LIST_TEST_TAG).performScrollToKey("bookmark:bookmark-one")
        composeRule.onNodeWithText("Continue reading here").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(8, openedToken) }
        capture("saved-landscape-large-text")
    }

    private fun showLibrary(tab: LibraryTab, fontScale: Float = 1f) {
        val annotations = listOf(
            annotation("note-one", SavedAnnotationKind.NOTE, 2, "A note worth returning to"),
            annotation("highlight-one", SavedAnnotationKind.HIGHLIGHT, 4, "A passage worth remembering"),
        )
        val bookmark = BookmarkItem(Bookmark("bookmark-one", books.first().id, 0, 8, "Continue reading here", 100L), books.first(), 1)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                KairoTheme {
                    LibraryScreen(
                        books = books, bookmarks = listOf(bookmark), annotations = annotations,
                        bookProgress = emptyMap(), initialTab = tab,
                        onOpen = { openedBook = it }, onOpenBookmark = { _, _, token -> openedToken = token },
                        onDeleteBookmark = {}, onDeleteBookmarksForBook = {}, onImportFile = {}, onImportUrl = {},
                        onSettings = {}, onSetCompleted = { _, _ -> }, onDelete = {},
                    )
                }
            }
        }
    }

    private fun annotation(id: String, kind: SavedAnnotationKind, token: Int, text: String) = SavedAnnotationItem(
        SavedAnnotation(
            id = id, bookId = books.first().id, chapterIndex = 0, startTokenIndex = token, endTokenIndex = token + 1,
            selectedText = if (kind == SavedAnnotationKind.HIGHLIGHT) text else "A passage from the book",
            note = if (kind == SavedAnnotationKind.NOTE) text else "", color = HighlightColor.YELLOW,
            kind = kind, createdAt = 100L, updatedAt = 100L,
        ),
        books.first(),
        1,
    )

    private fun capture(name: String) {
        composeRule.mainClock.advanceTimeBy(500)
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val directory = requireNotNull(composeRule.activity.getExternalFilesDir("ui-review"))
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
