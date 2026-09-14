package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.analysis.EnglishReadingFrequency
import com.kairo.reader.core.rsvp.analysis.ReadingDemandAnalyzer
import com.kairo.reader.core.rsvp.analysis.RsvpReadingDemand
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpReadingDemandTest {
    private val english = RsvpLanguagePolicy.ENGLISH
    private fun word(text: String) = Token(text, TokenType.WORD)
    private fun demand(text: String, seen: Int = 0) = ReadingDemandAnalyzer.analyze(word(text), english, seen)

    @Test
    fun bundledFrequenciesDistinguishActualFamiliarityFromLetterRarity() {
        assertNotNull(EnglishReadingFrequency.zipf("extraordinary"))
        assertTrue(EnglishReadingFrequency.zipf("people")!! > EnglishReadingFrequency.zipf("quartz")!!)
        assertTrue(demand("people").recognition < demand("xqtrpz").recognition)
        assertTrue(demand("the").allowanceMs(150.0) == 0.0)
    }

    @Test
    fun recognitionUsesTextRatherThanLegacyTokenEstimates() {
        val token = word("extraordinary")
        assertEquals(
            ReadingDemandAnalyzer.analyze(token, english),
            ReadingDemandAnalyzer.analyze(
                token.copy(syllableCount = 99, frequencyScore = 0.0, complexityMultiplier = 99.0),
                english,
            )
        )
    }

    @Test
    fun allowanceIsSmoothMonotoneAndBoundedAtEveryTempo() {
        for (tempo in listOf(20.0, 150.0, 500.0, 1000.0)) {
            val samples = (0..100).map { RsvpReadingDemand(it / 10.0).allowanceMs(tempo) }
            assertEquals(0.0, samples.first(), 0.0)
            assertTrue(samples.zipWithNext().all { (a, b) -> b >= a })
            assertTrue(samples.last() <= 180.0)
            assertTrue(samples[2] - samples[1] < samples[1] - samples[0])
        }
    }

    @Test
    fun repetitionOnlyReducesNoveltyAndNeverRecognition() {
        val first = demand("quizzacious")
        val repeated = demand("quizzacious", 8)
        assertEquals(first.recognition, repeated.recognition, 0.0)
        assertTrue(first.novelty > repeated.novelty)
        assertTrue(first.allowanceMs(150.0) > repeated.allowanceMs(150.0))
        assertTrue(repeated.allowanceMs(150.0) >= first.copy(novelty = 0.0).allowanceMs(150.0))
        assertTrue(first.allowanceMs(150.0) - repeated.allowanceMs(150.0) < 30.0)
    }

    @Test
    fun sourcePlanIsStableAcrossSeekingSentenceBoundariesAndParagraphResets() {
        val tokens = listOf(
            word("quizzacious"),
            Token(".", TokenType.PUNCTUATION),
            word("quizzacious"),
            Token("\n", TokenType.PARAGRAPH_BREAK),
            word("quizzacious")
        )
        val complete = ReadingDemandAnalyzer.plan(tokens, 0, english)
        val resumed = ReadingDemandAnalyzer.plan(tokens, 2, english)
        assertEquals(complete[2], resumed[2])
        assertEquals(complete[0], complete[4])
        assertTrue(complete.getValue(0).novelty > complete.getValue(2).novelty)
        assertEquals(complete, ReadingDemandAnalyzer.plan(tokens, 0, english))
    }

    @Test
    fun unknownLanguagesDoNotInheritEnglishSpellingOrFrequencyPenalties() {
        for (policy in RsvpLanguagePolicy.entries.filter { it != english }) {
            assertEquals(ReadingDemandAnalyzer.analyze(word("people"), policy), ReadingDemandAnalyzer.analyze(word("xqtrpz"), policy))
            assertEquals(0.0, ReadingDemandAnalyzer.analyze(word("école"), policy).novelty, 0.0)
        }
        assertTrue(ReadingDemandAnalyzer.analyze(word("123456"), RsvpLanguagePolicy.UNKNOWN).recognition > 0.0)
    }

    @Test
    fun normalizationIsIndependentOfDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals("isn't", ReadingDemandAnalyzer.normalize("ISN’T"))
            assertEquals(ReadingDemandAnalyzer.normalize("école"), ReadingDemandAnalyzer.normalize("e\u0301cole"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun splitWordsShareOneSourceAllowanceWithoutManufacturingNovelty() {
        val token = word("quizzacious")
        val pieces = listOf(
            ExpandedToken(token.copy(isSubwordChunk = true, highlightStart = 0, highlightEndExclusive = 4), 0, 0, 0, 11),
            ExpandedToken(token.copy(isSubwordChunk = true, highlightStart = 4, highlightEndExclusive = 11), 0, 1, 0, 11),
        )
        val attached = ReadingDemandAnalyzer.attach(pieces, listOf(token), 0, english)
        assertEquals(demand(token.text).allowanceMs(150.0), attached.sumOf { it.readingDemand!!.allowanceMs(150.0) }, 0.001)
        assertEquals(attached[0].readingDemand!!.novelty, attached[1].readingDemand!!.novelty, 0.0)
    }

    @Test
    fun obsoleteTuningCannotStackAnotherAllowanceAndSupportWorksWithCadenceDisabled() {
        val config = RsvpConfig(startDelayMs = 0, endDelayMs = 0, rampUpFrames = 0, rampDownFrames = 0, useAdaptiveTiming = false)
        val tokens = listOf(word("quizzacious"), word("12345"), word("neuroplasticity"))
        val engine = ComprehensionRsvpEngine()
        fun frames(settings: RsvpConfig) = engine.generateFrames(tokens, 0, settings, RsvpGenerationOptions(english))
        val normal = frames(config)
        val legacy = frames(
            config.copy(
                syllableExtraMs = 100, rarityExtraMaxMs = 100, complexityStrength = 1.0,
                lengthStrength = 3.0, lengthExponent = 3.0, complexWordHoldMs = 400, adaptiveDifficultyMaxHoldMs = 400,
                complexWordThreshold = 1.0, phraseBreathingRoomMs = 400
            )
        )
        assertEquals(normal, legacy)
        assertTrue(normal.sumOf { it.protectedWordMs } > frames(config.copy(difficultWordSupport = 0.0)).sumOf { it.protectedWordMs })
    }
}
