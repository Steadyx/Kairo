package com.example.kairo.data.books

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.countWords
import com.example.kairo.data.local.BookDao
import com.example.kairo.data.local.toDomain
import com.example.kairo.data.local.toEntity
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BookRepositoryImpl(
    private val bookDao: BookDao,
    private val parsers: List<BookParser>,
    private val appContext: android.content.Context,
) : BookRepository {
    // Mutex to prevent concurrent import operations which can crash the app
    private val importMutex = Mutex()

    override suspend fun importBook(uri: Uri): Book =
        importMutex.withLock {
            val extension = resolveExtension(uri)

            val parser =
                parsers.firstOrNull { it.supports(extension) }
                    ?: throw IllegalArgumentException("No parser found for .$extension files")

            // Parse the book - let errors propagate for proper error handling
            val parsedBook = parser.parse(appContext, uri)
            val book =
                parsedBook.copy(
                    coverImage = optimizeCoverForDb(parsedBook.coverImage),
                    chapters =
                    parsedBook.chapters.map { chapter ->
                        if (chapter.wordCount > 0) {
                            chapter
                        } else {
                            chapter.copy(wordCount = countWords(chapter.plainText))
                        }
                    },
                )

            // Save to database
            bookDao.insertBook(book.toEntity(), book.chapters.map { it.toEntity(book.id) })
            return@withLock book
        }

    private fun resolveExtension(uri: Uri): String {
        // Try to get extension from the display name (most reliable for file pickers)
        val displayName =
            runCatching {
                appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME
                    )
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else {
                        null
                    }
                }
            }.getOrNull()

        val extFromDisplay =
            displayName
                ?.substringAfterLast('.', "")
                ?.lowercase()
                .orEmpty()

        // Check the MIME type
        val mime =
            appContext.contentResolver
                .getType(uri)
                ?.lowercase()
                .orEmpty()
        val extFromMime =
            when {
                mime.contains("epub") || mime == "application/epub+zip" -> "epub"
                mime.contains("mobi") || mime.contains("x-mobipocket") -> "mobi"
                else -> ""
            }

        // Try path segment as fallback
        val pathExt =
            uri.lastPathSegment
                ?.substringAfterLast('.', "")
                ?.lowercase()
                .orEmpty()

        return when {
            extFromDisplay.isNotEmpty() -> extFromDisplay
            extFromMime.isNotEmpty() -> extFromMime
            pathExt.isNotEmpty() -> pathExt
            else -> DEFAULT_EXTENSION
        }
    }

    override suspend fun getBook(bookId: BookId): Book {
        val bookEntity = requireNotNull(bookDao.getBook(bookId.value)) { "Book not found" }
        val chapters = bookDao.getChapters(bookId.value)
        return bookEntity.toDomain(chapters)
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

    override fun observeBooks(): Flow<List<Book>> =
        bookDao.getBooks().map { entities ->
            entities.map { bookEntity ->
                val chapters = bookDao.getChapters(bookEntity.id)
                bookEntity.toDomain(chapters)
            }
        }

    private fun optimizeCoverForDb(coverImage: ByteArray?): ByteArray? {
        if (coverImage == null || coverImage.isEmpty()) return coverImage

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(coverImage, 0, coverImage.size, bounds)

            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return@runCatching null

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
                    ?: return@runCatching null

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
        }.getOrNull() ?: null
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
        private const val DEFAULT_EXTENSION = "epub"
        private const val MAX_COVER_DB_BYTES = 256 * 1024
        private const val COVER_MAX_DIM_PX = 1080
        private const val INITIAL_COVER_JPEG_QUALITY = 90
        private const val JPEG_QUALITY_STEP = 10
        private const val MIN_COVER_JPEG_QUALITY = 60
    }
}
