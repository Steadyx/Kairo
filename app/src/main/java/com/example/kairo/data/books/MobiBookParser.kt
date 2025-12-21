@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "MagicNumber"
)

package com.example.kairo.data.books

import android.content.Context
import android.net.Uri
import com.example.kairo.core.dispatchers.DispatcherProvider
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * MOBI/PRC file parser with basic header parsing support.
 *
 * MOBI files are based on the PalmDOC format with MOBI headers.
 * Structure:
 * - PDB Header (78 bytes)
 * - Record list
 * - Record 0: PalmDOC header + MOBI header
 * - Records 1-N: Compressed or uncompressed content
 *
 * Note: This implementation handles basic uncompressed and PalmDOC compressed MOBI files.
 * DRM-protected files are not supported.
 * For full MOBI/AZW3/KF8 support, consider using a dedicated library.
 */
class MobiBookParser(
    private val dispatcherProvider: DispatcherProvider
) : BookParser {

    companion object {
        // Max file size (50 MB) to prevent OOM
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024
        private const val MIN_MOBI_SIZE_BYTES = 78
        private const val MIN_MOBI_RECORDS = 2
    }

    override suspend fun parse(context: Context, uri: Uri): Book =
        withContext(dispatcherProvider.io) {
            val fileName = uri.lastPathSegment ?: "book.mobi"

            // Check file size before reading
            val fileSize = context.contentResolver.openInputStream(uri)?.use { input ->
                input.available().toLong()
            } ?: 0L

            require(fileSize <= MAX_FILE_SIZE) {
                "MOBI file too large (max ${MAX_FILE_SIZE / 1024 / 1024}MB)"
            }

            val data = requireNotNull(context.contentResolver.openInputStream(uri)) {
                "Unable to read MOBI file"
            }.use { input ->
                BufferedInputStream(input).readBytes()
            }

            // Validate minimum size
            require(data.size >= MIN_MOBI_SIZE_BYTES) { "File too small to be a valid MOBI" }

            runCatching { parseMobiFile(data, fileName) }
                .getOrElse { fallbackParse(data, fileName) }
        }

    override fun supports(extension: String): Boolean =
        extension == "mobi" || extension == "prc" || extension == "azw"

    /**
     * Attempts to parse the MOBI file structure.
     */
    private fun parseMobiFile(data: ByteArray, fileName: String): Book {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        // Read PDB header
        val pdbName = ByteArray(32)
        buffer.get(pdbName)
        val bookName = String(pdbName).trim('\u0000').takeIf { it.isNotBlank() }
            ?: fileName.substringBeforeLast('.')

        // Skip to record count at offset 76
        buffer.position(76)
        val numRecords = buffer.short.toInt() and 0xFFFF

        require(numRecords >= MIN_MOBI_RECORDS) { "Invalid MOBI: too few records" }

        // Read record offsets (8 bytes each: 4 offset + 4 attributes)
        val recordOffsets = mutableListOf<Int>()
        repeat(numRecords) {
            recordOffsets.add(buffer.int)
            buffer.int  // Skip attributes
        }

        // Record 0 contains headers
        val record0Start = recordOffsets[0]
        val record0End = if (recordOffsets.size > 1) recordOffsets[1] else data.size
        val record0 = data.copyOfRange(record0Start, record0End)

        // Parse PalmDOC header (first 16 bytes of record 0)
        val palmDocBuffer = ByteBuffer.wrap(record0).order(ByteOrder.BIG_ENDIAN)
        val compression = palmDocBuffer.short.toInt() and 0xFFFF
        palmDocBuffer.short  // unused
        palmDocBuffer.int
        val recordCount = palmDocBuffer.short.toInt() and 0xFFFF
        palmDocBuffer.short

        // Check for MOBI header (at offset 16 of record 0)
        var title = bookName
        val author: String? = null
        val coverData: ByteArray? = null

        if (record0.size > 20) {
            val mobiCheck = String(record0.copyOfRange(16, 20))
            if (mobiCheck == "MOBI") {
                // Parse MOBI header for metadata
                val mobiBuffer = ByteBuffer.wrap(record0, 16, record0.size - 16)
                    .order(ByteOrder.BIG_ENDIAN)

                mobiBuffer.int  // MOBI identifier
                val headerLength = mobiBuffer.int

                if (headerLength >= 84) {
                    mobiBuffer.position(mobiBuffer.position() + 76)  // Skip to title offset
                    val fullNameOffset = mobiBuffer.int
                    val fullNameLength = mobiBuffer.int

                    // Try to read full title
                    if (fullNameOffset > 0 && fullNameLength > 0 &&
                        fullNameOffset + fullNameLength <= record0.size) {
                        title = String(record0.copyOfRange(fullNameOffset, fullNameOffset + fullNameLength))
                            .trim('\u0000')
                    }
                }
            }
        }

        // Extract text content from records
        val textBuilder = StringBuilder()
        val textRecordStart = 1
        val textRecordEnd = minOf(textRecordStart + recordCount, recordOffsets.size)

        for (i in textRecordStart until textRecordEnd) {
            val start = recordOffsets[i]
            val end = if (i + 1 < recordOffsets.size) recordOffsets[i + 1] else data.size
            val recordData = data.copyOfRange(start, end)

            val text = when (compression) {
                1 -> String(recordData, Charsets.UTF_8)  // No compression
                2 -> decompressPalmDoc(recordData)       // PalmDOC compression
                else -> String(recordData, Charsets.UTF_8)  // Try as-is
            }
            textBuilder.append(text)
        }

        val fullText = textBuilder.toString()
            .replace(Regex("<[^>]+>"), "")  // Strip HTML tags
            .replace(Regex("\\s+"), " ")
            .trim()

        // Split into chapters (use paragraph breaks or fixed size)
        val chapters = splitIntoChapters(fullText)

        return Book(
            id = BookId(UUID.randomUUID().toString()),
            title = title,
            authors = listOfNotNull(author),
            coverImage = coverData,
            chapters = chapters
        )
    }

    /**
     * PalmDOC LZ77 decompression.
     */
    private fun decompressPalmDoc(data: ByteArray): String {
        val output = StringBuilder()
        var i = 0

        while (i < data.size) {
            val byte = data[i].toInt() and 0xFF
            i++

            when (byte) {
                0 -> output.append('\u0000')
                in 1..8 -> {
                    // Copy next 1-8 bytes as-is
                    repeat(byte) {
                        if (i < data.size) {
                            output.append(data[i].toInt().toChar())
                            i++
                        }
                    }
                }
                in 9..0x7F -> output.append(byte.toChar())
                in 0x80..0xBF -> {
                    // LZ77 distance-length pair
                    if (i < data.size) {
                        val next = data[i].toInt() and 0xFF
                        i++
                        val distance = ((byte shl 8) or next) shr 3 and 0x7FF
                        val length = (next and 0x07) + 3

                        val pos = output.length - distance
                        if (pos >= 0) {
                            repeat(length) { j ->
                                if (pos + j < output.length) {
                                    output.append(output[pos + j])
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Space + character
                    output.append(' ')
                    output.append((byte xor 0x80).toChar())
                }
            }
        }

        return output.toString()
    }

    /**
     * Splits text into chapters based on content structure.
     */
    private fun splitIntoChapters(text: String): List<Chapter> {
        // Try to find chapter markers
        val chapterPattern = Regex("(?i)(chapter|part|section|book)\\s*\\d+", RegexOption.MULTILINE)
        val matches = chapterPattern.findAll(text).toList()

        val chapters = if (matches.size >= 2) {
            // Split by chapter markers
            val indices = matches.map { it.range.first } + text.length
            indices.zipWithNext().mapIndexed { index, (start, end) ->
                val content = text.substring(start, end).trim()
                val title = matches.getOrNull(index)?.value ?: "Chapter ${index + 1}"
                Chapter(
                    index = index,
                    title = title,
                    htmlContent = "<p>${content.replace("\n", "</p><p>")}</p>",
                    plainText = content
                )
            }.filter { it.plainText.isNotBlank() }
        } else {
            // Split by paragraph count (roughly 2000 words per chapter)
            val paragraphs = text.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }
            val wordsPerChapter = 2000
            val chunks = mutableListOf<List<String>>()
            val currentChunk = mutableListOf<String>()
            var wordCount = 0

            for (para in paragraphs) {
                val paraWords = para.split(Regex("\\s+")).size
                if (wordCount + paraWords > wordsPerChapter && currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toList())
                    currentChunk.clear()
                    wordCount = 0
                }
                currentChunk.add(para)
                wordCount += paraWords
            }
            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toList())
            }

            chunks.mapIndexed { index, chunk ->
                val content = chunk.joinToString("\n\n")
                Chapter(
                    index = index,
                    title = "Chapter ${index + 1}",
                    htmlContent = "<p>${content.replace("\n\n", "</p><p>")}</p>",
                    plainText = content
                )
            }
        }

        return chapters.ifEmpty {
            listOf(Chapter(
                index = 0,
                title = "Content",
                htmlContent = "<p>$text</p>",
                plainText = text
            ))
        }
    }

    /**
     * Fallback parser when MOBI structure parsing fails.
     */
    private fun fallbackParse(data: ByteArray, fileName: String): Book {
        // Try to extract any readable text
        val text = String(data, Charsets.UTF_8)
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")  // Remove control chars
            .replace(Regex("<[^>]+>"), "")  // Strip HTML
            .trim()

        val chapters = splitIntoChapters(text)

        return Book(
            id = BookId(UUID.randomUUID().toString()),
            title = fileName.substringBeforeLast('.', "MOBI Import"),
            authors = emptyList(),
            coverImage = null,
            chapters = chapters
        )
    }
}
