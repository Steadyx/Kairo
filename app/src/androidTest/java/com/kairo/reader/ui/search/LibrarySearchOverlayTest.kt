package com.kairo.reader.ui.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.LibrarySearchResult
import com.kairo.reader.core.model.LibrarySearchResultKind
import com.kairo.reader.data.search.LibrarySearchState
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySearchOverlayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun selectingResultDoesNotClearSuccessfulResultSet() {
        val result = passageResult()
        val submittedQueries = mutableListOf<String>()
        var openedResult: LibrarySearchResult? = null
        composeRule.setContent {
            KairoTheme {
                LibrarySearchOverlay(
                    title = "Search this book",
                    hint = "Find text",
                    state = LibrarySearchState.Success("needle", listOf(result)),
                    initialQuery = "needle",
                    onQuery = submittedQueries::add,
                    onRetry = {},
                    onOpenResult = { openedResult = it },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Late chapter").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(result, openedResult)
            assertFalse(submittedQueries.contains(""))
        }
    }
}

private fun passageResult(): LibrarySearchResult =
    LibrarySearchResult(
        id = "passage",
        kind = LibrarySearchResultKind.PASSAGE,
        bookId = BookId("book"),
        bookTitle = "Book",
        chapterIndex = 2,
        chapterTitle = "Late chapter",
        tokenIndex = 0,
        matchStartCodePointOffset = 300_123,
        matchLengthCodePoints = "needle phrase".length,
        title = "Late chapter",
        snippet = "…needle phrase…",
    )
