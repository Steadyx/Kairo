package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingSession
import com.kairo.reader.core.model.ReadingSessionMode
import java.util.UUID
import kotlin.math.roundToInt

object ReadingSessionFactory {
    fun create(draft: ReadingSessionDraft): ReadingSession? {
        val safeDuration = draft.activeDurationMs.coerceAtLeast(0L)
        val safeWords = draft.wordsRead.coerceAtLeast(0)
        if (safeDuration == 0L || safeWords == 0) return null
        val effectiveWpm =
            ((safeWords * MILLIS_PER_MINUTE.toDouble()) / safeDuration.toDouble())
                .roundToInt()
                .coerceIn(MIN_EFFECTIVE_WPM, MAX_EFFECTIVE_WPM)
        return ReadingSession(
            id = UUID.randomUUID().toString(),
            bookId = draft.bookId,
            mode = draft.mode,
            startedAt = draft.startedAt,
            endedAt = draft.endedAt.coerceAtLeast(draft.startedAt),
            activeDurationMs = safeDuration,
            startChapterIndex = draft.start.chapterIndex,
            startTokenIndex = draft.start.tokenIndex,
            endChapterIndex = draft.end.chapterIndex,
            endTokenIndex = draft.end.tokenIndex,
            wordsRead = safeWords,
            effectiveWpm = effectiveWpm,
            isWordCountEstimated = draft.isWordCountEstimated,
        )
    }

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MIN_EFFECTIVE_WPM = 1
    private const val MAX_EFFECTIVE_WPM = 2_000
}

data class ReadingSessionDraft(
    val bookId: BookId,
    val mode: ReadingSessionMode,
    val startedAt: Long,
    val endedAt: Long,
    val activeDurationMs: Long,
    val start: ReadingSessionLocation,
    val end: ReadingSessionLocation,
    val wordsRead: Int,
    val isWordCountEstimated: Boolean,
)

data class ReadingSessionLocation(
    val chapterIndex: Int,
    val tokenIndex: Int,
    val wordIndex: Int = -1,
)
