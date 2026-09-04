package com.kairo.reader.data.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.reader.core.export.NoteExportDocument
import com.kairo.reader.core.export.NoteExportEntry
import com.kairo.reader.core.export.NoteExportLocalization
import com.kairo.reader.core.export.NoteExportScope
import com.kairo.reader.core.export.NoteExportSource
import com.kairo.reader.core.model.HighlightColor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteExportPdfRendererTest {
    @Test
    fun cancellationAfterPageStartPreservesCancellationFailure() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.cacheDir, "cancelled-note-export.pdf")
        var checks = 0
        val document =
            NoteExportDocument(
                scope = NoteExportScope.All,
                generatedAt = 1L,
                sources =
                listOf(
                    NoteExportSource(
                        title = "Source",
                        authors = emptyList(),
                        entries = listOf(entry("Note", "Passage")),
                    ),
                ),
            )

        org.junit.Assert.assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            NoteExportPdfRenderer(context, PdfTestLocalization).render(
                document = document,
                target = output,
                cancellationCheck = {
                    checks++
                    if (checks == 2) throw kotlinx.coroutines.CancellationException("cancelled")
                },
            )
        }
    }

    @Test
    fun paginatedPdfIsReadableAndContainsEachLongNoteMarkerOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), SAMPLE_PDF_FILE)
        val repeatedText = "Evidence, interpretation, and a reproducible observation. ".repeat(70)
        val document =
            NoteExportDocument(
                scope = NoteExportScope.Book("research-source"),
                generatedAt = 1_788_451_200_000L,
                sources =
                listOf(
                    NoteExportSource(
                        title = "A deliberately long source title about multilingual research methods and reliable evidence ".repeat(3),
                        authors = listOf("Dr Ada Researcher", "ليلى الباحثة"),
                        entries =
                        listOf(
                            entry(
                                note =
                                buildString {
                                    appendLine("UNIQUE_NOTE_START")
                                    appendLine(repeatedText)
                                    appendLine("بحث متعدد اللغات — English evidence and العربية context.")
                                    appendLine("多言語の研究ノートと中文证据。")
                                    append("UNIQUE_NOTE_END")
                                },
                                passage = "UNIQUE_PASSAGE_START ${"Quoted evidence with context. ".repeat(95)} UNIQUE_PASSAGE_END",
                            ),
                            entry(
                                note = "SECOND_NOTE_MARKER ${"A concise follow-up finding. ".repeat(35)}",
                                passage = "SECOND_PASSAGE_MARKER A shorter supporting quotation.",
                            ),
                        ),
                    ),
                ),
            )

        NoteExportPdfRenderer(context, PdfTestLocalization).render(document, output)

        assertTrue(output.length() > 1_000L)
        val signature = ByteArray(4)
        output.inputStream().buffered().use { input -> java.io.DataInputStream(input).readFully(signature) }
        assertEquals("%PDF", String(signature, Charsets.US_ASCII))
        assertFrameworkPagesRender(output)

        PDFBoxResourceLoader.init(context)
        PDDocument.load(output).use { pdf ->
            assertTrue(pdf.numberOfPages >= 2)
            val text = PDFTextStripper().getText(pdf)
            listOf(
                "UNIQUE_NOTE_START",
                "UNIQUE_NOTE_END",
                "UNIQUE_PASSAGE_START",
                "UNIQUE_PASSAGE_END",
                "SECOND_NOTE_MARKER",
                "SECOND_PASSAGE_MARKER",
            ).forEach { marker -> assertEquals(marker, 1, text.windowed(marker.length).count { it == marker }) }
        }
    }

    private fun assertFrameworkPagesRender(output: File) {
        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertTrue(renderer.pageCount >= 2)
                for (pageIndex in 0 until renderer.pageCount) {
                    renderer.openPage(pageIndex).use { page ->
                        assertEquals(595, page.width)
                        assertEquals(842, page.height)
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        if (pageIndex == 0 || pageIndex == renderer.pageCount - 1) {
                            val image = File(output.parentFile, "note-export-page-${pageIndex + 1}.png")
                            FileOutputStream(image).use { stream ->
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                            }
                        }
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    private fun entry(
        note: String,
        passage: String,
    ) =
        NoteExportEntry(
            chapterTitle = "Findings — النتائج",
            chapterNumber = 3,
            note = note,
            passage = passage,
            highlightColor = HighlightColor.BLUE,
            createdAt = 1_788_364_800_000L,
            updatedAt = 1_788_451_200_000L,
        )

    private companion object {
        const val SAMPLE_PDF_FILE = "note-export-sample.pdf"
    }
}

private object PdfTestLocalization : NoteExportLocalization {
    override val authorLabel = "Authors"
    override val unknownAuthor = "Unknown author"
    override val noteLabel = "Note"
    override val passageLabel = "Quoted passage"
    override val createdLabel = "Created"
    override val updatedLabel = "Updated"

    override fun documentTitle(scope: NoteExportScope) = "Research notes"

    override fun formatDate(timestamp: Long) = if (timestamp > 1_788_400_000_000L) "4 Sep 2026" else "3 Sep 2026"

    override fun exportedOn(formattedDate: String) = "Exported $formattedDate"

    override fun contentsSummary(noteCount: Int, sourceCount: Int) = "$noteCount notes from $sourceCount source"

    override fun chapterFallback(chapterNumber: Int) = "Chapter $chapterNumber"

    override fun continued(label: String) = "$label (continued)"

    override fun pageNumber(pageNumber: Int) = "Page $pageNumber"
}
