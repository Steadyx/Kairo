package com.example.kairo.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kairo.R
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import com.example.kairo.ui.theme.KairoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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
                    initialTab = LibraryTab.Library,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onImportFile = {},
                    onSettings = {},
                    onDelete = { deleteCalls++ },
                )
            }
        }

        val deleteDesc = composeRule.activity.getString(R.string.content_desc_delete_book)
        composeRule.onNodeWithContentDescription(deleteDesc).performClick()

        val dialogTitle = composeRule.activity.getString(R.string.library_delete_title)
        composeRule.onNodeWithText(dialogTitle).assertIsDisplayed()

        val cancelText = composeRule.activity.getString(R.string.action_cancel)
        composeRule.onNodeWithText(cancelText).performClick()
        composeRule.onNodeWithText(dialogTitle).assertDoesNotExist()

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
                    initialTab = LibraryTab.Library,
                    onOpen = {},
                    onOpenBookmark = { _, _, _ -> },
                    onDeleteBookmark = {},
                    onImportFile = {},
                    onSettings = {},
                    onDelete = { deleteCalls++ },
                )
            }
        }

        val deleteDesc = composeRule.activity.getString(R.string.content_desc_delete_book)
        composeRule.onNodeWithContentDescription(deleteDesc).performClick()

        val deleteText = composeRule.activity.getString(R.string.action_delete)
        composeRule.onNodeWithText(deleteText).performClick()

        composeRule.runOnIdle { assertEquals(1, deleteCalls) }
    }
}
