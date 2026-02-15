package com.example.kairo.data.books.mobi

import android.content.Context
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class MobiParserEngine(
    private val headerParser: MobiHeaderParser = MobiHeaderParser(),
    private val contentProcessor: MobiContentProcessor = MobiContentProcessor(),
    private val imageProcessor: MobiImageProcessor = MobiImageProcessor(),
) {
    fun parse(
        context: Context,
        bookId: BookId,
        data: ByteArray,
        fallbackFileName: String,
    ): Book {
        require(data.size >= MobiLimits.MIN_FILE_SIZE_BYTES) { "File too small to be a valid MOBI" }

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val pdbName = ByteArray(32)
        buffer.get(pdbName)
        val parsedBookName =
            String(pdbName).trim('\u0000').takeIf { it.isNotBlank() }
                ?: fallbackFileName.substringBeforeLast('.')

        buffer.position(76)
        val recordCount = buffer.short.toInt() and 0xFFFF
        require(recordCount >= MobiLimits.MIN_RECORD_COUNT) { "Invalid MOBI: too few records" }

        val recordOffsets = MobiBinary.parseRecordOffsets(data, recordCount)
        require(recordOffsets.size >= MobiLimits.MIN_RECORD_COUNT) { "Invalid MOBI: malformed record table" }

        val record0Start = recordOffsets[0]
        val record0End = if (recordOffsets.size > 1) recordOffsets[1] else data.size
        require(record0Start in 0 until record0End && record0End <= data.size) {
            "Invalid MOBI: corrupt first record range"
        }
        val record0 = data.copyOfRange(record0Start, record0End)

        val palmDoc = headerParser.parsePalmDocHeader(record0)
        val headers = headerParser.parseHeaders(record0, parsedBookName, fallbackFileName)
        val header = headers.primary
        val imageHeader =
            headers.kf8?.takeIf { it.firstImageIndex > 0 || it.coverRecordIndex != null } ?: header

        val kf8CoverRecordIndex =
            headerParser.findKf8CoverRecordIndex(
                data = data,
                recordOffsets = recordOffsets,
                textRecordCount = palmDoc.textRecordCount,
                charset = header.textCharset,
                firstResourceIndexHint = imageHeader.firstImageIndex,
            )
        val resolvedCoverRecordIndex = kf8CoverRecordIndex ?: imageHeader.coverRecordIndex
        val textHtml =
            contentProcessor.extractHtml(
                data = data,
                recordOffsets = recordOffsets,
                compression = palmDoc.compression,
                textRecordCount = palmDoc.textRecordCount,
                header = header,
                firstImageIndexHint = imageHeader.firstImageIndex,
            )

        val coverRecindexCandidates = contentProcessor.extractCoverImageRecindices(textHtml)
        val referencedImages =
            contentProcessor.extractReferencedImageIndices(textHtml, coverRecindexCandidates)

        val imageExtraction =
            imageProcessor.extractImages(
                context = context,
                bookId = bookId,
                data = data,
                recordOffsets = recordOffsets,
                firstImageIndex = imageHeader.firstImageIndex,
                coverRecordIndex = resolvedCoverRecordIndex,
                textRecordCount = palmDoc.textRecordCount,
                coverRecindexCandidates = coverRecindexCandidates,
                referencedImageIndices = referencedImages,
            )

        val recindexBase = imageExtraction.recindexBase ?: imageExtraction.resolvedFirstImageIndex ?: -1
        val chapters = contentProcessor.splitHtmlIntoChapters(textHtml, header.title)
        val rewrittenChapters =
            if (chapters.isEmpty()) {
                val rewrittenHtml =
                    imageProcessor.rewriteImageSrcs(
                        html = textHtml,
                        imagePathByRecordIndex = imageExtraction.imagePathByRecordIndex,
                        recindexBase = recindexBase,
                    )
                val plain = contentProcessor.extractPlainText(rewrittenHtml)
                listOf(
                    Chapter(
                        index = 0,
                        title = "Content",
                        htmlContent = rewrittenHtml.ifBlank { "<p>No readable content found.</p>" },
                        plainText = plain.ifBlank { "No readable content found." },
                        imagePaths = contentProcessor.extractImagePathsFromHtml(rewrittenHtml),
                    ),
                )
            } else {
                chapters.map { chapter ->
                    val rewrittenHtml =
                        imageProcessor.rewriteImageSrcs(
                            html = chapter.htmlContent,
                            imagePathByRecordIndex = imageExtraction.imagePathByRecordIndex,
                            recindexBase = recindexBase,
                        )
                    chapter.copy(
                        htmlContent = rewrittenHtml,
                        imagePaths = contentProcessor.extractImagePathsFromHtml(rewrittenHtml),
                    )
                }
            }

        return Book(
            id = bookId,
            title = header.title,
            authors = header.authors,
            languageTag = null,
            coverImage = imageExtraction.coverImage,
            chapters = rewrittenChapters,
        )
    }
}
