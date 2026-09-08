package com.kairo.reader.data.books

import android.net.Uri
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    suspend fun importBook(uri: Uri): BookImportResult

    suspend fun importUrl(rawUrl: String): BookImportResult

    suspend fun importText(request: TextImportRequest): BookImportResult

    /** Loads metadata, chapter summaries and navigation; use [getChapter] for chapter content. */
    suspend fun getBook(bookId: BookId): Book

    suspend fun getChapter(
        bookId: BookId,
        chapterIndex: Int,
    ): Chapter

    suspend fun updateChapterWordCount(
        bookId: BookId,
        chapterIndex: Int,
        wordCount: Int,
    )

    suspend fun getBookLanguageTag(bookId: BookId): String?

    fun observeBooks(): Flow<List<Book>>
}
