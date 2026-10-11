package com.kairo.reader.ui.rsvp

import androidx.compose.runtime.snapshots.Snapshot
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.data.rsvp.RsvpFrameRepository
import com.kairo.reader.data.rsvp.RsvpFrameSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RsvpFramePreparationTest {
    private val config = RsvpConfig()
    private val book = RsvpBookContext(BookId("book"), 0, List(100) { Token("word", TokenType.WORD) }, 0)
    private val chapter = RsvpFrameSet(emptyList(), config.tempoMsPerWord)

    @Test
    fun cachedChapterDoesNotWaitForOpeningGeneration() = runTest {
        val repository = repository { error("Cached chapters must not generate opening frames") }

        assertNull(loadRsvpOpeningFrames(repository, book, config, 0, CompletableDeferred(chapter)))
    }

    @Test
    fun chapterFinishingFirstCancelsSlowOpeningGeneration() = runTest {
        val chapterReady = CompletableDeferred<RsvpFrameSet?>()
        var openingCancelled = false
        val repository = repository {
            try {
                awaitCancellation()
            } finally {
                openingCancelled = true
            }
        }
        val result = async { loadRsvpOpeningFrames(repository, book, config, 0, chapterReady) }
        runCurrent()
        chapterReady.complete(chapter)

        assertNull(result.await())
        assertTrue(openingCancelled)
    }

    @Test
    fun openingFailureLeavesChapterPreparationAvailable() = runTest {
        val chapterReady = CompletableDeferred<RsvpFrameSet?>()
        val repository = repository { error("Opening unavailable") }

        assertNull(loadRsvpOpeningFrames(repository, book, config, 0, chapterReady))
        assertFalse(chapterReady.isCancelled)
        assertTrue(chapterReady.complete(chapter))
    }

    @Test
    fun navigationActivatesReadyChapterAndRebasesScrubbingWithoutWaitingForTheOpening() = runTest {
        val frames = List(100) { index ->
            RsvpFrame(listOf(book.tokens[index]), 120L, originalTokenIndex = index, nextOriginalTokenIndex = index + 1)
        }
        val full = RsvpFrameSet(frames, 120L)
        val preview = RsvpFrameSet(frames.subList(40, 80), 120L)
        val runtime = RsvpRuntimeState().apply {
            frameIndex = 5
            currentTokenIndex = 45
            isScrubbing = true
            isPlaying = false
            dragStartFrameIndex = 2
        }
        var published: RsvpFrameSet? = null
        val handoff = async {
            awaitRsvpChapterHandoff(full, preview, runtime, book, config, repository { chapter }) { published = it }
        }
        runCurrent()
        assertFalse(handoff.isCompleted)

        assertSame(full, requireNotNull(runtime.activatePreparedChapter).invoke())
        assertSame(full, published)
        assertEquals(45, runtime.frameIndex)
        assertEquals(42, runtime.dragStartFrameIndex)
        assertTrue(runtime.isScrubbing)
        runtime.frameIndex = 30
        Snapshot.sendApplyNotifications()
        assertSame(full, handoff.await())
        assertEquals(30, runtime.frameIndex)
        assertNull(runtime.activatePreparedChapter)
    }

    @Test
    fun cancelingHandoffDiscardsItsNavigationCallback() = runTest {
        val frame = RsvpFrame(listOf(book.tokens.first()), 120L, originalTokenIndex = 0, nextOriginalTokenIndex = 1)
        val preview = RsvpFrameSet(listOf(frame), 120L)
        val runtime = RsvpRuntimeState()
        val handoff = async {
            awaitRsvpChapterHandoff(preview, preview, runtime, book, config, repository { chapter })
        }
        runCurrent()
        assertTrue(runtime.activatePreparedChapter != null)
        handoff.cancel()
        runCurrent()
        assertNull(runtime.activatePreparedChapter)
    }

    @Test
    fun navigationWinsWhenPausedHandoffPreparationIsAlreadyInFlight() = runTest {
        val frame = RsvpFrame(listOf(book.tokens.first()), 120L, originalTokenIndex = 0, nextOriginalTokenIndex = 1)
        val full = RsvpFrameSet(listOf(frame), 120L)
        val runtime = RsvpRuntimeState().apply { isPlaying = false }
        val release = CompletableDeferred<Unit>()
        val delayed = object : RsvpFrameRepository by repository({ chapter }) {
            override suspend fun getSeekableFrames(
                bookId: BookId,
                chapterIndex: Int,
                config: RsvpConfig,
                startIndex: Int,
                options: RsvpGenerationOptions,
            ): RsvpFrameSet {
                release.await()
                return full.copy()
            }
        }
        val handoff = async { awaitRsvpChapterHandoff(full, full, runtime, book, config, delayed) }
        runCurrent()
        requireNotNull(runtime.activatePreparedChapter).invoke()
        release.complete(Unit)

        assertSame("A stale paused handoff must not replace the chapter activated by navigation", full, handoff.await())
    }

    private fun repository(opening: suspend () -> RsvpFrameSet): RsvpFrameRepository =
        object : RsvpFrameRepository {
            override suspend fun getFrames(bookId: BookId, chapterIndex: Int, config: RsvpConfig, startIndex: Int): RsvpFrameSet = chapter

            override suspend fun getPreviewFrames(
                tokens: List<Token>,
                startIndex: Int,
                config: RsvpConfig,
                maxTokenCount: Int,
            ): RsvpFrameSet = opening()

            override fun clearCache() = Unit
        }
}
