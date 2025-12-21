package com.example.kairo.data.bookmarks

import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Bookmark
import com.example.kairo.core.model.BookmarkItem
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun observeBookmarks(): Flow<List<BookmarkItem>>

    fun observeBookmarksForBook(bookId: BookId): Flow<List<Bookmark>>

    suspend fun add(bookmark: Bookmark)

    suspend fun delete(bookmarkId: String)

    suspend fun deleteForBook(bookId: BookId)
}
