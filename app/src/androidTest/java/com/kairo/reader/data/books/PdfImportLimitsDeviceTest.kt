package com.kairo.reader.data.books

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.reader.core.model.BookId
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real PDFBox glyph processing without touching the library or preferences. */
@RunWith(AndroidJUnit4::class)
class PdfImportLimitsDeviceTest {
    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun oversizedPageStopsBeforeAllItsGlyphsAreBuffered() {
        val text = "Readable text repeated across a very large page. ".repeat(20)
        document(text).use { document ->
            var activeChecks = 0
            val error = assertThrows(IllegalArgumentException::class.java) {
                extractBoundedPdfText(document, false, "\n", maxChars = 16) { activeChecks += 1 }
            }
            assertTrue(error.message.orEmpty().contains("too much extracted text"))
            assertTrue(activeChecks in 1 until text.length)
        }
    }

    @Test
    fun pageGlyphBudgetIsEnforcedBeforeTheDocumentTextBudget() {
        document("Readable text repeated across a very large page. ".repeat(20)).use { document ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                extractBoundedPdfText(document, false, "\n", maxChars = 4096, maxGlyphsPerPage = 16) {}
            }
            assertTrue(error.message.orEmpty().contains("too many text glyphs"))
        }
    }

    @Test
    fun cancellationInterruptsGlyphProcessing() {
        document("Readable text repeated across a very large page. ".repeat(20)).use { document ->
            var activeChecks = 0
            assertThrows(CancellationException::class.java) {
                extractBoundedPdfText(document, false, "\n", maxChars = 4096) {
                    activeChecks += 1
                    if (activeChecks == 8) throw CancellationException("cancel import")
                }
            }
            assertEquals(8, activeChecks)
        }
    }

    @Test
    fun singleWordSelectablePdfImportsSuccessfully() {
        document("Hello").use { document ->
            val book = PdfParserEngine.parse(document, PdfBookParseRequest(BookId("pdf-fixture"), "short.pdf"))
            assertEquals("Hello", book.chapters.single().plainText)
            assertEquals(1, book.chapters.single().wordCount)
        }
    }

    private fun document(text: String): PDDocument = PDDocument().apply {
        val page = PDPage()
        addPage(page)
        PDPageContentStream(this, page).use { content ->
            content.beginText()
            content.setFont(PDType1Font.HELVETICA, 12f)
            content.newLineAtOffset(20f, 700f)
            content.showText(text)
            content.endText()
        }
    }
}
