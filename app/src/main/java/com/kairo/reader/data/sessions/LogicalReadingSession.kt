package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingSession
import com.kairo.reader.core.model.ReadingSessionMode
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

internal class LogicalReadingSession private constructor(
    val sessionKey: String,
    private val logicalSessionId: String,
    private val bookId: BookId,
    private val mode: ReadingSessionMode,
    private val logicalStartedAt: Long,
    private var location: ReadingSessionLocation,
    private var lastReaderWordIndex: Int?,
    private var lastReaderBasisFingerprint: Long?,
    private val segments: LinkedHashMap<Long, DailyReadingSegment>,
) {
    private var activeSince: ReadingSessionTimestamp? = null
    private var readerNeedsRebase: Boolean = false

    val isActive: Boolean
        get() = activeSince != null

    fun resume(
        timestamp: ReadingSessionTimestamp,
        timeZone: TimeZone,
    ) {
        if (activeSince != null) return
        activeSince = timestamp
        segmentFor(timestamp.wallTimeMillis, location, timeZone)
    }

    fun pause(
        timestamp: ReadingSessionTimestamp,
        timeZone: TimeZone,
    ) {
        accrueActiveTime(timestamp, timeZone)
        activeSince = null
    }

    fun checkpoint(
        timestamp: ReadingSessionTimestamp,
        timeZone: TimeZone,
    ): List<ReadingSessionCheckpoint> {
        accrueActiveTime(timestamp, timeZone)
        return segments.values.map { it.toCheckpoint() }
    }

    fun requestReaderRebase() {
        readerNeedsRebase = true
    }

    fun rebaseReader(
        progress: ReaderProgress,
    ) {
        location = progress.location
        lastReaderWordIndex = progress.absoluteWordIndex
        lastReaderBasisFingerprint = progress.basisFingerprint
        readerNeedsRebase = false
    }

    fun moveReaderBatch(
        firstProgress: ReaderProgress,
        finalProgress: ReaderProgress,
        internalForwardWords: Int,
        timestamp: ReadingSessionTimestamp,
        timeZone: TimeZone,
    ) {
        accrueActiveTime(timestamp, timeZone)
        val previousWordIndex = lastReaderWordIndex
        val firstWordIndex = firstProgress.absoluteWordIndex
        val firstForwardWords =
            if (
                !readerNeedsRebase &&
                lastReaderBasisFingerprint == firstProgress.basisFingerprint &&
                previousWordIndex != null &&
                firstWordIndex != null
            ) {
                (firstWordIndex - previousWordIndex).coerceAtLeast(0)
            } else {
                0
            }
        if (isActive) {
            addWords(
                safeAdd(firstForwardWords, internalForwardWords.coerceAtLeast(0)),
                timestamp.wallTimeMillis,
                timeZone,
            )
        }
        location = finalProgress.location
        lastReaderWordIndex = finalProgress.absoluteWordIndex
        lastReaderBasisFingerprint = finalProgress.basisFingerprint
        readerNeedsRebase = false
        updateCurrentSegmentEnd(timestamp.wallTimeMillis, finalProgress.location, timeZone)
    }

    fun consumeTimedFrame(
        words: Int,
        newLocation: ReadingSessionLocation,
        timestamp: ReadingSessionTimestamp,
        timeZone: TimeZone,
    ) {
        accrueActiveTime(timestamp, timeZone)
        if (isActive) addWords(words.coerceAtLeast(0), timestamp.wallTimeMillis, timeZone)
        location = newLocation
        updateCurrentSegmentEnd(timestamp.wallTimeMillis, newLocation, timeZone)
    }

    fun finalSessions(
        timestamp: ReadingSessionTimestamp,
        timeZone: TimeZone,
    ): List<ReadingSession> {
        pause(timestamp, timeZone)
        val duration = segments.values.sumOf { it.activeDurationMs }
        val words = segments.values.sumOf { it.wordsRead.toLong() }
        if (duration <= 0L || words <= 0L) return emptyList()
        return segments.values
            .filter { it.activeDurationMs > 0L || it.wordsRead > 0 }
            .map { it.toReadingSession() }
    }

    fun isEligibleForFinalization(): Boolean =
        segments.values.sumOf { it.activeDurationMs } > 0L &&
            segments.values.any { it.wordsRead > 0 }

    private fun accrueActiveTime(
        timestamp: ReadingSessionTimestamp,
        timeZone: TimeZone,
    ) {
        val started = activeSince ?: return
        val elapsed = (timestamp.elapsedRealtimeMillis - started.elapsedRealtimeMillis).coerceAtLeast(0L)
        if (elapsed == 0L) {
            activeSince = timestamp
            return
        }

        val wallEnd = timestamp.wallTimeMillis.coerceAtLeast(started.wallTimeMillis)
        val wallDuration = wallEnd - started.wallTimeMillis
        if (wallDuration <= 0L) {
            val segment = segmentFor(timestamp.wallTimeMillis, location, timeZone)
            segment.activeDurationMs = safeAdd(segment.activeDurationMs, elapsed)
            segment.endedAt = maxOf(segment.endedAt, timestamp.wallTimeMillis)
            segment.end = location
            activeSince = timestamp
            return
        }

        var cursorWall = started.wallTimeMillis
        var remainingElapsed = elapsed
        while (cursorWall < wallEnd) {
            val nextDay = nextLocalDayStart(cursorWall, timeZone)
            val sliceEnd = minOf(wallEnd, nextDay)
            val sliceWall = sliceEnd - cursorWall
            val sliceElapsed =
                if (sliceEnd == wallEnd) {
                    remainingElapsed
                } else {
                    ((elapsed.toDouble() * sliceWall.toDouble()) / wallDuration.toDouble())
                        .toLong()
                        .coerceIn(0L, remainingElapsed)
                }
            val segment = segmentFor(cursorWall, location, timeZone)
            segment.activeDurationMs = safeAdd(segment.activeDurationMs, sliceElapsed)
            segment.endedAt = maxOf(segment.endedAt, sliceEnd)
            segment.end = location
            remainingElapsed -= sliceElapsed
            cursorWall = sliceEnd
        }
        activeSince = timestamp
    }

    private fun addWords(
        count: Int,
        wallTimeMillis: Long,
        timeZone: TimeZone,
    ) {
        if (count <= 0) return
        val segment = segmentFor(wallTimeMillis, location, timeZone)
        segment.wordsRead = safeAdd(segment.wordsRead, count)
    }

    private fun updateCurrentSegmentEnd(
        wallTimeMillis: Long,
        newLocation: ReadingSessionLocation,
        timeZone: TimeZone,
    ) {
        if (!isActive) return
        val segment = segmentFor(wallTimeMillis, newLocation, timeZone)
        segment.endedAt = maxOf(segment.endedAt, wallTimeMillis)
        segment.end = newLocation
    }

    private fun segmentFor(
        wallTimeMillis: Long,
        initialLocation: ReadingSessionLocation,
        timeZone: TimeZone,
    ): DailyReadingSegment {
        val dayStartedAt = localDayStartMillis(wallTimeMillis, timeZone)
        return segments.getOrPut(dayStartedAt) {
            DailyReadingSegment(
                id = "$logicalSessionId:$dayStartedAt",
                dayStartedAt = dayStartedAt,
                startedAt = wallTimeMillis,
                endedAt = wallTimeMillis,
                start = initialLocation,
                end = initialLocation,
            )
        }
    }

    private fun DailyReadingSegment.toCheckpoint(): ReadingSessionCheckpoint =
        ReadingSessionCheckpoint(
            id = id,
            sessionKey = sessionKey,
            logicalSessionId = logicalSessionId,
            bookId = bookId,
            mode = mode,
            logicalStartedAt = logicalStartedAt,
            dayStartedAt = dayStartedAt,
            startedAt = startedAt,
            endedAt = endedAt,
            activeDurationMs = activeDurationMs,
            start = start,
            end = end,
            wordsRead = wordsRead,
            isWordCountEstimated = mode == ReadingSessionMode.READER,
            lastReaderWordIndex = lastReaderWordIndex,
        )

    private fun DailyReadingSegment.toReadingSession(): ReadingSession {
        val effectiveDuration = activeDurationMs.coerceAtLeast(1L)
        val effectiveWpm =
            if (wordsRead <= 0) {
                0
            } else {
                ((wordsRead * MILLIS_PER_MINUTE.toDouble()) / effectiveDuration.toDouble())
                    .roundToInt()
                    .coerceIn(MIN_EFFECTIVE_WPM, MAX_EFFECTIVE_WPM)
            }
        return ReadingSession(
            id = id,
            bookId = bookId,
            mode = mode,
            startedAt = startedAt,
            endedAt = endedAt.coerceAtLeast(startedAt),
            activeDurationMs = activeDurationMs,
            startChapterIndex = start.chapterIndex,
            startTokenIndex = start.tokenIndex,
            endChapterIndex = end.chapterIndex,
            endTokenIndex = end.tokenIndex,
            wordsRead = wordsRead,
            effectiveWpm = effectiveWpm,
            isWordCountEstimated = mode == ReadingSessionMode.READER,
        )
    }

    companion object {
        fun create(
            sessionKey: String,
            logicalSessionId: String,
            bookId: BookId,
            mode: ReadingSessionMode,
            timestamp: ReadingSessionTimestamp,
            location: ReadingSessionLocation,
            lastReaderWordIndex: Int?,
            lastReaderBasisFingerprint: Long? = null,
        ): LogicalReadingSession =
            LogicalReadingSession(
                sessionKey = sessionKey,
                logicalSessionId = logicalSessionId,
                bookId = bookId,
                mode = mode,
                logicalStartedAt = timestamp.wallTimeMillis,
                location = location,
                lastReaderWordIndex = lastReaderWordIndex,
                lastReaderBasisFingerprint = lastReaderBasisFingerprint,
                segments = linkedMapOf(),
            )

        fun restore(checkpoints: List<ReadingSessionCheckpoint>): LogicalReadingSession? {
            val ordered = checkpoints.sortedBy { it.dayStartedAt }
            val first = ordered.firstOrNull() ?: return null
            if (
                ordered.any {
                    it.sessionKey != first.sessionKey ||
                        it.logicalSessionId != first.logicalSessionId ||
                        it.bookId != first.bookId ||
                        it.mode != first.mode
                }
            ) {
                return null
            }
            val last = ordered.last()
            return LogicalReadingSession(
                sessionKey = first.sessionKey,
                logicalSessionId = first.logicalSessionId,
                bookId = first.bookId,
                mode = first.mode,
                logicalStartedAt = first.logicalStartedAt,
                location = last.end.copy(wordIndex = last.lastReaderWordIndex ?: -1),
                lastReaderWordIndex = last.lastReaderWordIndex,
                lastReaderBasisFingerprint = null,
                segments =
                ordered.associateTo(linkedMapOf()) { checkpoint ->
                    checkpoint.dayStartedAt to
                        DailyReadingSegment(
                            id = checkpoint.id,
                            dayStartedAt = checkpoint.dayStartedAt,
                            startedAt = checkpoint.startedAt,
                            endedAt = checkpoint.endedAt,
                            activeDurationMs = checkpoint.activeDurationMs.coerceAtLeast(0L),
                            start = checkpoint.start,
                            end = checkpoint.end,
                            wordsRead = checkpoint.wordsRead.coerceAtLeast(0),
                        )
                },
            ).apply {
                readerNeedsRebase = mode == ReadingSessionMode.READER
            }
        }
    }
}

private data class DailyReadingSegment(
    val id: String,
    val dayStartedAt: Long,
    val startedAt: Long,
    var endedAt: Long,
    var activeDurationMs: Long = 0L,
    val start: ReadingSessionLocation,
    var end: ReadingSessionLocation,
    var wordsRead: Int = 0,
)

internal fun localDayStartMillis(
    timestamp: Long,
    timeZone: TimeZone,
): Long =
    Calendar.getInstance(timeZone).apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun nextLocalDayStart(
    timestamp: Long,
    timeZone: TimeZone,
): Long =
    Calendar.getInstance(timeZone).apply {
        timeInMillis = localDayStartMillis(timestamp, timeZone)
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

private fun safeAdd(
    first: Long,
    second: Long,
): Long = if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second

private fun safeAdd(
    first: Int,
    second: Int,
): Int = if (Int.MAX_VALUE - first < second) Int.MAX_VALUE else first + second

private const val MILLIS_PER_MINUTE = 60_000L
private const val MIN_EFFECTIVE_WPM = 1
private const val MAX_EFFECTIVE_WPM = 2_000
