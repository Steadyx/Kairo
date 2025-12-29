@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "MagicNumber",
)

package com.example.kairo.data.books

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Color
import com.example.kairo.core.dispatchers.DispatcherProvider
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import java.io.BufferedInputStream
import java.io.File
import java.net.URLDecoder
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
class MobiBookParser(private val dispatcherProvider: DispatcherProvider) : BookParser {
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
        private const val TOC_ANCHOR_THRESHOLD = 12
        private const val MAX_TOC_TEXT_CHARS = 200_000
        private const val MIN_FILEPOS_GAP_CHARS = 1200
        private const val MAX_FILEPOS_CHAPTERS = 400
        private const val CHAPTER_START_SCAN_BACK = 20000
        private const val CHAPTER_START_SCAN_FORWARD = 2000
        private const val COVER_FALLBACK_IMAGE_SCAN = 12
        private const val COVER_HTML_SCAN_CHARS = 120_000
        private const val MIN_COLOR_COVER_AREA = 120_000
        private const val MIN_COLOR_SCORE = 0.08f
        private const val MAX_NOISE_TITLE_LENGTH = 32

        private const val MOBI_HEADER_OFFSET = 16
        private const val EXTH_PRESENT_FLAG = 0x40
        private const val EXTH_FLAGS_OFFSET = 0x80
        private const val FIRST_IMAGE_INDEX_OFFSET = 0x6C

        private val FILE_LABEL_WITH_NUMBER_REGEX =
            Regex("(?i)^(part|chapter|section|book)(0*)(\\d{1,6})$")
        private val GENERIC_FILE_LABEL_REGEX =
            Regex("(?i)^[a-z]{2,}\\d{3,}$")
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

        val headers = parseMobiHeaders(record0, bookName, fileName)
        val header = headers.primary
        val imageHeader =
            headers.kf8?.takeIf { it.firstImageIndex > 0 || it.coverRecordIndex != null } ?: header
        val kf8CoverRecordIndex =
            findKf8CoverRecordIndex(
                data = data,
                recordOffsets = recordOffsets,
                textRecordCount = recordCount,
                charset = header.textCharset,
            )
        val resolvedCoverRecordIndex = kf8CoverRecordIndex ?: imageHeader.coverRecordIndex

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
            if (end !in 0..data.size || start !in 0 until end) continue
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

        val cleanedText = cleanMobiHtml(textBuilder.toString())
        val hasHtmlTags = looksLikeHtml(cleanedText)
        val hasEscapedTags =
            !hasHtmlTags &&
                cleanedText.contains("&lt;", ignoreCase = true) &&
                cleanedText.contains("&gt;", ignoreCase = true)
        val htmlCandidate =
            if (hasEscapedTags) {
                decodeHtmlEntities(cleanedText)
            } else {
                cleanedText
            }
        val html =
            if (looksLikeHtml(htmlCandidate)) {
                htmlCandidate
            } else {
                wrapPlainTextAsHtml(breakLongRuns(htmlCandidate))
            }

        val coverRecindexCandidates = extractCoverImageRecindices(html)
        val referencedImages = extractReferencedImageIndices(html, coverRecindexCandidates)
        val imageExtraction =
            extractImages(
                context = context,
                bookId = bookId,
                data = data,
                recordOffsets = recordOffsets,
                firstImageIndex = imageHeader.firstImageIndex,
                coverRecordIndex = resolvedCoverRecordIndex,
                textRecordCount = recordCount,
                coverRecindexCandidates = coverRecindexCandidates,
                referencedImageIndices = referencedImages,
            )

        val resolvedFirstImageIndex = imageExtraction.resolvedFirstImageIndex ?: header.firstImageIndex
        val recindexBase = imageExtraction.recindexBase ?: resolvedFirstImageIndex
        val chapters = splitHtmlIntoChapters(html, header.title)
        val chaptersWithImages =
            chapters.map { chapter ->
                val rewritten =
                    rewriteMobiImageSrcs(
                        html = chapter.htmlContent,
                        imagePathByRecordIndex = imageExtraction.imagePathByRecordIndex,
                        recindexBase = recindexBase,
                    )
                val imagePaths = extractImagePathsFromHtml(rewritten)
                if (rewritten == chapter.htmlContent && imagePaths == chapter.imagePaths) {
                    chapter
                } else {
                    chapter.copy(htmlContent = rewritten, imagePaths = imagePaths)
                }
            }

        val finalChapters =
            if (chaptersWithImages.isNotEmpty()) {
                chaptersWithImages
            } else {
                val rewrittenHtml =
                    rewriteMobiImageSrcs(
                        html = html,
                        imagePathByRecordIndex = imageExtraction.imagePathByRecordIndex,
                        recindexBase = recindexBase,
                    )
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
            languageTag = null,
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

    private data class MobiHeaders(
        val primary: MobiHeader,
        val kf8: MobiHeader?,
    )

    private class ImageExtractionResult(
        val imagePathByRecordIndex: Map<Int, String>,
        val coverImage: ByteArray?,
        val resolvedFirstImageIndex: Int?,
        val recindexBase: Int?,
    )

    private fun parseMobiHeaders(
        record0: ByteArray,
        fallbackTitle: String,
        fileName: String,
    ): MobiHeaders {
        val primary =
            parseMobiHeaderAtOffset(
                record0 = record0,
                fallbackTitle = fallbackTitle,
                fileName = fileName,
                headerOffset = MOBI_HEADER_OFFSET,
            ) ?: buildFallbackHeader(fallbackTitle, fileName)
        val kf8 = parseSecondaryMobiHeader(record0, fallbackTitle, fileName)
        return MobiHeaders(primary, kf8)
    }

    private fun parseSecondaryMobiHeader(
        record0: ByteArray,
        fallbackTitle: String,
        fileName: String,
    ): MobiHeader? {
        var offset = indexOfMobiHeader(record0, MOBI_HEADER_OFFSET + 4)
        var candidate: MobiHeader? = null
        while (offset >= 0) {
            val parsed =
                parseMobiHeaderAtOffset(
                    record0 = record0,
                    fallbackTitle = fallbackTitle,
                    fileName = fileName,
                    headerOffset = offset,
                )
            if (parsed != null) {
                candidate = parsed
            }
            offset = indexOfMobiHeader(record0, offset + 4)
        }
        return candidate
    }

    private fun findKf8CoverRecordIndex(
        data: ByteArray,
        recordOffsets: List<Int>,
        textRecordCount: Int,
        charset: Charset,
    ): Int? {
        val opf = findOpfPackageXml(data, recordOffsets, charset) ?: return null
        val coverHref = parseCoverHrefFromOpf(opf) ?: return null
        val resourceNames = findResourceNameList(data, recordOffsets) ?: return null
        val startIndex =
            findFirstImageRecordIndex(data, recordOffsets)
                ?: (1 + textRecordCount).takeIf { it in recordOffsets.indices }
                ?: return null
        val target = normalizeResourceName(coverHref)
        val targetBase = target.substringAfterLast('/', target)
        val resourceIndex =
            resourceNames.indexOfFirst { name ->
                val normalized = normalizeResourceName(name)
                normalized == target || normalized.substringAfterLast('/', normalized) == targetBase
            }
        if (resourceIndex < 0) return null
        val recordIndex = startIndex + resourceIndex
        return recordIndex.takeIf { it in recordOffsets.indices }
    }

    private fun findOpfPackageXml(
        data: ByteArray,
        recordOffsets: List<Int>,
        charset: Charset,
    ): String? {
        val startTag = "<package"
        val endTag = "</package>"
        for (index in recordOffsets.indices) {
            val start = recordOffsets[index]
            val end =
                if (index + 1 < recordOffsets.size) {
                    recordOffsets[index + 1]
                } else {
                    data.size
                }
            if (start < 0 || end > data.size || end <= start) continue
            val bytes = data.copyOfRange(start, end)
            if (detectImageType(bytes) != null) continue
            val text = runCatching { String(bytes, charset) }.getOrDefault(String(bytes))
            val lower = text.lowercase()
            val startIndex = lower.indexOf(startTag)
            if (startIndex < 0) continue
            val endIndex = lower.indexOf(endTag, startIndex)
            if (endIndex < 0) continue
            val endPos = endIndex + endTag.length
            return text.substring(startIndex, endPos)
        }
        return null
    }

    private fun parseCoverHrefFromOpf(opf: String): String? {
        val metaCoverRegex =
            Regex(
                """<meta[^>]+name\s*=\s*['"]cover['"][^>]+content\s*=\s*['"]([^'"]+)['"][^>]*>""",
                RegexOption.IGNORE_CASE,
            )
        val coverId = metaCoverRegex.find(opf)?.groupValues?.getOrNull(1)
        val itemRegex = Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE)
        var fallback: String? = null
        for (match in itemRegex.findAll(opf)) {
            val tag = match.value
            val id = extractAttribute(tag, "id")
            val href = extractAttribute(tag, "href")
            val props = extractAttribute(tag, "properties").orEmpty()
            if (coverId != null && id == coverId && !href.isNullOrBlank()) {
                return href
            }
            if (props.contains("cover-image", ignoreCase = true) && !href.isNullOrBlank()) {
                return href
            }
            if (fallback == null &&
                !href.isNullOrBlank() &&
                (id?.contains("cover", ignoreCase = true) == true ||
                    href.contains("cover", ignoreCase = true))
            ) {
                fallback = href
            }
        }
        if (fallback != null) return fallback
        val referenceRegex = Regex("<reference\\b[^>]*>", RegexOption.IGNORE_CASE)
        for (match in referenceRegex.findAll(opf)) {
            val tag = match.value
            val type = extractAttribute(tag, "type")
            val href = extractAttribute(tag, "href")
            if (type?.equals("cover", ignoreCase = true) == true && !href.isNullOrBlank()) {
                return href
            }
        }
        return null
    }

