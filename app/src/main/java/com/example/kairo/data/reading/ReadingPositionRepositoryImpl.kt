package com.example.kairo.data.reading

import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.ReadingPosition
import com.example.kairo.data.local.ReadingPositionDao
import com.example.kairo.data.local.toDomain
import com.example.kairo.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReadingPositionRepositoryImpl(
    private val dao: ReadingPositionDao
) : ReadingPositionRepository {
    override suspend fun getPosition(bookId: BookId): ReadingPosition? =
        dao.getPosition(bookId.value)?.toDomain()

    override suspend fun savePosition(position: ReadingPosition) {
        dao.savePosition(position.toEntity())
    }

    override fun observePositions(): Flow<List<ReadingPosition>> =
        dao.getPositions().map { entities -> entities.map { it.toDomain() } }
}
