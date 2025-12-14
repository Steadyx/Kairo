package com.example.kairo.data.library

import android.net.Uri
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.data.books.BookRepository
import com.example.kairo.data.local.BookDao
import com.example.kairo.data.local.BookmarkDao
import com.example.kairo.data.local.ReadingPositionDao
import kotlinx.coroutines.flow.Flow

class LibraryRepositoryImpl(
    private val bookRepository: BookRepository,
    private val bookDao: BookDao,
    private val positionDao: ReadingPositionDao,
    private val bookmarkDao: BookmarkDao
) : LibraryRepository {
    override fun observeLibrary(): Flow<List<Book>> = bookRepository.observeBooks()

    override suspend fun import(uri: Uri): Book {
        // Don't silently swallow errors - let them propagate so UI can show error message
        return bookRepository.importBook(uri)
    }

    override suspend fun delete(bookId: String) {
        bookDao.deleteChaptersForBook(bookId)
        bookDao.deleteBook(bookId)
        positionDao.deleteForBook(bookId)
        bookmarkDao.deleteForBook(bookId)
    }
}
