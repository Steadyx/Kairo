package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingSession
import com.kairo.reader.core.model.ReadingSessionItem
import com.kairo.reader.data.local.ReadingSessionCheckpointEntity
import com.kairo.reader.data.local.ReadingSessionDao
import com.kairo.reader.data.local.toDomain
import com.kairo.reader.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReadingSessionRepositoryImpl(private val sessionDao: ReadingSessionDao,) : ReadingSessionRepository {
    override fun observeSessions(): Flow<List<ReadingSessionItem>> =
        sessionDao.observeWithBook().map { sessions -> sessions.map { it.toDomain() } }

    override suspend fun add(session: ReadingSession): Boolean = sessionDao.insert(session.toEntity())

    override suspend fun loadCheckpoints(sessionKey: String): List<ReadingSessionCheckpoint> =
        sessionDao.getCheckpoints(sessionKey).map { it.toCheckpoint() }

    override suspend fun loadAllCheckpoints(): List<ReadingSessionCheckpoint> =
        sessionDao.getAllCheckpoints().map { it.toCheckpoint() }

    override suspend fun saveCheckpoints(
        sessionKey: String,
        checkpoints: List<ReadingSessionCheckpoint>,
    ): Boolean =
        sessionDao.replaceCheckpoints(sessionKey, checkpoints.map { it.toEntity() })

    override suspend fun finalizeCheckpoints(
        sessionKey: String,
        sessions: List<ReadingSession>,
    ): Boolean = sessionDao.finalizeCheckpoints(sessionKey, sessions.map { it.toEntity() })

    override suspend fun deleteForBook(bookId: BookId) {
        sessionDao.deleteForBook(bookId.value)
    }
}

private fun ReadingSessionCheckpointEntity.toCheckpoint(): ReadingSessionCheckpoint =
    ReadingSessionCheckpoint(
        id = id,
        sessionKey = sessionKey,
        logicalSessionId = logicalSessionId,
        bookId = BookId(bookId),
        mode = enumValues<com.kairo.reader.core.model.ReadingSessionMode>().firstOrNull { it.name == mode }
            ?: com.kairo.reader.core.model.ReadingSessionMode.READER,
        logicalStartedAt = logicalStartedAt,
        dayStartedAt = dayStartedAt,
        startedAt = startedAt,
        endedAt = endedAt,
        activeDurationMs = activeDurationMs,
        start = ReadingSessionLocation(startChapterIndex, startTokenIndex),
        end = ReadingSessionLocation(endChapterIndex, endTokenIndex),
        wordsRead = wordsRead,
        isWordCountEstimated = isWordCountEstimated,
        lastReaderWordIndex = lastReaderWordIndex,
    )

private fun ReadingSessionCheckpoint.toEntity(): ReadingSessionCheckpointEntity =
    ReadingSessionCheckpointEntity(
        id = id,
        sessionKey = sessionKey,
        logicalSessionId = logicalSessionId,
        bookId = bookId.value,
        mode = mode.name,
        logicalStartedAt = logicalStartedAt,
        dayStartedAt = dayStartedAt,
        startedAt = startedAt,
        endedAt = endedAt,
        activeDurationMs = activeDurationMs,
        startChapterIndex = start.chapterIndex,
        startTokenIndex = start.tokenIndex,
        endChapterIndex = end.chapterIndex,
        endTokenIndex = end.tokenIndex,
        wordsRead = wordsRead,
        isWordCountEstimated = isWordCountEstimated,
        lastReaderWordIndex = lastReaderWordIndex,
    )
