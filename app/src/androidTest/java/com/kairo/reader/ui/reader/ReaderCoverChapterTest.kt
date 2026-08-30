package com.kairo.reader.ui.reader

import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.TimedReadingMode
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderCoverChapterTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun coverOnlyChapterRendersCover() {
        val coverImage =
            Base64.decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg==",
                Base64.DEFAULT,
            )
        val book =
            Book(
                id = BookId("book-1"),
                title = "Cover Test",
                authors = listOf("Author"),
                chapters = listOf(Chapter(index = 0, title = null, htmlContent = "", plainText = "")),
                coverImage = coverImage,
            )
        val uiState =
            ReaderUiState(
                isLoading = false,
                chapterIndex = 0,
                focusIndex = 0,
                chapterData = null,
            )

        composeRule.setContent {
            KairoTheme {
                ReaderScreen(
                    book = book,
                    uiState = uiState,
                    fontSizeSp = 18f,
                    invertedScroll = false,
                    readerTheme = ReaderTheme.LIGHT,
                    textBrightness = 1.0f,
                    estimatedWpm = 0,
                    onFontSizeChange = {},
                    onThemeChange = {},
                    onTextBrightnessChange = {},
                    onInvertedScrollChange = {},
                    focusModeEnabled = false,
                    onFocusModeEnabledChange = {},
                    onAddBookmark = { _, _, _ -> },
                    onOpenBookmarks = {},
                    onOpenLibrary = {},
                    onFocusChange = {},
                    onPageChange = { _, _ -> },
                    timedReadingMode = TimedReadingMode.RSVP,
                    onStartTimedReading = { _, _ -> },
                    onSelectTimedReadingMode = { _, _ -> },
                    onChapterChange = { _, _ -> },
                    onTableOfContentsTargetSelected = {},
                    onViewportMetricsChanged = { _, _ -> },
                )
            }
        }

        composeRule.waitForIdle()
        val desc = composeRule.activity.getString(
            R.string.content_desc_cover_of_title,
            book.title,
        )
        composeRule.onNodeWithContentDescription(desc).assertIsDisplayed()
    }
}
