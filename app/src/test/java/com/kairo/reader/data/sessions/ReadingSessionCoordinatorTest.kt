package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingSession
import com.kairo.reader.core.model.ReadingSessionItem
import com.kairo.reader.core.model.ReadingSessionMode
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingSessionCoordinatorTest {
    @Test
    fun fourMinuteReaderSessionWithForwardProgressPersists() =
        runTest {
            val repository = FakeReadingSessionRepository()
            val clock = FakeReadingSessionClock(wallTime = 500_000L)
            val bookId = BookId("short-reader")
            val coordinator = coordinator(repository, clock, logicalId = "logical-short-reader")

            coordinator.beginReader(bookId, progress(location(0), listOf(100)), active = true)
            clock.advance(240_000L)
            coordinator.moveReader(bookId, progress(location(20), listOf(100)))
            coordinator.finalizeReader(bookId)
            coordinator.awaitIdle()

            val session = repository.sessions.single()
            assertEquals(ReadingSessionMode.READER, session.mode)
            assertEquals(240_000L, session.activeDurationMs)
            assertEquals(20, session.wordsRead)
        }

    @Test
    fun fourMinuteRsvpSessionWithConsumedWordsPersists() =
        runTest {
            val repository = FakeReadingSessionRepository()
            val clock = FakeReadingSessionClock(wallTime = 750_000L)
            val bookId = BookId("short-rsvp")
            val coordinator = coordinator(repository, clock, logicalId = "logical-short-rsvp")

            coordinator.beginTimed(
                bookId = bookId,
                mode = ReadingSessionMode.RSVP,
                location = location(0),
                active = true,
            )
            clock.advance(240_000L)
            coordinator.consumeTimedFrame(
                bookId = bookId,
                mode = ReadingSessionMode.RSVP,
                location = location(100),
                words = 100,
            )
            coordinator.finalizeTimed(bookId, ReadingSessionMode.RSVP)
            coordinator.awaitIdle()

            val session = repository.sessions.single()
            assertEquals(ReadingSessionMode.RSVP, session.mode)
            assertEquals(240_000L, session.activeDurationMs)
            assertEquals(100, session.wordsRead)
        }

    @Test
    fun checkpointResumesAcrossCoordinatorAndFinalizeIsIdempotent() =
        runTest {
            val repository = FakeReadingSessionRepository()
            val clock = FakeReadingSessionClock(wallTime = 1_000_000L)
            val bookId = BookId("book")
            val counts = listOf(100)
            val first = coordinator(repository, clock, logicalId = "logical-1")

            first.beginReader(bookId, progress(location(word = 10), counts), active = true)
            clock.advance(120_000L)
            first.checkpointReader(bookId)
            first.awaitIdle()

            assertTrue(repository.checkpoints.isNotEmpty())

            val restored = coordinator(repository, clock, logicalId = "unused-new-id")
            restored.beginReader(bookId, progress(location(word = 10), counts), active = true)
            clock.advance(240_000L)
            restored.moveReader(bookId, progress(location(word = 40), counts))
            restored.finalizeReader(bookId)
            restored.finalizeReader(bookId)
            restored.awaitIdle()

            val session = repository.sessions.single()
            assertEquals(360_000L, session.activeDurationMs)
            assertEquals(30, session.wordsRead)
            assertTrue(session.id.startsWith("logical-1:"))
            assertTrue(repository.checkpoints.isEmpty())
        }

    @Test
    fun readerCountsForwardMovementAndReplayWithoutSubtractingBacktracking() =
        runTest {
            val repository = FakeReadingSessionRepository()
            val clock = FakeReadingSessionClock(wallTime = 2_000_000L)
            val bookId = BookId("book")
            val coordinator = coordinator(repository, clock, logicalId = "logical-2")

            coordinator.beginReader(bookId, progress(location(word = 10), listOf(100)), active = true)
            clock.advance(300_000L)
            coordinator.moveReader(bookId, progress(location(word = 20), listOf(100)))
            coordinator.moveReader(bookId, progress(location(word = 5), listOf(100)))
            coordinator.moveReader(bookId, progress(location(word = 20), listOf(100)))
            coordinator.finalizeReader(bookId)
            coordinator.awaitIdle()

            assertEquals(25, repository.sessions.single().wordsRead)
        }

    @Test
    fun monotonicDurationIsSplitAcrossLocalMidnight() =
        runTest {
            val utc = TimeZone.getTimeZone("UTC")
            val start =
                Calendar.getInstance(utc).apply {
                    set(2026, Calendar.JANUARY, 1, 23, 55, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            val repository = FakeReadingSessionRepository()
            val clock = FakeReadingSessionClock(wallTime = start)
            val bookId = BookId("book")
            val coordinator = coordinator(repository, clock, logicalId = "logical-3", timeZone = utc)

            coordinator.beginReader(bookId, progress(location(word = 0), listOf(200)), active = true)
            clock.advance(wallDelta = 600_000L, elapsedDelta = 600_000L)
            coordinator.moveReader(bookId, progress(location(word = 100), listOf(200)))
            coordinator.finalizeReader(bookId)
            coordinator.awaitIdle()

            assertEquals(2, repository.sessions.size)
            assertEquals(listOf(300_000L, 300_000L), repository.sessions.map { it.activeDurationMs })
            assertEquals(600_000L, repository.sessions.sumOf { it.activeDurationMs })
            assertEquals(100, repository.sessions.sumOf { it.wordsRead })
            assertEquals(2, repository.sessions.map { localDayStartMillis(it.startedAt, utc) }.distinct().size)
        }

    @Test
    fun wordCountBackfillRebasesBeforeCountingTheNextWord() =
        runTest {
            val repository = FakeReadingSessionRepository()
            val clock = FakeReadingSessionClock(wallTime = 3_000_000L)
            val bookId = BookId("book")
            val coordinator = coordinator(repository, clock, logicalId = "logical-backfill")

            coordinator.beginReader(
                bookId,
                progress(chapterLocation(chapter = 1, word = 0), listOf(0, 100)),
                active = true,
            )
            clock.advance(300_000L)
            coordinator.moveReader(
                bookId,
                progress(chapterLocation(chapter = 1, word = 0), listOf(500, 100)),
            )
            coordinator.moveReader(
                bookId,
                progress(chapterLocation(chapter = 1, word = 1), listOf(500, 100)),
            )
            coordinator.finalizeReader(bookId)
            coordinator.awaitIdle()

            assertEquals(1, repository.sessions.single().wordsRead)
        }

    @Test
    fun previousChapterCountChangeDoesNotLookLikeCurrentChapterProgress() =
        runTest {
            val repository = FakeReadingSessionRepository()
            val clock = FakeReadingSessionClock(wallTime = 4_000_000L)
            val bookId = BookId("book")
            val coordinator = coordinator(repository, clock, logicalId = "logical-prefix")

            coordinator.beginReader(
                bookId,
                progress(chapterLocation(chapter = 2, word = 20), listOf(100, 0, 100)),
                active = true,
            )
            clock.advance(300_000L)
            coordinator.moveReader(
                bookId,
                progress(chapterLocation(chapter = 2, word = 20), listOf(100, 250, 100)),
            )
            coordinator.moveReader(
                bookId,
                progress(chapterLocation(chapter = 2, word = 21), listOf(100, 250, 100)),
            )
            coordinator.finalizeReader(bookId)
            coordinator.awaitIdle()

            assertEquals(1, repository.sessions.single().wordsRead)
        }

    @Test
    fun explicitRebaseIsAnOrderingBoundaryForCoalescedMoves() =
        runTest {
            val repository = FakeReadingSessionRepository()
            val clock = FakeReadingSessionClock(wallTime = 4_500_000L)
            val bookId = BookId("book")
            val counts = listOf(100, 100)
            val basis = ReaderWordBasis.from(counts)
            val coordinator = coordinator(repository, clock, logicalId = "logical-boundary")

            coordinator.beginReader(bookId, basis.progress(location(0)), active = true)
            clock.advance(300_000L)
            coordinator.moveReader(bookId, basis.progress(location(10)))
            coordinator.rebaseReader(bookId)
            coordinator.moveReader(bookId, basis.progress(chapterLocation(chapter = 1, word = 50)))
            coordinator.moveReader(bookId, basis.progress(chapterLocation(chapter = 1, word = 51)))
            coordinator.finalizeReader(bookId)
            coordinator.awaitIdle()

            assertEquals(11, repository.sessions.single().wordsRead)
        }

    @Test
    fun progressSaturationCoalescesWithoutDroppingFinalizationOrWords() =
        runTest {
            val repository = FakeReadingSessionRepository()
            val clock = FakeReadingSessionClock(wallTime = 5_000_000L)
            val bookId = BookId("book")
            val coordinator = coordinator(repository, clock, logicalId = "logical-stress")
            val basis = ReaderWordBasis.from(listOf(20_000))

            coordinator.beginReader(bookId, basis.progress(location(word = 0)), active = true)
            clock.advance(300_000L)
            repeat(10_000) { index ->
                coordinator.moveReader(bookId, basis.progress(location(word = index + 1)))
            }
            coordinator.finalizeReader(bookId)
            coordinator.awaitIdle()

            assertEquals(10_000, repository.sessions.single().wordsRead)
            assertTrue(repository.checkpoints.isEmpty())
        }

    @Test
    fun repositoryFailureIsReportedAndActorRecoversWithoutHangingBarrier() =
        runTest {
            val failure = IllegalStateException("injected load failure")
            val repository = FakeReadingSessionRepository().apply { nextLoadFailure = failure }
            val clock = FakeReadingSessionClock(wallTime = 6_000_000L)
            val errors = mutableListOf<Throwable>()
            val bookId = BookId("book")
            val coordinator =
                coordinator(
                    repository = repository,
                    clock = clock,
                    logicalId = "logical-recovery",
                    onError = errors::add,
                )

            coordinator.beginReader(bookId, progress(location(0), listOf(100)), active = true)
            coordinator.awaitIdle()
            assertSame(failure, errors.single())

            coordinator.beginReader(bookId, progress(location(0), listOf(100)), active = true)
            clock.advance(300_000L)
            coordinator.moveReader(bookId, progress(location(10), listOf(100)))
            coordinator.finalizeReader(bookId)
            coordinator.awaitIdle()

            assertEquals(10, repository.sessions.single().wordsRead)
        }

    @Test
    fun startupFinalizesShortProgressCheckpointAndRetainsNoProgressCheckpointIdempotently() =
        runTest {
            val repository = FakeReadingSessionRepository()
            val clock = FakeReadingSessionClock(wallTime = 7_000_000L)
            val shortProgressBook = BookId("short-progress")
            val noProgressBook = BookId("no-progress")
            val writer = coordinator(repository, clock, logicalId = "writer")

            writer.beginReader(shortProgressBook, progress(location(0), listOf(100)), active = true)
            clock.advance(240_000L)
            writer.moveReader(shortProgressBook, progress(location(20), listOf(100)))
            writer.checkpointReader(shortProgressBook)
            writer.beginReader(noProgressBook, progress(location(0), listOf(100)), active = true)
            clock.advance(60_000L)
            writer.checkpointReader(noProgressBook)
            writer.awaitIdle()

            val recovered = coordinator(repository, clock, logicalId = "unused")
            recovered.awaitIdle()

            val recoveredSession = repository.sessions.single()
            assertEquals(shortProgressBook, recoveredSession.bookId)
            assertEquals(240_000L, recoveredSession.activeDurationMs)
            assertEquals(20, recoveredSession.wordsRead)
            assertEquals(
                listOf(ReadingSessionCoordinator.readerSessionKey(noProgressBook)),
                repository.checkpoints.map { it.sessionKey }.distinct(),
            )

            val secondRecovery = coordinator(repository, clock, logicalId = "unused-again")
            secondRecovery.awaitIdle()
            assertEquals(1, repository.sessions.size)
            assertEquals(1, repository.checkpoints.map { it.sessionKey }.distinct().size)
        }

    @Test
    fun lifecycleCommandAfterActorShutdownIsReportedInsteadOfSilentlyQueued() =
        runTest {
            val actorJob = SupervisorJob()
            val errors = mutableListOf<Throwable>()
            val coordinator =
                ReadingSessionCoordinator(
                    scope = CoroutineScope(StandardTestDispatcher(testScheduler) + actorJob),
                    repository = FakeReadingSessionRepository(),
                    clock = FakeReadingSessionClock(wallTime = 8_000_000L),
                    onError = errors::add,
                )
            testScheduler.runCurrent()

            actorJob.cancel()
            testScheduler.runCurrent()
            coordinator.finalizeReader(BookId("book"))

            assertTrue(errors.single().message.orEmpty().contains("closed"))
        }

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        repository: ReadingSessionRepository,
        clock: ReadingSessionClock,
        logicalId: String,
        timeZone: TimeZone = TimeZone.getTimeZone("UTC"),
        onError: (Throwable) -> Unit = {},
    ): ReadingSessionCoordinator =
        ReadingSessionCoordinator(
            scope = backgroundScope,
            repository = repository,
            clock = clock,
            timeZoneProvider = { timeZone },
            logicalIdProvider = { logicalId },
            onError = onError,
        )

    private fun location(word: Int): ReadingSessionLocation =
        ReadingSessionLocation(chapterIndex = 0, tokenIndex = word, wordIndex = word)

    private fun chapterLocation(
        chapter: Int,
        word: Int,
    ): ReadingSessionLocation =
        ReadingSessionLocation(chapterIndex = chapter, tokenIndex = word, wordIndex = word)

    private fun progress(
        location: ReadingSessionLocation,
        counts: List<Int>,
    ): ReaderProgress = ReaderWordBasis.from(counts).progress(location)
}

private class FakeReadingSessionClock(
    private var wallTime: Long,
    private var elapsedTime: Long = 0L,
) : ReadingSessionClock {
    override fun wallTimeMillis(): Long = wallTime

    override fun elapsedRealtimeMillis(): Long = elapsedTime

    fun advance(
        wallDelta: Long,
        elapsedDelta: Long = wallDelta,
    ) {
        wallTime += wallDelta
        elapsedTime += elapsedDelta
    }
}

private class FakeReadingSessionRepository : ReadingSessionRepository {
    val checkpoints = mutableListOf<ReadingSessionCheckpoint>()
    private val sessionsById = linkedMapOf<String, ReadingSession>()
    val sessions: List<ReadingSession>
        get() = sessionsById.values.toList()
    var nextLoadFailure: Throwable? = null

    override fun observeSessions(): Flow<List<ReadingSessionItem>> = emptyFlow()

    override suspend fun add(session: ReadingSession): Boolean {
        sessionsById[session.id] = session
        return true
    }

    override suspend fun loadCheckpoints(sessionKey: String): List<ReadingSessionCheckpoint> =
        nextLoadFailure?.let { failure ->
            nextLoadFailure = null
            throw failure
        } ?: checkpoints.filter { it.sessionKey == sessionKey }

    override suspend fun loadAllCheckpoints(): List<ReadingSessionCheckpoint> = checkpoints.toList()

    override suspend fun saveCheckpoints(
        sessionKey: String,
        checkpoints: List<ReadingSessionCheckpoint>,
    ): Boolean {
        this.checkpoints.removeAll { it.sessionKey == sessionKey }
        this.checkpoints.addAll(checkpoints)
        return true
    }

    override suspend fun finalizeCheckpoints(
        sessionKey: String,
        sessions: List<ReadingSession>,
    ): Boolean {
        sessions.forEach { sessionsById[it.id] = it }
        checkpoints.removeAll { it.sessionKey == sessionKey }
        return true
    }

    override suspend fun deleteForBook(bookId: BookId) = Unit
}
