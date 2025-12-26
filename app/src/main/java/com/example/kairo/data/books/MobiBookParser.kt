@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "MagicNumber",
)

package com.example.kairo.data.books

import android.content.Context
import android.net.Uri
import com.example.kairo.core.dispatchers.DispatcherProvider
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import java.io.BufferedInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.UUID
import kotlinx.coroutines.withContext

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
class MobiBookParser(private val dispatcherProvider: DispatcherProvider,) : BookParser {
    companion object {
        // Max file size (50 MB) to prevent OOM
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024
        private const val MIN_MOBI_SIZE_BYTES = 78
        private const val MIN_MOBI_RECORDS = 2

        private const val MAX_IMAGE_ENTRY_SIZE = 2 * 1024 * 1024
        private const val MAX_COVER_IMAGE_ENTRY_SIZE = 6 * 1024 * 1024
        private const val MAX_TOTAL_IMAGE_SIZE = 25 * 1024 * 1024
        private const val MAX_FALLBACK_TEXT_CHARS = 250_000
        private const val MAX_CHAPTER_WORDS = 3500
        private const val MAX_CHAPTER_TEXT_CHARS = MAX_CHAPTER_WORDS * 7

        private const val MOBI_HEADER_OFFSET = 16
        private const val EXTH_PRESENT_FLAG = 0x40
        private const val EXTH_FLAGS_OFFSET = 0x80
        private const val FIRST_IMAGE_INDEX_OFFSET = 0x6C
    }

    override suspend fun parse(
        context: Context,
        uri: Uri,
    ): Book =
        withContext(dispatcherProvider.io) {
            val fileName = uri.lastPathSegment ?: "book.mobi"
            val bookId = BookId(UUID.randomUUID().toString())

            // Check file size before reading
            val fileSize =
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.available().toLong()
                } ?: 0L

            require(fileSize <= MAX_FILE_SIZE) {
                "MOBI file too large (max ${MAX_FILE_SIZE / 1024 / 1024}MB)"
            }

            val data =
                requireNotNull(context.contentResolver.openInputStream(uri)) {
                    "Unable to read MOBI file"
                }.use { input ->
                    BufferedInputStream(input).readBytes()
                }

            // Validate minimum size
            require(data.size >= MIN_MOBI_SIZE_BYTES) { "File too small to be a valid MOBI" }

