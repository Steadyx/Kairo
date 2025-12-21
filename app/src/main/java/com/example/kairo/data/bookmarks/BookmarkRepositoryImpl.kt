package com.example.kairo.data.bookmarks

import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Bookmark
import com.example.kairo.core.model.BookmarkItem
import com.example.kairo.data.local.BookmarkDao
import com.example.kairo.data.local.toDomain
import com.example.kairo.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookmarkRepositoryImpl(private val bookmarkDao: BookmarkDao,) : BookmarkRepository {
    override fun observeBookmarks(): Flow<List<BookmarkItem>> = bookmarkDao.observeWithBook().map { items ->
        items.map { it.toDomain() }
    }

    override fun observeBookmarksForBook(bookId: BookId): Flow<List<Bookmark>> =
        bookmarkDao.observeForBook(bookId.value).map { items -> items.map { it.toDomain() } }

    override suspend fun add(bookmark: Bookmark) {
        bookmarkDao.upsert(bookmark.toEntity())
    }

    override suspend fun delete(bookmarkId: String) {
        bookmarkDao.delete(bookmarkId)
    }

    override suspend fun deleteForBook(bookId: BookId) {
        bookmarkDao.deleteForBook(bookId.value)
    }
}
