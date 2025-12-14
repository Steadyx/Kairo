package com.example.kairo.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class BookmarkWithBookEntity(
    @Embedded val bookmark: BookmarkEntity,
    @Embedded(prefix = "book_") val book: BookEntity
)

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun delete(bookmarkId: String)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Query(
        """
        SELECT
            bookmarks.id,
            bookmarks.bookId,
            bookmarks.chapterIndex,
            bookmarks.tokenIndex,
            bookmarks.previewText,
            bookmarks.createdAt,
            books.id AS book_id,
            books.title AS book_title,
            books.authors AS book_authors,
            books.coverImage AS book_coverImage
        FROM bookmarks
        JOIN books ON bookmarks.bookId = books.id
        ORDER BY bookmarks.createdAt DESC
        """
    )
    fun observeWithBook(): Flow<List<BookmarkWithBookEntity>>
}