    private fun findResourceNameList(
        data: ByteArray,
        recordOffsets: List<Int>,
    ): List<String>? {
        var best: List<String>? = null
        for (index in recordOffsets.indices) {
            val start = recordOffsets[index]
            val end =
                if (index + 1 < recordOffsets.size) {
                    recordOffsets[index + 1]
                } else {
                    data.size
                }
            if (start < 0 || end > data.size || end <= start) continue
            val bytes = data.copyOfRange(start, end)
            if (detectImageType(bytes) != null) continue
            val names = extractResourceNamesFromRecord(bytes)
            if (names.size >= 3 && (best == null || names.size > best!!.size)) {
                best = names
            }
        }
        return best
    }

    private fun extractResourceNamesFromRecord(bytes: ByteArray): List<String> {
        val names = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isEmpty()) return
            val value = current.toString()
            if (looksLikeResourceName(value)) {
                names.add(value)
            }
            current.setLength(0)
        }
        bytes.forEach { b ->
            val ch = b.toInt() and 0xFF
            if (ch in 32..126) {
                current.append(ch.toChar())
            } else {
                flush()
            }
        }
        flush()
        return names
    }

    private fun looksLikeResourceName(value: String): Boolean {
        if (!value.contains('.')) return false
        if (value.length > 180) return false
        val lower = value.lowercase()
        val ext = lower.substringAfterLast('.', "")
        return ext in setOf(
            "jpg",
            "jpeg",
            "png",
            "gif",
            "svg",
            "webp",
            "bmp",
            "css",
            "html",
            "xhtml",
            "opf",
            "ncx",
            "otf",
            "ttf",
            "woff",
            "woff2",
        )
    }

    private fun normalizeResourceName(value: String): String {
        var cleaned = value.trim()
        cleaned = cleaned.substringBefore('#')
        cleaned = cleaned.substringBefore('?')
        cleaned = cleaned.replace('\\', '/')
        while (cleaned.startsWith("./")) {
            cleaned = cleaned.removePrefix("./")
        }
        return runCatching { URLDecoder.decode(cleaned, "UTF-8") }
            .getOrDefault(cleaned)
            .lowercase()
    }

    private fun buildFallbackHeader(
        fallbackTitle: String,
        fileName: String,
    ): MobiHeader {
        val title =
            if (fallbackTitle.isNotBlank()) {
                fallbackTitle
            } else {
                fileName.substringBeforeLast('.', "Unknown Book")
            }
        return MobiHeader(
            title = title,
            authors = emptyList(),
            textCharset = Charsets.UTF_8,
            firstImageIndex = -1,
            coverRecordIndex = null,
        )
    }

    private fun parseMobiHeaderAtOffset(
        record0: ByteArray,
        fallbackTitle: String,
        fileName: String,
        headerOffset: Int,
    ): MobiHeader? {
        if (headerOffset < 0 || headerOffset + 4 > record0.size) return null
        val mobiCheck = String(record0.copyOfRange(headerOffset, headerOffset + 4))
        if (mobiCheck != "MOBI") return null

        var title = fallbackTitle
        var authors = emptyList<String>()
        var textCharset = Charsets.UTF_8
        var firstImageIndex = -1
        var coverRecordIndex: Int? = null

        val headerLength = readInt(record0, headerOffset + 4)
        if (headerLength <= 0 || headerOffset + headerLength > record0.size) return null
        val textEncoding = readInt(record0, headerOffset + 12)
        textCharset = resolveCharset(textEncoding)

        val fullNameOffset = readInt(record0, headerOffset + 28)
        val fullNameLength = readInt(record0, headerOffset + 32)
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
            firstImageIndex = readInt(record0, headerOffset + FIRST_IMAGE_INDEX_OFFSET)
        }

        val exthFlags =
            if (headerLength >= EXTH_FLAGS_OFFSET + 4) {
                readInt(record0, headerOffset + EXTH_FLAGS_OFFSET)
            } else {
                0
            }
        val exthStart = headerOffset + headerLength
        val hasExthFlag = (exthFlags and EXTH_PRESENT_FLAG) != 0
        val hasExthMagic =
            exthStart + 4 <= record0.size &&
                String(record0.copyOfRange(exthStart, exthStart + 4)) == "EXTH"
        if ((hasExthFlag || hasExthMagic) && exthStart + 12 <= record0.size) {
            val exth =
                parseExthHeader(
                    record0 = record0,
                    start = exthStart,
                    charset = textCharset,
                )
            if (exth.title != null) {
                title = exth.title
            }
            if (exth.authors.isNotEmpty()) {
                authors = exth.authors
            }
            coverRecordIndex = exth.coverRecordIndex
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

    private fun indexOfMobiHeader(
        data: ByteArray,
        startIndex: Int,
    ): Int {
        if (startIndex < 0 || startIndex >= data.size - 4) return -1
        val limit = data.size - 4
        var index = startIndex
        while (index <= limit) {
            if (data[index] == 'M'.code.toByte() &&
                data[index + 1] == 'O'.code.toByte() &&
                data[index + 2] == 'B'.code.toByte() &&
                data[index + 3] == 'I'.code.toByte()
            ) {
                return index
            }
            index += 1
        }
        return -1
    }

    private fun readLittleEndianShort(data: ByteArray, offset: Int): Int {
        if (offset + 2 > data.size || offset < 0) return 0
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readLittleEndianInt(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size || offset < 0) return 0
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun cleanMobiHtml(html: String): String =
        html.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")

    private fun looksLikeHtml(text: String): Boolean =
        text.contains("<p", ignoreCase = true) ||
            text.contains("<div", ignoreCase = true) ||
            text.contains("<html", ignoreCase = true) ||
            text.contains("<body", ignoreCase = true) ||
            text.contains("<a", ignoreCase = true) ||
            text.contains("<h", ignoreCase = true) ||
            text.contains("<br", ignoreCase = true)

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
        textRecordCount: Int,
        coverRecindexCandidates: Set<Int>,
        referencedImageIndices: Set<Int>,
    ): ImageExtractionResult {
        val imagePathByRecordIndex = mutableMapOf<Int, String>()
        var firstImage: ByteArray? = null
        var bestOverall: ByteArray? = null
        var bestOverallScore = 0L
        var bestPortrait: ByteArray? = null
        var bestPortraitScore = 0L

        val imageDir = File(context.filesDir, "kairo_mobi_assets/${bookId.value}/images")
        val canWriteImages = runCatching { imageDir.mkdirs() || imageDir.exists() }.getOrDefault(false)

        val textRecords = recordOffsets.size
        var resolvedStartIndex =
            when {
                firstImageIndex > 0 && firstImageIndex < textRecords -> firstImageIndex
                textRecordCount > 0 -> {
                    val candidate = 1 + textRecordCount
                    candidate.takeIf { it in 0 until textRecords } ?: -1
                }
                else -> -1
            }
        if (resolvedStartIndex >= 0 &&
            !isImageRecord(data, recordOffsets, resolvedStartIndex)
        ) {
            resolvedStartIndex = -1
        }
        if (resolvedStartIndex < 0) {
            resolvedStartIndex = findFirstImageRecordIndex(data, recordOffsets) ?: -1
        }
        val hasValidStartIndex = resolvedStartIndex >= 0
        val startIndex = if (hasValidStartIndex) resolvedStartIndex else 0

        val filterImages = false
        val recindexBase =
            if (hasValidStartIndex && startIndex > 0 && !isImageRecord(data, recordOffsets, startIndex - 1)) {
                startIndex - 1
            } else if (hasValidStartIndex) {
                startIndex
            } else {
                -1
            }
        val hasValidRecindexBase = recindexBase >= 0

        val explicitCoverIndices =
            buildExplicitCoverIndices(
                coverRecordIndex = coverRecordIndex,
                recindexBase = recindexBase,
                firstImageIndex = firstImageIndex,
                recordCount = recordOffsets.size,
                hasValidRecindexBase = hasValidRecindexBase,
            )
        val htmlCoverCandidateIndices =
            buildHtmlCoverCandidateIndices(
                coverRecindexCandidates = coverRecindexCandidates,
                recindexBase = recindexBase,
                recordCount = recordOffsets.size,
                hasValidRecindexBase = hasValidRecindexBase,
            )
        val htmlCoverPreferredIndex =
            resolveHtmlCoverPreferredIndex(
                data = data,
                recordOffsets = recordOffsets,
                coverRecindexCandidates = coverRecindexCandidates,
                recindexBase = recindexBase,
                hasValidRecindexBase = hasValidRecindexBase,
            )
        val coverCandidateIndices =
            buildCoverCandidateIndices(
                coverRecindexCandidates = coverRecindexCandidates,
                startIndex = startIndex,
                recindexBase = recindexBase,
                firstImageIndex = firstImageIndex,
                coverRecordIndex = coverRecordIndex,
                recordCount = recordOffsets.size,
                hasValidStartIndex = hasValidStartIndex,
            ).toMutableSet().also { it.addAll(explicitCoverIndices) }
        val neededIndices =
            if (filterImages) {
                buildNeededImageIndices(
                    referencedImageIndices = referencedImageIndices,
                    coverCandidateIndices = coverCandidateIndices,
                    startIndex = startIndex,
                    hasValidStartIndex = hasValidStartIndex,
                )
            } else {
                emptySet()
            }
        val loopStart = if (filterImages) neededIndices.minOrNull() ?: startIndex else startIndex
        val loopEnd =
            if (filterImages) neededIndices.maxOrNull() ?: recordOffsets.lastIndex else recordOffsets.lastIndex

        var totalImageBytes = 0L
        var htmlCoverPreferred: ByteArray? = null
        var htmlCoverCandidate: ByteArray? = null
        var htmlCoverCandidateScore = 0L
        var colorCoverCandidate: ByteArray? = null
        var colorCoverScore = 0f
        var coverCandidate: ByteArray? = null
        var coverCandidateScore = 0L
        var coverPortraitCandidate: ByteArray? = null
        var coverPortraitScore = 0L
        var explicitCoverImage: ByteArray? = null

        for (index in loopStart..loopEnd) {
            if (filterImages && index !in neededIndices) continue
            val start = recordOffsets[index]
            val end = if (index + 1 < recordOffsets.size) recordOffsets[index + 1] else data.size
            if (end !in 0..data.size || start !in 0 until end) continue

            val raw = data.copyOfRange(start, end)
            val imageType = detectImageType(raw) ?: continue
            val dimensions = readImageDimensions(imageType, raw)
            val score = dimensions?.area ?: raw.size.toLong()
            val isPortrait = dimensions?.isPortrait == true
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
            if (firstImage == null) {
                firstImage = raw
            }

            if (dimensions != null && isPortrait && dimensions.area >= MIN_COLOR_COVER_AREA) {
                val colorScore = estimateColorScore(raw, dimensions)
                if (colorScore != null && colorScore > colorCoverScore) {
                    colorCoverScore = colorScore
                    colorCoverCandidate = raw
                }
            }
            if (htmlCoverPreferred == null && index == htmlCoverPreferredIndex) {
                htmlCoverPreferred = raw
            }
            if (index in htmlCoverCandidateIndices && score > htmlCoverCandidateScore) {
                htmlCoverCandidateScore = score
                htmlCoverCandidate = raw
            }
            if (explicitCoverImage == null && index in explicitCoverIndices) {
                explicitCoverImage = raw
            }
            if (index in coverCandidateIndices) {
                if (score > coverCandidateScore) {
                    coverCandidateScore = score
                    coverCandidate = raw
                }
                if (isPortrait && score > coverPortraitScore) {
                    coverPortraitScore = score
                    coverPortraitCandidate = raw
                }
            }
            if (score > bestOverallScore) {
                bestOverallScore = score
                bestOverall = raw
            }
            if (isPortrait && score > bestPortraitScore) {
                bestPortraitScore = score
                bestPortrait = raw
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

        val coverImage =
            if (colorCoverCandidate != null && colorCoverScore >= MIN_COLOR_SCORE) {
                colorCoverCandidate
            } else {
                explicitCoverImage ?: htmlCoverPreferred ?: htmlCoverCandidate ?: coverPortraitCandidate
                    ?: coverCandidate ?: firstImage ?: bestPortrait ?: bestOverall
            }

        return ImageExtractionResult(
            imagePathByRecordIndex = imagePathByRecordIndex,
            coverImage = coverImage,
            resolvedFirstImageIndex = resolvedStartIndex.takeIf { it >= 0 },
            recindexBase = recindexBase.takeIf { it >= 0 },
        )
    }

    private data class ImageType(val extension: String)
    private data class ImageDimensions(val width: Int, val height: Int) {
        val area: Long = width.toLong() * height.toLong()
        val isPortrait: Boolean = height >= width
    }

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

    private fun readImageDimensions(type: ImageType, bytes: ByteArray): ImageDimensions? =
        when (type.extension) {
            "jpg" -> readJpegDimensions(bytes)
            "png" -> readPngDimensions(bytes)
            "gif" -> readGifDimensions(bytes)
            "bmp" -> readBmpDimensions(bytes)
            else -> null
        }

    private fun readPngDimensions(bytes: ByteArray): ImageDimensions? {
        if (bytes.size < 24) return null
        val width = readInt(bytes, 16)
        val height = readInt(bytes, 20)
        return if (width > 0 && height > 0) ImageDimensions(width, height) else null
    }

    private fun readGifDimensions(bytes: ByteArray): ImageDimensions? {
        if (bytes.size < 10) return null
        val width = readLittleEndianShort(bytes, 6)
        val height = readLittleEndianShort(bytes, 8)
        return if (width > 0 && height > 0) ImageDimensions(width, height) else null
    }

    private fun readBmpDimensions(bytes: ByteArray): ImageDimensions? {
        if (bytes.size < 26) return null
        val width = readLittleEndianInt(bytes, 18)
        val height = readLittleEndianInt(bytes, 22)
        val absHeight = if (height < 0) -height else height
        return if (width > 0 && absHeight > 0) ImageDimensions(width, absHeight) else null
    }

    private fun readJpegDimensions(bytes: ByteArray): ImageDimensions? {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
        var index = 2
        while (index + 1 < bytes.size) {
            if (bytes[index] != 0xFF.toByte()) {
                index++
                continue
            }
            while (index < bytes.size && bytes[index] == 0xFF.toByte()) {
                index++
            }
            if (index >= bytes.size) break
            val marker = bytes[index].toInt() and 0xFF
            index++
            if (marker == 0xD8 || marker == 0xD9) continue
            if (index + 1 >= bytes.size) break
            val length = ((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)
            if (length < 2) return null
            if (marker in listOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)) {
                if (index + 7 >= bytes.size) return null
                val height = ((bytes[index + 3].toInt() and 0xFF) shl 8) or (bytes[index + 4].toInt() and 0xFF)
                val width = ((bytes[index + 5].toInt() and 0xFF) shl 8) or (bytes[index + 6].toInt() and 0xFF)
                return if (width > 0 && height > 0) ImageDimensions(width, height) else null
            }
            index += length
        }
        return null
    }

    private fun estimateColorScore(
        bytes: ByteArray,
        dimensions: ImageDimensions,
    ): Float? {
        val sampleMax = 72
        val sample =
            if (dimensions.width > sampleMax || dimensions.height > sampleMax) {
                val sampleSize =
                    maxOf(
                        1,
                        minOf(dimensions.width / sampleMax, dimensions.height / sampleMax),
                    )
                sampleSize
            } else {
                1
            }
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            bitmap.recycle()
            return null
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        var totalSat = 0f
        var count = 0
        val hsv = FloatArray(3)
        val step = if (pixels.size > 4096) 2 else 1
        var i = 0
        while (i < pixels.size) {
            Color.colorToHSV(pixels[i], hsv)
            totalSat += hsv[1]
            count += 1
            i += step
        }
        return if (count > 0) totalSat / count else null
    }

    private fun findFirstImageRecordIndex(
        data: ByteArray,
        recordOffsets: List<Int>,
    ): Int? {
        if (recordOffsets.isEmpty()) return null
        val lastIndex = recordOffsets.lastIndex
        for (index in 1..lastIndex) {
            val start = recordOffsets[index]
            val end = if (index + 1 <= lastIndex) recordOffsets[index + 1] else data.size
            if (start < 0 || end > data.size || end <= start) continue
            if (isImageRecord(data, recordOffsets, index)) return index
        }
        return null
    }

    private fun isImageRecord(
        data: ByteArray,
        recordOffsets: List<Int>,
        index: Int,
    ): Boolean {
        if (index !in recordOffsets.indices) return false
        val start = recordOffsets[index]
        val end =
            if (index + 1 < recordOffsets.size) {
                recordOffsets[index + 1]
            } else {
                data.size
            }
        if (start < 0 || end > data.size || end <= start) return false
        val headerEnd = (start + 32).coerceAtMost(end)
        if (headerEnd - start < 12) return false
        val head = data.copyOfRange(start, headerEnd)
        return detectImageType(head) != null
    }

    private fun rewriteMobiImageSrcs(
        html: String,
        imagePathByRecordIndex: Map<Int, String>,
        recindexBase: Int,
    ): String {
        var updated = html
        val recindexRegex =
            Regex(
                """(<img[^>]+?)\s+recindex\s*=\s*['"](\d+)['"]([^>]*>)""",
                RegexOption.IGNORE_CASE
            )
        updated = recindexRegex.replace(updated) { match ->
            val recIndex = match.groupValues[2].toIntOrNull() ?: return@replace match.value
            val resolved = resolveImagePath(recIndex, imagePathByRecordIndex, recindexBase)
                ?: return@replace match.value
            "${match.groupValues[1]} src=\"$resolved\"${match.groupValues[3]}"
        }

        val embedRegex =
            Regex(
                """(src\s*=\s*['"])kindle:embed:(\d+)(['"])""",
                RegexOption.IGNORE_CASE
            )
        updated = embedRegex.replace(updated) { match ->
            val embedIndex = match.groupValues[2].toIntOrNull() ?: return@replace match.value
            val resolved = resolveImagePath(embedIndex, imagePathByRecordIndex, recindexBase)
                ?: return@replace match.value
            "${match.groupValues[1]}$resolved${match.groupValues[3]}"
        }

        if (imagePathByRecordIndex.isNotEmpty()) {
            val orderedPaths = imagePathByRecordIndex.toSortedMap().values.toList()
            var fallbackIndex = 0
            val imgRegex = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
            updated = imgRegex.replace(updated) { match ->
                val tag = match.value
                if (tag.contains("recindex", ignoreCase = true)) return@replace tag
                if (tag.contains("kindle:embed", ignoreCase = true)) return@replace tag
                val src = extractAttribute(tag, "src") ?: return@replace tag
                if (src.startsWith("data:", ignoreCase = true) ||
                    src.startsWith("http://", ignoreCase = true) ||
                    src.startsWith("https://", ignoreCase = true) ||
                    src.contains("kairo_mobi_assets/", ignoreCase = true)
                ) {
                    return@replace tag
                }
                val replacement = orderedPaths.getOrNull(fallbackIndex) ?: return@replace tag
                fallbackIndex += 1
                replaceSrcInTag(tag, replacement)
            }
        }

        return updated
    }

    private fun replaceSrcInTag(
        tag: String,
        src: String,
    ): String {
        val srcRegex = Regex("""\bsrc\s*=\s*(?:'[^']*'|"[^"]*"|[^\s>]+)""", RegexOption.IGNORE_CASE)
        return if (srcRegex.containsMatchIn(tag)) {
            srcRegex.replace(tag) { "src=\"$src\"" }
        } else {
            val (prefix, suffix) =
                if (tag.endsWith("/>")) {
                    tag.dropLast(2) to "/>"
                } else {
                    tag.dropLast(1) to ">"
                }
            "$prefix src=\"$src\"$suffix"
        }
    }

    private fun resolveImagePath(
        index: Int,
        imagePathByRecordIndex: Map<Int, String>,
        recindexBase: Int,
    ): String? {
        imagePathByRecordIndex[index]?.let { return it }
        if (recindexBase >= 0) {
            imagePathByRecordIndex[recindexBase + index]?.let { return it }
        }
        return null
    }

    private fun splitHtmlIntoChapters(
        html: String,
        fallbackTitle: String,
    ): List<Chapter> {
        val slices = splitHtmlIntoChapterSlices(html, fallbackTitle)
        if (slices.isEmpty()) return emptyList()

        val baseChapters = slices.map { it.chapter }
        val idToBaseIndex = buildAnchorIdIndex(baseChapters)
        val expansion = splitLargeChapters(baseChapters, fallbackTitle)

        return rewriteMobiAnchorHrefs(
            chapters = expansion.chapters,
            idToBaseIndex = idToBaseIndex,
            baseIndexToExpandedIndex = expansion.baseIndexToExpandedIndex,
            slices = slices,
        )
    }

    private data class MobiChapterSlice(
        val start: Int,
        val end: Int,
        val chapter: Chapter,
    )

    private class ChapterExpansion(
        val chapters: List<Chapter>,
        val baseIndexToExpandedIndex: IntArray,
    )

    private fun splitHtmlIntoChapterSlices(
        html: String,
        fallbackTitle: String,
    ): List<MobiChapterSlice> {
        val headingRegex =
            Regex("<h[1-3][^>]*>.*?</h[1-3]>", RegexOption.IGNORE_CASE)
        val matches = headingRegex.findAll(html).toList()

        if (matches.size < 2) {
            val byFilepos = splitHtmlIntoChapterSlicesByFilepos(html, fallbackTitle)
            if (byFilepos.isNotEmpty()) return byFilepos
            val cleanedHtml = stripNoiseTitleBlocks(html)
            val plainText = extractPlainText(cleanedHtml)
            if (plainText.isBlank()) return emptyList()
            return listOf(
                MobiChapterSlice(
                    start = 0,
                    end = html.length,
                    chapter =
                    Chapter(
                        index = 0,
                        title = sanitizeChapterTitle(fallbackTitle),
                        htmlContent = cleanedHtml,
                        plainText = plainText,
                        imagePaths = extractImagePathsFromHtml(cleanedHtml),
                    ),
                ),
            )
        }

        val collected = mutableListOf<MobiChapterSlice>()
        val indices = matches.map { it.range.first } + html.length
        indices.zipWithNext().forEachIndexed { index, (start, end) ->
            val segment = html.substring(start, end).trim()
            val cleanedSegment = stripNoiseTitleBlocks(segment)
            val rawTitle =
                extractPlainText(matches.getOrNull(index)?.value.orEmpty())
                    .lineSequence()
                    .firstOrNull()
                    ?.take(100)
                    ?.takeIf { it.isNotBlank() }
            val title = sanitizeChapterTitle(rawTitle)
            val plain = extractPlainText(cleanedSegment)
            if (plain.isBlank()) return@forEachIndexed
            collected.add(
                MobiChapterSlice(
                    start = start,
                    end = end,
                    chapter =
                    Chapter(
                        index = collected.size,
                        title = title,
                        htmlContent = cleanedSegment,
                        plainText = plain,
                        imagePaths = extractImagePathsFromHtml(cleanedSegment),
                    ),
                ),
            )
        }
        return collected
    }

    private data class TocEntry(
        val filepos: Int,
        val title: String,
    )

    private fun splitHtmlIntoChapterSlicesByFilepos(
        html: String,
        fallbackTitle: String,
    ): List<MobiChapterSlice> {
        if (html.isBlank()) return emptyList()
        val tocRegion = html.take(MAX_TOC_TEXT_CHARS)
        val entries = extractTocEntries(tocRegion, html.length)
        if (entries.size < 2) return emptyList()

        val filteredEntries = mutableListOf<TocEntry>()
        var lastPos = -1
        for (entry in entries) {
            val pos = entry.filepos
            if (pos <= 0 || pos >= html.length) continue
            if (pos - lastPos < MIN_FILEPOS_GAP_CHARS) continue
            filteredEntries.add(entry)
            lastPos = pos
            if (filteredEntries.size >= MAX_FILEPOS_CHAPTERS) break
        }
        if (filteredEntries.size < 2) return emptyList()

        val adjustedStarts = mutableListOf<Int>()
        var lastStart = 0
        filteredEntries.forEach { entry ->
            val adjusted = adjustChapterStart(html, entry.filepos, entry.title)
            val start =
                when {
                    adjusted > lastStart -> adjusted
                    entry.filepos > lastStart -> entry.filepos
                    else -> lastStart
                }
            adjustedStarts.add(start)
            lastStart = start
        }
        if (adjustedStarts.size < 2) return emptyList()

        val slices = mutableListOf<MobiChapterSlice>()
        val tocEnd = adjustedStarts.first()
        val tocTitle =
            if (tocRegion.contains("table of contents", true) ||
                tocRegion.contains("contents", true)
            ) {
                "Table of Contents"
            } else {
                fallbackTitle
            }
        val sanitizedTocTitle = sanitizeChapterTitle(tocTitle)
        if (tocEnd > 0) {
            val segment = html.substring(0, tocEnd).trim()
            val cleanedSegment = stripNoiseTitleBlocks(segment)
            val plain = extractPlainText(cleanedSegment)
            if (plain.isNotBlank()) {
                slices.add(
                    MobiChapterSlice(
                        start = 0,
                        end = tocEnd,
                        chapter =
                        Chapter(
                            index = slices.size,
                            title = sanitizedTocTitle,
                            htmlContent = cleanedSegment,
                            plainText = plain,
                            imagePaths = extractImagePathsFromHtml(cleanedSegment),
                        ),
                    ),
                )
            }
        }

        filteredEntries.forEachIndexed { index, entry ->
            val start = adjustedStarts[index]
            val end = adjustedStarts.getOrNull(index + 1) ?: html.length
            if (start >= end || start < 0 || end > html.length) return@forEachIndexed
            val segment = html.substring(start, end).trim()
            val cleanedSegment = stripNoiseTitleBlocks(segment)
            val plain = extractPlainText(cleanedSegment)
            if (plain.isBlank()) return@forEachIndexed
            val sanitizedTitle = sanitizeChapterTitle(entry.title)
            slices.add(
                MobiChapterSlice(
                    start = start,
                    end = end,
                    chapter =
                    Chapter(
                        index = slices.size,
                        title = sanitizedTitle,
                        htmlContent = cleanedSegment,
                        plainText = plain,
                        imagePaths = extractImagePathsFromHtml(cleanedSegment),
                    ),
                ),
            )
        }

        return slices
    }

    private fun adjustChapterStart(
        html: String,
        filepos: Int,
        title: String,
    ): Int {
        if (filepos <= 0 || filepos >= html.length) return filepos
        val windowStart = (filepos - CHAPTER_START_SCAN_BACK).coerceAtLeast(0)
        val windowEnd = (filepos + CHAPTER_START_SCAN_FORWARD).coerceAtMost(html.length)
        val window = html.substring(windowStart, windowEnd)
        val relativePos = filepos - windowStart
        val normalizedTitle = normalizeTitle(title)

        val normalizedWindow = buildNormalizedWindow(window, windowStart)
        if (normalizedTitle.isNotEmpty() && normalizedWindow.text.isNotEmpty()) {
            val fileposNormalizedIndex =
                findNormalizedIndexForFilepos(normalizedWindow.indexMap, filepos)
                    ?: normalizedWindow.text.lastIndex
            val keys = buildTitleKeys(title)
            val matchIndex = findBestNormalizedMatch(normalizedWindow.text, keys, fileposNormalizedIndex)
            if (matchIndex != null) {
                val htmlIndex = normalizedWindow.indexMap[matchIndex]
                val blockStart = findBlockStartBefore(window, htmlIndex - windowStart)
                if (blockStart != null) {
                    return windowStart + blockStart
                }
                return htmlIndex
            }
        }

        val headingRegex =
            Regex(
                """<h[1-6][^>]*>[\s\S]*?</h[1-6]>""",
                RegexOption.IGNORE_CASE
            )
        var titleMatchBefore: Int? = null
        var titleMatchAfter: Int? = null
        var lastHeadingBefore: Int? = null

        for (match in headingRegex.findAll(window)) {
            val headingStart = match.range.first
            val headingText = extractLinkText(match.value)
            val headingNormalized = normalizeTitle(headingText)
            val matchesTitle =
                normalizedTitle.isNotEmpty() &&
                    (headingNormalized.contains(normalizedTitle) ||
                        normalizedTitle.contains(headingNormalized))
            if (headingStart <= relativePos) {
                lastHeadingBefore = headingStart
                if (matchesTitle) titleMatchBefore = headingStart
            } else if (matchesTitle && titleMatchAfter == null) {
                titleMatchAfter = headingStart
            }
        }

        if (titleMatchBefore != null) {
            return windowStart + titleMatchBefore
        }
        if (titleMatchAfter != null) {
            return windowStart + titleMatchAfter
        }
        if (lastHeadingBefore != null) {
            return windowStart + lastHeadingBefore
        }

        if (normalizedTitle.isNotEmpty()) {
            val titleIndex = window.lowercase().indexOf(title.lowercase())
            if (titleIndex in 0..relativePos) {
                return windowStart + titleIndex
            }
        }

        val blockRegex =
            Regex(
                """</?(p|div|br|h[1-6]|li|tr|blockquote|pre)[^>]*>""",
                RegexOption.IGNORE_CASE
            )
        var lastBlockBefore: Int? = null
        for (match in blockRegex.findAll(window)) {
            val start = match.range.first
            if (start <= relativePos) {
                lastBlockBefore = start
            } else {
                break
            }
        }
        if (lastBlockBefore != null) {
            return windowStart + lastBlockBefore
        }

        return filepos
    }

    private data class NormalizedWindow(
        val text: String,
        val indexMap: IntArray,
    )

    private fun buildNormalizedWindow(
        window: String,
        windowStart: Int,
    ): NormalizedWindow {
        val textBuilder = StringBuilder(window.length)
        val indexMap = ArrayList<Int>(window.length / 2)
        var inTag = false
        window.forEachIndexed { index, ch ->
            when {
                ch == '<' -> inTag = true
                ch == '>' && inTag -> inTag = false
                inTag -> Unit
                ch.isLetterOrDigit() -> {
                    textBuilder.append(ch.lowercaseChar())
                    indexMap.add(windowStart + index)
                }
            }
        }
        return NormalizedWindow(textBuilder.toString(), indexMap.toIntArray())
    }

    private fun buildTitleKeys(title: String): List<String> {
        val tokens =
            title.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val keys = LinkedHashSet<String>()
        keys.add(tokens.joinToString(""))
        if (tokens.size >= 2) {
            keys.add(tokens[0] + tokens[1])
        }
        if (tokens.size >= 3) {
            keys.add(tokens[0] + tokens[1] + tokens[2])
        }
        return keys.sortedByDescending { it.length }
    }

    private fun findNormalizedIndexForFilepos(
        indexMap: IntArray,
        filepos: Int,
    ): Int? {
        for (i in indexMap.indices) {
            if (indexMap[i] >= filepos) return i
        }
        return null
    }

    private fun findBestNormalizedMatch(
        normalized: String,
        keys: List<String>,
        endIndex: Int,
    ): Int? {
        if (keys.isEmpty()) return null
        val safeEnd = endIndex.coerceIn(0, normalized.lastIndex)
        var bestIndex: Int? = null
        for (key in keys) {
            if (key.isBlank()) continue
            val index = normalized.lastIndexOf(key, safeEnd)
            if (index >= 0 && (bestIndex == null || index > bestIndex)) {
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun findBlockStartBefore(
        window: String,
        relativePos: Int,
    ): Int? {
        val blockRegex =
            Regex(
                """</?(p|div|br|h[1-6]|li|tr|blockquote|pre)[^>]*>""",
                RegexOption.IGNORE_CASE
            )
        var lastBlockBefore: Int? = null
        for (match in blockRegex.findAll(window)) {
            val start = match.range.first
            if (start <= relativePos) {
                lastBlockBefore = start
            } else {
                break
            }
        }
        return lastBlockBefore
    }

    private fun extractTocEntries(
        html: String,
        maxLength: Int,
    ): List<TocEntry> {
        if (html.isBlank()) return emptyList()
        val anchorRegex =
            Regex(
                """<a\b[^>]*\bfilepos\s*=\s*['"]?(\d+)['"]?[^>]*>([\s\S]*?)</a>""",
                RegexOption.IGNORE_CASE,
            )
        val entries = mutableListOf<TocEntry>()
        val seen = HashSet<Int>()
        for (match in anchorRegex.findAll(html)) {
            val filepos = match.groupValues[1].toIntOrNull() ?: continue
            if (filepos <= 0 || filepos >= maxLength) continue
            if (!seen.add(filepos)) continue
            val text = extractLinkText(match.groupValues[2])
            if (text.isBlank() || isPageNumberText(text)) continue
            entries.add(TocEntry(filepos, text))
            if (entries.size >= MAX_FILEPOS_CHAPTERS * 2) break
        }
        return entries.sortedBy { it.filepos }
    }

    private fun splitLargeChapters(
        chapters: List<Chapter>,
        fallbackTitle: String,
    ): ChapterExpansion {
        if (chapters.isEmpty()) return ChapterExpansion(emptyList(), IntArray(0))
        val expanded = mutableListOf<Chapter>()
        val baseIndexToExpandedIndex = IntArray(chapters.size) { -1 }

        chapters.forEachIndexed { baseIndex, chapter ->
            baseIndexToExpandedIndex[baseIndex] = expanded.size
            val isTocLike = isTocHeavyChapter(chapter)
            if (chapter.plainText.length <= MAX_CHAPTER_TEXT_CHARS || isTocLike) {
                expanded.add(chapter.copy(index = expanded.size))
                return@forEachIndexed
            }

            val parts = splitIntoChapters(chapter.plainText)
            if (parts.size <= 1) {
                expanded.add(chapter.copy(index = expanded.size))
                return@forEachIndexed
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

        return ChapterExpansion(expanded, baseIndexToExpandedIndex)
    }

    private fun buildAnchorIdIndex(chapters: List<Chapter>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        chapters.forEach { chapter ->
            extractAnchorIds(chapter.htmlContent).forEach { id ->
                map.putIfAbsent(id, chapter.index)
            }
        }
        return map
    }

    private fun extractAnchorIds(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val ids = LinkedHashSet<String>()
        val attrRegex =
            Regex(
                """\b(?:id|name)\s*=\s*['"]([^'"]+)['"]""",
                RegexOption.IGNORE_CASE
            )
        attrRegex.findAll(html).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotEmpty()) {
                ids.add(value)
            }
        }
        return ids.toList()
    }

    private fun isTocHeavyChapter(chapter: Chapter): Boolean {
        if (chapter.htmlContent.isBlank()) return false
        if (chapter.plainText.length > MAX_TOC_TEXT_CHARS) return false
        return countAnchorTags(chapter.htmlContent, TOC_ANCHOR_THRESHOLD) >= TOC_ANCHOR_THRESHOLD
    }

    private fun countAnchorTags(
        html: String,
        limit: Int,
    ): Int {
        var count = 0
        var index = 0
        while (true) {
            val found = html.indexOf("<a", index, ignoreCase = true)
            if (found == -1) return count
            count += 1
            if (count >= limit) return count
            index = found + 2
        }
    }

    private fun rewriteMobiAnchorHrefs(
        chapters: List<Chapter>,
        idToBaseIndex: Map<String, Int>,
        baseIndexToExpandedIndex: IntArray,
        slices: List<MobiChapterSlice>,
    ): List<Chapter> {
        if (chapters.isEmpty()) return chapters
        if (idToBaseIndex.isEmpty() && slices.isEmpty()) return chapters
        val titleIndex = buildTitleIndex(chapters)

        return chapters.map { chapter ->
            val html = chapter.htmlContent
            if (!html.contains("<a", ignoreCase = true)) return@map chapter
            val rewritten =
                rewriteMobiAnchorTagHrefs(
                    html = html,
                    idToBaseIndex = idToBaseIndex,
                    baseIndexToExpandedIndex = baseIndexToExpandedIndex,
                    slices = slices,
                    titleIndex = titleIndex,
                )
            chapter.copy(htmlContent = rewritten)
        }
    }

    private fun rewriteMobiAnchorTagHrefs(
        html: String,
        idToBaseIndex: Map<String, Int>,
        baseIndexToExpandedIndex: IntArray,
        slices: List<MobiChapterSlice>,
        titleIndex: Map<String, Int>,
    ): String {
        val rewrittenWithContent =
            rewriteMobiAnchorsWithContent(
                html = html,
                idToBaseIndex = idToBaseIndex,
                baseIndexToExpandedIndex = baseIndexToExpandedIndex,
                slices = slices,
                titleIndex = titleIndex,
            )

        return rewriteMobiAnchorOpenTags(
            html = rewrittenWithContent,
            idToBaseIndex = idToBaseIndex,
            baseIndexToExpandedIndex = baseIndexToExpandedIndex,
            slices = slices,
        )
    }

    private fun rewriteMobiAnchorsWithContent(
        html: String,
        idToBaseIndex: Map<String, Int>,
        baseIndexToExpandedIndex: IntArray,
        slices: List<MobiChapterSlice>,
        titleIndex: Map<String, Int>,
    ): String {
        val anchorRegex =
            Regex("""(<a\b[^>]*>)([\s\S]*?)(</a>)""", RegexOption.IGNORE_CASE)
        val hrefRegex =
            Regex(
                """\bhref\s*=\s*(?:['"][^'"]*['"]|[^\s>]+)""",
                RegexOption.IGNORE_CASE,
            )

        val rewritten = anchorRegex.replace(html) { match ->
            val openTag = match.groupValues[1]
            val innerHtml = match.groupValues[2]
            val closeTag = match.groupValues[3]
            if (openTag.contains("kairo://", ignoreCase = true)) return@replace match.value

            val href = extractAttribute(openTag, "href")
            val filepos = extractAttribute(openTag, "filepos")
            val resolvedFromHref =
                resolveTargetIndexFromHref(
                    href,
                    idToBaseIndex,
                    baseIndexToExpandedIndex,
                    slices,
                )
            val resolvedFromFilepos =
                if (resolvedFromHref == null) {
                    resolveTargetIndexFromFilepos(
                        filepos,
                        slices,
                        baseIndexToExpandedIndex
                    )
                } else {
                    null
                }
            val resolvedFromTitle =
                if (resolvedFromHref == null && resolvedFromFilepos == null) {
                    resolveTargetIndexFromLinkText(
                        innerHtml,
                        titleIndex,
                    )
                } else {
                    null
                }
            val resolvedIndex =
                resolvedFromHref ?: resolvedFromFilepos ?: resolvedFromTitle
                    ?: return@replace match.value

            val newHref = "kairo://chapter/$resolvedIndex"
            val rewrittenOpenTag =
                if (href.isNullOrBlank()) {
                    insertHref(openTag, newHref)
                } else {
                    openTag.replace(hrefRegex) { "href=\"$newHref\"" }
                }

            "$rewrittenOpenTag$innerHtml$closeTag"
        }
        return rewritten
    }

    private fun rewriteMobiAnchorOpenTags(
        html: String,
        idToBaseIndex: Map<String, Int>,
        baseIndexToExpandedIndex: IntArray,
        slices: List<MobiChapterSlice>,
    ): String {
        val anchorRegex = Regex("""<a\b[^>]*>""", RegexOption.IGNORE_CASE)
        val hrefRegex =
            Regex(
                """\bhref\s*=\s*(?:['"][^'"]*['"]|[^\s>]+)""",
                RegexOption.IGNORE_CASE,
            )

        val rewritten = anchorRegex.replace(html) { match ->
            val tag = match.value
            if (tag.contains("kairo://", ignoreCase = true)) return@replace tag

            val href = extractAttribute(tag, "href")
            val filepos = extractAttribute(tag, "filepos")
            val resolvedFromHref =
                resolveTargetIndexFromHref(
                    href,
                    idToBaseIndex,
                    baseIndexToExpandedIndex,
                    slices,
                )
            val resolvedFromFilepos =
                if (resolvedFromHref == null) {
                    resolveTargetIndexFromFilepos(
                        filepos,
                        slices,
                        baseIndexToExpandedIndex
                    )
                } else {
                    null
                }
            val resolvedIndex = resolvedFromHref ?: resolvedFromFilepos ?: return@replace tag
            val newHref = "kairo://chapter/$resolvedIndex"
            if (href.isNullOrBlank()) {
                insertHref(tag, newHref)
            } else {
                tag.replace(hrefRegex) { "href=\"$newHref\"" }
            }
        }
        return rewritten
    }

    private fun insertHref(
        tag: String,
        href: String,
    ): String {
        val (prefix, suffix) =
            if (tag.endsWith("/>")) {
                tag.dropLast(2) to "/>"
            } else {
                tag.dropLast(1) to ">"
            }
        return "$prefix href=\"$href\"$suffix"
    }

    private fun extractAttribute(
        tag: String,
        name: String,
    ): String? {
        val regex =
            Regex(
                """\b${Regex.escape(name)}\s*=\s*(?:['"]([^'"]+)['"]|([^\s>]+))""",
                RegexOption.IGNORE_CASE,
            )
        val match = regex.find(tag) ?: return null
        return match.groupValues.getOrNull(1)?.ifBlank { null }
            ?: match.groupValues.getOrNull(2)?.ifBlank { null }
    }

    private fun resolveTargetIndexFromHref(
        href: String?,
        idToBaseIndex: Map<String, Int>,
        baseIndexToExpandedIndex: IntArray,
        slices: List<MobiChapterSlice>,
    ): Int? {
        if (href.isNullOrBlank()) return null
        val trimmed = href.trim()
        if (trimmed.startsWith("http://", true) ||
            trimmed.startsWith("https://", true) ||
            trimmed.startsWith("mailto:", true) ||
            trimmed.startsWith("data:", true) ||
            trimmed.startsWith("kairo://", true)
        ) {
            return null
        }

        extractFilePosFromHref(trimmed)?.let { offset ->
            return resolveTargetIndexFromFilepos(
                offset.toString(),
                slices,
                baseIndexToExpandedIndex,
            )
        }

        val fragment =
            trimmed.substringAfter('#', "").trim()
        if (fragment.isNotEmpty()) {
            val decoded = decodeFragment(fragment)
            val baseIndex = idToBaseIndex[decoded] ?: return null
            return resolveExpandedIndex(baseIndex, baseIndexToExpandedIndex)
        }

        return null
    }

    private fun resolveTargetIndexFromFilepos(
        filepos: String?,
        slices: List<MobiChapterSlice>,
        baseIndexToExpandedIndex: IntArray,
    ): Int? {
        if (filepos.isNullOrBlank() || slices.isEmpty()) return null
        val offset = filepos.toIntOrNull() ?: return null
        val slice =
            slices.firstOrNull { offset >= it.start && offset < it.end }
                ?: return null
        return resolveExpandedIndex(slice.chapter.index, baseIndexToExpandedIndex)
    }

    private fun resolveExpandedIndex(
        baseIndex: Int,
        baseIndexToExpandedIndex: IntArray,
    ): Int? {
        if (baseIndex < 0 || baseIndex >= baseIndexToExpandedIndex.size) return null
        val mapped = baseIndexToExpandedIndex[baseIndex]
        return if (mapped >= 0) mapped else null
    }

    private fun decodeFragment(fragment: String): String {
        if (!fragment.contains('%')) return fragment
        return runCatching { URLDecoder.decode(fragment, "UTF-8") }.getOrDefault(fragment)
    }

    private fun extractFilePosFromHref(href: String): Int? {
        val patterns =
            listOf(
                Regex("filepos[:=](\\d+)", RegexOption.IGNORE_CASE),
                Regex("kindle:pos:\\S*?off:(\\d+)", RegexOption.IGNORE_CASE),
                Regex("kindle:pos:off:(\\d+)", RegexOption.IGNORE_CASE),
                Regex("#filepos(\\d+)", RegexOption.IGNORE_CASE),
                Regex("#pos(\\d+)", RegexOption.IGNORE_CASE),
            )
        for (pattern in patterns) {
            val match = pattern.find(href) ?: continue
            val value = match.groupValues.getOrNull(1)?.toIntOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun resolveTargetIndexFromLinkText(
        innerHtml: String,
        titleIndex: Map<String, Int>,
    ): Int? {
        if (titleIndex.isEmpty()) return null
        val linkText = extractLinkText(innerHtml)
        if (linkText.isBlank()) return null
        val normalized = normalizeTitle(linkText)
        if (normalized.isEmpty()) return null
        titleIndex[normalized]?.let { return it }

        val fallback =
            titleIndex.entries.firstOrNull { (key, _) ->
                (key.length >= 4 && normalized.contains(key)) ||
                    (normalized.length >= 4 && key.contains(normalized))
            }
        return fallback?.value
    }

    private fun buildTitleIndex(chapters: List<Chapter>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        chapters.forEach { chapter ->
            val title = chapter.title?.takeIf { it.isNotBlank() } ?: return@forEach
            val normalized = normalizeTitle(title)
            if (normalized.isNotEmpty()) {
                map.putIfAbsent(normalized, chapter.index)
            }
        }
        return map
    }

    private fun normalizeTitle(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9]+"), "")

    private fun extractLinkText(html: String): String =
        html
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
                m.groupValues[1].toIntOrNull(16)?.toChar()?.toString().orEmpty()
            }
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isPageNumberText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.all { it.isDigit() }) return true
        val romanNumeralPattern = Regex("^[ivxlcdm]+$", RegexOption.IGNORE_CASE)
        return romanNumeralPattern.matches(trimmed) && trimmed.length > 1
    }

    private fun extractImagePathsFromHtml(html: String): List<String> {
        if (!html.contains("<img", ignoreCase = true)) return emptyList()
        val regex =
            Regex(
                """<img[^>]+?src\s*=\s*['"]([^'"]+)['"][^>]*>""",
                RegexOption.IGNORE_CASE
            )
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

    private fun buildNeededImageIndices(
        referencedImageIndices: Set<Int>,
        coverCandidateIndices: Set<Int>,
        startIndex: Int,
        hasValidStartIndex: Boolean,
    ): Set<Int> {
        val needed = LinkedHashSet<Int>()
        needed.addAll(coverCandidateIndices)
        fun addRecindex(recindex: Int) {
            needed.add(recindex)
            if (hasValidStartIndex) {
                needed.add(startIndex + recindex)
            }
        }

        referencedImageIndices.forEach { addRecindex(it) }
        if (needed.isEmpty() && hasValidStartIndex) {
            repeat(COVER_FALLBACK_IMAGE_SCAN) { offset ->
                needed.add(startIndex + offset)
            }
        }
        return needed
    }

    private fun buildCoverCandidateIndices(
        coverRecindexCandidates: Set<Int>,
        startIndex: Int,
        recindexBase: Int,
        firstImageIndex: Int,
        coverRecordIndex: Int?,
        recordCount: Int,
        hasValidStartIndex: Boolean,
    ): Set<Int> {
        val candidates = LinkedHashSet<Int>()
        coverRecindexCandidates.forEach { recindex ->
            candidates.add(recindex)
            if (recindexBase >= 0) {
                candidates.add(recindexBase + recindex)
            }
        }
        resolveCoverRecordIndex(coverRecordIndex, firstImageIndex, recordCount)?.let { index ->
            candidates.add(index)
        }
        if (candidates.isEmpty() && startIndex >= 0) {
            repeat(COVER_FALLBACK_IMAGE_SCAN) { offset ->
                candidates.add(startIndex + offset)
            }
        }
        return candidates
    }

    private fun buildHtmlCoverCandidateIndices(
        coverRecindexCandidates: Set<Int>,
        recindexBase: Int,
        recordCount: Int,
        hasValidRecindexBase: Boolean,
    ): Set<Int> {
        if (coverRecindexCandidates.isEmpty()) return emptySet()
        val indices = LinkedHashSet<Int>()
        coverRecindexCandidates.forEach { recindex ->
            if (recindex in 0 until recordCount) {
                indices.add(recindex)
            }
            if (hasValidRecindexBase) {
                val absolute = recindexBase + recindex
                if (absolute in 0 until recordCount) {
                    indices.add(absolute)
                }
            }
        }
        return indices
    }

    private fun resolveHtmlCoverPreferredIndex(
        data: ByteArray,
        recordOffsets: List<Int>,
        coverRecindexCandidates: Set<Int>,
        recindexBase: Int,
        hasValidRecindexBase: Boolean,
    ): Int? {
        if (coverRecindexCandidates.isEmpty()) return null
        val seen = LinkedHashSet<Int>()
        for (recindex in coverRecindexCandidates) {
            if (recindex >= 0) {
                seen.add(recindex)
            }
            if (hasValidRecindexBase) {
                seen.add(recindexBase + recindex)
            }
        }
        for (candidate in seen) {
            if (isImageRecord(data, recordOffsets, candidate)) {
                return candidate
            }
        }
        return null
    }

    private fun buildExplicitCoverIndices(
        coverRecordIndex: Int?,
        recindexBase: Int,
        firstImageIndex: Int,
        recordCount: Int,
        hasValidRecindexBase: Boolean,
    ): Set<Int> {
        val indices = LinkedHashSet<Int>()
        if (coverRecordIndex == null) return indices

        if (coverRecordIndex in 0 until recordCount) {
            indices.add(coverRecordIndex)
        }
        resolveCoverRecordIndex(coverRecordIndex, firstImageIndex, recordCount)?.let { index ->
            indices.add(index)
        }
        if (hasValidRecindexBase) {
            val relative = recindexBase + coverRecordIndex
            if (relative in 0 until recordCount) {
                indices.add(relative)
            }
        }
        return indices
    }

    private fun resolveCoverRecordIndex(
        coverRecordIndex: Int?,
        firstImageIndex: Int,
        recordCount: Int,
    ): Int? {
        if (coverRecordIndex == null) return null
        val absolute =
            coverRecordIndex.takeIf { it in 0 until recordCount }
                ?.takeIf { firstImageIndex <= 0 || it >= firstImageIndex }
        if (absolute != null) return absolute
        val relative =
            if (firstImageIndex > 0) {
                (firstImageIndex + coverRecordIndex)
                    .takeIf { it in 0 until recordCount }
            } else {
                null
            }
        return relative ?: coverRecordIndex.takeIf { it in 0 until recordCount }
    }

    private fun extractReferencedImageIndices(
        html: String,
        extraIndices: Set<Int>,
    ): Set<Int> {
        if (html.isBlank()) return extraIndices
        if (!html.contains("recindex", ignoreCase = true) &&
            !html.contains("kindle:embed", ignoreCase = true)
        ) {
            return extraIndices
        }
        val indices = LinkedHashSet<Int>(extraIndices)
        val recindexRegex =
            Regex("""recindex\s*=\s*['"]?(\d+)['"]?""", RegexOption.IGNORE_CASE)
        recindexRegex.findAll(html).forEach { match ->
            match.groupValues[1].toIntOrNull()?.let { indices.add(it) }
        }
        val embedRegex =
            Regex("""kindle:embed:(\d+)""", RegexOption.IGNORE_CASE)
        embedRegex.findAll(html).forEach { match ->
            match.groupValues[1].toIntOrNull()?.let { indices.add(it) }
        }
        return indices
    }

    private fun extractCoverImageRecindices(html: String): Set<Int> {
        if (html.isBlank()) return emptySet()
        val limited = html.take(COVER_HTML_SCAN_CHARS)
        val coverIds = LinkedHashSet<String>()
        val metaRegex =
            Regex(
                """<meta[^>]+name\s*=\s*['"]cover['"][^>]+content\s*=\s*['"]([^'"]+)['"][^>]*>""",
                RegexOption.IGNORE_CASE,
            )
        metaRegex.findAll(limited).forEach { match ->
            val id = match.groupValues[1].trim()
            if (id.isNotEmpty()) coverIds.add(id)
        }

        val imgRegex = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
        val candidates = LinkedHashSet<Int>()
        var firstRecindex: Int? = null
        for (match in imgRegex.findAll(limited)) {
            val tag = match.value
            val recindex = extractRecindexFromImgTag(tag)
            if (recindex != null && firstRecindex == null) {
                firstRecindex = recindex
            }
            if (recindex == null) continue
            val id = extractAttribute(tag, "id") ?: extractAttribute(tag, "name")
            val alt = extractAttribute(tag, "alt").orEmpty()
            val title = extractAttribute(tag, "title").orEmpty()
            val classAttr = extractAttribute(tag, "class").orEmpty()
            val looksCover =
                (id != null && coverIds.contains(id)) ||
                    id?.contains("cover", ignoreCase = true) == true ||
                    alt.contains("cover", ignoreCase = true) ||
                    title.contains("cover", ignoreCase = true) ||
                    classAttr.contains("cover", ignoreCase = true)
            if (looksCover) {
                candidates.add(recindex)
                if (candidates.size >= COVER_FALLBACK_IMAGE_SCAN) break
            }
        }

        if (candidates.isEmpty()) {
            val firstImg = imgRegex.find(limited)?.value
            if (firstImg != null) {
                val recindex = extractRecindexFromImgTag(firstImg)
                if (recindex != null) {
                    candidates.add(recindex)
                } else {
                    val src = extractAttribute(firstImg, "src")
                    if (!src.isNullOrBlank() &&
                        !src.startsWith("data:", ignoreCase = true) &&
                        !src.startsWith("http://", ignoreCase = true) &&
                        !src.startsWith("https://", ignoreCase = true)
                    ) {
                        candidates.add(0)
                    }
                }
            }
            firstRecindex?.let { candidates.add(it) }
        }
        return candidates
    }

    private fun extractRecindexFromImgTag(tag: String): Int? {
        extractAttribute(tag, "recindex")?.toIntOrNull()?.let { return it }
        val src = extractAttribute(tag, "src") ?: return null
        val match = Regex("kindle:embed:(\\d+)", RegexOption.IGNORE_CASE).find(src)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
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

    private fun sanitizeChapterTitle(title: String?): String? {
        val trimmed = title?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (isLikelyFileLabel(trimmed)) null else trimmed
    }

    private fun stripNoiseTitleBlocks(html: String): String {
        if (html.isBlank()) return html
        val blockRegex =
            Regex(
                "(?is)<(h[1-6]|p|div)[^>]*>([\\s\\S]*?)</\\1>"
            )
        return blockRegex.replace(html) { match ->
            val inner = match.groupValues[2]
            val text =
                inner.replace(Regex("<[^>]+>"), " ")
                    .replace("&nbsp;", " ")
                    .trim()
            if (text.length <= MAX_NOISE_TITLE_LENGTH && isLikelyFileLabel(text)) {
                ""
            } else {
                match.value
            }
        }
    }

    private fun isLikelyFileLabel(text: String): Boolean {
        val normalized = normalizeNoiseLabel(text)
        if (normalized.isBlank()) return false
        val compact = normalized.replace(Regex("[\\s_-]+"), "")
        val numberedMatch = FILE_LABEL_WITH_NUMBER_REGEX.matchEntire(compact)
        if (numberedMatch != null) {
            val zeros = numberedMatch.groupValues[2]
            val digits = numberedMatch.groupValues[3]
            if (zeros.isNotEmpty() || digits.length >= 3) return true
        }
        return GENERIC_FILE_LABEL_REGEX.matches(compact)
    }

    private fun normalizeNoiseLabel(text: String): String {
        val trimmed = text.trim().lowercase()
        if (trimmed.isBlank()) return ""
        return trimmed.substringBeforeLast('.', trimmed)
    }

    private fun decodeHtmlEntities(text: String): String =
        text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
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

    private fun normalizePageBreakElements(html: String): String {
        var text = html
        val pageBreakToken = " "

        val pageBreakTag =
            Regex("""<\s*mbp:pagebreak\s*/?>""", RegexOption.IGNORE_CASE)
        val classPageBreak =
            Regex(
                """<[^>]+\bclass\s*=\s*['"][^'"]*(?:pagebreak|page-break)[^'"]*['"][^>]*>""",
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
            languageTag = null,
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
                when (value) {
                    0x09, 0x0A, 0x0D -> ' '
                    in 0x20..0x7E -> value.toChar()
                    in 0xA0..0xFF -> value.toChar()
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
