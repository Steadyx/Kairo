package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingSession
import com.kairo.reader.core.model.ReadingSessionItem
import com.kairo.reader.core.model.ReadingSessionMode
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingMomentumCalculatorTest {
    @Test
    fun summarizesTheCurrentLocaleCalendarWeekAndScopesRecentSessions() {
        val timeZone = TimeZone.getTimeZone("UTC")
        val now = timestamp(timeZone, 2026, Calendar.JANUARY, 7)
        val monday = session(timestamp(timeZone, 2026, Calendar.JANUARY, 5), 60_000L, 100)
        val wednesday = session(now, 180_000L, 300, ReadingSessionMode.BIONIC)
        val previousSunday =
            session(timestamp(timeZone, 2026, Calendar.JANUARY, 4), 600_000L, 1_000)

        val momentum =
            buildReadingMomentum(
                sessions = listOf(wednesday, previousSunday, monday),
                now = now,
                timeZone = timeZone,
                locale = Locale.UK,
            )

        assertEquals(240_000L, momentum.weekDurationMs)
        assertEquals(400, momentum.weekWordsRead)
        assertEquals(2, momentum.activeDaysInLastSeven)
        assertEquals(listOf(wednesday, monday), momentum.sessions)
        assertEquals(
            listOf(60_000L, 0L, 180_000L, 0L, 0L, 0L, 0L),
            momentum.dailyActivity.map { it.activeDurationMs },
        )
        assertEquals(1, momentum.previousWeeks.size)
        assertEquals(600_000L, momentum.previousWeeks.single().activeDurationMs)
    }

    @Test
    fun localeFirstDayControlsTheWeekBoundary() {
        val timeZone = TimeZone.getTimeZone("UTC")
        val now = timestamp(timeZone, 2026, Calendar.JANUARY, 7)
        val sunday = session(timestamp(timeZone, 2026, Calendar.JANUARY, 4), 60_000L, 100)
        val saturday = session(timestamp(timeZone, 2026, Calendar.JANUARY, 3), 120_000L, 200)

        val momentum =
            buildReadingMomentum(
                sessions = listOf(sunday, saturday),
                now = now,
                timeZone = timeZone,
                locale = Locale.US,
            )

        assertEquals(listOf(sunday), momentum.sessions)
        assertEquals(60_000L, momentum.dailyActivity.first().activeDurationMs)
        assertEquals(120_000L, momentum.previousWeeks.single().activeDurationMs)
    }

    @Test
    fun unchangedSessionsMoveToHistoryWhenTheCalendarWeekChanges() {
        val timeZone = TimeZone.getTimeZone("UTC")
        val session = session(timestamp(timeZone, 2026, Calendar.JANUARY, 11), 300_000L, 500)

        val sunday =
            buildReadingMomentum(
                listOf(session),
                now = timestamp(timeZone, 2026, Calendar.JANUARY, 11),
                timeZone = timeZone,
                locale = Locale.UK,
            )
        val monday =
            buildReadingMomentum(
                listOf(session),
                now = timestamp(timeZone, 2026, Calendar.JANUARY, 12),
                timeZone = timeZone,
                locale = Locale.UK,
            )

        assertEquals(300_000L, sunday.weekDurationMs)
        assertEquals(0L, monday.weekDurationMs)
        assertTrue(monday.sessions.isEmpty())
        assertEquals(300_000L, monday.previousWeeks.single().activeDurationMs)
    }

    @Test
    fun calendarWeekBucketingIsDstSafe() {
        val timeZone = TimeZone.getTimeZone("Europe/London")
        val monday = timestamp(timeZone, 2026, Calendar.MARCH, 23)
        val sunday = timestamp(timeZone, 2026, Calendar.MARCH, 29)
        val period = localWeekPeriod(sunday, timeZone, Locale.UK)

        val momentum =
            buildReadingMomentum(
                sessions =
                listOf(
                    session(monday, 60_000L, 100),
                    session(sunday, 120_000L, 200),
                ),
                now = sunday,
                timeZone = timeZone,
                locale = Locale.UK,
            )

        assertEquals(167L * MILLIS_PER_HOUR, period.endedAt - period.startedAt)
        assertEquals(60_000L, momentum.dailyActivity.first().activeDurationMs)
        assertEquals(120_000L, momentum.dailyActivity.last().activeDurationMs)
        assertEquals(
            startOfDay(timeZone, 2026, Calendar.MARCH, 29),
            momentum.dailyActivity.last().startedAt,
        )
    }

    @Test
    fun resetCutoffOnlyHidesCurrentWeekAndPreservesRawHistory() {
        val timeZone = TimeZone.getTimeZone("UTC")
        val beforeResetAt = timestamp(timeZone, 2026, Calendar.JANUARY, 6)
        val resetAt = timestamp(timeZone, 2026, Calendar.JANUARY, 7, hour = 13)
        val afterResetAt = timestamp(timeZone, 2026, Calendar.JANUARY, 7, hour = 14)
        val previousWeekAt = timestamp(timeZone, 2026, Calendar.JANUARY, 2)
        val sessions =
            listOf(
                session(afterResetAt, 180_000L, 300),
                session(beforeResetAt, 120_000L, 200),
                session(previousWeekAt, 60_000L, 100),
            )

        val resetMomentum =
            buildReadingMomentum(
                sessions = sessions,
                now = timestamp(timeZone, 2026, Calendar.JANUARY, 8),
                timeZone = timeZone,
                locale = Locale.UK,
                resetCutoffAt = resetAt,
            )

        assertEquals(listOf(sessions.first()), resetMomentum.sessions)
        assertEquals(180_000L, resetMomentum.weekDurationMs)
        assertEquals(60_000L, resetMomentum.previousWeeks.single().activeDurationMs)

        val followingWeek =
            buildReadingMomentum(
                sessions = sessions,
                now = timestamp(timeZone, 2026, Calendar.JANUARY, 12),
                timeZone = timeZone,
                locale = Locale.UK,
                resetCutoffAt = resetAt,
            )

        assertEquals(2, followingWeek.previousWeeks.size)
        assertEquals(300_000L, followingWeek.previousWeeks.first().activeDurationMs)
        assertEquals(500, followingWeek.previousWeeks.first().wordsRead)
        assertEquals(2, followingWeek.previousWeeks.first().activeDays)
    }

    @Test
    fun futureCalendarDaysRemainEmptyAndTodayIsIdentified() {
        val timeZone = TimeZone.getTimeZone("UTC")
        val now = timestamp(timeZone, 2026, Calendar.JANUARY, 7)
        val momentum =
            buildReadingMomentum(
                sessions = listOf(session(now, 60_000L, 100)),
                now = now,
                timeZone = timeZone,
                locale = Locale.UK,
            )

        assertEquals(momentum.dailyActivity[2].startedAt, momentum.todayStartedAt)
        assertTrue(momentum.dailyActivity.drop(3).all { it.activeDurationMs == 0L })
        assertTrue(momentum.dailyActivity.drop(3).all { it.sessionCount == 0 })
    }

    private fun session(
        startedAt: Long,
        durationMs: Long,
        words: Int,
        mode: ReadingSessionMode = ReadingSessionMode.READER,
    ): ReadingSessionItem =
        ReadingSessionItem(
            session =
            ReadingSession(
                id = "$startedAt:$mode",
                bookId = BOOK.id,
                mode = mode,
                startedAt = startedAt,
                endedAt = startedAt + durationMs,
                activeDurationMs = durationMs,
                startChapterIndex = 0,
                startTokenIndex = 0,
                endChapterIndex = 0,
                endTokenIndex = words,
                wordsRead = words,
                effectiveWpm = 100,
                isWordCountEstimated = false,
            ),
            book = BOOK,
        )

    private fun timestamp(
        timeZone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
    ): Long =
        Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis

    private fun startOfDay(
        timeZone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
    ): Long =
        Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.timeInMillis

    private companion object {
        const val MILLIS_PER_HOUR = 3_600_000L
        val BOOK = Book(BookId("book"), "Book", emptyList(), chapters = emptyList())
    }
}
