package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.rsvp.analysis.RsvpEnglishSyntax
import com.kairo.reader.core.rsvp.analysis.RsvpPhraseBoundaries
import com.kairo.reader.core.rsvp.analysis.RsvpThoughtPlan
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import com.kairo.reader.core.tokenization.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpNaturalPhrasingTest {
    @Test
    fun namesAndNounSubjectsReceiveTheSameTemporalBoundaryAsPronouns() {
        for (text in listOf(
            "We waited until she arrived.",
            "We waited until Sarah arrived.",
            "We waited until the train arrived.",
            "We waited until Sarah arrived in London.",
            "We waited until the train had arrived in London.",
            "We waited until the train went to London.",
            "We waited until the late train had arrived.",
            "We waited until Mary Jane arrived."
        )) {
            val tokens = tokenize(text)
            val index = tokens.indexOfFirst { it.text == "until" }
            assertTrue(text, RsvpPhraseBoundaries.isBoundary(tokens, index, RsvpLanguagePolicy.ENGLISH))
            assertFalse(text, RsvpPhraseBoundaries.isBoundary(tokens, index, RsvpLanguagePolicy.UNKNOWN))
        }
    }

    @Test
    fun pastParticipleNounDescriptionsStayInsideTheirThought() {
        for (text in listOf(
            "She reached for the key and the note left on the table.",
            "She packed her boots and the coat covered in mud.",
            "We chose that sculpture made by Sarah.",
            "We spoke about the paint before the gate built by Sarah."
        )) {
            val tokens = tokenize(text)
            val boundary = tokens.indexOfFirst { it.text in setOf("and", "that", "before") }
            assertFalse(text, RsvpPhraseBoundaries.isBoundary(tokens, boundary, RsvpLanguagePolicy.ENGLISH))
            val frames = ComprehensionRsvpEngine().generateFrames(
                tokens,
                0,
                RsvpConfig(),
                RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH),
            )
            assertFalse(text, frames.any { it.phraseStartTokenIndex == boundary })
        }
    }

    @Test
    fun nounPhrasesAndAmbiguousModifiersDoNotBecomeClauses() {
        for (text in listOf(
            "We waited until morning.",
            "We waited until the next train.",
            "We met after Sarah.",
            "We met after the painted gate.",
            "We met after the old painted gate."
        )) {
            val tokens = tokenize(text)
            val index = tokens.indexOfFirst { it.text == "until" || it.text == "after" }
            assertFalse(text, RsvpPhraseBoundaries.isBoundary(tokens, index, RsvpLanguagePolicy.ENGLISH))
        }
    }

    @Test
    fun resolutionRedistributesExistingProcessingTimeWithoutAddingToTheBudget() {
        val tokens = tokenize("The detailed scientific report that the careful editor revised was rejected.")
        val expanded = tokens.mapIndexed { index, token -> ExpandedToken(token, index, index, 0, token.text.length) }
        val config = RsvpConfig()
        val before = RsvpThoughtPlan.analyze(expanded, config.copy(useProsodyPacing = false), RsvpLanguagePolicy.ENGLISH)
        val after = RsvpThoughtPlan.analyze(expanded, config, RsvpLanguagePolicy.ENGLISH)
        val resolution = tokens.indexOfFirst { it.text == "was" }
        assertEquals(setOf(resolution), RsvpEnglishSyntax.relativeClauseResolutions(tokens))
        assertTrue(after.getValue(resolution).processingHoldMs > before.getValue(resolution).processingHoldMs)
        assertEquals(before.values.sumOf { it.processingHoldMs }, after.values.sumOf { it.processingHoldMs }, 0.00001)
        assertEquals(before.values.sumOf { it.integrationHoldMs }, after.values.sumOf { it.integrationHoldMs }, 0.00001)
        val off = RsvpThoughtPlan.analyze(expanded, config.copy(difficultWordSupport = 0.0), RsvpLanguagePolicy.ENGLISH)
        assertEquals(0.0, off.values.sumOf { it.processingHoldMs + it.integrationHoldMs }, 0.0)
    }

    @Test
    fun ambiguousComplementsAndNestedClausesStayNeutral() {
        for (text in listOf(
            "She said that the editor was tired.",
            "The report that the editor believed was accurate surprised us.",
            "The report that the editor who we met revised was rejected."
        )) {
            assertTrue(text, RsvpEnglishSyntax.relativeClauseResolutions(tokenize(text)).isEmpty())
        }
    }

    @Test
    fun generatedGroupsRespectNamedSubjectBoundariesWithoutLosingSourceWords() {
        val tokens = tokenize("We waited until Sarah arrived with the report.")
        val boundary = tokens.indexOfFirst { it.text == "until" }
        for (width in 1..3) {
            val config = RsvpConfig(enablePhraseChunking = width > 1, maxWordsPerUnit = width, maxCharsPerUnit = 32)
            val frames = ComprehensionRsvpEngine().generateFrames(tokens, 0, config, RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH))
            assertTrue(frames.none { it.originalTokenIndex < boundary && it.displayOriginalEndExclusive > boundary })
            assertEquals(tokens.size, frames.last().nextOriginalTokenIndex)
            assertTrue(frames.any { it.phraseStartTokenIndex == boundary })
        }
    }

    private fun tokenize(text: String) = Tokenizer().tokenize(Chapter(0, null, "", text))
}
