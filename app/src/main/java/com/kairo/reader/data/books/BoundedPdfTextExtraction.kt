package com.kairo.reader.data.books

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.StringWriter
import java.io.Writer

internal fun extractBoundedPdfText(
    document: PDDocument,
    sortByPosition: Boolean,
    pageSeparator: String,
    maxChars: Long,
    maxGlyphsPerPage: Int = MAX_GLYPHS_PER_PAGE,
    checkActive: () -> Unit,
): String {
    // PDFBox buffers glyphs for each page before writing any text. Bound that work
    // as well as the output, including on a single unusually large page.
    val stripper = object : PDFTextStripper() {
        private var glyphChars = 0L
        private var pageGlyphs = 0

        override fun processPage(page: PDPage) {
            checkActive()
            pageGlyphs = 0
            super.processPage(page)
        }

        override fun processTextPosition(text: TextPosition) {
            checkActive()
            pageGlyphs += 1
            require(pageGlyphs <= maxGlyphsPerPage) { "PDF page contains too many text glyphs" }
            glyphChars += text.unicode.length
            require(glyphChars <= maxChars) { "PDF contains too much extracted text" }
            super.processTextPosition(text)
        }
    }.apply {
        this.sortByPosition = sortByPosition
        lineSeparator = "\n"
        pageStart = ""
        pageEnd = pageSeparator
    }
    val output = StringWriter()
    BoundedPdfTextWriter(output, maxChars, checkActive).use { writer ->
        checkActive()
        stripper.writeText(document, writer)
    }
    return output.toString()
}

private const val MAX_GLYPHS_PER_PAGE = 100_000

internal class BoundedPdfTextWriter(private val output: Writer, private val maxChars: Long, private val checkActive: () -> Unit,) :
    Writer() {
    private var writtenChars = 0L

    override fun write(buffer: CharArray, offset: Int, length: Int) {
        checkActive()
        require(length.toLong() <= maxChars - writtenChars) { "PDF contains too much extracted text" }
        output.write(buffer, offset, length)
        writtenChars += length
    }

    override fun flush() = output.flush()

    override fun close() = output.close()
}
