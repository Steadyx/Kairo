package com.kairo.reader.data.local

import androidx.room.Dao
import androidx.room.Query

data class SearchPassageBookEntity(val bookId: String, val bookTitle: String,)

data class SearchPassageChapterPageEntity(val chapterIndex: Int, val chapterTitle: String?, val plainText: String,)

@Dao
interface SearchDao {
    @Query(
        """
        SELECT
            books.id AS bookId,
            books.title AS bookTitle
        FROM books
        WHERE (:bookId IS NULL OR books.id = :bookId)
          AND EXISTS (SELECT 1 FROM chapters WHERE chapters.bookId = books.id)
        ORDER BY lower(books.title), books.id
        """,
    )
    suspend fun searchPassageBooks(bookId: String?): List<SearchPassageBookEntity>

    @Query(
        """
        SELECT
            `index` AS chapterIndex,
            title AS chapterTitle,
            plainText
        FROM chapters
        WHERE bookId = :bookId AND `index` > :afterChapterIndex
        ORDER BY `index`
        LIMIT :pageSize
        """,
    )
    suspend fun searchPassageChapterPage(
        bookId: String,
        afterChapterIndex: Int,
        pageSize: Int,
    ): List<SearchPassageChapterPageEntity>

    @Query(
        """
        SELECT id, title, authors, languageTag, NULL AS coverImage, isCompleted, importFingerprint
        FROM books
        WHERE lower(title) LIKE :pattern ESCAPE '\'
           OR lower(authors) LIKE :pattern ESCAPE '\'
        ORDER BY lower(title)
        LIMIT :limit
        """,
    )
    suspend fun searchBooks(
        pattern: String,
        limit: Int,
    ): List<BookEntity>
}
