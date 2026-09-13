package com.kairo.reader.ui.rsvp

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.ui.theme.KairoTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RsvpSweepDeviceTest {
    @get:Rule
    val rule = createAndroidComposeRule<TestActivity>()

    @Test
    fun fullChapterSweepSurvivesBothOrientationChangesAndSettingsReload() {
        val fixture = RsvpDeviceFixture(List(40) { word() }, config(), startIndex = 20)
        fixture.state = fixture.state.copy(initialIsPlaying = false)
        var landscape by mutableStateOf(false)
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            CompositionLocalProvider(LocalDensity provides if (landscape) Density(1f) else LocalDensity.current) {
                Box(
                    (if (landscape) Modifier.size(800.dp, 360.dp) else Modifier.fillMaxSize())
                        .testTag("sweep-surface"),
                ) {
                    KairoTheme { RsvpScreen(fixture.state, fixture.callbacks, fixture.dependencies) }
                }
            }
        }
        awaitLoaded(fixture, 1)
        assertEquals(20, requireNotNull(fixture.position).tokenIndex)
        sweepEntireChapter(fixture)

        for ((index, orientation) in listOf(true, false).withIndex()) {
            rule.runOnIdle { landscape = orientation }
            restoration.emulateSavedInstanceStateRestore()
            awaitLoaded(fixture, index + 2)
            assertEquals(39, requireNotNull(fixture.position).tokenIndex)
            sweepEntireChapter(fixture)
        }

        rule.runOnIdle {
            fixture.state = fixture.state.copy(
                book = fixture.state.book.copy(startIndex = 39),
                profile = fixture.state.profile.copy(config = config().copy(commaPauseMs = 90L, blinkMode = BlinkMode.SUBTLE)),
            )
        }
        awaitLoaded(fixture, 4)
        assertEquals(39, requireNotNull(fixture.position).tokenIndex)
        sweepEntireChapter(fixture)
    }

    @Test
    fun largeChapterReloadPreparationIsMeasuredOffTheUiThread() = runBlocking<Unit> {
        val fixture = RsvpDeviceFixture(List(5000) { word() }, config())
        val times = mutableListOf<Long>()
        for (start in listOf(2500, 1000, 4000, 2500)) {
            val before = SystemClock.elapsedRealtimeNanos()
            val frames = fixture.dependencies.frameRepository.getSeekableFrames(
                fixture.state.book.bookId,
                0,
                config(),
                start,
            )
            times += (SystemClock.elapsedRealtimeNanos() - before) / 1_000_000L
            assertEquals(0, frames.frames.first().originalTokenIndex)
            assertEquals(4999, frames.frames.last().originalTokenIndex)
            assertTrue(frames.initialRampStartFrameIndex > 0)
        }
        Log.i("RsvpSweepBenchmark", "5000 words: cold=${times.first()} ms, cached reloads=${times.drop(1)} ms")
    }

    private fun sweepEntireChapter(fixture: RsvpDeviceFixture) {
        repeat(8) { sweep(forward = false) }
        rule.runOnIdle { assertEquals(0, requireNotNull(fixture.position).tokenIndex) }
        repeat(8) { sweep(forward = true) }
        rule.runOnIdle { assertEquals(39, requireNotNull(fixture.position).tokenIndex) }
    }

    private fun sweep(forward: Boolean) {
        rule.onNodeWithTag("sweep-surface").performTouchInput {
            val left = Offset(width * 0.15f, height * 0.4f)
            val right = Offset(width * 0.85f, height * 0.4f)
            swipe(if (forward) left else right, if (forward) right else left, durationMillis = 160L)
        }
        rule.waitForIdle()
    }

    private fun awaitLoaded(fixture: RsvpDeviceFixture, count: Int) {
        rule.waitUntil(timeoutMillis = 10_000) { fixture.loads.get() >= count }
        rule.waitForIdle()
    }

    private fun word(): Token = Token("reading", TokenType.WORD)
    private fun config(): RsvpConfig = RsvpConfig(enablePhraseChunking = false, blinkMode = BlinkMode.OFF)
}
