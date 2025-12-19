package com.example.kairo.data.token

import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.Token

interface TokenRepository {
    suspend fun getTokens(bookId: BookId, chapterIndex: Int, chapter: Chapter? = null): List<Token>
}
