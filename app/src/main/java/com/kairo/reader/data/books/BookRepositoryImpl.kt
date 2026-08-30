package com.kairo.reader.data.books

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.language.BookLanguageResolver
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.countWords
import com.kairo.reader.data.local.BookDao
import com.kairo.reader.data.local.BookEntity
import com.kairo.reader.data.local.EpubChapterCoordinate
import com.kairo.reader.data.local.EpubNavigationDao
import com.kairo.reader.data.local.toDomain
import com.kairo.reader.data.local.toEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BookRepositoryImpl(
    private val bookDao: BookDao,
    private val epubNavigationDao: EpubNavigationDao,
    private val parsers: List<BookParser>,
    private val webArticleExtractor: WebArticleExtractor,
    private val appContext: android.content.Context,
    private val dispatcherProvider: DispatcherProvider,
) : BookRepository {
    // Mutex to prevent concurrent import operations which can crash the app
    private val importMutex = Mutex()
    private val persistedNavigationRepairResolver = PersistedEpubNavigationRepairResolver()
    private val persistedNavigationContentRewriter = EpubContentRewriter()

    override suspend fun importBook(uri: Uri): BookImportResult =
        importMutex.withLock {
            val extensionCandidates = resolveExtensionCandidates(uri)
            val supportedExtensionList = extensionCandidates.joinToString { extension -> ".$extension" }
            val provisionalExtension =
                findParserMatch(extensionCandidates)?.second ?: throw IllegalArgumentException(
                    "No parser found for $supportedExtensionList files"
                )

            val importSource = prepareImportSource(uri, provisionalExtension)
            try {
                val detectedExtension = BookImportFormatDetector.detect(appContext, importSource.parseUri)
                val parserCandidates =
                    (listOfNotNull(detectedExtension) + extensionCandidates).distinct()
                val (parser, extension) =
                    findParserMatch(parserCandidates) ?: throw IllegalArgumentException(
                        "No parser found for $supportedExtensionList files"
                    )
                val sourceFingerprint =
                    importSource.sourceFingerprint?.let { fingerprint ->
                        ImportFingerprint.withSourceExtension(fingerprint, extension)
                    }
                val existingByFingerprint =
                    sourceFingerprint?.let { fingerprint ->
                        bookDao.getBookByImportFingerprint(fingerprint)
                    }
                if (existingByFingerprint != null) {
                    val navigationRepaired =
                        repairPersistedEpubNavigation(
                            bookId = existingByFingerprint.id,
                            extension = extension,
                        )
                    if (extension in BookImportFormats.epub.extensions &&
                        bookDao.getTableOfContentsEntries(existingByFingerprint.id).isEmpty()
                    ) {
                        refreshMissingEpubTableOfContents(
                            existing = existingByFingerprint,
                            parser = parser,
                            importSource = importSource,
                        )?.let { refreshed -> return@withLock refreshed }
                    }
                    return@withLock existingBookImportResult(
                        existing = existingByFingerprint,
                        includeChapterContent = navigationRepaired,
                    )
                }

                val bookId =
                    sourceFingerprint?.let(ImportFingerprint::bookIdForFingerprint)
                        ?: BookId(UUID.randomUUID().toString())
                var importCompleted = false
                try {
                    val parsedBook =
                        parser.parse(
                            context = appContext,
                            uri = importSource.parseUri,
                            bookId = bookId,
                            sourceDisplayName = importSource.sourceDisplayName,
                        )
                    val result = persistImportedBook(parsedBook, sourceFingerprint)
                    importCompleted = true
                    return@withLock result
                } finally {
                    if (!importCompleted) {
                        deleteBookAssets(bookId.value)
                    }
                }
            } finally {
                importSource.deleteTempFile()
            }
        }

    private suspend fun repairPersistedEpubNavigation(
        bookId: String,
        extension: String,
    ): Boolean {
        if (extension !in BookImportFormats.epub.extensions) return false
        val candidates =
            epubNavigationDao.getMarkerlessNavigationCandidates(
                bookId = bookId,
                canonicalMarker = EpubReaderNavigationContent.MARKER,
                maxHtmlCharacters = MAX_PERSISTED_NAVIGATION_HTML_CHARACTERS,
                limit = MAX_LEGACY_NAVIGATION_CANDIDATES + 1,
            )
        if (candidates.isEmpty() || candidates.size > MAX_LEGACY_NAVIGATION_CANDIDATES) return false
        val validChapterIndexes =
            epubNavigationDao.getChapterCoordinates(bookId).mapTo(mutableSetOf()) { coordinate ->
                coordinate.chapterIndex
            }
        val repair =
            persistedNavigationRepairResolver.resolve(candidates, validChapterIndexes) ?: return false
        val candidate = repair.candidate
        val canonicalHtml = repair.canonicalHtml
        val plainText = persistedNavigationContentRewriter.extractPlainText(canonicalHtml)
        if (plainText.isBlank()) return false
        return epubNavigationDao.canonicalizeLegacyNavigationChapter(
            bookId = bookId,
            chapterIndex = candidate.chapterIndex,
            expectedHtmlContent = candidate.htmlContent,
            htmlContent = canonicalHtml,
            plainText = plainText,
            wordCount = countWords(plainText),
        )
    }

    private suspend fun refreshMissingEpubTableOfContents(
        existing: BookEntity,
        parser: BookParser,
        importSource: PreparedImportSource,
    ): BookImportResult? {
        val existingChapters =
            bookDao.getChaptersWithContent(existing.id).map { chapter -> chapter.toDomain() }
        val probeBookId = BookId(UUID.randomUUID().toString())
        val probeBook =
            try {
                parser.parse(
                    context = appContext,
                    uri = importSource.parseUri,
                    bookId = probeBookId,
                    sourceDisplayName = importSource.sourceDisplayName,
                )
            } finally {
                deleteBookAssets(probeBookId.value)
            }
        if (probeBook.tableOfContents.isEmpty()) return null
        if (!hasCompatibleEpubTocCoordinates(
                existingChapters = existingChapters,
                probedChapters = probeBook.chapters,
                probedTableOfContents = probeBook.tableOfContents,
            )
        ) {
            return null
        }

        val existingBookId = BookId(existing.id)
        val expectedCoordinates =
            existingChapters.map { chapter ->
                EpubChapterCoordinate(chapterIndex = chapter.index, plainText = chapter.plainText)
            }
        val entries =
            probeBook.tableOfContents.mapIndexed { index, entry ->
                entry.toEntity(existingBookId, index)
            }
        if (!epubNavigationDao.replaceTableOfContentsIfCoordinatesMatch(
                bookId = existing.id,
                expectedCoordinates = expectedCoordinates,
                entries = entries,
            )
        ) {
            return null
        }
        return existingBookImportResult(existing, includeChapterContent = false)
    }

    private suspend fun existingBookImportResult(
        existing: BookEntity,
        includeChapterContent: Boolean,
    ): BookImportResult =
        BookImportResult(
            book =
            existing.toDomain(
                chapters =
                if (includeChapterContent) {
                    bookDao.getChaptersWithContent(existing.id)
                } else {
                    bookDao.getChapters(existing.id)
                },
                tableOfContentsEntries = bookDao.getTableOfContentsEntries(existing.id),
            ),
            alreadyImported = true,
        )

    private fun findParserMatch(extensionCandidates: List<String>): Pair<BookParser, String>? =
        extensionCandidates.firstNotNullOfOrNull { extension ->
            parsers.firstOrNull { parser -> parser.supports(extension) }
                ?.let { parser -> parser to extension }
        }

    override suspend fun importUrl(rawUrl: String): BookImportResult =
        importMutex.withLock {
            val normalizedUrl = WebArticleUrl.normalize(rawUrl)
            val sourceFingerprint = ImportFingerprint.webUrlFingerprint(normalizedUrl)
            bookDao.getBookByImportFingerprint(sourceFingerprint)?.let { existing ->
                return@withLock BookImportResult(
                    book =
                    existing.toDomain(
                        chapters = bookDao.getChapters(existing.id),
                        tableOfContentsEntries = bookDao.getTableOfContentsEntries(existing.id),
                    ),
                    alreadyImported = true,
                )
            }

            val bookId = ImportFingerprint.bookIdForFingerprint(sourceFingerprint)
            val parsedBook = webArticleExtractor.extract(normalizedUrl, bookId)
            return@withLock persistImportedBook(parsedBook, sourceFingerprint)
        }

    override suspend fun importText(request: TextImportRequest): BookImportResult =
        importMutex.withLock {
            val parsedText = TextImportParser.parse(request)
            val sourceFingerprint = ImportFingerprint.textFingerprint(parsedText.plainText)
            bookDao.getBookByImportFingerprint(sourceFingerprint)?.let { existing ->
                return@withLock BookImportResult(
                    book =
                    existing.toDomain(
                        chapters = bookDao.getChapters(existing.id),
                        tableOfContentsEntries = bookDao.getTableOfContentsEntries(existing.id),
                    ),
                    alreadyImported = true,
                )
            }

            val bookId = ImportFingerprint.bookIdForFingerprint(sourceFingerprint)
            persistImportedBook(parsedText.toBook(bookId), sourceFingerprint)
        }

    private fun prepareImportSource(
        uri: Uri,
        extension: String,
    ): PreparedImportSource {
        val sourceDisplayName = resolveDisplayName(uri)
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            stageImportSource(uri, extension, sourceDisplayName)?.let { return it }
        }

        return PreparedImportSource(
            parseUri = uri,
            sourceFingerprint = resolveSourceFingerprint(uri, extension),
            sourceDisplayName = sourceDisplayName,
            tempFile = null,
        )
    }

    private fun stageImportSource(
        uri: Uri,
        extension: String,
        sourceDisplayName: String?,
    ): PreparedImportSource? {
        val importDir = File(appContext.cacheDir, IMPORT_CACHE_DIR_NAME)
        if (!importDir.exists() && !importDir.mkdirs()) {
            return null
        }

        var tempFile: File? = null
        return runCatching {
            pruneStaleImportCache(importDir)

            val stagedFile =
                File.createTempFile(
                    IMPORT_CACHE_FILE_PREFIX,
                    ".${sanitizeImportExtension(extension)}",
                    importDir,
                )
            tempFile = stagedFile

            val sourceInput = appContext.contentResolver.openInputStream(uri)
            if (sourceInput == null) {
                stagedFile.delete()
                null
            } else {
                val sourceFingerprint =
                    sourceInput.use { input ->
                        stagedFile.outputStream().use { output ->
                            ImportFingerprint.sourceFingerprint(extension, input, output)
                        }
                    }
                PreparedImportSource(
                    parseUri = Uri.fromFile(stagedFile),
                    sourceFingerprint = sourceFingerprint,
                    sourceDisplayName = sourceDisplayName,
                    tempFile = stagedFile,
                )
            }
        }.getOrElse {
            tempFile?.delete()
            null
        }
    }

    private fun resolveSourceFingerprint(
        uri: Uri,
        extension: String,
    ): String? =
        runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                ImportFingerprint.sourceFingerprint(extension, input)
            }
        }.getOrNull()

    private fun resolveDisplayName(uri: Uri): String? =
        runCatching {
            appContext.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else {
                        null
                    }
                }
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

    private suspend fun findExistingDuplicate(
        parsedBook: Book,
        sourceFingerprint: String?,
    ): Book? {
        val candidates = bookDao.getBooksByTitleForImportDedupe(parsedBook.title)
        if (candidates.isEmpty()) return null

        var parsedFingerprint: String? = null
        candidates.forEach { candidate ->
            if (candidate.id == parsedBook.id.value) return@forEach
            if (candidate.authors != parsedBook.authors) return@forEach

            val candidateChapters = bookDao.getChaptersWithContent(candidate.id)
            if (candidateChapters.size != parsedBook.chapters.size) return@forEach

            val candidateBook =
                candidate.toDomain(
                    chapters = candidateChapters,
                    tableOfContentsEntries = bookDao.getTableOfContentsEntries(candidate.id),
                )
            val contentFingerprint =
                parsedFingerprint ?: ImportFingerprint.contentFingerprint(parsedBook).also {
                    parsedFingerprint = it
                }
            if (ImportFingerprint.contentFingerprint(candidateBook) == contentFingerprint) {
                sourceFingerprint?.let { fingerprint ->
                    runCatching {
                        bookDao.setImportFingerprintIfEmpty(candidate.id, fingerprint)
                    }
                }
                return candidateBook
            }
        }
        return null
    }

    private suspend fun persistImportedBook(
        parsedBook: Book,
        sourceFingerprint: String?,
    ): BookImportResult {
        requireReadableImportContent(parsedBook)
        val resolvedLanguageTag = BookLanguageResolver.resolve(parsedBook)
        val book =
            parsedBook.copy(
                languageTag = resolvedLanguageTag,
                coverImage = optimizeCoverForDb(parsedBook.coverImage),
                chapters =
                parsedBook.chapters.map { chapter ->
                    if (chapter.wordCount > 0) {
                        chapter
                    } else if (chapter.plainText.length <= MAX_WORD_COUNT_CHARS) {
                        chapter.copy(wordCount = countWords(chapter.plainText))
                    } else {
                        // Defer heavy word counts for very large chapters.
                        chapter
                    }
                },
            )
        findExistingDuplicate(
            parsedBook = book,
            sourceFingerprint = sourceFingerprint,
        )?.let { existing ->
            deleteBookAssets(book.id.value)
            return BookImportResult(
                book = existing,
                alreadyImported = true,
            )
        }

        bookDao.insertBook(
            book.toEntity(importFingerprint = sourceFingerprint),
            book.chapters.map { it.toEntity(book.id) },
            book.tableOfContents.mapIndexed { index, entry -> entry.toEntity(book.id, index) },
        )
        return BookImportResult(
            book = book,
            alreadyImported = false,
        )
    }

    private fun deleteBookAssets(bookId: String) {
        BookImportFormats.assetRootNames.forEach { rootName ->
            runCatching {
                File(appContext.filesDir, "$rootName/$bookId").deleteRecursively()
            }
        }
    }

    private fun resolveExtensionCandidates(uri: Uri): List<String> {
        val displayName = resolveDisplayName(uri)

        val extensionsFromDisplay = BookImportFormats.extensionsForDisplayName(displayName)

        // Check the MIME type
        val mime =
            appContext.contentResolver
                .getType(uri)
                ?.lowercase()
                .orEmpty()
        val extensionFromMime = BookImportFormats.extensionForMimeType(mime)

        // Try path segment as fallback
        val extensionsFromPath = BookImportFormats.extensionsForDisplayName(uri.lastPathSegment)

        return (extensionsFromDisplay + extensionFromMime + extensionsFromPath + DEFAULT_EXTENSION)
            .filterNotNull()
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun requireReadableImportContent(book: Book) {
        require(book.chapters.any { chapter -> hasReadableImportText(chapter.plainText) }) {
            "No readable content found in this import"
        }
    }

    private fun hasReadableImportText(text: String): Boolean {
        val normalized = text.trim()
        if (normalized in UNREADABLE_IMPORT_PLACEHOLDERS) return false

        var words = 0
        var inWord = false
        var index = 0
        while (index < normalized.length) {
            val codePoint = Character.codePointAt(normalized, index)
            if (Character.isLetterOrDigit(codePoint)) {
                if (!inWord) {
                    words += 1
                    if (words >= MIN_READABLE_IMPORT_WORDS) return true
                }
                inWord = true
            } else {
                inWord = false
            }
            index += Character.charCount(codePoint)
        }
        return false
    }

    override suspend fun getBook(bookId: BookId): Book {
        val bookEntity = requireNotNull(bookDao.getBook(bookId.value)) { "Book not found" }
        val chapters = bookDao.getChaptersWithContent(bookId.value)
        val tableOfContentsEntries = bookDao.getTableOfContentsEntries(bookId.value)
        return bookEntity.toDomain(chapters, tableOfContentsEntries)
    }

    override suspend fun getChapter(
        bookId: BookId,
        chapterIndex: Int,
    ): Chapter {
        val entity =
            requireNotNull(bookDao.getChapter(bookId.value, chapterIndex)) { "Chapter missing" }
        return entity.toDomain()
    }

    override suspend fun updateChapterWordCount(
        bookId: BookId,
        chapterIndex: Int,
        wordCount: Int,
    ) {
        if (wordCount <= 0) return
        bookDao.updateChapterWordCount(bookId.value, chapterIndex, wordCount)
    }

    override suspend fun getBookLanguageTag(bookId: BookId): String? =
        bookDao.getBookLanguageTag(bookId.value)

    override fun observeBooks(): Flow<List<Book>> =
        combine(
            bookDao.getBooks(),
            bookDao.getChapterSummaries(),
            bookDao.getTableOfContentsEntries(),
        ) { entities, chapters, tableOfContentsEntries ->
            val chaptersByBookId = chapters.groupBy { it.bookId }
            val tableOfContentsByBookId = tableOfContentsEntries.groupBy { it.bookId }
            entities.map { bookEntity ->
                val chapters = chaptersByBookId[bookEntity.id].orEmpty()
                val tableOfContents = tableOfContentsByBookId[bookEntity.id].orEmpty()
                bookEntity.toDomain(chapters, tableOfContents)
            }
        }.flowOn(dispatcherProvider.default)

    private fun optimizeCoverForDb(coverImage: ByteArray?): ByteArray? {
        if (coverImage == null || coverImage.isEmpty()) return coverImage
        if (coverImage.size <= MAX_COVER_DB_BYTES) return coverImage

        val safeFallback =
            coverImage.takeIf { it.size <= MAX_COVER_DB_BYTES }

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(coverImage, 0, coverImage.size, bounds)

            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return@runCatching safeFallback

            // CursorWindow on many devices is ~2MB; keep cover comfortably under that, and also
            // cap pixel dimensions so first-time decode/render is fast.
            val shouldOptimize =
                coverImage.size > MAX_COVER_DB_BYTES ||
                    width > COVER_MAX_DIM_PX ||
                    height > COVER_MAX_DIM_PX
            if (!shouldOptimize) return@runCatching coverImage

            val sampleSize = calculateInSampleSize(width, height, COVER_MAX_DIM_PX)
            val decode =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            val bitmap =
                BitmapFactory.decodeByteArray(coverImage, 0, coverImage.size, decode)
                    ?: return@runCatching safeFallback

            try {
                val out = ByteArrayOutputStream()
                var quality = INITIAL_COVER_JPEG_QUALITY
                var encoded: ByteArray
                do {
                    out.reset()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    encoded = out.toByteArray()
                    quality -= JPEG_QUALITY_STEP
                } while (encoded.size > MAX_COVER_DB_BYTES && quality >= MIN_COVER_JPEG_QUALITY)
                encoded
            } finally {
                bitmap.recycle()
            }
        }.getOrNull() ?: safeFallback
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimPx: Int,
    ): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (w > maxDimPx || h > maxDimPx) {
            w /= 2
            h /= 2
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private companion object {
        const val MAX_LEGACY_NAVIGATION_CANDIDATES = 16
        const val MAX_PERSISTED_NAVIGATION_HTML_CHARACTERS = 5 * 1024 * 1024
        private const val DEFAULT_EXTENSION = "epub"
        private const val IMPORT_CACHE_DIR_NAME = "book_imports"
        private const val IMPORT_CACHE_FILE_PREFIX = "kairo-import-"
        private const val IMPORT_CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1000L
        private const val MAX_IMPORT_EXTENSION_LENGTH = 16
        private const val MAX_COVER_DB_BYTES = 256 * 1024
        private const val COVER_MAX_DIM_PX = 1080
        private const val INITIAL_COVER_JPEG_QUALITY = 90
        private const val JPEG_QUALITY_STEP = 10
        private const val MIN_COVER_JPEG_QUALITY = 60
        private const val MAX_WORD_COUNT_CHARS = 120_000
        private const val MIN_READABLE_IMPORT_WORDS = 5
        private val UNREADABLE_IMPORT_PLACEHOLDERS =
            setOf(
                "No readable content found.",
                "No readable content found in this EPUB.",
            )
    }

    private data class PreparedImportSource(
        val parseUri: Uri,
        val sourceFingerprint: String?,
        val sourceDisplayName: String?,
        val tempFile: File?,
    ) {
        fun deleteTempFile() {
            tempFile?.delete()
        }
    }

    private fun sanitizeImportExtension(extension: String): String =
        extension
            .lowercase()
            .filter { it.isLetterOrDigit() }
            .take(MAX_IMPORT_EXTENSION_LENGTH)
            .takeIf { it.isNotBlank() }
            ?: DEFAULT_EXTENSION

    private fun pruneStaleImportCache(directory: File) {
        val cutoff = System.currentTimeMillis() - IMPORT_CACHE_MAX_AGE_MS
        directory.listFiles()?.forEach { file ->
            if (
                file.isFile &&
                file.name.startsWith(IMPORT_CACHE_FILE_PREFIX) &&
                file.lastModified() < cutoff
            ) {
                file.delete()
            }
        }
    }
}
