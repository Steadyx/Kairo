package com.example.kairo.data.library

import android.net.Uri
import com.example.kairo.core.model.Book
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeLibrary(): Flow<List<Book>>
    suspend fun import(uri: Uri): Book
    suspend fun delete(bookId: String)
}
