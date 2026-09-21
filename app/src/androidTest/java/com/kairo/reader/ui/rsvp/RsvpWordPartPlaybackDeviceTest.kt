package com.kairo.reader.ui.rsvp

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.defaultConfig
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy
import com.kairo.reader.core.tokenization.Tokenizer
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Covers tokenization, repository, phrase generation and actual on-device playback together. */
@RunWith(AndroidJUnit4::class)
class RsvpWordPartPlaybackDeviceTest {
    @Test
    fun normalProseReceivesAdvancingWordPartsDuringPlayback() {
        val tokens = Tokenizer().tokenize(
            Chapter(
                index = 0,
                title = "Word support fixture",
                htmlContent = "",
                plainText = "She saw the crystalline rock and made observations before the instructors came home.",
            ),
        )
        val config = RsvpProfile.BALANCED.defaultConfig().copy(
            tempoMsPerWord = 131L,
            maxChunkLength = 24,
            difficultWordSupport = 2.0,
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
        )
        val fixture = RsvpDeviceFixture(tokens, config)
        fixture.state = fixture.state.copy(
            book = fixture.state.book.copy(generationOptions = RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH)),
        )
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent { KairoTheme { RsvpScreen(fixture.state, fixture.callbacks, fixture.dependencies) } }
            }
            val deadline = SystemClock.elapsedRealtime() + 15_000L
            while (!fixture.finished && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20L)
            assertTrue("Playback must finish", fixture.finished)
            for (text in listOf("crystalline", "observations")) {
                assertPlayedParts(fixture, tokens.indexOfFirst { it.text == text }, text)
            }
            val ordinary = fixture.consumed.flatMap { it.tokens }.filter { it.text == "instructors" }
            assertEquals(1, ordinary.size)
            assertTrue(ordinary.none { it.isSubwordChunk })
        }
    }

    private fun assertPlayedParts(fixture: RsvpDeviceFixture, sourceIndex: Int, text: String) {
        val positions = fixture.consumed.indices.filter { index ->
            fixture.consumed[index].tokens.any { it.text == text && it.isSubwordChunk }
        }
        assertTrue("$text needs moving highlights", positions.size > 1)
        assertEquals("No gaps between parts", (positions.first()..positions.last()).toList(), positions)
        val parts = positions.map { index ->
            val frame = fixture.consumed[index]
            assertEquals(sourceIndex, frame.originalTokenIndex)
            assertTrue(!frame.isWordSeparation)
            val displayedMs = fixture.consumedAtMs[index] - fixture.consumedAtMs[index - 1]
            assertTrue("Part must remain visible for its reading time", displayedMs >= frame.durationMs)
            frame.tokens.single { it.isSubwordChunk }
        }
        assertEquals(0, parts.first().highlightStart)
        parts.zipWithNext().forEach { (left, right) -> assertEquals(left.highlightEndExclusive, right.highlightStart) }
        assertEquals(text, parts.joinToString("") { it.text.substring(it.highlightStart!!, it.highlightEndExclusive!!) })
    }
}
