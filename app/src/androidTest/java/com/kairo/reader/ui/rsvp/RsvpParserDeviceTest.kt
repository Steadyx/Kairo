package com.kairo.reader.ui.rsvp

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.TestActivity
import com.kairo.reader.core.dispatchers.DefaultDispatcherProvider
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.TimedReadingMode
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.ComprehensionRsvpEngine
import com.kairo.reader.core.tokenization.Tokenizer
import com.kairo.reader.data.books.TextFileBookParser
import com.kairo.reader.ui.reader.ParagraphText
import com.kairo.reader.ui.reader.ParagraphTextActions
import com.kairo.reader.ui.reader.ParagraphTextState
import com.kairo.reader.ui.reader.toParagraphs
import com.kairo.reader.ui.theme.KairoTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RsvpParserDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun htmlFileImportProducesTwoReaderParagraphsAndAnRsvpBreak(): Unit = runBlocking {
        val context = composeRule.activity
        val source = File.createTempFile("rsvp-smoke-", ".html", context.cacheDir)
        val book = try {
            source.writeText("<html><body><p>First paragraph.</p><p>Second paragraph.</p></body></html>")
            TextFileBookParser(DefaultDispatcherProvider()).parse(context, Uri.fromFile(source), BookId("isolated-html-smoke"))
        } finally {
            source.delete()
        }
        val chapter = book.chapters.single()
        assertEquals("First paragraph.\n\nSecond paragraph.", chapter.plainText)
        val tokens = Tokenizer().tokenize(chapter)
        val paragraphs = tokens.toParagraphs()
        assertEquals(2, paragraphs.size)
        val frames = ComprehensionRsvpEngine().generateFrames(tokens, 0, RsvpConfig())
        val paragraphBreakIndex = tokens.indexOfFirst { it.type == TokenType.PARAGRAPH_BREAK }
        assertTrue(paragraphBreakIndex >= 0)
        assertTrue(
            frames.any { frame ->
                frame.originalTokenIndex == paragraphBreakIndex && frame.tokens.none { it.type == TokenType.WORD }
            }
        )
        assertEquals(listOf("First", "paragraph", "Second", "paragraph"), tokens.filter { it.type == TokenType.WORD }.map { it.text })
        composeRule.setContent {
            KairoTheme {
                Surface {
                    Column {
                        paragraphs.forEach { paragraph ->
                            ParagraphText(
                                ParagraphTextState(
                                    paragraph = paragraph,
                                    focusIndex = 0,
                                    fontSizeSp = 18f,
                                    textBrightness = 1f,
                                    timedReadingMode = TimedReadingMode.RSVP,
                                ),
                                ParagraphTextActions(onFocusChange = {}, onStartTimedReading = {}),
                            )
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithText("First paragraph.").assertIsDisplayed()
        composeRule.onNodeWithText("Second paragraph.").assertIsDisplayed()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val directory = requireNotNull(context.getExternalFilesDir("rsvp-device-review"))
        File(directory, "html-reader-paragraphs.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun resumingInsideParenthesesAndQuotesPreservesContextOnAndroid() {
        val engine = ComprehensionRsvpEngine()
        val config = RsvpConfig(
            enablePhraseChunking = false,
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
            useParentheticalAside = true,
            parentheticalAsideMultiplier = 0.75,
        )
        val aside = listOf(word("Earlier"), punctuation("("), word("aside"), punctuation(","), word("continued"), punctuation(")"))
        val resumed = engine.generateFrames(aside, 4, config).first()
        val withoutContext = engine.generateFrames(aside.drop(4), 0, config).first()
        assertTrue(resumed.durationMs < withoutContext.durationMs)
        val quote = listOf(punctuation("\""), word("hello"), word("there"), punctuation("\""), word("outside"))
        assertEquals(listOf("there", "\""), engine.generateFrames(quote, 2, config).first().tokens.map { it.text })
    }

    private fun word(text: String): Token = Token(text, TokenType.WORD)

    private fun punctuation(text: String): Token = Token(text, TokenType.PUNCTUATION)
}
