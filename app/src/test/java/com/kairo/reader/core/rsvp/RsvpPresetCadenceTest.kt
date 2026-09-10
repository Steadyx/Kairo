package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.defaultConfig
import com.kairo.reader.core.tokenization.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpPresetCadenceTest {
    private val engine = ComprehensionRsvpEngine()
    private val options = RsvpGenerationOptions(languagePolicy = RsvpLanguagePolicy.ENGLISH)
    private val tokens = Tokenizer().tokenize(
        Chapter(
            index = 0,
            title = "Cadence fixture",
            htmlContent = "",
            plainText = "Mara put down her bag and listened. She had remembered the date, the place, and the promise; " +
                "she had simply needed more time. The measurements contain uncertainty. " +
                "Repeated observations distinguish a consistent effect from random variation. " +
                "Do not send another payment until the first one has been confirmed.",
        )
    )

    @Test
    fun allProfilesKeepSourceWordsAndShowWholeOrdinaryTermsAtMultipleSpeeds() {
        for (tempo in listOf(80L, 150L, 250L)) {
            RsvpProfile.entries.forEach { profile ->
                val frames = engine.generateFrames(tokens, 0, profile.defaultConfig().copy(tempoMsPerWord = tempo), options)
                assertEquals(
                    tokens.filter { it.type == TokenType.WORD }.map { it.text },
                    frames.flatMap { it.tokens }.filter { it.type == TokenType.WORD }.map { it.text },
                )
                assertTrue(frames.all { it.durationMs > 0 })
            }
        }
    }

    @Test
    fun flowGroupsWordsWhileFocusAndStudyShowOneAtATime() {
        val flow = frames(RsvpProfile.FLOW.defaultConfig())
        assertTrue(flow.any { frame -> frame.tokens.count { it.type == TokenType.WORD } == 3 })
        for (profile in listOf(RsvpProfile.FOCUS, RsvpProfile.STUDY)) {
            assertTrue(frames(profile.defaultConfig()).all { frame -> frame.tokens.count { it.type == TokenType.WORD } <= 1 })
        }
    }

    @Test
    fun skimIsLighterAndStudyMoreDeliberateAtTheSameTempo() {
        val focus = frames(RsvpProfile.FOCUS.defaultConfig()).sumOf { it.durationMs }
        val skim = frames(RsvpProfile.SPRINT.defaultConfig()).sumOf { it.durationMs }
        val study = frames(RsvpProfile.STUDY.defaultConfig()).sumOf { it.durationMs }
        assertTrue("Skim should spend less time than Focus on this review passage", skim < focus)
        assertTrue("Study should allow more processing time", study > focus)
    }

    @Test
    fun naturalExpressionStillChangesCadenceAtComfortableTempo() {
        val narrative = RsvpProfile.NARRATIVE.defaultConfig().copy(tempoMsPerWord = 250L, enablePhraseChunking = false)
        val expressive = engine.generateFrames(tokens, 0, narrative, options).map { it.durationMs }
        val restrained = engine.generateFrames(tokens, 0, narrative.copy(useProsodyPacing = false), options).map { it.durationMs }
        assertNotEquals(expressive, restrained)
        val noFocalStress = engine.generateFrames(tokens, 0, narrative.copy(useFocalStress = false), options).map { it.durationMs }
        assertNotEquals(expressive, noFocalStress)
    }

    private fun frames(config: RsvpConfig) = engine.generateFrames(tokens, 0, config.copy(tempoMsPerWord = 150L), options)
}
