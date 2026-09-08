package com.kairo.reader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kairo.reader.core.tokenization.CHAPTER_WORD_COUNT_VERSION
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Transaction
    suspend fun insertBook(
        book: BookEntity,
        chapters: List<ChapterEntity>,
        tableOfContentsEntries: List<TableOfContentsEntryEntity>,
    ) {
        val inserted = insertBookIfAbsent(book)
        if (inserted == INSERT_CONFLICT) {
            val existingCompleted = getCompletedForUpdate(book.id) ?: book.isCompleted
            check(updateBookInternal(book.copy(isCompleted = existingCompleted)) == 1) {
                "Book metadata update failed for ${book.id}"
            }
        }
        deleteChaptersForBook(book.id)
        deleteTableOfContentsForBook(book.id)
        insertChapters(chapters)
        if (tableOfContentsEntries.isNotEmpty()) {
            insertTableOfContentsEntries(tableOfContentsEntries)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookIfAbsent(book: BookEntity): Long

    @Update
    suspend fun updateBookInternal(book: BookEntity): Int

    @Query("SELECT isCompleted FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getCompletedForUpdate(bookId: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTableOfContentsEntries(entries: List<TableOfContentsEntryEntity>)

    @Query(
        """
        SELECT books.id, books.title, books.authors, books.languageTag,
               CASE
                   WHEN coverImage IS NOT NULL AND length(coverImage) <= 1900000 THEN coverImage
                   ELSE NULL
               END AS coverImage,
               books.isCompleted AS isCompleted,
               books.importFingerprint AS importFingerprint
        FROM books
        ORDER BY lower(books.title), books.id
        """,
    )
    fun getBooks(): Flow<List<BookEntity>>

    @Query(
        """
        SELECT bookId, `index`, title, '' AS htmlContent, '' AS plainText, imagePaths, wordCount, wordCountVersion
        FROM chapters
        ORDER BY bookId, `index`
        """,
    )
    fun getChapterSummaries(): Flow<List<ChapterEntity>>

    @Query(
        """
        SELECT bookId, entryIndex, label, depth, chapterIndex, characterOffset
        FROM table_of_contents_entries
        ORDER BY bookId, entryIndex
        """,
    )
    fun getTableOfContentsEntries(): Flow<List<TableOfContentsEntryEntity>>

    @Query(
        """
        SELECT id, title, authors, languageTag,
               CASE
                   WHEN coverImage IS NOT NULL AND length(coverImage) <= 1900000 THEN coverImage
                   ELSE NULL
               END AS coverImage,
               isCompleted,
               importFingerprint
        FROM books
        WHERE id = :bookId
        LIMIT 1
        """,
    )
    suspend fun getBook(bookId: String): BookEntity?

    @Query(
        """
        SELECT id, title, authors, languageTag,
               CASE
                   WHEN coverImage IS NOT NULL AND length(coverImage) <= 1900000 THEN coverImage
                   ELSE NULL
               END AS coverImage,
               isCompleted,
               importFingerprint
        FROM books
        LIMIT 1
        """,
    )
    suspend fun peekBook(): BookEntity?

    @Query(
        """
        SELECT id, title, authors, languageTag,
               CASE
                   WHEN coverImage IS NOT NULL AND length(coverImage) <= 1900000 THEN coverImage
                   ELSE NULL
               END AS coverImage,
               isCompleted,
               importFingerprint
        FROM books
        WHERE importFingerprint = :fingerprint
        LIMIT 1
        """,
    )
    suspend fun getBookByImportFingerprint(fingerprint: String): BookEntity?

    @Query(
        """
        SELECT id, title, authors, languageTag, NULL AS coverImage, isCompleted, importFingerprint
        FROM books
        WHERE title = :title
        """,
    )
    suspend fun getBooksByTitleForImportDedupe(title: String): List<BookEntity>

    @Query(
        """
        UPDATE books
        SET importFingerprint = :fingerprint
        WHERE id = :bookId AND importFingerprint IS NULL
        """,
    )
    suspend fun setImportFingerprintIfEmpty(
        bookId: String,
        fingerprint: String,
    ): Int

    @Query("SELECT languageTag FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookLanguageTag(bookId: String): String?

    @Query(
        """
        SELECT bookId, `index`, title, '' AS htmlContent, '' AS plainText, imagePaths, wordCount, wordCountVersion
        FROM chapters
        WHERE bookId = :bookId
        ORDER BY `index`
        """,
    )
    suspend fun getChapters(bookId: String): List<ChapterEntity>

    @Query(
        """
        SELECT bookId, `index`, title, htmlContent, plainText, imagePaths, wordCount, wordCountVersion
        FROM chapters
        WHERE bookId = :bookId
        ORDER BY `index`
        """,
    )
    suspend fun getChaptersWithContent(bookId: String): List<ChapterEntity>

    @Query(
        """
        SELECT bookId, entryIndex, label, depth, chapterIndex, characterOffset
        FROM table_of_contents_entries
        WHERE bookId = :bookId
        ORDER BY entryIndex
        """,
    )
    suspend fun getTableOfContentsEntries(bookId: String): List<TableOfContentsEntryEntity>

    @Query(
        """
        SELECT bookId, `index`, title, htmlContent, plainText, imagePaths, wordCount, wordCountVersion
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
        SET wordCount = :wordCount, wordCountVersion = $CHAPTER_WORD_COUNT_VERSION
        WHERE bookId = :bookId AND `index` = :index
          AND (wordCount != :wordCount OR wordCountVersion != $CHAPTER_WORD_COUNT_VERSION)
        """,
    )
    suspend fun updateChapterWordCount(
        bookId: String,
        index: Int,
        wordCount: Int,
    )

    @Query("UPDATE books SET isCompleted = :isCompleted WHERE id = :bookId")
    suspend fun setCompleted(
        bookId: String,
        isCompleted: Boolean,
    )

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteChaptersForBook(bookId: String)

    @Query("DELETE FROM table_of_contents_entries WHERE bookId = :bookId")
    suspend fun deleteTableOfContentsForBook(bookId: String)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: String)

    private companion object {
        const val INSERT_CONFLICT = -1L
    }
}
