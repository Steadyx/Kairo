package com.kairo.reader.data.reading

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.data.local.ReadingPositionDao
import com.kairo.reader.data.local.toDomain
import com.kairo.reader.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReadingPositionRepositoryImpl(private val dao: ReadingPositionDao,) : ReadingPositionRepository {
    override suspend fun getPosition(
        bookId: BookId
    ): ReadingPosition? = dao.getPosition(bookId.value)?.toDomain()

    override suspend fun savePosition(position: ReadingPosition) {
        val current = dao.getPosition(position.bookId.value)?.toDomain()
        if (shouldIgnoreUnqualifiedStartOverwrite(current, position)) return
        dao.savePosition(position.toEntity())
    }

    override fun observePositions(): Flow<List<ReadingPosition>> = dao.getPositions().map { entities ->
        entities.map { it.toDomain() }
    }
}

internal fun shouldIgnoreUnqualifiedStartOverwrite(
    current: ReadingPosition?,
    next: ReadingPosition,
): Boolean {
    if (current == null || current.bookId != next.bookId) return false
    val nextIsUnqualifiedBookStart =
        next.chapterIndex == 0 &&
            next.tokenIndex == 0 &&
            next.wordIndex < 0 &&
            next.rsvpResumeCursor < 0 &&
            next.imagePageIndex < 0
    if (!nextIsUnqualifiedBookStart) return false
    return current.chapterIndex > 0 ||
        current.tokenIndex > 0 ||
        current.wordIndex > 0 ||
        current.rsvpResumeCursor > 0 ||
        current.imagePageIndex > 0
}
