package com.kairo.reader.ui.library

import com.kairo.reader.core.model.ReadingMomentum
import com.kairo.reader.core.model.ReadingMomentumDay
import com.kairo.reader.core.model.ReadingSessionItem
import com.kairo.reader.data.preferences.MAX_WEEKLY_READING_GOAL_MINUTES
import com.kairo.reader.data.preferences.MIN_WEEKLY_READING_GOAL_MINUTES
import com.kairo.reader.data.sessions.localDayStartedAt
import com.kairo.reader.data.sessions.localWeekPeriod
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal data class MomentumDurationValue(val hours: Int, val minutes: Int, val isLessThanMinute: Boolean,)

internal fun momentumDurationValue(durationMs: Long): MomentumDurationValue {
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    val totalMinutes = safeDurationMs / MILLIS_PER_MINUTE
    return MomentumDurationValue(
        hours = (totalMinutes / MINUTES_PER_HOUR).toInt(),
        minutes = (totalMinutes % MINUTES_PER_HOUR).toInt(),
        isLessThanMinute = safeDurationMs in 1 until MILLIS_PER_MINUTE,
    )
}

internal fun visibleMomentumSessions(sessions: List<ReadingSessionItem>): List<ReadingSessionItem> =
    sessions.take(RECENT_SESSION_LIMIT)

internal fun momentumDaysForDisplay(
    dailyActivity: List<ReadingMomentumDay>,
    now: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
): List<ReadingMomentumDay> {
    if (dailyActivity.size == ReadingMomentum.DAYS_PER_WEEK) return dailyActivity
    val period = localWeekPeriod(now, timeZone, locale)
    val start =
        Calendar.getInstance(timeZone, locale).apply { timeInMillis = period.startedAt }
    return List(ReadingMomentum.DAYS_PER_WEEK) { index ->
        val day = start.clone() as Calendar
        day.add(Calendar.DAY_OF_YEAR, index)
        ReadingMomentumDay(startedAt = day.timeInMillis)
    }
}

internal fun momentumTodayDayIndex(
    days: List<ReadingMomentumDay>,
    todayStartedAt: Long,
    now: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
): Int {
    val today =
        todayStartedAt.takeIf { it > 0L }
            ?: localDayStartedAt(now, timeZone, locale)
    return days.indexOfFirst { it.startedAt == today }.takeIf { it >= 0 } ?: days.lastIndex
}

internal fun validatedWeeklyGoalMinutes(input: String): Int? =
    input
        .toIntOrNull()
        ?.takeIf { it in MIN_WEEKLY_READING_GOAL_MINUTES..MAX_WEEKLY_READING_GOAL_MINUTES }

internal const val RECENT_SESSION_LIMIT = 5
private const val MINUTES_PER_HOUR = 60L
private const val MILLIS_PER_MINUTE = 60_000L
