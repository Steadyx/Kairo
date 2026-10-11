package com.kairo.reader.ui.rsvp

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.data.rsvp.RsvpFrameRepository
import com.kairo.reader.data.rsvp.RsvpFrameSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
