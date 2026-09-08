package com.kairo.reader.data.search

import com.kairo.reader.core.model.LibrarySearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface LibrarySearchRepository {
    suspend fun search(
        query: String,
        bookId: String? = null,
    ): List<LibrarySearchResult>
    fun searchUpdates(query: String, bookId: String? = null): Flow<LibrarySearchUpdate> = flow {
        emit(LibrarySearchUpdate(search(query, bookId), isComplete = true))
    }
}

data class LibrarySearchUpdate(val results: List<LibrarySearchResult>, val isComplete: Boolean)
