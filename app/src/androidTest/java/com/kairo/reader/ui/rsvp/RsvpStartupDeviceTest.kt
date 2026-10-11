package com.kairo.reader.ui.rsvp

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.defaultConfig
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.core.tokenization.Tokenizer
import com.kairo.reader.data.rsvp.RsvpFrameRepository
import com.kairo.reader.data.rsvp.RsvpFrameSet
import com.kairo.reader.sample.SampleBooks
import com.kairo.reader.ui.theme.KairoTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RsvpStartupDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun slowPreparationKeepsPreviewStillThenReadsFromSelectedWordInOrder() {
        val fixture = fixture(startDelayMs = 0L)
        val releaseFrames = showWithBlockedPreparation(fixture)
        composeRule.onNodeWithText("Start", useUnmergedTree = true).assertIsDisplayed()
        // Keep preparation blocked beyond the preview's reading interval.
        composeRule.mainClock.advanceTimeBy(800L)
        composeRule.runOnIdle {
            assertTrue("The loading preview must not be consumed", fixture.consumed.isEmpty())
        }
        composeRule.onNodeWithText("Start", useUnmergedTree = true).assertIsDisplayed()
        releaseFrames.complete(Unit)
        composeRule.waitUntil(timeoutMillis = 10_000L) { fixture.finished }
        assertEquals(listOf(2, 3, 4), fixture.consumed.map { it.originalTokenIndex })
    }

    @Test
    fun coldPreparationOverlapsStartPauseWithoutSkippingTheFirstWord() {
        val fixture = fixture(startDelayMs = 1000L)
        val releaseFrames = showWithBlockedPreparation(fixture)
        composeRule.onNodeWithText("Start", useUnmergedTree = true).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
        // The app measures real preparation time; playback uses Compose's test clock.
        SystemClock.sleep(1200L)
        releaseFrames.complete(Unit)
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.mainClock.advanceTimeByFrame()
            fixture.loads.get() > 0
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue("The first word still needs its reading interval", fixture.consumed.isEmpty())
        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.runOnIdle {
            assertTrue("Do not add another full startup pause after loading", fixture.consumed.isNotEmpty())
            assertEquals(2, fixture.consumed.first().originalTokenIndex)
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(timeoutMillis = 10_000L) { fixture.finished }
        assertEquals(listOf(2, 3, 4), fixture.consumed.map { it.originalTokenIndex })
    }

    @Test
    fun preparedOpeningPlaysBeforeChapterArrivesAndWaitsAtItsBoundaryWithoutRepeating() {
        val fixture = fixture(startDelayMs = 0L, tokens = List(120) { Token("reading", TokenType.WORD) })
        val releaseFrames = showWithBlockedPreparation(fixture)
        composeRule.waitUntil(timeoutMillis = 15_000L) { fixture.consumed.size >= 64 }
        composeRule.mainClock.advanceTimeBy(1000L)
        composeRule.runOnIdle {
            assertEquals("Playback must start before full-chapter preparation completes", 0, fixture.loads.get())
            assertEquals((2 until 66).toList(), fixture.consumed.map { it.originalTokenIndex })
            assertTrue(!fixture.finished)
        }
        releaseFrames.complete(Unit)
        composeRule.waitUntil(timeoutMillis = 20_000L) { fixture.finished }
        assertEquals((2 until 120).toList(), fixture.consumed.map { it.originalTokenIndex })
    }

    @Test
    fun pausingTheOpeningKeepsTheSameWordWhenFullChapterArrives() {
        val fixture = fixture(startDelayMs = 0L, tokens = List(120) { Token("word$it", TokenType.WORD) })
        val releaseFrames = showWithBlockedPreparation(fixture)
        composeRule.waitUntil(timeoutMillis = 10_000L) { fixture.consumed.size >= 2 }
        composeRule.onRoot().performTouchInput { click() }
        val play = composeRule.activity.getString(R.string.content_desc_play)
        composeRule.waitUntil(timeoutMillis = 3000L) {
            composeRule.onAllNodesWithContentDescription(play).fetchSemanticsNodes().isNotEmpty()
        }
        val focus = composeRule.onNodeWithTag("rsvp-focus-word", useUnmergedTree = true)
        val pausedText = focus.fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        releaseFrames.complete(Unit)
        composeRule.waitUntil(timeoutMillis = 10_000L) { fixture.loads.get() >= 2 }
        focus.assertTextEquals(pausedText)
    }

    @Test
    fun startingNearTheEndDoesNotWaitForOrReplayTheWholeChapter() {
        val fixture = fixture(startDelayMs = 0L, tokens = List(120) { Token("reading", TokenType.WORD) })
        fixture.state = fixture.state.copy(book = fixture.state.book.copy(startIndex = 115, sessionStartIndex = 115))
        val releaseFrames = showWithBlockedPreparation(fixture)
        composeRule.waitUntil(timeoutMillis = 10_000L) { fixture.finished }
        assertEquals(0, fixture.loads.get())
        assertEquals((115 until 120).toList(), fixture.consumed.map { it.originalTokenIndex })
        releaseFrames.complete(Unit)
        composeRule.waitUntil(timeoutMillis = 10_000L) { fixture.loads.get() > 0 }
        composeRule.mainClock.advanceTimeBy(1000L)
        assertEquals((115 until 120).toList(), fixture.consumed.map { it.originalTokenIndex })
    }

    @Test
    fun measureFirstPlaybackOnAColdLargeEnglishChapter() {
        val paragraph = Tokenizer().tokenize(SampleBooks.starterBook().chapters[2])
        val fixture = RsvpDeviceFixture(List(8000) { paragraph[it % paragraph.size] }, RsvpProfile.BALANCED.defaultConfig())
        fixture.state = fixture.state.copy(book = fixture.state.book.copy(generationOptions = RsvpGenerationOptions.fromLanguageTag("en")))
        val startedAt = SystemClock.elapsedRealtime()
        composeRule.setContent {
            KairoTheme { RsvpScreen(fixture.state, fixture.callbacks, fixture.dependencies) }
        }
        composeRule.waitUntil(timeoutMillis = 15_000L) { fixture.consumed.isNotEmpty() }
        Log.i(
            "RsvpStartupBenchmark",
            "8000-token UI: first consumed=${fixture.consumedAtMs.first() - startedAt} ms, chapter loads=${fixture.loads.get()}",
        )
        composeRule.waitUntil(timeoutMillis = 15_000L) { fixture.loads.get() > 0 }
    }

    @Test
    fun replayDuringOpeningCanReachBeforeTheLaunchPositionOnceChapterIsReady() {
        val fixture = startBufferedMidChapter()

        composeRule.onRoot().performTouchInput { doubleClick() }

        composeRule.waitUntil(timeoutMillis = 3000L) { (fixture.position?.tokenIndex ?: 80) < 80 }
        assertTrue("Replay must reach text before the opening buffer", requireNotNull(fixture.position).tokenIndex < 80)
    }

    @Test
    fun backwardSwipeDuringOpeningCanReachBeforeTheLaunchPositionOnceChapterIsReady() {
        val fixture = startBufferedMidChapter()

        composeRule.onRoot().performTouchInput {
            swipe(Offset(width * 0.85f, height * 0.4f), Offset(width * 0.15f, height * 0.4f), durationMillis = 160L)
        }

        composeRule.waitUntil(timeoutMillis = 3000L) { (fixture.position?.tokenIndex ?: 80) < 80 }
        assertTrue("Swipe must reach text before the opening buffer", requireNotNull(fixture.position).tokenIndex < 80)
    }

    private fun startBufferedMidChapter(): RsvpDeviceFixture {
        val fixture = fixture(startDelayMs = 0L, tokens = List(200) { Token("word$it", TokenType.WORD) })
        fixture.state = fixture.state.copy(
            book = fixture.state.book.copy(startIndex = 80, sessionStartIndex = 80),
            profile = fixture.state.profile.copy(config = fixture.state.profile.config.copy(tempoMsPerWord = 600L)),
        )
        val releaseFrames = showWithBlockedPreparation(fixture)
        composeRule.waitUntil(timeoutMillis = 10_000L) { fixture.consumed.isNotEmpty() }
        assertEquals(0, fixture.loads.get())
        releaseFrames.complete(Unit)
        composeRule.waitUntil(timeoutMillis = 10_000L) { fixture.loads.get() > 0 }
        composeRule.waitForIdle()
        assertTrue("Exercise navigation before the opening has been consumed", fixture.consumed.size < 64)
        return fixture
    }

    private fun fixture(
        startDelayMs: Long,
        tokens: List<Token> = listOf("Earlier", "Before", "Start", "Next", "Last").map { Token(it, TokenType.WORD) },
    ): RsvpDeviceFixture =
        RsvpDeviceFixture(
            tokens,
            RsvpConfig(
                tempoMsPerWord = 100L,
                enablePhraseChunking = false,
                blinkMode = BlinkMode.OFF,
                startDelayMs = startDelayMs,
                endDelayMs = 0L,
                rampUpFrames = 0,
                rampDownFrames = 0,
            ),
            startIndex = 2,
        )

    private fun showWithBlockedPreparation(fixture: RsvpDeviceFixture): CompletableDeferred<Unit> {
        val releaseFrames = CompletableDeferred<Unit>()
        val repository = fixture.dependencies.frameRepository
        val delayedRepository = object : RsvpFrameRepository by repository {
            override suspend fun getSeekableFrames(
                bookId: BookId,
                chapterIndex: Int,
                config: RsvpConfig,
                startIndex: Int,
                options: RsvpGenerationOptions,
            ): RsvpFrameSet {
                releaseFrames.await()
                return repository.getSeekableFrames(bookId, chapterIndex, config, startIndex, options)
            }
        }
        composeRule.setContent {
            KairoTheme {
                RsvpScreen(fixture.state, fixture.callbacks, RsvpScreenDependencies(delayedRepository))
            }
        }
        return releaseFrames
    }
}
