package com.kairo.reader.ui.rsvp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.RsvpFontWeight
import com.kairo.reader.core.model.RsvpFrame
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
class RsvpContextLayoutDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun cueSharesTheReadingLineAndYieldsToLongWordsAndLargeText() {
        var large by mutableStateOf(false)
        composeRule.setContent { ReadingFixture(large = large) }
        composeRule.onNodeWithTag("rsvp-inline-cue").assertIsDisplayed()
        assertCueDoesNotOverlap()
        capture("inline-context")
        composeRule.runOnIdle { large = true }
        assertCueDoesNotOverlap()
        capture("inline-context-large-text")
    }

    @Test
    fun pausingShowsThePhraseWithoutMovingTheFocus() {
        var playing by mutableStateOf(true)
        composeRule.setContent { ReadingFixture(playing = playing) }
        val before = bounds("rsvp-focus-row")
        composeRule.onNodeWithText("she had already left").assertDoesNotExist()
        composeRule.runOnIdle { playing = false }
        composeRule.onNodeWithTag("rsvp-inline-cue").assertDoesNotExist()
        composeRule.onNodeWithText("she had already left").assertIsDisplayed()
        assertEquals(before, bounds("rsvp-focus-row"))
        assertTrue(before.bottom < bounds("rsvp-paused-context").top)
        capture("inline-context-paused")
    }

    @Test
    fun softSeparationKeepsTheWordAndContextWhileRepeatedWordsGetAClearGap() {
        var pulse by mutableStateOf(false)
        var repeated by mutableStateOf(false)
        composeRule.setContent { ReadingFixture(pulse = pulse, repeated = repeated) }
        val wordBefore = composeRule.onNodeWithTag("rsvp-focus-row").captureToImage().asAndroidBitmap()
        val cueBefore = composeRule.onNodeWithTag("rsvp-inline-cue").captureToImage().asAndroidBitmap()
        composeRule.runOnIdle { pulse = true }
        composeRule.onNodeWithTag("rsvp-focus-word").assertIsDisplayed()
        val wordDimmed = composeRule.onNodeWithTag("rsvp-focus-row").captureToImage().asAndroidBitmap()
        assertTrue(!wordBefore.sameAs(wordDimmed))
        assertTrue(cueBefore.sameAs(composeRule.onNodeWithTag("rsvp-inline-cue").captureToImage().asAndroidBitmap()))
        capture("soft-word-separation")
        composeRule.runOnIdle { repeated = true }
        composeRule.onNodeWithTag("rsvp-focus-word").assertDoesNotExist()
        assertTrue(cueBefore.sameAs(composeRule.onNodeWithTag("rsvp-inline-cue").captureToImage().asAndroidBitmap()))
        capture("repeated-word-separation")
    }

    @Test
    fun clauseKeepsBothSidesClearOfTheMainWord() {
        composeRule.setContent { ReadingFixture(mode = RsvpContextAssistMode.FULL_CLAUSE) }
        composeRule.onNodeWithTag("rsvp-following-cue").assertIsDisplayed()
        assertCueDoesNotOverlap()
        val focus = focusTextBounds()
        val following = bounds("rsvp-following-cue")
        assertTrue(following.left > focus.right)
        assertTrue(following.top >= focus.top && following.bottom <= focus.bottom)
        capture("inline-clause-context")
    }

    @Test
    fun longUpcomingWordUsesAnEllipsisWithoutMovingTheFocus() {
        var clause by mutableStateOf(false)
        composeRule.setContent {
            ReadingFixture(
                mode = if (clause) RsvpContextAssistMode.FULL_CLAUSE else RsvpContextAssistMode.PREVIOUS_WORDS,
                currentWord = "go",
                nextWord = "extraordinarily",
            )
        }
        val before = focusTextBounds()
        composeRule.runOnIdle { clause = true }
        val cue = composeRule.onNodeWithTag("rsvp-following-cue").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        cue.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().isLineEllipsized(0))
        assertEquals(before, focusTextBounds())
        assertTrue(cue.fetchSemanticsNode().boundsInRoot.left > before.right)
        capture("inline-clause-shortened-upcoming")
    }

    @Test
    fun changingModesSeparatesContinuousClauseAndPreviousContext() {
        var mode by mutableStateOf(RsvpContextAssistMode.SENTENCE_TICKER)
        val tokens = listOf(
            Token("she", TokenType.WORD),
            Token("left", TokenType.WORD),
            Token("but", TokenType.WORD, isClauseBoundary = true),
            Token("we", TokenType.WORD),
            Token("stayed", TokenType.WORD),
        )
        composeRule.setContent { ReadingFixture(mode = mode, sourceTokens = tokens, phraseStart = 2) }
        val focus = focusTextBounds()
        composeRule.onNodeWithTag("rsvp-inline-cue").assertIsDisplayed()
        composeRule.onNodeWithTag("rsvp-following-cue").assertIsDisplayed()
        capture("mode-continuous")
        composeRule.runOnIdle { mode = RsvpContextAssistMode.FULL_CLAUSE }
        composeRule.onNodeWithTag("rsvp-inline-cue").assertDoesNotExist()
        composeRule.onNodeWithTag("rsvp-following-cue").assertIsDisplayed()
        assertEquals(focus, focusTextBounds())
        capture("mode-clause")
        composeRule.runOnIdle { mode = RsvpContextAssistMode.PREVIOUS_WORDS }
        composeRule.onNodeWithTag("rsvp-inline-cue").assertDoesNotExist()
        composeRule.onNodeWithTag("rsvp-following-cue").assertDoesNotExist()
        assertEquals(focus, focusTextBounds())
        capture("mode-previous")
    }

    private fun assertCueDoesNotOverlap() {
        val cues = composeRule.onAllNodesWithTag("rsvp-inline-cue").fetchSemanticsNodes()
        if (cues.isEmpty()) return
        val cue = cues.single().boundsInRoot
        val focus = focusTextBounds()
        assertTrue("Cue $cue must end before rendered focus $focus", cue.right < focus.left)
        assertTrue(cue.top >= focus.top && cue.bottom <= focus.bottom)
    }

    private fun focusTextBounds(): Rect {
        val node = composeRule.onNodeWithTag("rsvp-focus-word")
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        return Rect(bounds.left + layout.getLineLeft(0), bounds.top, bounds.left + layout.getLineRight(0), bounds.bottom)
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Composable
    private fun ReadingFixture(
        large: Boolean = false,
        playing: Boolean = true,
        pulse: Boolean = false,
        repeated: Boolean = false,
        mode: RsvpContextAssistMode = RsvpContextAssistMode.PREVIOUS_WORDS,
        currentWord: String = "already",
        nextWord: String = "left",
        sourceTokens: List<Token>? = null,
        phraseStart: Int = 0,
    ) {
        KairoTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.5f else 1f)) {
                val fontSize = if (large) 64f else 40f
                val tokens =
                    sourceTokens
                        ?: listOf("she", "had", if (large) "extraordinarily" else currentWord, nextWord).map { Token(it, TokenType.WORD) }
                val frame =
                    RsvpFrame(
                        listOf(tokens[2]),
                        200L,
                        originalTokenIndex = 2,
                        phraseStartTokenIndex = phraseStart,
                        phraseEndTokenIndexExclusive = 4
                    )
                val context = requireNotNull(buildRsvpReadingContext(tokens, frame, MaterialTheme.colorScheme.onBackground, mode))
                Box(Modifier.size(360.dp, 520.dp).background(MaterialTheme.colorScheme.background)) {
                    RsvpContextStage(
                        verticalBias = 0f,
                        pausedContext = {
                            if (!playing) RsvpPausedPhrase(context.pausedPhrase, fontSize, RsvpFontFamily.INTER, RsvpFontWeight.NORMAL)
                        },
                    ) {
                        val alpha =
                            wordSeparationAlpha(
                                frame.copy(isWordSeparation = pulse, isRepeatedWordSeparation = repeated),
                                playing,
                                RsvpConfig(blinkMode = BlinkMode.SUBTLE)
                            )
                        OrpAlignedText(
                            tokens = frame.tokens,
                            contextCues = if (playing) context.precedingCues else emptyList(),
                            followingContextCues = if (playing) context.followingCues else emptyList(),
                            typography = OrpTypography(
                                fontSize,
                                resolveFontFamily(RsvpFontFamily.INTER),
                                resolveFontWeight(RsvpFontWeight.NORMAL)
                            ),
                            colors = OrpColors(
                                MaterialTheme.colorScheme.onBackground,
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.outline,
                                MaterialTheme.colorScheme.primary
                            ),
                            layout = OrpTextLayout(0f, false, false, false, false, true, true, 1f, wordAlpha = alpha),
                        )
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val directory = requireNotNull(composeRule.activity.getExternalFilesDir("rsvp-inline-review"))
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
