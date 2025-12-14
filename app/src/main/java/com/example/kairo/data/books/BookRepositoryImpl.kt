package com.example.kairo.data.books

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import com.example.kairo.data.local.BookDao
import com.example.kairo.data.local.toDomain
import com.example.kairo.data.local.toEntity
import com.example.kairo.sample.SampleBooks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream

class BookRepositoryImpl(
    private val bookDao: BookDao,
    private val parsers: List<BookParser>,
    private val appContext: android.content.Context
) : BookRepository {

    // Mutex to prevent concurrent import operations which can crash the app
    private val importMutex = Mutex()

    override suspend fun importBook(uri: Uri): Book = importMutex.withLock {
        val extension = resolveExtension(uri)

        val parser = parsers.firstOrNull { it.supports(extension) }
            ?: throw IllegalArgumentException("No parser found for .$extension files")

        // Parse the book - let errors propagate for proper error handling
        val parsedBook = parser.parse(appContext, uri)
        val book = parsedBook.copy(coverImage = optimizeCoverForDb(parsedBook.coverImage))

        // Save to database
        bookDao.insertBook(book.toEntity(), book.chapters.map { it.toEntity(book.id) })
        return@withLock book
    }

    private fun resolveExtension(uri: Uri): String {
        // Try to get extension from the display name (most reliable for file pickers)
        val displayName = try {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            null
        }

        if (displayName != null) {
            val ext = displayName.substringAfterLast('.', "").lowercase()
            if (ext.isNotEmpty()) {
                return ext
            }
        }

        // Check the MIME type
        val mime = appContext.contentResolver.getType(uri)?.lowercase().orEmpty()
        when {
            mime.contains("epub") || mime == "application/epub+zip" -> return "epub"
            mime.contains("mobi") || mime.contains("x-mobipocket") -> return "mobi"
        }

        // Try path segment as fallback
        val pathExt = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (pathExt.isNotEmpty()) {
            return pathExt
        }

        // Default to epub for unknown files from file picker
        return "epub"
    }

    override suspend fun getBook(bookId: BookId): Book {
        val bookEntity = requireNotNull(bookDao.getBook(bookId.value)) { "Book not found" }
        val chapters = bookDao.getChapters(bookId.value)
        return bookEntity.toDomain(chapters)
    }

    override suspend fun getChapter(bookId: BookId, chapterIndex: Int): Chapter {
        val entity =
            requireNotNull(bookDao.getChapter(bookId.value, chapterIndex)) { "Chapter missing" }
        return entity.toDomain()
    }

    override fun observeBooks(): Flow<List<Book>> = bookDao.getBooks().map { entities ->
        entities.map { bookEntity ->
            val chapters = bookDao.getChapters(bookEntity.id)
            bookEntity.toDomain(chapters)
        }
    }

    private fun optimizeCoverForDb(coverImage: ByteArray?): ByteArray? {
        if (coverImage == null || coverImage.isEmpty()) return coverImage

        // CursorWindow on many devices is ~2MB; keep cover comfortably under that.
        if (coverImage.size <= MAX_COVER_DB_BYTES) return coverImage

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(coverImage, 0, coverImage.size, bounds)

            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return@runCatching null

            val sampleSize = calculateInSampleSize(width, height, COVER_MAX_DIM_PX)
            val decode = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(coverImage, 0, coverImage.size, decode) ?: return@runCatching null

            try {
                val out = ByteArrayOutputStream()
                var quality = 90
                var encoded: ByteArray
                do {
                    out.reset()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    encoded = out.toByteArray()
                    quality -= 10
                } while (encoded.size > MAX_COVER_DB_BYTES && quality >= MIN_COVER_JPEG_QUALITY)
                encoded
            } finally {
                bitmap.recycle()
            }
        }.getOrNull() ?: run {
            // If we can't safely shrink it, drop the cover rather than crash on read.
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimPx: Int): Int {
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
        private const val MAX_COVER_DB_BYTES = 512 * 1024
        private const val COVER_MAX_DIM_PX = 1200
        private const val MIN_COVER_JPEG_QUALITY = 60
    }
}
