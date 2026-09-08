package com.kairo.reader.data.books

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.countWords
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class PdfBookParser(private val dispatcherProvider: DispatcherProvider,) : BookParser {
    override suspend fun parse(
        context: Context,
        uri: Uri,
        bookId: BookId,
    ): Book = parse(context, uri, bookId, sourceDisplayName = null)

    override suspend fun parse(
        context: Context,
        uri: Uri,
        bookId: BookId,
        sourceDisplayName: String?,
    ): Book =
        withContext(dispatcherProvider.io) {
            PDFBoxResourceLoader.init(context.applicationContext)
            require(BookImportFormatDetector.detect(context, uri) in BookImportFormats.pdf.extensions) {
                "Selected file is not a valid PDF"
            }
            val fileSize = resolveFileSize(context, uri)
            require(fileSize < 0L || fileSize <= MAX_FILE_SIZE_BYTES) {
                "File is too large to import (maximum ${MAX_FILE_SIZE_BYTES.toMebibytes()} MB)"
            }
            val request =
                PdfBookParseRequest(
                    bookId = bookId,
                    sourceSizeBytes = fileSize,
                    sourceDisplayName =
                    sourceDisplayName?.takeIf(String::isNotBlank)
                        ?: uri.lastPathSegment?.substringAfterLast('/')
                        ?: DEFAULT_SOURCE_NAME,
                )
            try {
                loadDocument(context, uri, fileSize).use { document ->
                    val parsingContext = currentCoroutineContext()
                    PdfParserEngine.parse(document, request) { parsingContext.ensureActive() }
                }
            } catch (error: InvalidPasswordException) {
                throw IllegalArgumentException("Password-protected or encrypted PDFs are not supported", error)
            } catch (error: IllegalArgumentException) {
                throw error
            } catch (error: IOException) {
                throw IllegalArgumentException("Unable to parse PDF document", error)
            }
        }

    override fun supports(extension: String): Boolean =
        extension.trim().lowercase(Locale.ROOT) in BookImportFormats.pdf.extensions

    private fun loadDocument(
        context: Context,
        uri: Uri,
        sourceSizeBytes: Long,
    ): PDDocument {
        val memoryUsage =
            MemoryUsageSetting
                .setupMixed(PdfImportPerformancePolicy.memoryBudgetBytes(sourceSizeBytes))
                .setTempDir(context.cacheDir)
        val localFile = uri.localFileOrNull()
        if (localFile?.isFile == true) {
            return PDDocument.load(localFile, memoryUsage)
        }
        return requireNotNull(context.contentResolver.openInputStream(uri)) {
            "Unable to read imported file"
        }.use { input ->
            PDDocument.load(BufferedInputStream(input), memoryUsage)
        }
    }

    private fun resolveFileSize(
        context: Context,
        uri: Uri,
    ): Long {
        uri.localFileOrNull()?.takeIf(File::isFile)?.let(File::length)?.let { return it }
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst() && sizeIndex >= 0) return cursor.getLong(sizeIndex)
                }
        }
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor -> descriptor.statSize }
        }.getOrNull() ?: -1L
    }

    private fun Uri.localFileOrNull(): File? =
        path?.takeIf { scheme == ContentResolver.SCHEME_FILE }?.let(::File)

    private fun Long.toMebibytes(): Long = this / BYTES_PER_MEBIBYTE

    private companion object {
        private const val DEFAULT_SOURCE_NAME = "Imported PDF"
        private const val MAX_FILE_SIZE_BYTES = 96L * 1024L * 1024L
        private const val BYTES_PER_MEBIBYTE = 1024L * 1024L
    }
}

internal data class PdfBookParseRequest(
    val bookId: BookId,
    val sourceDisplayName: String,
    val sourceSizeBytes: Long = UNKNOWN_SOURCE_SIZE_BYTES,
) {
    private companion object {
        private const val UNKNOWN_SOURCE_SIZE_BYTES = -1L
    }
}

internal object PdfImportPerformancePolicy {
    fun memoryBudgetBytes(sourceSizeBytes: Long): Long =
        if (sourceSizeBytes in 0..SMALL_SOURCE_MAX_BYTES) {
            SMALL_SOURCE_MEMORY_BUDGET_BYTES
        } else {
            DEFAULT_MEMORY_BUDGET_BYTES
        }

    fun shouldSortByPosition(
        sourceSizeBytes: Long,
        pageCount: Int,
    ): Boolean =
        sourceSizeBytes in 0..POSITION_SORT_SOURCE_MAX_BYTES &&
            pageCount <= POSITION_SORT_PAGE_MAX

    private const val SMALL_SOURCE_MAX_BYTES = 4L * 1024L * 1024L
    private const val SMALL_SOURCE_MEMORY_BUDGET_BYTES = 32L * 1024L * 1024L
    private const val DEFAULT_MEMORY_BUDGET_BYTES = 16L * 1024L * 1024L
    private const val POSITION_SORT_SOURCE_MAX_BYTES = 1L * 1024L * 1024L
    private const val POSITION_SORT_PAGE_MAX = 80
}

internal data class ExtractedPdfDocument(val title: String?, val author: String?, val pages: List<String>,)

internal object PdfParserEngine {
    fun parse(
        document: PDDocument,
        request: PdfBookParseRequest,
        checkActive: () -> Unit = {},
    ): Book {
        require(!document.isEncrypted) { "Password-protected or encrypted PDFs are not supported" }
        val pageCount = document.numberOfPages
        require(pageCount in 1..MAX_PAGES) { "PDF page count is outside the supported range" }
        return buildBook(
            request = request,
            extracted =
            ExtractedPdfDocument(
                title = document.documentInformation?.title,
                author = document.documentInformation?.author,
                pages = extractPages(document, request.sourceSizeBytes, checkActive),
            ),
        )
    }

