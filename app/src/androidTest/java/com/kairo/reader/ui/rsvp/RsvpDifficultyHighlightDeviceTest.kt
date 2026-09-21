package com.kairo.reader.ui.rsvp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.defaultConfig
import com.kairo.reader.core.rsvp.ComprehensionRsvpEngine
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RsvpDifficultyHighlightDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun difficultWordsMoveLeftToRightWithoutMovingTheRenderedWord() {
        val source = listOf("the", "neuroplasticity", "changed").map { Token(it, TokenType.WORD) }
        val config = RsvpConfig(
            maxChunkLength = 32,
            enablePhraseChunking = false,
            longWordMinMs = 400L,
            startDelayMs = 0,
            endDelayMs = 0,
            rampUpFrames = 0,
            rampDownFrames = 0,
        )
        val engine = ComprehensionRsvpEngine()
        val options = RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH)
        val parts = engine.generateFrames(source, 0, config, options).filter { it.originalTokenIndex == 1 }
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.durationMs >= config.longWordMinMs + it.protectedWordMs })
        var shown by mutableStateOf(parts.first().tokens)
        composeRule.setContent {
            KairoTheme {
                Box(Modifier.size(360.dp, 180.dp)) {
                    OrpAlignedText(
                        tokens = shown,
                        typography = OrpTypography(28f, FontFamily.Monospace, FontWeight.Normal),
                        colors = OrpColors(Color.Black, Color.Red, Color.Gray, Color.Blue),
                        layout = OrpTextLayout(
                            horizontalBias = 0.5f,
                            lockPivot = true,
                            smoothTranslation = false,
                            preferWindowing = false,
                            simplifyPunctuation = false,
                            guideVisible = true,
                            pivotHighlightVisible = true,
                            guideThickness = 1f,
                        ),
                    )
                }
            }
        }
        var initialPivot: Float? = null
        for (part in parts) {
            composeRule.runOnIdle { shown = part.tokens }
            val token = part.tokens.single()
            val layout = renderedLayout()
            val text = layout.layoutInput.text
            assertEquals("neuroplasticity", text.text)
            assertEquals(token.text.substring(token.highlightStart!!, token.highlightEndExclusive!!), highlightedText(layout))
            val pivot = text.spanStyles.last { it.item.color == Color.Red }.start
            val pivotX = layout.getBoundingBox(pivot).center.x
            initialPivot?.let { assertEquals(it, pivotX, 0.001f) }
            initialPivot = pivotX
        }
    }

    @Test
    fun ordinaryInflectionsStayWholeWhileLongerTermsReceiveMovingParts() {
        val words = listOf("Instructors", "representatives", "respresentatives", "responsibility")
        val tokens = words.map { Token(it, TokenType.WORD) }
        val config = RsvpProfile.BALANCED.defaultConfig().copy(enablePhraseChunking = false)
        val frames = ComprehensionRsvpEngine().generateFrames(tokens, 0, config, RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH))
        val ordinary = frames.filter { it.originalTokenIndex == 0 && !it.isWordSeparation }
        assertEquals(1, ordinary.size)
        assertEquals(null, ordinary.single().tokens.single().highlightStart)
        for (index in 1..words.lastIndex) {
            val parts = frames.filter { it.originalTokenIndex == index && !it.isWordSeparation }.flatMap { it.tokens }
            assertTrue(words[index], parts.size > 1)
            assertTrue(parts.all { it.isSubwordChunk && it.text == words[index] })
            assertEquals(0, parts.first().highlightStart)
            assertEquals(words[index].length, parts.last().highlightEndExclusive)
        }
    }

    @Test
    fun wordFamiliesKeepTheSamePresentationWithPackagedDataAndNormalPreset() {
        val words =
            listOf("instructor", "instructors", "university", "Universities", "representative", "representatives", "representative’s")
        val config = RsvpProfile.BALANCED.defaultConfig().copy(enablePhraseChunking = false)
        val frames = ComprehensionRsvpEngine().generateFrames(
            words.map { Token(it, TokenType.WORD) },
            0,
            config,
            RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH),
        )
        fun parts(index: Int) = frames.filter { it.originalTokenIndex == index && !it.isWordSeparation }.flatMap { it.tokens }
        for (index in 0..3) {
            assertEquals(words[index], 1, parts(index).size)
            assertEquals(null, parts(index).single().highlightStart)
        }
        val base = parts(4)
        assertTrue(base.size > 1)
        for (index in 5..6) {
            val inflected = parts(index)
            assertEquals(base.size, inflected.size)
            assertEquals(base.dropLast(1).map { it.highlightEndExclusive }, inflected.dropLast(1).map { it.highlightEndExclusive })
            assertEquals(words[index].length, inflected.last().highlightEndExclusive)
        }
    }

    @Test
    fun selectionDistinguishesFamiliarLongWordsFromUncommonShorterFamilies() {
        val ordinary = listOf("comfortable", "everything", "conversation", "whispering", "explanation")
        val supported = listOf("crucible", "Crucibles", "crystalline", "slateboard", "slateboards", "dormitory", "dormitories")
        val frames = ComprehensionRsvpEngine().generateFrames(
            (ordinary + supported).map { Token(it, TokenType.WORD) },
            0,
            RsvpProfile.BALANCED.defaultConfig(),
            RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH),
        )
        for (text in ordinary) {
            val shown = frames.flatMap { it.tokens }.filter { it.text == text }
            assertEquals(text, 1, shown.size)
            assertTrue(shown.none { it.isSubwordChunk })
        }
        for (text in supported) {
            val shown = frames.flatMap { it.tokens }.filter { it.text == text }
            assertTrue(text, shown.size > 1 && shown.all { it.isSubwordChunk })
        }
    }

    private fun renderedLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag("rsvp-focus-word").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun highlightedText(layout: TextLayoutResult): String {
        val text = layout.layoutInput.text
        return text.spanStyles.filter { it.item.color == Color.Blue }.joinToString("") { text.text.substring(it.start, it.end) }
    }
}
