package com.kairo.reader.ui.rsvp

import android.graphics.Bitmap
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpResumeCursor
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.ui.theme.KairoTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RsvpDeviceSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun hyphenatedWordStartsAtBeginningAndResumesAtSavedPart() {
        val fixture = RsvpDeviceFixture(
            listOf(word("Earlier"), word("mother-in-law"), word("continued")),
            config(),
            startIndex = 1,
        )
        show(fixture)
        awaitLoaded(fixture)
        composeRule.onNodeWithText("mother-", useUnmergedTree = true).assertIsDisplayed()
        capture("hyphen-start")
        pause()
        click(R.string.content_desc_next)
        capture("hyphen-paused-after-next")
        click(R.string.content_desc_close)
        composeRule.runOnIdle {
            val saved = requireNotNull(fixture.saved)
            assertEquals(1, saved.tokenIndex)
            assertEquals(7, RsvpResumeCursor.characterOffset(saved.resumeCursor))
            fixture.state = fixture.state.copy(
                book = fixture.state.book.copy(startResumeCursor = saved.resumeCursor),
                initialIsPlaying = false,
            )
            fixture.session++
        }
        awaitLoaded(fixture, minimumLoads = 2)
        composeRule.onNodeWithText("in-", useUnmergedTree = true).assertIsDisplayed()
        capture("hyphen-resumed")
    }

    @Test
    fun changingChunkWidthWhilePausedKeepsTheSourcePosition() {
        val fixture = RsvpDeviceFixture(
            listOf(word("Earlier"), word("abcdefghijkl"), word("continued")),
            config().copy(maxChunkLength = 3),
            startIndex = 1,
            resumeCursor = RsvpResumeCursor.fromCharacterOffset(6),
        )
        fixture.state = fixture.state.copy(initialIsPlaying = false)
        show(fixture)
        awaitLoaded(fixture)
        composeRule.onNodeWithText("abcdefghijkl", useUnmergedTree = true).assertIsDisplayed()
        capture("long-word-before-resize")
        composeRule.runOnIdle {
            fixture.state = fixture.state.copy(profile = fixture.state.profile.copy(config = config().copy(maxChunkLength = 4)))
        }
        awaitLoaded(fixture, minimumLoads = 2)
        capture("long-word-after-resize")
        click(R.string.content_desc_close)
        composeRule.runOnIdle {
            assertEquals(1, requireNotNull(fixture.saved).tokenIndex)
            assertEquals(4, RsvpResumeCursor.characterOffset(requireNotNull(fixture.saved).resumeCursor))
        }
    }

    @Test
    fun highSpeedPlaybackConsumesBlinkFramesAndCompletesInOrder() {
        val tokens = List(16) { word("reading") }
        val fixture = RsvpDeviceFixture(
            tokens,
            config().copy(tempoMsPerWord = 80L, blinkMode = BlinkMode.SUBTLE, startDelayMs = 250L, rampUpFrames = 4, rampDownFrames = 4),
        )
        show(fixture)
        awaitLoaded(fixture)
        composeRule.waitUntil(timeoutMillis = 20_000) { fixture.finished }
        val words = fixture.consumed.filter { frame -> frame.tokens.any { it.type == TokenType.WORD } }
        assertEquals(tokens.indices.toList(), words.map { it.originalTokenIndex })
        assertTrue(
            "Playback must actually consume blink separators",
            fixture.consumed.any { frame ->
                frame.tokens.none {
                    it.type ==
                        TokenType.WORD
                }
            }
        )
        assertTrue(words.first().durationMs > words[words.size / 2].durationMs)
        capture("playback-completed")
    }

    private fun show(fixture: RsvpDeviceFixture) {
        composeRule.setContent {
            KairoTheme {
                key(fixture.session) {
                    RsvpScreen(fixture.state, fixture.callbacks, fixture.dependencies)
                }
            }
        }
    }

    private fun awaitLoaded(fixture: RsvpDeviceFixture, minimumLoads: Int = 1) {
        composeRule.waitUntil(timeoutMillis = 10_000) { fixture.loads.get() >= minimumLoads }
        composeRule.waitForIdle()
    }

    private fun pause() {
        composeRule.onRoot().performTouchInput { click() }
        val play = composeRule.activity.getString(R.string.content_desc_play)
        composeRule.waitUntil(timeoutMillis = 3000) {
            composeRule.onAllNodesWithContentDescription(play).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(play).assertIsDisplayed()
    }

    private fun click(stringId: Int) {
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(stringId)).performClick()
    }

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val directory = requireNotNull(composeRule.activity.getExternalFilesDir("rsvp-device-review"))
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun config(): RsvpConfig = RsvpConfig(
        enablePhraseChunking = false,
        startDelayMs = 2000L,
        endDelayMs = 0L,
        rampUpFrames = 0,
        rampDownFrames = 0,
    )

    private fun word(text: String): Token = Token(text, TokenType.WORD)
}