    internal fun buildBook(
        request: PdfBookParseRequest,
        extracted: ExtractedPdfDocument,
    ): Book {
        require(extracted.pages.sumOf { page -> page.length.toLong() } <= MAX_EXTRACTED_TEXT_CHARS) {
            "PDF contains too much extracted text"
        }
        val chapters = buildChapters(extracted.pages)
        require(chapters.any { hasReadableImportText(it.plainText) }) {
            "No selectable text was found. Scanned PDFs require OCR and are not supported yet."
        }
        val title =
            extracted.title?.normalizePdfMetadata()?.takeIf(String::isNotBlank)
                ?: request.sourceDisplayName.toPdfFilenameTitle()
        val authors =
            extracted.author
                ?.split(AUTHOR_SEPARATOR)
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                ?.distinct()
                .orEmpty()
        return Book(
            id = request.bookId,
            title = title,
            authors = authors,
            chapters = chapters,
        )
    }

    private fun extractPages(
        document: PDDocument,
        sourceSizeBytes: Long,
        checkActive: () -> Unit,
    ): List<String> {
        return splitExtractedPages(
            extractedText = extractBoundedPdfText(
                document = document,
                sortByPosition = PdfImportPerformancePolicy.shouldSortByPosition(
                    sourceSizeBytes = sourceSizeBytes,
                    pageCount = document.numberOfPages,
                ),
                pageSeparator = EXTRACTION_PAGE_SEPARATOR,
                maxChars = MAX_EXTRACTED_TEXT_CHARS,
                checkActive = checkActive,
            ),
            pageCount = document.numberOfPages,
        )
    }

    internal fun splitExtractedPages(
        extractedText: String,
        pageCount: Int,
    ): List<String> {
        val rawPages = extractedText.split(EXTRACTION_PAGE_SEPARATOR)
        return List(pageCount) { pageIndex ->
            normalizePdfPageText(rawPages.getOrElse(pageIndex) { "" })
        }
    }

    internal fun normalizePdfPageText(rawText: String): String {
        val lines =
            rawText
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\u0000", "")
                .lines()
                .map { line -> line.replace(HORIZONTAL_WHITESPACE, " ").trim() }
        val paragraphs = mutableListOf<String>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            paragraph.toString().trim().takeIf(String::isNotBlank)?.let(paragraphs::add)
            paragraph.clear()
        }

        lines.forEach { line ->
            if (line.isBlank()) {
                flushParagraph()
            } else if (paragraph.isEmpty()) {
                paragraph.append(line)
            } else if (paragraph.last() == '-' && line.firstOrNull()?.isLowerCase() == true) {
                paragraph.deleteCharAt(paragraph.lastIndex)
                paragraph.append(line)
            } else {
                paragraph.append(' ')
                paragraph.append(line)
            }
        }
        flushParagraph()
        return paragraphs.joinToString("\n\n")
    }

    private fun buildChapters(pages: List<String>): List<Chapter> =
        pages.chunked(PAGES_PER_CHAPTER).mapIndexedNotNull { chapterIndex, pageGroup ->
            val firstPage = chapterIndex * PAGES_PER_CHAPTER + 1
            val lastPage = firstPage + pageGroup.lastIndex
            val readablePages = pageGroup.filter(String::isNotBlank)
            if (readablePages.isEmpty()) return@mapIndexedNotNull null
            val plainText = readablePages.joinToString(PAGE_BREAK_MARKER.toString())
            val html =
                readablePages.mapIndexed { index, page ->
                    val pageHtml =
                        page.split(PARAGRAPH_SEPARATOR)
                            .filter(String::isNotBlank)
                            .joinToString("\n") { paragraph -> "<p>${paragraph.escapeBookHtml()}</p>" }
                    if (index == readablePages.lastIndex) {
                        pageHtml
                    } else {
                        "$pageHtml\n<span epub:type=\"pagebreak\"></span>"
                    }
                }.joinToString("\n")
            Chapter(
                index = chapterIndex,
                title = if (firstPage == lastPage) "Page $firstPage" else "Pages $firstPage–$lastPage",
                htmlContent = html,
                plainText = plainText,
                wordCount = countWords(plainText),
            )
        }.mapIndexed { index, chapter -> chapter.copy(index = index) }

    private fun String.normalizePdfMetadata(): String = replace(Regex("\\s+"), " ").trim()

    private fun String.toPdfFilenameTitle(): String =
        substringBeforeLast('.', this)
            .replace('_', ' ')
            .replace('-', ' ')
            .normalizePdfMetadata()
            .ifBlank { DEFAULT_TITLE }

    private const val DEFAULT_TITLE = "PDF import"
    private const val MAX_PAGES = 5000
    private const val PAGES_PER_CHAPTER = 10
    private const val MAX_EXTRACTED_TEXT_CHARS = 16L * 1024L * 1024L
    private const val PAGE_BREAK_MARKER = '\u000C'
    private const val EXTRACTION_PAGE_SEPARATOR = "\u0000KAIRO_PDF_PAGE\u0000"
    private val HORIZONTAL_WHITESPACE = Regex("[\\t\\x0B\\f ]+")
    private val PARAGRAPH_SEPARATOR = Regex("\\n{2,}")
    private val AUTHOR_SEPARATOR = Regex("\\s*(?:;|,|\\band\\b)\\s*", RegexOption.IGNORE_CASE)
}
