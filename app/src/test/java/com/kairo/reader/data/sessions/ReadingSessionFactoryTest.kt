package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingSessionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSessionFactoryTest {
    @Test
    fun acceptsPositiveSubFiveMinuteSession() {
        val session = create(durationMs = 240_000L, wordsRead = 100)

        assertTrue(session != null)
        assertEquals(240_000L, session?.activeDurationMs)
    }

    @Test
    fun ignoresZeroDurationOrStationarySessions() {
        assertNull(create(durationMs = 0L, wordsRead = 100))
        assertNull(create(durationMs = 240_000L, wordsRead = 0))
    }

    @Test
    fun calculatesEffectiveWpmForCompletedSession() {
        val session = create(durationMs = 600_000L, wordsRead = 2_000)

        assertTrue(session != null)
        assertEquals(200, session?.effectiveWpm)
    }

    private fun create(
        durationMs: Long,
        wordsRead: Int,
    ) =
        ReadingSessionFactory.create(
            ReadingSessionDraft(
                bookId = BookId("book"),
                mode = ReadingSessionMode.RSVP,
                startedAt = 1_000L,
                endedAt = 1_000L + durationMs,
                activeDurationMs = durationMs,
                start = ReadingSessionLocation(chapterIndex = 0, tokenIndex = 0),
                end = ReadingSessionLocation(chapterIndex = 0, tokenIndex = wordsRead),
                wordsRead = wordsRead,
                isWordCountEstimated = false,
            ),
        )
}
