package com.kairo.reader.data.books.mobi

import android.content.Context
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
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
        val palmDatabase = readPalmDatabase(data, fallbackFileName)
        val parsedBookName = palmDatabase.bookName
        val recordOffsets = palmDatabase.recordOffsets
        val record0 = palmDatabase.firstRecord

        val palmDoc = headerParser.parsePalmDocHeader(record0)
        require(palmDoc.encryptionType == 0) {
            "DRM-protected MOBI, PRC, and AZW files are not supported"
        }
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
                MobiImageExtractionRequest(
                    context = context,
                    bookId = bookId,
                    data = data,
                    recordOffsets = recordOffsets,
                    firstImageIndex = imageHeader.firstImageIndex,
                    coverRecordIndex = resolvedCoverRecordIndex,
                    textRecordCount = palmDoc.textRecordCount,
                    coverRecindexCandidates = coverRecindexCandidates,
                    referencedImageIndices = referencedImages,
                ),
            )

        val recindexBase = imageExtraction.recindexBase ?: imageExtraction.resolvedFirstImageIndex ?: -1
        val chapters = rewriteChapters(textHtml, header.title, imageExtraction, recindexBase)

        return Book(
            id = bookId,
            title = header.title,
            authors = header.authors,
            languageTag = null,
            coverImage = imageExtraction.coverImage,
            chapters = chapters,
        )
    }

    private fun readPalmDatabase(
        data: ByteArray,
        fallbackFileName: String,
    ): PalmDatabaseRecords {
        require(data.size >= MobiLimits.MIN_FILE_SIZE_BYTES) { "File too small to be a valid MOBI" }
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val pdbName = ByteArray(PALM_DATABASE_NAME_BYTES)
        buffer.get(pdbName)
        val bookName =
            String(pdbName).trim('\u0000').takeIf { it.isNotBlank() }
                ?: fallbackFileName.substringBeforeLast('.')
        buffer.position(PALM_DATABASE_RECORD_COUNT_OFFSET)
        val recordCount = buffer.short.toInt() and UNSIGNED_SHORT_MASK
        require(recordCount >= MobiLimits.MIN_RECORD_COUNT) { "Invalid MOBI: too few records" }
        val offsets = MobiBinary.parseRecordOffsets(data, recordCount)
        require(offsets.size >= MobiLimits.MIN_RECORD_COUNT) { "Invalid MOBI: malformed record table" }
        val firstRecordStart = offsets.first()
        val firstRecordEnd = offsets.getOrElse(1) { data.size }
        require(firstRecordStart in 0 until firstRecordEnd && firstRecordEnd <= data.size) {
            "Invalid MOBI: corrupt first record range"
        }
        return PalmDatabaseRecords(
            bookName = bookName,
            recordOffsets = offsets,
            firstRecord = data.copyOfRange(firstRecordStart, firstRecordEnd),
        )
    }

    private fun rewriteChapters(
        textHtml: String,
        title: String,
        imageExtraction: MobiImageExtraction,
        recindexBase: Int,
    ): List<Chapter> {
        val chapters = contentProcessor.splitHtmlIntoChapters(textHtml, title)
        if (chapters.isEmpty()) {
            val rewrittenHtml = rewriteImages(textHtml, imageExtraction, recindexBase)
            contentProcessor.requireDisplayableImages(rewrittenHtml)
            val plain = contentProcessor.extractPlainText(rewrittenHtml)
            return listOf(
                Chapter(
                    index = 0,
                    title = "Content",
                    htmlContent = rewrittenHtml.ifBlank { "<p>No readable content found.</p>" },
                    plainText = plain.ifBlank { "No readable content found." },
                    imagePaths = contentProcessor.extractImagePathsFromHtml(rewrittenHtml),
                ),
            )
        }
        return chapters.map { chapter ->
            val rewrittenHtml = rewriteImages(chapter.htmlContent, imageExtraction, recindexBase)
            contentProcessor.requireDisplayableImages(rewrittenHtml)
            chapter.copy(
                htmlContent = rewrittenHtml,
                imagePaths = contentProcessor.extractImagePathsFromHtml(rewrittenHtml),
            )
        }
    }

    private fun rewriteImages(
        html: String,
        imageExtraction: MobiImageExtraction,
        recindexBase: Int,
    ): String =
        imageProcessor.rewriteImageSrcs(
            html = html,
            imagePathByRecordIndex = imageExtraction.imagePathByRecordIndex,
            recindexBase = recindexBase,
        )
}

private data class PalmDatabaseRecords(val bookName: String, val recordOffsets: List<Int>, val firstRecord: ByteArray,)

private const val PALM_DATABASE_NAME_BYTES = 32
private const val PALM_DATABASE_RECORD_COUNT_OFFSET = 76
private const val UNSIGNED_SHORT_MASK = 0xFFFF
