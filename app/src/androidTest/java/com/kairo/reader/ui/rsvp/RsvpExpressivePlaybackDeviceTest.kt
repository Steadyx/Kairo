package com.kairo.reader.ui.rsvp

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.defaultConfig
import com.kairo.reader.core.rsvp.ComprehensionRsvpEngine
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy
import com.kairo.reader.core.tokenization.Tokenizer
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises authored HTML through the repository and real grouped Narrative playback. */
@RunWith(AndroidJUnit4::class)
class RsvpExpressivePlaybackDeviceTest {
    @Test
    fun authoredStressAndContextualThoughtsReachTheDisplayWithoutLosingWords() {
        val text = "I want that one. I said Tuesday, then waited before opening the door."
        val tokens = Tokenizer().tokenize(Chapter(0, null, "<p>${text.replace("Tuesday", "<em>Tuesday</em>")}</p>", text))
        val options = RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH)
        val config = RsvpProfile.NARRATIVE.defaultConfig().copy(
            maxWordsPerUnit = 3,
            maxCharsPerUnit = 32,
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
        )
        val fixture = RsvpDeviceFixture(tokens, config)
        fixture.state = fixture.state.copy(book = fixture.state.book.copy(generationOptions = options))
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent { KairoTheme { RsvpScreen(fixture.state, fixture.callbacks, fixture.dependencies) } }
            }
            val deadline = SystemClock.elapsedRealtime() + 20_000L
            while (!fixture.finished && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20L)
            assertTrue("Narrative playback finishes", fixture.finished)
            val played = fixture.consumed.filter { frame -> frame.tokens.any { it.type == TokenType.WORD } }
            assertTrue("Fixture exercises grouping", played.any { frame -> frame.tokens.count { it.type == TokenType.WORD } > 1 })
            assertEquals(
                tokens.indices.filter { tokens[it].type == TokenType.WORD },
                played.flatMap { frame ->
                    (frame.displayOriginalStartIndex until frame.displayOriginalEndExclusive).filter { tokens[it].type == TokenType.WORD }
                }.distinct(),
            )
            val that = played.first { frame -> frame.tokens.any { it.text == "that" } }
            assertEquals(0, that.phraseStartTokenIndex)
            val before = played.first { frame -> frame.tokens.any { it.text == "before" } }
            assertEquals(tokens.indexOfFirst { it.text == "before" }, before.phraseStartTokenIndex)
            val accent = played.first { frame -> frame.tokens.any { it.authorEmphasis } }
            val neutral = ComprehensionRsvpEngine().generateFrames(tokens.map { it.copy(authorEmphasis = false) }, 0, config, options)
                .first { it.originalTokenIndex == accent.originalTokenIndex }
            assertTrue("Authored word retains extra exposure in playback", accent.durationMs > neutral.durationMs)
            val consumedIndex = fixture.consumed.indexOf(accent)
            assertTrue(consumedIndex > 0)
            assertTrue(
                "The scheduler preserves that exposure",
                fixture.consumedAtMs[consumedIndex] - fixture.consumedAtMs[consumedIndex - 1] >= accent.durationMs,
            )
        }
    }
}
