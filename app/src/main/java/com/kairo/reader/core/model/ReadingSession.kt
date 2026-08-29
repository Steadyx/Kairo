package com.kairo.reader.core.model

enum class ReadingSessionMode { READER, RSVP, BIONIC }

data class ReadingSession(
    val id: String,
    val bookId: BookId,
    val mode: ReadingSessionMode,
    val startedAt: Long,
    val endedAt: Long,
    val activeDurationMs: Long,
    val startChapterIndex: Int,
    val startTokenIndex: Int,
    val endChapterIndex: Int,
    val endTokenIndex: Int,
    val wordsRead: Int,
    val effectiveWpm: Int,
    val isWordCountEstimated: Boolean,
)

data class ReadingSessionItem(val session: ReadingSession, val book: Book,)

data class ReadingMomentumDay(val startedAt: Long, val activeDurationMs: Long = 0L, val wordsRead: Int = 0, val sessionCount: Int = 0,)

data class ReadingMomentumWeek(val startedAt: Long, val endedAt: Long, val activeDurationMs: Long, val wordsRead: Int, val activeDays: Int,)

data class ReadingMomentum(
    val sessions: List<ReadingSessionItem> = emptyList(),
    val weekDurationMs: Long = 0L,
    val weekWordsRead: Int = 0,
    val activeDaysInLastSeven: Int = 0,
    val averageEffectiveWpm: Int? = null,
    val preferredMode: ReadingSessionMode? = null,
    val dailyActivity: List<ReadingMomentumDay> = emptyList(),
    val todayStartedAt: Long = 0L,
    val previousWeeks: List<ReadingMomentumWeek> = emptyList(),
) {
    companion object {
        const val DAYS_PER_WEEK = 7
    }
}
