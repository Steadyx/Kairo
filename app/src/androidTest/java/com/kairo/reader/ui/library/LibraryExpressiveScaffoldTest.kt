package com.kairo.reader.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.ui.theme.KairoTheme
import com.kairo.reader.ui.tutorial.StartingTutorialTargetIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryExpressiveScaffoldTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun compactLandscapeKeepsImportActionsAccessibleAndBooksScrollable() {
        val tutorialTargets = mutableStateMapOf<String, Rect>()
        var primaryImportClicks = 0
        var fileCardClicks = 0
        var linkClicks = 0
        var textClicks = 0
        val books = List(24) { index -> sampleBook(index) }

        composeRule.setContent {
            KairoTheme {
                Box(modifier = Modifier.width(640.dp).height(320.dp)) {
                    LibraryExpressiveScaffold(
                        selectedTab = LibraryTab.Books,
                        navigationSuiteType = NavigationSuiteType.WideNavigationRailCollapsed,
                        compactLandscape = true,
                        importEnabled = true,
                        tutorialTargets = tutorialTargets,
                        onTabSelected = {},
                        onImport = { primaryImportClicks += 1 },
                        onSearch = {},
                        onSettings = {},
                    ) {
                        LibraryBooksContent(
                            books = books,
                            filter = LibraryBookFilter.ALL,
                            bookProgress = emptyMap(),
                            compactLandscape = true,
                            horizontalImportActionVisible = false,
                            isImporting = false,
                            actions =
                            libraryActions(
                                onFileImport = { fileCardClicks += 1 },
                                onLink = { linkClicks += 1 },
                                onText = { textClicks += 1 },
                            ),
                            tutorialTargets = tutorialTargets,
                            onFilterChange = {},
                        )
                    }
                }
            }
        }

        val primaryImportDescription =
            composeRule.activity.getString(R.string.library_import_button)
        val linkDescription =
            composeRule.activity.getString(R.string.library_read_from_link_button)
        val textDescription =
            composeRule.activity.getString(R.string.library_text_import_button)

        composeRule
            .onAllNodesWithContentDescription(primaryImportDescription)
            .assertCountEquals(1)
        val primaryImport =
            composeRule
                .onNodeWithContentDescription(primaryImportDescription)
                .assertHasClickAction()
                .assertIsDisplayed()
        primaryImport.performClick()
        composeRule
            .onNodeWithContentDescription(linkDescription)
            .assertHasClickAction()
            .assertIsDisplayed()
            .performClick()
        composeRule
            .onNodeWithContentDescription(textDescription)
            .assertHasClickAction()
            .assertIsDisplayed()
            .performClick()

        composeRule
            .onAllNodesWithText(composeRule.activity.getString(R.string.library_source_book))
            .assertCountEquals(0)

        val booksList = composeRule.onNodeWithTag(LIBRARY_BOOKS_LIST_TEST_TAG)
        booksList.performScrollToKey("book-0")
        val firstBook = composeRule.onNodeWithText("Book 0").assertIsDisplayed()
        val firstBookInitialTop = firstBook.getUnclippedBoundsInRoot().top
        booksList.performTouchInput {
            swipeUp(
                startY = bottom * 0.8f,
                endY = bottom * 0.55f,
                durationMillis = 300,
            )
        }
        val firstBookMovedTop =
            composeRule.onNodeWithText("Book 0").getUnclippedBoundsInRoot().top
        assertTrue(firstBookMovedTop < firstBookInitialTop)

        booksList.performScrollToKey("book-23")
        val lastBookAction =
            composeRule
                .onNodeWithTag(libraryBookActionsTestTag("book-23"))
                .assertIsDisplayed()
        val lastBookActionBounds = lastBookAction.getUnclippedBoundsInRoot()
        val booksListBounds = booksList.getUnclippedBoundsInRoot()
        assertTrue(lastBookActionBounds.bottom <= booksListBounds.bottom)

        composeRule.runOnIdle {
            assertEquals(0, primaryImportClicks)
            assertEquals(1, fileCardClicks)
            assertEquals(1, linkClicks)
            assertEquals(1, textClicks)
            assertTrue(tutorialTargets.containsKey(StartingTutorialTargetIds.LIBRARY_IMPORT))
        }
    }

    @Test
    fun portraitHorizontalPrimaryActionClearsLastBookAction() {
        val tutorialTargets = mutableStateMapOf<String, Rect>()
        val books = List(24) { index -> sampleBook(index) }
        composeRule.setContent {
            KairoTheme {
                Box(modifier = Modifier.width(400.dp).height(800.dp)) {
                    LibraryExpressiveScaffold(
                        selectedTab = LibraryTab.Books,
                        navigationSuiteType = NavigationSuiteType.ShortNavigationBarCompact,
                        compactLandscape = false,
                        importEnabled = true,
                        tutorialTargets = tutorialTargets,
                        onTabSelected = {},
                        onImport = {},
                        onSearch = {},
                        onSettings = {},
                    ) {
                        LibraryBooksContent(
                            books = books,
                            filter = LibraryBookFilter.ALL,
                            bookProgress = emptyMap(),
                            compactLandscape = false,
                            horizontalImportActionVisible = true,
                            isImporting = false,
                            actions = libraryActions({}, {}, {}),
                            tutorialTargets = tutorialTargets,
                            onFilterChange = {},
                        )
                    }
                }
            }
        }

        val booksList = composeRule.onNodeWithTag(LIBRARY_BOOKS_LIST_TEST_TAG)
        booksList.performScrollToIndex(20)
        repeat(2) {
            booksList.performTouchInput {
                swipeUp(startY = bottom * 0.85f, endY = top, durationMillis = 500)
            }
        }

        val lastBookActionBounds =
            composeRule
                .onNodeWithTag(libraryBookActionsTestTag("book-23"))
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
        val primaryImportBounds =
            composeRule
                .onNodeWithContentDescription(
                    composeRule.activity.getString(R.string.library_import_button)
                ).assertIsDisplayed()
                .getUnclippedBoundsInRoot()

        assertTrue(lastBookActionBounds.bottom <= primaryImportBounds.top)
    }

    @Test
    fun collapsedWideRailUsesIconOnlyPrimaryAction() {
        val tutorialTargets = mutableStateMapOf<String, Rect>()
        composeRule.setContent {
            KairoTheme {
                Box(modifier = Modifier.width(800.dp).height(600.dp)) {
                    LibraryExpressiveScaffold(
                        selectedTab = LibraryTab.Books,
                        navigationSuiteType = NavigationSuiteType.WideNavigationRailCollapsed,
                        compactLandscape = false,
                        importEnabled = true,
                        tutorialTargets = tutorialTargets,
                        onTabSelected = {},
                        onImport = {},
                        onSearch = {},
                        onSettings = {},
                    ) {}
                }
            }
        }

        val importDescription = composeRule.activity.getString(R.string.library_import_button)
        val bounds =
            composeRule
                .onNodeWithContentDescription(importDescription)
                .assertHasClickAction()
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()

        assertTrue(bounds.right - bounds.left <= 72.dp)
    }

    private fun sampleBook(index: Int) =
        Book(
            id = BookId("book-$index"),
            title = "Book $index",
            authors = listOf("Author"),
            chapters = listOf(Chapter(index = 0, title = "Chapter", htmlContent = "", plainText = "")),
            coverImage = null,
        )

    private fun libraryActions(
        onFileImport: () -> Unit,
        onLink: () -> Unit,
        onText: () -> Unit,
    ) =
        LibraryTabContentActions(
            onOpen = {},
            onSetCompleted = { _, _ -> },
            onRequestDelete = {},
            onOpenBookmark = { _, _, _ -> },
            onDeleteBookmark = {},
            onDeleteAnnotation = {},
            onEditAnnotation = {},
            onRequestNoteExport = {},
            onWeeklyGoalChange = {},
            onResetMomentum = {},
            onRequestClearBookmarks = {},
            onLaunchBookImport = onFileImport,
            onShowReadLinkDialog = onLink,
            onShowAddTextDialog = onText,
        )
}
