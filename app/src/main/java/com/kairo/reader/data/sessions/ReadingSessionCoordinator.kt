package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingSessionMode
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class ReadingSessionCoordinator(
    private val scope: CoroutineScope,
    private val repository: ReadingSessionRepository,
    private val clock: ReadingSessionClock,
    private val timeZoneProvider: () -> TimeZone = TimeZone::getDefault,
    private val logicalIdProvider: () -> String = { UUID.randomUUID().toString() },
    private val onError: (Throwable) -> Unit = {},
) {
    private val commands = Channel<Command>(capacity = COMMAND_CAPACITY)
    private val sessions = mutableMapOf<String, LogicalReadingSession>()
    private val progressLock = Any()
    private val pendingProgress = linkedMapOf<Long, PendingProgress>()
    private var currentProgressGeneration = 0L
    private val actorJob =
        scope.launch {
            recoverEligibleCheckpoints()
            for (command in commands) executeSafely(command)
        }

    init {
        actorJob.invokeOnCompletion { failure ->
            commands.close(failure)
        }
    }

    fun beginReader(
        bookId: BookId,
        progress: ReaderProgress,
        active: Boolean,
    ) {
        enqueueOrdered(
            Command.Begin(
                sessionKey = readerSessionKey(bookId),
                bookId = bookId,
                mode = ReadingSessionMode.READER,
                location = progress.location,
                readerProgress = progress,
                active = active,
                stamp = stamp(),
            )
        )
    }

    fun moveReader(
        bookId: BookId,
        progress: ReaderProgress,
    ) {
        val sessionKey = readerSessionKey(bookId)
        val stampedProgress = StampedReaderProgress(progress, stamp())
        synchronized(progressLock) {
            val generation = currentProgressGeneration
            val pending = pendingProgress.getOrPut(generation, ::PendingProgress)
            pending.readerMoves[sessionKey]?.append(stampedProgress)
                ?: run { pending.readerMoves[sessionKey] = ReaderMoveBatch(stampedProgress) }
            if (!pending.flushEnqueued) {
                pending.flushEnqueued = true
                enqueueProgressFlush(generation)
            }
        }
    }

    fun rebaseReader(bookId: BookId) {
        enqueueOrdered(Command.RebaseReader(readerSessionKey(bookId)))
    }

    fun checkpointReader(bookId: BookId) {
        enqueueOrdered(Command.Checkpoint(readerSessionKey(bookId), stamp()))
    }

    fun finalizeReader(bookId: BookId) {
        enqueueOrdered(Command.Finalize(readerSessionKey(bookId), stamp()))
    }

    fun beginTimed(
        bookId: BookId,
        mode: ReadingSessionMode,
        location: ReadingSessionLocation,
        active: Boolean,
    ) {
        require(mode != ReadingSessionMode.READER)
        enqueueOrdered(
            Command.Begin(
                sessionKey = timedSessionKey(bookId, mode),
                bookId = bookId,
                mode = mode,
                location = location,
                readerProgress = null,
                active = active,
                stamp = stamp(),
            )
        )
    }

    fun consumeTimedFrame(
        bookId: BookId,
        mode: ReadingSessionMode,
        location: ReadingSessionLocation,
        words: Int,
    ) {
        require(mode != ReadingSessionMode.READER)
        val sessionKey = timedSessionKey(bookId, mode)
        val frame = StampedTimedFrame(location, words.coerceAtLeast(0), stamp())
        synchronized(progressLock) {
            val generation = currentProgressGeneration
            val pending = pendingProgress.getOrPut(generation, ::PendingProgress)
            pending.timedFrames[sessionKey]?.append(frame)
                ?: run { pending.timedFrames[sessionKey] = TimedFrameBatch(frame) }
            if (!pending.flushEnqueued) {
                pending.flushEnqueued = true
                enqueueProgressFlush(generation)
            }
        }
    }

    fun setTimedActive(
        bookId: BookId,
        mode: ReadingSessionMode,
        active: Boolean,
    ) {
        require(mode != ReadingSessionMode.READER)
        enqueueOrdered(Command.SetActive(timedSessionKey(bookId, mode), active, stamp()))
    }

    fun checkpointTimed(
        bookId: BookId,
        mode: ReadingSessionMode,
    ) {
        require(mode != ReadingSessionMode.READER)
        enqueueOrdered(Command.Checkpoint(timedSessionKey(bookId, mode), stamp()))
    }

    fun finalizeTimed(
        bookId: BookId,
        mode: ReadingSessionMode,
    ) {
        require(mode != ReadingSessionMode.READER)
        enqueueOrdered(Command.Finalize(timedSessionKey(bookId, mode), stamp()))
    }

    internal suspend fun awaitIdle() {
        val completion = CompletableDeferred<Unit>()
        commands.send(Command.Barrier(completion))
        completion.await()
    }

    private suspend fun executeSafely(command: Command) {
        runCatching { handle(command) }
            .exceptionOrNull()
            ?.let(::reportFailure)
    }

    private suspend fun handle(command: Command) {
        when (command) {
            is Command.Begin -> begin(command)
            is Command.FlushProgress -> flushProgress(command.generation)
            is Command.RebaseReader -> sessions[command.sessionKey]?.requestReaderRebase()
            is Command.SetActive -> setActive(command)
            is Command.Checkpoint -> checkpoint(command)
            is Command.Finalize -> finalize(command)
            is Command.Barrier -> command.completion.complete(Unit)
        }
    }

    private suspend fun begin(command: Command.Begin) {
        val existing = sessions[command.sessionKey]
        val session =
            existing ?: run {
                val restored =
                    LogicalReadingSession.restore(repository.loadCheckpoints(command.sessionKey))
                (restored ?: newSession(command)).also { loaded ->
                    command.readerProgress?.let(loaded::rebaseReader)
                    sessions[command.sessionKey] = loaded
                }
            }
        if (command.active) {
            session.resume(command.stamp.timestamp, command.stamp.timeZone)
        } else if (session.isActive) {
            session.pause(command.stamp.timestamp, command.stamp.timeZone)
        }
    }

    private fun newSession(command: Command.Begin): LogicalReadingSession =
        LogicalReadingSession.create(
            sessionKey = command.sessionKey,
            logicalSessionId = logicalIdProvider(),
            bookId = command.bookId,
            mode = command.mode,
            timestamp = command.stamp.timestamp,
            location = command.location,
            lastReaderWordIndex = command.readerProgress?.absoluteWordIndex,
            lastReaderBasisFingerprint = command.readerProgress?.basisFingerprint,
        )

    private fun flushProgress(generation: Long) {
        val snapshot =
            synchronized(progressLock) {
                pendingProgress.remove(generation)?.snapshot() ?: ProgressSnapshot.EMPTY
            }
        snapshot.readerMoves.forEach { (sessionKey, batch) ->
            sessions[sessionKey]?.moveReaderBatch(
                firstProgress = batch.first.progress,
                finalProgress = batch.last.progress,
                internalForwardWords = batch.internalForwardWords,
                timestamp = batch.last.stamp.timestamp,
                timeZone = batch.last.stamp.timeZone,
            )
        }
        snapshot.timedFrames.forEach { (sessionKey, batch) ->
            sessions[sessionKey]?.consumeTimedFrame(
                words = batch.words,
                newLocation = batch.last.location,
                timestamp = batch.last.stamp.timestamp,
                timeZone = batch.last.stamp.timeZone,
            )
        }
    }

    private suspend fun setActive(command: Command.SetActive) {
        val session = sessions[command.sessionKey] ?: return
        if (command.active) {
            session.resume(command.stamp.timestamp, command.stamp.timeZone)
        } else {
            session.pause(command.stamp.timestamp, command.stamp.timeZone)
            saveCheckpoint(session, command.stamp)
        }
    }

    private suspend fun checkpoint(command: Command.Checkpoint) {
        val session = sessions[command.sessionKey] ?: return
        session.pause(command.stamp.timestamp, command.stamp.timeZone)
        saveCheckpoint(session, command.stamp)
    }

    private suspend fun saveCheckpoint(
        session: LogicalReadingSession,
        stamp: StampedTime,
    ) {
        check(
            repository.saveCheckpoints(
                sessionKey = session.sessionKey,
                checkpoints = session.checkpoint(stamp.timestamp, stamp.timeZone),
            )
        ) { "Reading-session checkpoint was rejected for ${session.sessionKey}" }
    }

    private suspend fun finalize(command: Command.Finalize) {
        val resident = sessions[command.sessionKey]
        val session =
            resident ?: LogicalReadingSession.restore(repository.loadCheckpoints(command.sessionKey))
        val finalized = session?.finalSessions(command.stamp.timestamp, command.stamp.timeZone).orEmpty()
        check(repository.finalizeCheckpoints(command.sessionKey, finalized)) {
            "Reading-session finalization was rejected for ${command.sessionKey}"
        }
        if (resident != null) sessions.remove(command.sessionKey, resident)
    }

    private suspend fun recoverEligibleCheckpoints() {
        val checkpointResult = runCatching { repository.loadAllCheckpoints() }
        checkpointResult.exceptionOrNull()?.let { failure ->
            reportFailure(failure)
            return
        }
        val checkpoints = checkpointResult.getOrThrow()
        checkpoints.groupBy { it.sessionKey }.forEach { (sessionKey, group) ->
            runCatching {
                val session = LogicalReadingSession.restore(group) ?: return@forEach
                if (!session.isEligibleForFinalization()) return@forEach
                val recoveryStamp = stamp()
                val finalized =
                    session.finalSessions(recoveryStamp.timestamp, recoveryStamp.timeZone)
                check(repository.finalizeCheckpoints(sessionKey, finalized)) {
                    "Recovered reading-session finalization was rejected for $sessionKey"
                }
            }.exceptionOrNull()?.let(::reportFailure)
        }
    }

    private fun enqueueReliable(command: Command) {
        val result = commands.trySend(command)
        if (result.isSuccess) return
        if (result.isClosed) {
            onError(IllegalStateException("Reading-session coordinator is closed", result.exceptionOrNull()))
            return
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching { commands.send(command) }
                .exceptionOrNull()
                ?.let(::reportFailure)
        }
    }

    private fun enqueueOrdered(command: Command) {
        synchronized(progressLock) {
            currentProgressGeneration++
            enqueueReliable(command)
        }
    }

    private fun enqueueProgressFlush(generation: Long) {
        val command = Command.FlushProgress(generation)
        val result = commands.trySend(command)
        if (result.isSuccess) return
        if (result.isClosed) {
            pendingProgress.remove(generation)
            onError(IllegalStateException("Reading-session coordinator is closed", result.exceptionOrNull()))
            return
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching { commands.send(command) }
                .exceptionOrNull()
                ?.let { failure ->
                    synchronized(progressLock) { pendingProgress.remove(generation) }
                    reportFailure(failure)
                }
        }
    }

    private fun reportFailure(failure: Throwable) {
        if (failure is CancellationException) throw failure
        onError(failure)
    }

    private fun stamp(): StampedTime =
        StampedTime(
            timestamp = clock.timestamp(),
            timeZone = timeZoneProvider(),
        )

    private sealed interface Command {
        data class Begin(
            val sessionKey: String,
            val bookId: BookId,
            val mode: ReadingSessionMode,
            val location: ReadingSessionLocation,
            val readerProgress: ReaderProgress?,
            val active: Boolean,
            val stamp: StampedTime,
        ) : Command

        data class FlushProgress(val generation: Long) : Command

        data class RebaseReader(val sessionKey: String) : Command

        data class SetActive(val sessionKey: String, val active: Boolean, val stamp: StampedTime,) : Command

        data class Checkpoint(val sessionKey: String, val stamp: StampedTime,) : Command

        data class Finalize(val sessionKey: String, val stamp: StampedTime,) : Command

        data class Barrier(val completion: CompletableDeferred<Unit>) : Command
    }

    private data class StampedTime(val timestamp: ReadingSessionTimestamp, val timeZone: TimeZone,)

    private data class StampedReaderProgress(val progress: ReaderProgress, val stamp: StampedTime,)

    private class ReaderMoveBatch(firstProgress: StampedReaderProgress) {
        val first = firstProgress
        var last = firstProgress
            private set
        var internalForwardWords = 0
            private set

        fun append(progress: StampedReaderProgress) {
            val previousAbsolute = last.progress.absoluteWordIndex
            val nextAbsolute = progress.progress.absoluteWordIndex
            if (
                last.progress.basisFingerprint == progress.progress.basisFingerprint &&
                previousAbsolute != null &&
                nextAbsolute != null
            ) {
                internalForwardWords =
                    safeAdd(internalForwardWords, (nextAbsolute - previousAbsolute).coerceAtLeast(0))
            }
            last = progress
        }
    }

    private data class StampedTimedFrame(val location: ReadingSessionLocation, val words: Int, val stamp: StampedTime,)

    private class TimedFrameBatch(firstFrame: StampedTimedFrame) {
        var last = firstFrame
            private set
        var words = firstFrame.words
            private set

        fun append(frame: StampedTimedFrame) {
            words = safeAdd(words, frame.words)
            last = frame
        }
    }

    private data class ProgressSnapshot(val readerMoves: Map<String, ReaderMoveBatch>, val timedFrames: Map<String, TimedFrameBatch>,) {
        companion object {
            val EMPTY = ProgressSnapshot(emptyMap(), emptyMap())
        }
    }

    private class PendingProgress {
        val readerMoves = linkedMapOf<String, ReaderMoveBatch>()
        val timedFrames = linkedMapOf<String, TimedFrameBatch>()
        var flushEnqueued = false

        fun snapshot(): ProgressSnapshot =
            ProgressSnapshot(
                readerMoves = readerMoves.toMap(),
                timedFrames = timedFrames.toMap(),
            )
    }

    companion object {
        internal fun readerSessionKey(bookId: BookId): String = "reader:${bookId.value}"

        internal fun timedSessionKey(
            bookId: BookId,
            mode: ReadingSessionMode,
        ): String = "timed:${mode.name}:${bookId.value}"

        private const val COMMAND_CAPACITY = 32
    }
}

private fun safeAdd(
    first: Int,
    second: Int,
): Int = (first.toLong() + second.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
