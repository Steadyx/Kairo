package com.example.kairo.data.books

import android.net.Uri
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
        val book = parser.parse(appContext, uri)

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
}
