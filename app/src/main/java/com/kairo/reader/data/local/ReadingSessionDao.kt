package com.kairo.reader.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kairo.reader.core.model.ReadingSessionMode
import kotlinx.coroutines.flow.Flow

data class ReadingSessionWithBookEntity(@Embedded val session: ReadingSessionEntity, @Embedded(prefix = "book_") val book: BookEntity,)

@Dao
interface ReadingSessionDao {
    @Transaction
    suspend fun insert(entity: ReadingSessionEntity): Boolean {
        if (!bookExists(entity.bookId)) return false
        insertInternal(entity)
        return true
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(entity: ReadingSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(entities: List<ReadingSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckpointsInternal(entities: List<ReadingSessionCheckpointEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE id = :bookId)")
    suspend fun bookExists(bookId: String): Boolean

    @Query(
        "SELECT * FROM reading_session_checkpoints WHERE sessionKey = :sessionKey " +
            "ORDER BY dayStartedAt",
    )
    suspend fun getCheckpoints(sessionKey: String): List<ReadingSessionCheckpointEntity>

    @Query("SELECT * FROM reading_session_checkpoints ORDER BY sessionKey, dayStartedAt")
    suspend fun getAllCheckpoints(): List<ReadingSessionCheckpointEntity>

    @Query("DELETE FROM reading_session_checkpoints WHERE sessionKey = :sessionKey")
    suspend fun deleteCheckpoints(sessionKey: String)

    @Transaction
    suspend fun replaceCheckpoints(
        sessionKey: String,
        entities: List<ReadingSessionCheckpointEntity>,
    ): Boolean {
        if (entities.isNotEmpty()) {
            val first = entities.first()
            if (
                entities.any {
                    it.sessionKey != sessionKey ||
                        it.bookId != first.bookId ||
                        it.mode != first.mode
                } ||
                expectedSessionKey(first.bookId, first.mode) != sessionKey
            ) {
                return false
            }
            val stored = getCheckpoints(sessionKey)
            if (stored.any { it.bookId != first.bookId || it.mode != first.mode }) return false
            if (!bookExists(first.bookId)) return false
        }
        deleteCheckpoints(sessionKey)
        if (entities.isNotEmpty()) insertCheckpointsInternal(entities)
        return true
    }

    @Transaction
    suspend fun finalizeCheckpoints(
        sessionKey: String,
        sessions: List<ReadingSessionEntity>,
    ): Boolean {
        if (sessions.isNotEmpty()) {
            val first = sessions.first()
            if (
                sessions.any { it.bookId != first.bookId || it.mode != first.mode } ||
                expectedSessionKey(first.bookId, first.mode) != sessionKey
            ) {
                return false
            }
            val stored = getCheckpoints(sessionKey)
            if (stored.any { it.bookId != first.bookId || it.mode != first.mode }) return false
            if (!bookExists(first.bookId)) return false
        }
        if (sessions.isNotEmpty()) insertAllInternal(sessions)
        deleteCheckpoints(sessionKey)
        return true
    }

    @Query("DELETE FROM reading_sessions WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Query(
        """
        SELECT
            reading_sessions.*,
            books.id AS book_id,
            books.title AS book_title,
            books.authors AS book_authors,
            books.languageTag AS book_languageTag,
            NULL AS book_coverImage,
            books.isCompleted AS book_isCompleted,
            books.importFingerprint AS book_importFingerprint
        FROM reading_sessions
        JOIN books ON reading_sessions.bookId = books.id
        ORDER BY reading_sessions.startedAt DESC
        """,
    )
    fun observeWithBook(): Flow<List<ReadingSessionWithBookEntity>>
}

private fun expectedSessionKey(
    bookId: String,
    mode: String,
): String? =
    when (mode) {
        ReadingSessionMode.READER.name -> "reader:$bookId"
        ReadingSessionMode.RSVP.name,
        ReadingSessionMode.BIONIC.name,
        -> "timed:$mode:$bookId"
        else -> null
    }
