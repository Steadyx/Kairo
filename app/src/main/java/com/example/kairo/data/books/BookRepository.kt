package com.example.kairo.data.books

import android.net.Uri
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    suspend fun importBook(uri: Uri): Book

    suspend fun getBook(bookId: BookId): Book

    suspend fun getChapter(
        bookId: BookId,
        chapterIndex: Int,
    ): Chapter

    fun observeBooks(): Flow<List<Book>>
}
