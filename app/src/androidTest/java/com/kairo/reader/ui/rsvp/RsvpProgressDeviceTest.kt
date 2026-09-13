package com.kairo.reader.ui.rsvp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.buildWordCountByToken
import com.kairo.reader.ui.reader.ReaderProgressInput
import com.kairo.reader.ui.reader.rememberReaderProgressState
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RsvpProgressDeviceTest {
    @get:Rule
    val rule = createAndroidComposeRule<TestActivity>()

    @Test
    fun midChapterProgressAndPausedPositionSurviveRestorationAndRegeneration() {
        verifyProgressRestoration(landscape = false)
    }

    @Test
    fun landscapeProgressAndPausedPositionSurviveRestorationAndRegeneration() {
        verifyProgressRestoration(landscape = true)
    }

    private fun verifyProgressRestoration(landscape: Boolean) {
        val fixture = RsvpDeviceFixture(
            tokens = List(100) { Token("word", TokenType.WORD) },
            config = RsvpConfig(enablePhraseChunking = false, blinkMode = BlinkMode.OFF, startDelayMs = 10_000L),
            startIndex = 74,
        )
        fixture.state = fixture.state.copy(
            book = fixture.state.book.copy(chapterIndex = 1),
            initialIsPlaying = true,
        )
        var isLandscape by mutableStateOf(false)
        var reading by mutableStateOf(true)
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            CompositionLocalProvider(LocalDensity provides if (isLandscape) Density(1f) else LocalDensity.current) {
                Box(if (isLandscape) Modifier.size(800.dp, 360.dp) else Modifier.fillMaxSize()) {
                    KairoTheme {
                        if (reading) {
                            ReaderProgressFixture(fixture) { reading = false }
                        } else {
                            RsvpScreen(fixture.state, fixture.callbacks, fixture.dependencies)
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("Reader: 75%").assertIsDisplayed()
        rule.onNodeWithText("Open RSVP").performClick()
        awaitLoaded(fixture, 1)
        pause()
        assertProgress(0.75f)
        assertPosition(75)
        click(R.string.content_desc_next)
        assertPosition(76)

        rule.runOnIdle { isLandscape = landscape }
        assertPosition(76)
        restoration.emulateSavedInstanceStateRestore()
        awaitLoaded(fixture, 2)
        assertProgress(0.76f)

        rule.runOnIdle {
            fixture.state = fixture.state.copy(
                profile = fixture.state.profile.copy(config = fixture.state.profile.config.copy(blinkMode = BlinkMode.SUBTLE)),
            )
        }
        awaitLoaded(fixture, 3)
        assertProgress(0.76f)
        click(R.string.content_desc_close)
        rule.runOnIdle { assertEquals(75, requireNotNull(fixture.saved).tokenIndex) }
    }

    @Composable
    private fun ReaderProgressFixture(fixture: RsvpDeviceFixture, onLaunch: () -> Unit) {
        val progress = rememberReaderProgressState(
            ReaderProgressInput(
                safeFocusIndex = fixture.state.book.startIndex,
                totalChapterWords = 100,
                wordCountByToken = buildWordCountByToken(fixture.state.book.tokens),
                resolvedPageIndex = -1,
                pages = emptyList(),
                currentPage = null,
                estimatedWpm = 0,
                // This source position is 75% of the chapter but only 20% of the book.
                bookWordCounts = listOf(100, 100, 675),
                chapterIndex = 1,
                chapterCount = 3,
            ),
        )
        Column {
            Text("Reader: ${progress.progressPercent}%")
            Button(onClick = onLaunch) { Text("Open RSVP") }
        }
    }

    private fun assertProgress(fraction: Float) {
        rule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(fraction, 0f..1f),
            ),
        ).assertIsDisplayed()
    }

    private fun pause() {
        rule.onRoot().performTouchInput { click() }
        val play = rule.activity.getString(R.string.content_desc_play)
        rule.waitUntil(timeoutMillis = 3000) {
            rule.onAllNodesWithContentDescription(play).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    private fun assertPosition(word: Int) {
        rule.onNodeWithText(rule.activity.getString(R.string.rsvp_frame_progress, word, 100)).assertIsDisplayed()
    }

    private fun click(stringId: Int) {
        rule.onNodeWithContentDescription(rule.activity.getString(stringId)).performClick()
    }

    private fun awaitLoaded(fixture: RsvpDeviceFixture, count: Int) {
        rule.waitUntil(timeoutMillis = 10_000) { fixture.loads.get() >= count }
        rule.waitForIdle()
    }
}
