package com.example.kairo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Transaction
    suspend fun insertBook(
        book: BookEntity,
        chapters: List<ChapterEntity>,
    ) {
        insertBookInternal(book)
        insertChapters(chapters)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookInternal(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Query(
        """
        SELECT books.id, books.title, books.authors,
               CASE
                   WHEN coverImage IS NOT NULL AND length(coverImage) <= 1900000 THEN coverImage
                   ELSE NULL
               END AS coverImage
        FROM books
        LEFT JOIN chapters ON chapters.bookId = books.id
        GROUP BY books.id
        """,
    )
    fun getBooks(): Flow<List<BookEntity>>

    @Query(
        """
        SELECT id, title, authors,
               CASE
                   WHEN coverImage IS NOT NULL AND length(coverImage) <= 1900000 THEN coverImage
                   ELSE NULL
               END AS coverImage
        FROM books
        WHERE id = :bookId
        LIMIT 1
        """,
    )
    suspend fun getBook(bookId: String): BookEntity?

    @Query(
        """
        SELECT id, title, authors,
               CASE
                   WHEN coverImage IS NOT NULL AND length(coverImage) <= 1900000 THEN coverImage
                   ELSE NULL
               END AS coverImage
        FROM books
        LIMIT 1
        """,
    )
    suspend fun peekBook(): BookEntity?

    @Query(
        """
        SELECT bookId, `index`, title, '' AS htmlContent, '' AS plainText, imagePaths, wordCount
        FROM chapters
        WHERE bookId = :bookId
        ORDER BY `index`
        """,
    )
    suspend fun getChapters(bookId: String): List<ChapterEntity>

    @Query(
        """
        SELECT bookId, `index`, title, htmlContent, plainText, imagePaths, wordCount
        FROM chapters
        WHERE bookId = :bookId AND `index` = :index
        LIMIT 1
        """,
    )
    suspend fun getChapter(
        bookId: String,
        index: Int,
    ): ChapterEntity?

    @Query(
        """
        UPDATE chapters
        SET wordCount = :wordCount
        WHERE bookId = :bookId AND `index` = :index AND wordCount <= 0
        """,
    )
    suspend fun updateChapterWordCount(
        bookId: String,
        index: Int,
        wordCount: Int,
    )

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteChaptersForBook(bookId: String)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: String)
}
