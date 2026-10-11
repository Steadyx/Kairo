package com.kairo.reader.core.rsvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComprehensionRsvpContourTest : ComprehensionRsvpTestBase() {
    @Test
    fun abbreviationDotsDoNotTriggerAnticipatoryLanding() {
        val config =
            punctuationConfig.copy(
                useAdaptiveTiming = false,
                useClausePausing = false,
                useProsodyPacing = false,
                useFocalStress = true,
                useAnticipatoryLanding = true,
            )
        val withoutLanding = config.copy(useAnticipatoryLanding = false)
        val tokens = listOf(w("I"), w("met"), w("Dr"), p("."), w("Alice"))

        val withFrames = engine.generateFrames(tokens, 0, config)
        val withoutFrames = engine.generateFrames(tokens, 0, withoutLanding)

        val withMet =
            withFrames.first { frame -> frame.tokens.any { it.text == "met" } }.durationMs
        val withoutMet =
            withoutFrames.first { frame -> frame.tokens.any { it.text == "met" } }.durationMs
        assertEquals(
            "Abbreviation punctuation should not create a pre-boundary landing boost",
            withoutMet,
            withMet,
        )
    }

    @Test
    fun boundaryContourLiftsLastWordBeforeStrongerStops() {
        val contourConfig =
            punctuationConfig.copy(
                commaPauseMs = 0L,
                periodPauseMs = 0L,
                sentenceEndPauseMs = 0L,
                semicolonPauseMs = 0L,
                colonPauseMs = 0L,
                dashPauseMs = 0L,
                usePunctuationLandingHold = false,
                useAdaptiveTiming = false,
                useClausePausing = false,
                useProsodyPacing = false,
            )

        val plain = engine.generateFrames(
            tokens = listOf(w("Wait"), w("again")),
            startIndex = 0,
            config = contourConfig,
        )[0].durationMs
        val comma = engine.generateFrames(
            tokens = listOf(w("Wait"), p(","), w("again")),
            startIndex = 0,
            config = contourConfig,
        )[0].durationMs
        val semicolon = engine.generateFrames(
            tokens = listOf(w("Wait"), p(";"), w("again")),
            startIndex = 0,
            config = contourConfig,
        )[0].durationMs
        val period = engine.generateFrames(
            tokens = listOf(w("Wait"), p("."), w("again")),
            startIndex = 0,
            config = contourConfig,
        )[0].durationMs

        assertTrue("Expected plain word baseline", plain > 0L)
        assertTrue("Expected non-clause comma to add a soft lift", comma > plain + 1L)
        assertTrue("Expected semicolon tail lift to stay above comma", semicolon > comma + 2L)
        assertTrue("Expected period tail lift over semicolon", period > semicolon + 3L)
    }

    @Test
    fun sentenceStartGetsABoostAtHighSpeed() {
        val config = stableConfig.copy(tempoMsPerWord = 60L, sentenceEndPauseMs = 0L)

        val plainNext = engine.generateFrames(
            tokens = listOf(w("Hello"), w("Next")),
            startIndex = 0,
            config = config
        )[1].durationMs
        val boundaryNext =
            engine
                .generateFrames(
                    tokens = listOf(w("Hello"), p("."), w("Next")),
                    startIndex = 0,
                    config = config,
                )[1]
                .durationMs

        assertTrue(
            "Expected a sentence-start boost after a full stop",
            boundaryNext - plainNext >= 4L
        )
    }

    @Test
    fun sentenceRestartGetsPhraseContourAtHighSpeed() {
        val config =
            stableConfig.copy(
                tempoMsPerWord = 60L,
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
                sentenceEndPauseMs = 0L,
                periodPauseMs = 0L,
                useAdaptiveTiming = false,
                useClausePausing = false,
                useDialogueDetection = false,
                useProsodyPacing = false,
                useFocalStress = false,
                useAnticipatoryLanding = false,
            )

        val plainNext =
            engine
                .generateFrames(
                    tokens = listOf(w("Hello"), w("Next")),
                    startIndex = 0,
                    config = config,
                )[1]
                .durationMs
        val sentenceNext =
            engine
                .generateFrames(
                    tokens = listOf(w("Hello"), p("."), w("Next")),
                    startIndex = 0,
                    config = config,
                )[1]
                .durationMs

        assertTrue(
            "Expected phrase contour to settle the restart word after a sentence",
            sentenceNext > plainNext,
        )
    }

    @Test
    fun abbreviationDotDoesNotCreatePhraseContourRestart() {
        val config =
            stableConfig.copy(
                tempoMsPerWord = 60L,
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
                useAdaptiveTiming = false,
                useClausePausing = false,
                useDialogueDetection = false,
                useProsodyPacing = false,
                useFocalStress = false,
                useAnticipatoryLanding = false,
            )

        val plainAlice =
            engine
                .generateFrames(
                    tokens = listOf(w("I"), w("met"), w("Dr"), w("Alice")),
                    startIndex = 0,
                    config = config,
                ).first { frame -> frame.tokens.any { it.text == "Alice" } }
                .durationMs
        val abbreviationAlice =
            engine
                .generateFrames(
                    tokens = listOf(w("I"), w("met"), w("Dr"), p("."), w("Alice")),
                    startIndex = 0,
                    config = config,
                ).first { frame -> frame.tokens.any { it.text == "Alice" } }
                .durationMs

        assertEquals(
            "Abbreviation dot should not make the following word restart like a new sentence",
            plainAlice,
            abbreviationAlice,
        )
    }

    @Test
    fun clauseStartersGetExtraTimeAtHighSpeed() {
        val baseConfig =
            stableConfig.copy(
                tempoMsPerWord = 60L,
                startDelayMs = 0L,
                endDelayMs = 0L,
                rampUpFrames = 0,
                rampDownFrames = 0,
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
                sentenceEndPauseMs = 0L,
            )

        val withoutClause =
            englishFrames(
                tokens = listOf(w("because")),
                startIndex = 0,
                config = baseConfig.copy(useClausePausing = false)
            )
                .first()
                .durationMs
        val withClause =
            englishFrames(
                tokens = listOf(w("because")),
                startIndex = 0,
                config = baseConfig.copy(useClausePausing = true)
            )
                .first()
                .durationMs

        assertTrue(
            "Expected clause pacing to slow clause starters at high speed",
            withClause > withoutClause
        )
    }
}
