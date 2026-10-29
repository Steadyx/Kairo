package com.kairo.reader.ui.reader

import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.TimedReadingMode
import com.kairo.reader.core.tokenization.Tokenizer
import com.kairo.reader.data.books.mobi.MobiContentProcessor
import com.kairo.reader.data.books.mobi.MobiImageProcessor
import com.kairo.reader.ui.theme.KairoTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeMobiReaderDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun largeMobiChapterRetainsImagesAndLinksAndPaginatesInReader() {
        val context = composeRule.activity
        val image = File.createTempFile("mobi-audit-", ".png", context.filesDir)
        try {
            image.writeBytes(
                Base64.decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg==",
                    Base64.DEFAULT,
                ),
            )
            val imagePath = image.name
            val prose = (1..500).joinToString("") { index ->
                "<p>Paragraph $index preserves these ordinary reading words in a long chapter.</p>"
            }
            val html = """
                <h1>Beginning</h1><p>Opening words <a href="#finish">Navigate</a>.</p>
                <img recindex="1">$prose<p>Closing sentinel remains here.</p>
                <h1>Ending</h1><p id="finish">The destination.</p>
            """.trimIndent()
            val processor = MobiContentProcessor()
            val imported = processor.splitHtmlIntoChapters(html, "Book")
            assertEquals(2, imported.size)
            assertTrue(imported[0].plainText.length > 24_500)
            assertTrue(imported[0].plainText.contains("Closing sentinel remains here."))
            val rewrittenHtml = MobiImageProcessor().rewriteImageSrcs(imported[0].htmlContent, mapOf(1 to imagePath), 0)
            processor.requireDisplayableImages(rewrittenHtml)
            val chapter = imported[0].copy(
                htmlContent = rewrittenHtml,
                imagePaths = processor.extractImagePathsFromHtml(rewrittenHtml),
            )
            val tokens = Tokenizer().tokenize(chapter)
            val data = requireNotNull(ReaderChapterProcessor().process(chapter, tokens, wordsPerPage = 100))
            assertTrue(data.pages.size > 1)
            assertTrue(tokens.any { it.text == "Navigate" && it.linkChapterIndex == 1 })
            assertEquals(listOf(imagePath), data.blocks.filterIsInstance<ReaderImageBlock>().map { it.imagePath })
            val book =
                Book(
                    id = BookId("mobi-device-regression"),
                    title = "Long MOBI",
                    authors = emptyList(),
                    chapters = listOf(chapter, imported[1])
                )
            var pageIndex by mutableIntStateOf(0)
            composeRule.setContent {
                KairoTheme {
                    ReaderScreen(
                        book = book,
                        uiState = ReaderUiState(chapterIndex = 0, pageIndexOverride = pageIndex, chapterData = data),
                        fontSizeSp = 18f,
                        invertedScroll = false,
                        readerTheme = ReaderTheme.LIGHT,
                        textBrightness = 1f,
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
                        onPageChange = { index, _ -> pageIndex = index },
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
            composeRule.onNodeWithContentDescription(context.getString(R.string.content_desc_illustration)).assertExists()
            composeRule.onNodeWithContentDescription(context.getString(R.string.content_desc_next_page)).performClick()
            composeRule.runOnIdle { assertEquals(1, pageIndex) }
        } finally {
            image.delete()
        }
    }
}
