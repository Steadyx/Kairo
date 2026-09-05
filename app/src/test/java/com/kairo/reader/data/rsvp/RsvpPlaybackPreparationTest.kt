package com.kairo.reader.data.rsvp

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.ComprehensionRsvpEngine
import com.kairo.reader.core.rsvp.RsvpEngine
import com.kairo.reader.data.token.TokenRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RsvpPlaybackPreparationTest {
    @Test
    fun cachedPlaybackUsesTheSameRampAndBlinkOrderAsDirectGeneration() = runTest {
        val tokens = List(12) { word("reading") }
        val config = RsvpConfig(
            tempoMsPerWord = 100L,
            blinkMode = BlinkMode.SUBTLE,
            enablePhraseChunking = false,
            startDelayMs = 150L,
            endDelayMs = 120L,
            rampUpFrames = 4,
            rampDownFrames = 4,
        )
        val engine = ComprehensionRsvpEngine()
        val repository = repository(StandardTestDispatcher(testScheduler), tokens, engine)
        val expected = engine.generateFrames(tokens, 0, config)
        val actual = repository.getFrames(BookId("blink"), 0, config).frames

        assertTrue("Fixture must produce blink separators", expected.any { it.tokens.none { token -> token.type == TokenType.WORD } })
        assertEquals(expected, actual)
    }

    @Test
    fun freshPlaybackDoesNotDiscardHyphenatedWordBeginning() = runTest {
        val tokens = listOf(word("before"), word("mother-in-law"), word("after"))
        val repository = repository(StandardTestDispatcher(testScheduler), tokens)
        val frames = repository.getFrames(BookId("split"), 0, RsvpConfig(enablePhraseChunking = false), 1).frames

        assertEquals(listOf("mother-", "in-", "law"), frames.take(3).flatMap(RsvpFrame::tokens).map(Token::text))
    }

    @Test
    fun distantStartsShareOneChapterGeneration() = runTest {
        val tokens = List(1600) { word("reading") }
        val engine = RecordingEngine()
        val repository = repository(StandardTestDispatcher(testScheduler), tokens, engine)
        val config = RsvpConfig(enablePhraseChunking = false)

        for (start in listOf(4, 520, 1050)) {
            val frames = repository.getFrames(BookId("large"), 0, config, start).frames
            assertEquals(start, frames.first().originalTokenIndex)
        }

        assertEquals(listOf(0), engine.starts)
    }

    @Test
    fun previewRetainsParenthesesBeforeItsAnalysisWindow() = runTest {
        val tokens = listOf(Token("(", TokenType.PUNCTUATION)) + List(300) { word("aside") } +
            listOf(word("continued"), Token(")", TokenType.PUNCTUATION))
        val config = RsvpConfig(enablePhraseChunking = false, useParentheticalAside = true)
        val repository = repository(StandardTestDispatcher(testScheduler), tokens)
        val expected = ComprehensionRsvpEngine().generateFrames(tokens, 301, config)
        val preview = repository.getPreviewFrames(tokens, 301, config, 2).frames

        assertEquals(expected, preview)
    }

    private fun repository(
        dispatcher: CoroutineDispatcher,
        tokens: List<Token>,
        engine: RsvpEngine = ComprehensionRsvpEngine(),
    ): RsvpFrameRepositoryImpl = RsvpFrameRepositoryImpl(
        tokenRepository = object : TokenRepository {
            override suspend fun getTokens(bookId: BookId, chapterIndex: Int, chapter: Chapter?): List<Token> = tokens
        },
        engine = engine,
        dispatcherProvider = object : DispatcherProvider {
            override val default: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
        },
    )

    private fun word(text: String): Token = Token(text, TokenType.WORD)

    private class RecordingEngine : RsvpEngine {
        val starts = mutableListOf<Int>()

        override fun generateFrames(tokens: List<Token>, startIndex: Int, config: RsvpConfig): List<RsvpFrame> {
            starts += startIndex
            return ComprehensionRsvpEngine().generateFrames(tokens, startIndex, config)
        }
    }
}
