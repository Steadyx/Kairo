package com.kairo.reader.ui.rsvp

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Use the normal Android runtime, without Compose test-clock advancement, to measure exposure. */
@RunWith(AndroidJUnit4::class)
class RsvpPunctuationTimingDeviceTest {
    @Test
    fun fasterLiveTempoKeepsThePunctuationHoldOnScreen() {
        val fixture = RsvpDeviceFixture(
            listOf(word("Before"), word("home"), Token(".", TokenType.PUNCTUATION), word("Next")),
            RsvpConfig(
                enablePhraseChunking = false,
                tempoMsPerWord = 200L,
                periodPauseMs = 1000L,
                minPauseScale = 0.5,
                useAdaptiveTiming = false,
                usePunctuationLandingHold = false,
                startDelayMs = 0L,
                endDelayMs = 0L,
                rampUpFrames = 0,
                rampDownFrames = 0,
            ),
        )
        fixture.state = fixture.state.copy(launchTempoMsPerWord = 80L)
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    KairoTheme {
                        RsvpScreen(fixture.state, fixture.callbacks, fixture.dependencies)
                    }
                }
            }
            val deadline = SystemClock.elapsedRealtime() + 10_000L
            while (!fixture.finished && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20L)
            assertTrue("Playback should complete", fixture.finished)
            val index = fixture.consumed.indexOfFirst { frame -> frame.tokens.any { it.text == "home" } }
            assertTrue(index > 0)
            val hold = requireNotNull(fixture.consumed[index].punctuationHoldMs)
            val displayedMs = fixture.consumedAtMs[index] - fixture.consumedAtMs[index - 1]
            assertTrue("The test must exercise a full second of punctuation hold", hold >= 1000L)
            assertTrue("Live tempo must preserve the hold: $displayedMs ms vs $hold ms", displayedMs >= hold)
        }
    }

    private fun word(text: String) = Token(text, TokenType.WORD)
}
