package com.example.kairo.data.reading

import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.ReadingPosition
import kotlinx.coroutines.flow.Flow

interface ReadingPositionRepository {
    suspend fun getPosition(bookId: BookId): ReadingPosition?
    suspend fun savePosition(position: ReadingPosition)
    fun observePositions(): Flow<List<ReadingPosition>>
}
