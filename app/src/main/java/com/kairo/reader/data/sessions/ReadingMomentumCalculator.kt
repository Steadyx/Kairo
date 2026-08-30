package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.ReadingMomentum
import com.kairo.reader.core.model.ReadingMomentumDay
import com.kairo.reader.core.model.ReadingMomentumWeek
import com.kairo.reader.core.model.ReadingSessionItem
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

fun buildReadingMomentum(
    sessions: List<ReadingSessionItem>,
    now: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
    resetCutoffAt: Long = 0L,
): ReadingMomentum {
    val currentWeek = localWeekPeriod(now, timeZone, locale)
    val todayStartedAt = localDayStartedAt(now, timeZone, locale)
    val effectiveResetCutoff =
        resetCutoffAt.takeIf {
            it in currentWeek.startedAt until currentWeek.endedAt && it <= now
        }
    val recent =
        sessions.filter { item ->
            val startedAt = item.session.startedAt
            startedAt in currentWeek.startedAt until currentWeek.endedAt &&
                startedAt <= now &&
                (effectiveResetCutoff == null || startedAt > effectiveResetCutoff)
        }
    val daily = buildDailyActivity(currentWeek.startedAt, timeZone, locale)
    recent.forEach { item ->
        val day = startOfLocalDay(item.session.startedAt, timeZone, locale)
        val index = daysBetween(startOfLocalDay(currentWeek.startedAt, timeZone, locale), day)
        if (index in daily.indices) {
            val current = daily[index]
            daily[index] =
                current.copy(
                    activeDurationMs = current.activeDurationMs + item.session.activeDurationMs,
                    wordsRead = current.wordsRead + item.session.wordsRead,
                    sessionCount = current.sessionCount + 1,
                )
        }
    }
    val duration = recent.sumOf { it.session.activeDurationMs }
    val words = recent.sumOf { it.session.wordsRead }
    val averageWpm =
        if (duration > 0L && words > 0) {
            ((words * MILLIS_PER_MINUTE) / duration).toInt().coerceAtLeast(1)
        } else {
            null
        }
    val preferredMode =
        recent
            .groupBy { it.session.mode }
            .maxByOrNull { (_, modeSessions) -> modeSessions.sumOf { it.session.activeDurationMs } }
            ?.key
    return ReadingMomentum(
        sessions = recent.sortedByDescending { it.session.startedAt },
        weekDurationMs = duration,
        weekWordsRead = words,
        activeDaysInLastSeven = daily.count { it.activeDurationMs > 0L },
        averageEffectiveWpm = averageWpm,
        preferredMode = preferredMode,
        dailyActivity = daily,
        todayStartedAt = todayStartedAt,
        previousWeeks = buildPreviousWeeks(sessions, currentWeek.startedAt, timeZone, locale),
    )
}

internal data class LocalWeekPeriod(val startedAt: Long, val endedAt: Long,)

internal fun localWeekPeriod(
    timestamp: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
): LocalWeekPeriod {
    val start = startOfLocalDay(timestamp, timeZone, locale)
    val daysSinceFirstDay =
        (start.get(Calendar.DAY_OF_WEEK) - start.firstDayOfWeek + ReadingMomentum.DAYS_PER_WEEK) %
            ReadingMomentum.DAYS_PER_WEEK
    start.add(Calendar.DAY_OF_YEAR, -daysSinceFirstDay)
    val end = start.clone() as Calendar
    end.add(Calendar.DAY_OF_YEAR, ReadingMomentum.DAYS_PER_WEEK)
    return LocalWeekPeriod(start.timeInMillis, end.timeInMillis)
}

internal fun localDayStartedAt(
    timestamp: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
): Long = startOfLocalDay(timestamp, timeZone, locale).timeInMillis

internal fun nextLocalDayStartedAt(
    timestamp: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
): Long =
    startOfLocalDay(timestamp, timeZone, locale).apply {
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

private fun buildDailyActivity(
    periodStartedAt: Long,
    timeZone: TimeZone,
    locale: Locale,
): MutableList<ReadingMomentumDay> {
    val periodStart = startOfLocalDay(periodStartedAt, timeZone, locale)
    return MutableList(ReadingMomentum.DAYS_PER_WEEK) { index ->
        val day = periodStart.clone() as Calendar
        day.add(Calendar.DAY_OF_YEAR, index)
        ReadingMomentumDay(startedAt = day.timeInMillis)
    }
}

private fun buildPreviousWeeks(
    sessions: List<ReadingSessionItem>,
    currentWeekStartedAt: Long,
    timeZone: TimeZone,
    locale: Locale,
): List<ReadingMomentumWeek> =
    sessions
        .asSequence()
        .filter { it.session.startedAt < currentWeekStartedAt }
        .groupBy { localWeekPeriod(it.session.startedAt, timeZone, locale) }
        .entries
        .sortedByDescending { it.key.startedAt }
        .map { (period, weekSessions) ->
            ReadingMomentumWeek(
                startedAt = period.startedAt,
                endedAt = period.endedAt,
                activeDurationMs = weekSessions.sumOf { it.session.activeDurationMs },
                wordsRead = weekSessions.sumOf { it.session.wordsRead },
                activeDays =
                weekSessions
                    .filter { it.session.activeDurationMs > 0L }
                    .map { localDayStartedAt(it.session.startedAt, timeZone, locale) }
                    .distinct()
                    .size,
            )
        }

private fun startOfLocalDay(
    timestamp: Long,
    timeZone: TimeZone,
    locale: Locale,
): Calendar =
    Calendar.getInstance(timeZone, locale).apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

private fun daysBetween(
    start: Calendar,
    end: Calendar,
): Int {
    val cursor = start.clone() as Calendar
    var days = 0
    while (cursor.before(end) && days <= ReadingMomentum.DAYS_PER_WEEK) {
        cursor.add(Calendar.DAY_OF_YEAR, 1)
        days += 1
    }
    return days
}

private const val MILLIS_PER_MINUTE = 60_000L