            runCatching { parseMobiFile(context, bookId, data, fileName) }
                .getOrElse { fallbackParse(bookId, data, fileName) }
        }

    override fun supports(extension: String): Boolean =
        extension == "mobi" || extension == "prc" || extension == "azw"

    /**
     * Attempts to parse the MOBI file structure.
     */
    private fun parseMobiFile(
        context: Context,
        bookId: BookId,
        data: ByteArray,
        fileName: String,
    ): Book {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        // Read PDB header
        val pdbName = ByteArray(32)
        buffer.get(pdbName)
        val bookName =
            String(pdbName).trim('\u0000').takeIf { it.isNotBlank() }
                ?: fileName.substringBeforeLast('.')

        // Skip to record count at offset 76
        buffer.position(76)
        val numRecords = buffer.short.toInt() and 0xFFFF

        require(numRecords >= MIN_MOBI_RECORDS) { "Invalid MOBI: too few records" }

        // Read record offsets (8 bytes each: 4 offset + 4 attributes)
        val recordOffsets = mutableListOf<Int>()
        repeat(numRecords) {
            recordOffsets.add(buffer.int)
            buffer.int // Skip attributes
        }

        // Record 0 contains headers
        val record0Start = recordOffsets[0]
        val record0End = if (recordOffsets.size > 1) recordOffsets[1] else data.size
        val record0 = data.copyOfRange(record0Start, record0End)

        // Parse PalmDOC header (first 16 bytes of record 0)
        val palmDocBuffer = ByteBuffer.wrap(record0).order(ByteOrder.BIG_ENDIAN)
        val compression = palmDocBuffer.short.toInt() and 0xFFFF
        palmDocBuffer.short // unused
        palmDocBuffer.int
        val recordCount = palmDocBuffer.short.toInt() and 0xFFFF
        palmDocBuffer.short

        val header = parseMobiHeader(record0, bookName, fileName)

        // Extract text content from records
        val textBuilder = StringBuilder()
        val textRecordStart = 1
        val recordLimit =
            if (header.firstImageIndex > 0 && header.firstImageIndex <= recordOffsets.lastIndex) {
                header.firstImageIndex
            } else {
                recordOffsets.size
            }
        val textRecordEnd = minOf(textRecordStart + recordCount, recordLimit)

        for (i in textRecordStart until textRecordEnd) {
            val start = recordOffsets[i]
            val end = if (i + 1 < recordOffsets.size) recordOffsets[i + 1] else data.size
            if (start >= end || start < 0 || end > data.size) continue
            val recordData = data.copyOfRange(start, end)
            if (detectImageType(recordData) != null) continue

            val decodedBytes =
                when (compression) {
                    1 -> recordData // No compression
                    2 -> decompressPalmDoc(recordData)
                    else -> null
                }
            if (decodedBytes == null) {
                throw IllegalArgumentException("Unsupported MOBI compression: $compression")
            }
            if (looksMostlyBinary(decodedBytes)) continue

            val text = String(decodedBytes, header.textCharset)
            textBuilder.append(text)
        }

        val rawText = breakLongRuns(cleanMobiHtml(textBuilder.toString()))
        val html =
            if (looksLikeHtml(rawText)) {
                rawText
            } else {
                wrapPlainTextAsHtml(rawText)
            }

        val imageExtraction =
            extractImages(
                context = context,
                bookId = bookId,
                data = data,
                recordOffsets = recordOffsets,
                firstImageIndex = header.firstImageIndex,
                coverRecordIndex = header.coverRecordIndex,
            )

        val rewrittenHtml =
            rewriteMobiImageSrcs(
                html = html,
                imagePathByRecordIndex = imageExtraction.imagePathByRecordIndex,
                firstImageIndex = header.firstImageIndex,
            )
        val chapters = splitHtmlIntoChapters(rewrittenHtml, header.title)

        val finalChapters =
            chapters.ifEmpty {
                val plain = extractPlainText(rewrittenHtml)
                listOf(
                    Chapter(
                        index = 0,
                        title = "Content",
                        htmlContent = rewrittenHtml.ifBlank { "<p>No readable content found.</p>" },
                        plainText = plain.ifBlank { "No readable content found." },
                        imagePaths = extractImagePathsFromHtml(rewrittenHtml),
                    ),
                )
            }

        return Book(
            id = bookId,
            title = header.title,
            authors = header.authors,
            coverImage = imageExtraction.coverImage,
            chapters = finalChapters,
        )
    }

    private data class MobiHeader(
        val title: String,
        val authors: List<String>,
        val textCharset: Charset,
        val firstImageIndex: Int,
        val coverRecordIndex: Int?,
    )

    private data class ImageExtractionResult(
        val imagePathByRecordIndex: Map<Int, String>,
        val coverImage: ByteArray?,
    )

    private fun parseMobiHeader(
        record0: ByteArray,
        fallbackTitle: String,
        fileName: String,
    ): MobiHeader {
        var title = fallbackTitle
        var authors = emptyList<String>()
        var textCharset = Charsets.UTF_8
        var firstImageIndex = -1
        var coverRecordIndex: Int? = null

        if (record0.size > MOBI_HEADER_OFFSET + 4) {
            val mobiCheck =
                String(record0.copyOfRange(MOBI_HEADER_OFFSET, MOBI_HEADER_OFFSET + 4))
            if (mobiCheck == "MOBI") {
                val headerLength = readInt(record0, MOBI_HEADER_OFFSET + 4)
                val textEncoding = readInt(record0, MOBI_HEADER_OFFSET + 12)
                textCharset = resolveCharset(textEncoding)

                val fullNameOffset = readInt(record0, MOBI_HEADER_OFFSET + 28)
                val fullNameLength = readInt(record0, MOBI_HEADER_OFFSET + 32)
                if (fullNameOffset > 0 &&
                    fullNameLength > 0 &&
                    fullNameOffset + fullNameLength <= record0.size
                ) {
                    title =
                        runCatching {
                            String(
                                record0,
                                fullNameOffset,
                                fullNameLength,
                                textCharset,
                            ).trim('\u0000')
                        }.getOrDefault(title)
                }

                if (headerLength >= FIRST_IMAGE_INDEX_OFFSET + 4) {
                    firstImageIndex = readInt(record0, MOBI_HEADER_OFFSET + FIRST_IMAGE_INDEX_OFFSET)
                }

                val exthFlags =
                    if (headerLength >= EXTH_FLAGS_OFFSET + 4) {
                        readInt(record0, MOBI_HEADER_OFFSET + EXTH_FLAGS_OFFSET)
                    } else {
                        0
                    }
                val exthStart = MOBI_HEADER_OFFSET + headerLength
                if ((exthFlags and EXTH_PRESENT_FLAG) != 0 &&
                    exthStart + 12 <= record0.size
                ) {
                    val exth =
                        parseExthHeader(
                            record0 = record0,
                            start = exthStart,
                            charset = textCharset,
                        )
                    if (exth.title != null) {
                        title = exth.title
                    }
                    authors = exth.authors
                    coverRecordIndex = exth.coverRecordIndex
                }
            }
        }

        if (title.isBlank()) {
            title = fileName.substringBeforeLast('.', "Unknown Book")
        }

        return MobiHeader(
            title = title,
            authors = authors,
            textCharset = textCharset,
            firstImageIndex = firstImageIndex,
            coverRecordIndex = coverRecordIndex,
        )
    }

    private data class ExthMetadata(
        val title: String?,
        val authors: List<String>,
        val coverRecordIndex: Int?,
    )

    private fun parseExthHeader(
        record0: ByteArray,
        start: Int,
        charset: Charset,
    ): ExthMetadata {
        val exthMagic = String(record0.copyOfRange(start, start + 4))
        if (exthMagic != "EXTH") return ExthMetadata(null, emptyList(), null)

        val exthLength = readInt(record0, start + 4)
        val recordCount = readInt(record0, start + 8)
        var offset = start + 12
        val end = (start + exthLength).coerceAtMost(record0.size)

        val authors = mutableListOf<String>()
        var title: String? = null
        var coverRecordIndex: Int? = null

        repeat(recordCount) {
            if (offset + 8 > end) return@repeat
            val type = readInt(record0, offset)
            val length = readInt(record0, offset + 4)
            val dataStart = offset + 8
            val dataEnd = (offset + length).coerceAtMost(end)
            if (length < 8 || dataStart >= dataEnd) {
                offset += maxOf(length, 8)
                return@repeat
            }
            val payload = record0.copyOfRange(dataStart, dataEnd)
            when (type) {
                100 -> {
                    val raw = String(payload, charset).trim('\u0000')
                    raw.split(';', ',')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .forEach { authors.add(it) }
                }
                201 -> {
                    if (payload.size >= 4) {
                        coverRecordIndex = readInt(payload, 0)
                    }
                }
                503 -> {
                    val raw = String(payload, charset).trim('\u0000')
                    if (raw.isNotBlank()) {
                        title = raw
                    }
                }
            }
            offset += length
        }

        return ExthMetadata(title, authors, coverRecordIndex)
    }

    private fun resolveCharset(encoding: Int): Charset =
        when (encoding) {
            65001 -> Charsets.UTF_8
            1252 -> runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.UTF_8)
            else -> Charsets.UTF_8
        }

    private fun readInt(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size || offset < 0) return 0
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }

    private fun cleanMobiHtml(html: String): String =
        html.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")

    private fun looksLikeHtml(text: String): Boolean =
        text.contains("<p", ignoreCase = true) ||
            text.contains("<div", ignoreCase = true) ||
            text.contains("<html", ignoreCase = true) ||
            text.contains("<body", ignoreCase = true)

    private fun wrapPlainTextAsHtml(text: String): String {
        if (text.isBlank()) return ""
        val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotBlank() }
        return paragraphs.joinToString(separator = "</p><p>", prefix = "<p>", postfix = "</p>") {
            it.replace("\n", " ")
        }
    }

    private fun looksMostlyBinary(data: ByteArray): Boolean {
        if (data.isEmpty()) return true
        var printable = 0
        data.forEach { byte ->
            val value = byte.toInt() and 0xFF
            if (value == 0x09 || value == 0x0A || value == 0x0D ||
                value in 0x20..0x7E || value >= 0xC0
            ) {
                printable++
            }
        }
        val ratio = printable.toDouble() / data.size.toDouble()
        return ratio < 0.6
    }

    private fun breakLongRuns(
        text: String,
        maxRunLength: Int = 80,
    ): String {
        if (text.length <= maxRunLength) return text
        val builder = StringBuilder(text.length + (text.length / maxRunLength))
        var run = 0
        text.forEach { ch ->
            if (ch.isWhitespace()) {
                run = 0
                builder.append(ch)
                return@forEach
            }
            run += 1
            if (run > maxRunLength) {
                builder.append(' ')
                run = 1
            }
            builder.append(ch)
        }
        return builder.toString()
    }

    private fun extractImages(
        context: Context,
        bookId: BookId,
        data: ByteArray,
        recordOffsets: List<Int>,
        firstImageIndex: Int,
        coverRecordIndex: Int?,
    ): ImageExtractionResult {
        val imagePathByRecordIndex = mutableMapOf<Int, String>()
        var coverImage: ByteArray? = null

        val imageDir = File(context.filesDir, "kairo_mobi_assets/${bookId.value}/images")
        val canWriteImages = runCatching { imageDir.mkdirs() || imageDir.exists() }.getOrDefault(false)

        val textRecords = recordOffsets.size
        val fallbackImageStart =
            (firstImageIndex.takeIf { it > 0 } ?: -1).takeIf { it < textRecords }
                ?: -1
        val startIndex = if (fallbackImageStart >= 0) fallbackImageStart else -1
        if (startIndex < 0) {
            return ImageExtractionResult(imagePathByRecordIndex, coverImage)
        }

        var totalImageBytes = 0L

        for (index in startIndex until recordOffsets.size) {
            val start = recordOffsets[index]
            val end = if (index + 1 < recordOffsets.size) recordOffsets[index + 1] else data.size
            if (start >= end || start < 0 || end > data.size) continue

            val raw = data.copyOfRange(start, end)
            val imageType = detectImageType(raw) ?: continue
            val maxSize =
                if (coverRecordIndex != null &&
                    (index == coverRecordIndex || index == startIndex + coverRecordIndex)
                ) {
                    MAX_COVER_IMAGE_ENTRY_SIZE
                } else {
                    MAX_IMAGE_ENTRY_SIZE
                }
            if (raw.size > maxSize) continue

            totalImageBytes += raw.size
            if (totalImageBytes > MAX_TOTAL_IMAGE_SIZE) break

            if (coverImage == null &&
                coverRecordIndex != null &&
                (index == coverRecordIndex || index == startIndex + coverRecordIndex)
            ) {
                coverImage = raw
            }

            if (canWriteImages) {
                val fileName = "img_${index}.${imageType.extension}"
                val file = File(imageDir, fileName)
                val wrote =
                    runCatching {
                        file.outputStream().use { it.write(raw) }
                        true
                    }.getOrDefault(false)
                if (wrote) {
                    imagePathByRecordIndex[index] =
                        "kairo_mobi_assets/${bookId.value}/images/$fileName"
                }
            }
        }

        if (coverImage == null) {
            coverImage = imagePathByRecordIndex.keys.firstOrNull()?.let { index ->
                val start = recordOffsets.getOrNull(index) ?: return@let null
                val end =
                    if (index + 1 < recordOffsets.size) recordOffsets[index + 1] else data.size
                if (start < 0 || end > data.size || start >= end) return@let null
                data.copyOfRange(start, end)
            }
        }

        return ImageExtractionResult(imagePathByRecordIndex, coverImage)
    }

    private data class ImageType(val extension: String)

    private fun detectImageType(bytes: ByteArray): ImageType? {
        if (bytes.size < 12) return null
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> ImageType("jpg")
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> ImageType("png")
            bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() -> ImageType("gif")
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() -> ImageType("webp")
            bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte() -> ImageType("bmp")
            else -> null
        }
    }

    private fun rewriteMobiImageSrcs(
        html: String,
        imagePathByRecordIndex: Map<Int, String>,
        firstImageIndex: Int,
    ): String {
        var updated = html
        val recindexRegex =
            Regex("(<img[^>]+?)\\s+recindex\\s*=\\s*['\\\"](\\d+)['\\\"]([^>]*>)",
                RegexOption.IGNORE_CASE)
        updated = recindexRegex.replace(updated) { match ->
            val recIndex = match.groupValues[2].toIntOrNull() ?: return@replace match.value
            val resolved = resolveImagePath(recIndex, imagePathByRecordIndex, firstImageIndex)
                ?: return@replace match.value
            "${match.groupValues[1]} src=\"$resolved\"${match.groupValues[3]}"
        }

        val embedRegex =
            Regex("(src\\s*=\\s*['\\\"])kindle:embed:(\\d+)(['\\\"])",
                RegexOption.IGNORE_CASE)
        updated = embedRegex.replace(updated) { match ->
            val embedIndex = match.groupValues[2].toIntOrNull() ?: return@replace match.value
            val resolved = resolveImagePath(embedIndex, imagePathByRecordIndex, firstImageIndex)
                ?: return@replace match.value
            "${match.groupValues[1]}$resolved${match.groupValues[3]}"
        }

        return updated
    }

    private fun resolveImagePath(
        index: Int,
        imagePathByRecordIndex: Map<Int, String>,
        firstImageIndex: Int,
    ): String? {
        imagePathByRecordIndex[index]?.let { return it }
        if (firstImageIndex > 0) {
            imagePathByRecordIndex[firstImageIndex + index]?.let { return it }
        }
        return null
    }

    private fun splitHtmlIntoChapters(
        html: String,
        fallbackTitle: String,
    ): List<Chapter> {
        val headingRegex =
            Regex("<h[1-3][^>]*>.*?</h[1-3]>", RegexOption.IGNORE_CASE)
        val matches = headingRegex.findAll(html).toList()

        val chapters =
            if (matches.size < 2) {
                val plainText = extractPlainText(html)
                if (plainText.isBlank()) return emptyList()
                listOf(
                    Chapter(
                        index = 0,
                        title = fallbackTitle,
                        htmlContent = html,
                        plainText = plainText,
                        imagePaths = extractImagePathsFromHtml(html),
                    ),
                )
            } else {
                val collected = mutableListOf<Chapter>()
                val indices = matches.map { it.range.first } + html.length
                indices.zipWithNext().forEachIndexed { index, (start, end) ->
                    val segment = html.substring(start, end).trim()
                    val title =
                        extractPlainText(matches.getOrNull(index)?.value.orEmpty())
                            .lineSequence()
                            .firstOrNull()
                            ?.take(100)
                            ?.takeIf { it.isNotBlank() }
                            ?: "Chapter ${index + 1}"
                    val plain = extractPlainText(segment)
                    if (plain.isBlank()) return@forEachIndexed
                    collected.add(
                        Chapter(
                            index = index,
                            title = title,
                            htmlContent = segment,
                            plainText = plain,
                            imagePaths = extractImagePathsFromHtml(segment),
                        ),
                    )
                }
                collected
            }

        return splitLargeChapters(chapters, fallbackTitle)
    }

    private fun splitLargeChapters(
        chapters: List<Chapter>,
        fallbackTitle: String,
    ): List<Chapter> {
        if (chapters.isEmpty()) return emptyList()
        val expanded = mutableListOf<Chapter>()

        chapters.forEach { chapter ->
            if (chapter.plainText.length <= MAX_CHAPTER_TEXT_CHARS) {
                expanded.add(chapter.copy(index = expanded.size))
                return@forEach
            }

            val parts = splitIntoChapters(chapter.plainText)
            if (parts.size <= 1) {
                expanded.add(chapter.copy(index = expanded.size))
                return@forEach
            }

            val baseTitle =
                chapter.title?.takeIf { it.isNotBlank() }
                    ?: fallbackTitle.takeIf { it.isNotBlank() }
            val imageTags =
                if (chapter.imagePaths.isNotEmpty()) {
                    chapter.imagePaths.joinToString(separator = "") { "<img src=\"$it\" />" }
                } else {
                    ""
                }

            parts.forEachIndexed { partIndex, part ->
                val title =
                    baseTitle?.let { base ->
                        if (parts.size > 1) {
                            "$base (${partIndex + 1})"
                        } else {
                            base
                        }
                    } ?: part.title
                val htmlContent =
                    if (partIndex == 0 && imageTags.isNotEmpty()) {
                        imageTags + part.htmlContent
                    } else {
                        part.htmlContent
                    }
                val imagePaths = if (partIndex == 0) chapter.imagePaths else emptyList()
                expanded.add(
                    part.copy(
                        index = expanded.size,
                        title = title,
                        htmlContent = htmlContent,
                        imagePaths = imagePaths,
                    ),
                )
            }
        }

        return expanded
    }

    private fun extractImagePathsFromHtml(html: String): List<String> {
        val regex =
            Regex("<img[^>]+?src\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"][^>]*>",
                RegexOption.IGNORE_CASE)
        val unique = LinkedHashSet<String>(8)
        regex.findAll(html).forEach { match ->
            if (unique.size >= 6) return@forEach
            val src = match.groupValues[1].trim()
            if (src.isBlank()) return@forEach
            if (src.startsWith("data:", ignoreCase = true)) return@forEach
            if (src.startsWith("http://", ignoreCase = true) ||
                src.startsWith("https://", ignoreCase = true)
            ) {
                return@forEach
            }
            unique.add(src)
        }
        return unique.toList()
    }

    private fun extractPlainText(html: String): String =
        normalizePageBreakElements(html)
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<(p|div|br|h[1-6]|li|tr)[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|h[1-6]|li|tr)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1]
                    .toIntOrNull()
                    ?.toChar()
                    ?.toString()
                    .orEmpty()
            }.replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1]
                    .toIntOrNull(16)
                    ?.toChar()
                    ?.toString()
                    .orEmpty()
            }
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n\\s*\\n+"), "\n\n")
            .trim()

    private fun normalizePageBreakElements(html: String): String {
        val pageBreakToken = "\n\n\u000C\n\n"
        var text = html

        val pageBreakTag =
            Regex("<\\s*mbp:pagebreak\\s*/?>", RegexOption.IGNORE_CASE)
        val classPageBreak =
            Regex(
                "<[^>]+\\bclass\\s*=\\s*['\\\"][^'\\\"]*(?:pagebreak|page-break)[^'\\\"]*['\\\"][^>]*>",
                RegexOption.IGNORE_CASE,
            )
        text = text.replace(pageBreakTag, pageBreakToken)
        text = text.replace(classPageBreak, pageBreakToken)
        return text
    }

    /**
     * PalmDOC LZ77 decompression.
     */
    private fun decompressPalmDoc(data: ByteArray): ByteArray {
        val output = ArrayList<Byte>(data.size * 2)
        var i = 0

        while (i < data.size) {
            val byte = data[i].toInt() and 0xFF
            i++

            when (byte) {
                0 -> output.add(0)
                in 1..8 -> {
                    // Copy next 1-8 bytes as-is
                    repeat(byte) {
                        if (i < data.size) {
                            output.add(data[i])
                            i++
                        }
                    }
                }
                in 9..0x7F -> output.add(byte.toByte())
                in 0x80..0xBF -> {
                    // LZ77 distance-length pair
                    if (i < data.size) {
                        val next = data[i].toInt() and 0xFF
                        i++
                        val distance = ((byte shl 8) or next) shr 3 and 0x7FF
                        val length = (next and 0x07) + 3

                        val pos = output.size - distance
                        if (pos >= 0) {
                            repeat(length) { j ->
                                val idx = pos + j
                                if (idx in output.indices) {
                                    output.add(output[idx])
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Space + character
                    output.add(' '.code.toByte())
                    output.add((byte xor 0x80).toByte())
                }
            }
        }

        return output.toByteArray()
    }

    /**
     * Splits text into chapters based on content structure.
     */
    private fun splitIntoChapters(text: String): List<Chapter> {
        // Try to find chapter markers
        val chapterPattern = Regex("(?i)(chapter|part|section|book)\\s*\\d+", RegexOption.MULTILINE)
        val matches = chapterPattern.findAll(text).toList()

        val chapters =
            if (matches.size >= 2) {
                // Split by chapter markers
                val indices = matches.map { it.range.first } + text.length
                indices
                    .zipWithNext()
                    .mapIndexed { index, (start, end) ->
                        val content = text.substring(start, end).trim()
                        val title = matches.getOrNull(index)?.value ?: "Chapter ${index + 1}"
                        Chapter(
                            index = index,
                            title = title,
                            htmlContent = "<p>${content.replace("\n", "</p><p>")}</p>",
                            plainText = content,
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
                        plainText = content,
                    )
                }
            }

        return chapters.ifEmpty {
            listOf(
                Chapter(
                    index = 0,
                    title = "Content",
                    htmlContent = "<p>$text</p>",
                    plainText = text,
                ),
            )
        }
    }

    /**
     * Fallback parser when MOBI structure parsing fails.
     */
    private fun fallbackParse(
        bookId: BookId,
        data: ByteArray,
        fileName: String,
    ): Book {
        // Try to extract any readable text without pulling in binary blobs.
        val extracted = extractFallbackText(data)
        val text =
            when {
                extracted.isBlank() -> "No readable content found."
                else -> breakLongRuns(extracted)
            }

        val chapters = splitIntoChapters(text)

        return Book(
            id = bookId,
            title = fileName.substringBeforeLast('.', "MOBI Import"),
            authors = emptyList(),
            coverImage = null,
            chapters = chapters,
        )
    }

    private fun extractFallbackText(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val builder = StringBuilder(minOf(data.size, MAX_FALLBACK_TEXT_CHARS))
        var lastWasSpace = false

        for (byte in data) {
            if (builder.length >= MAX_FALLBACK_TEXT_CHARS) break
            val value = byte.toInt() and 0xFF
            val ch =
                when {
                    value == 0x09 || value == 0x0A || value == 0x0D -> ' '
                    value in 0x20..0x7E -> value.toChar()
                    value in 0xA0..0xFF -> value.toChar()
                    else -> null
                }
            if (ch == null) {
                if (!lastWasSpace) {
                    builder.append(' ')
                    lastWasSpace = true
                }
            } else {
                builder.append(ch)
                lastWasSpace = ch.isWhitespace()
            }
        }

        return builder.toString()
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
