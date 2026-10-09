package com.kairo.reader.core.rsvp

import org.junit.Assert.assertTrue
import org.junit.Test

class ComprehensionRsvpProsodyTest : ComprehensionRsvpTestBase() {
    @Test
    fun speakerTagsReadFasterWhenDialogueDetectionEnabled() {
        val baseConfig =
            stableConfig.copy(
                tempoMsPerWord = 200L,
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
                sentenceEndPauseMs = 0L,
                commaPauseMs = 0L,
                semicolonPauseMs = 0L,
                colonPauseMs = 0L,
                dashPauseMs = 0L,
                parenthesesPauseMs = 0L,
                quotePauseMs = 0L,
                paragraphPauseMs = 0L,
                useClausePausing = false,
                useDialogueDetection = false,
            )

        val tokens = listOf(w("he"), w("said"))
        val withoutDetection = englishFrames(tokens, 0, baseConfig)
        val withDetection = englishFrames(
            tokens,
            0,
            baseConfig.copy(useDialogueDetection = true)
        )

        assertTrue(withoutDetection.size >= 2 && withDetection.size >= 2)
        assertTrue(withDetection[0].durationMs < withoutDetection[0].durationMs)
        assertTrue(withDetection[1].durationMs < withoutDetection[1].durationMs)
    }

    @Test
    fun functionWordBridgeReadsFasterThanStandaloneFunctionWord() {
        val config =
            stableConfig.copy(
                tempoMsPerWord = 70L,
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
                sentenceEndPauseMs = 0L,
                commaPauseMs = 0L,
                semicolonPauseMs = 0L,
                colonPauseMs = 0L,
                dashPauseMs = 0L,
                parenthesesPauseMs = 0L,
                quotePauseMs = 0L,
                paragraphPauseMs = 0L,
                useAdaptiveTiming = false,
                useClausePausing = false,
                useDialogueDetection = false,
            )

        val standalone = englishFrames(listOf(w("the")), 0, config).first().durationMs
        val bridged = englishFrames(listOf(w("the"), w("mountain")), 0, config)

        assertTrue(bridged.isNotEmpty())
        assertTrue(
            "Expected function-word bridge to glide faster than standalone",
            bridged[0].durationMs < standalone,
        )
    }

    @Test
    fun negationWordsGetExtraWeightComparedToNeutralFunctionWords() {
        val config =
            stableConfig.copy(
                tempoMsPerWord = 70L,
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
                sentenceEndPauseMs = 0L,
                commaPauseMs = 0L,
                semicolonPauseMs = 0L,
                colonPauseMs = 0L,
                dashPauseMs = 0L,
                parenthesesPauseMs = 0L,
                quotePauseMs = 0L,
                paragraphPauseMs = 0L,
                useAdaptiveTiming = false,
                useClausePausing = false,
                useDialogueDetection = false,
            )

        val neutral = englishFrames(listOf(w("and"), w("ready")), 0, config)
        val negation = englishFrames(listOf(w("not"), w("ready")), 0, config)

        assertTrue(neutral.isNotEmpty() && negation.isNotEmpty())
        assertTrue(
            "Expected negation words to retain more time for comprehension",
            negation[0].durationMs > neutral[0].durationMs,
        )
    }

    @Test
    fun prosodyToggleOffDisablesFunctionWordGlide() {
        val config =
            stableConfig.copy(
                tempoMsPerWord = 70L,
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
                sentenceEndPauseMs = 0L,
                commaPauseMs = 0L,
                semicolonPauseMs = 0L,
                colonPauseMs = 0L,
                dashPauseMs = 0L,
                parenthesesPauseMs = 0L,
                quotePauseMs = 0L,
                paragraphPauseMs = 0L,
                useAdaptiveTiming = false,
                useClausePausing = false,
                useDialogueDetection = false,
            )

        val withProsody = englishFrames(listOf(w("the"), w("mountain")), 0, config)
        val withoutProsody = englishFrames(
            listOf(w("the"), w("mountain")),
            0,
            config.copy(useProsodyPacing = false),
        )

        assertTrue(withProsody.isNotEmpty() && withoutProsody.isNotEmpty())
        assertTrue(withProsody[0].durationMs < withoutProsody[0].durationMs)
    }

    @Test
    fun prosodyStrengthControlsHowStronglyGlideApplies() {
        val config =
            stableConfig.copy(
                tempoMsPerWord = 70L,
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
                sentenceEndPauseMs = 0L,
                commaPauseMs = 0L,
                semicolonPauseMs = 0L,
                colonPauseMs = 0L,
                dashPauseMs = 0L,
                parenthesesPauseMs = 0L,
                quotePauseMs = 0L,
                paragraphPauseMs = 0L,
                useAdaptiveTiming = false,
                useClausePausing = false,
                useDialogueDetection = false,
                useProsodyPacing = true,
            )

        val low = englishFrames(
            listOf(w("the"), w("mountain")),
            0,
            config.copy(prosodyStrength = 0.2),
        )
        val high = englishFrames(
            listOf(w("the"), w("mountain")),
            0,
            config.copy(prosodyStrength = 1.6),
        )

        assertTrue(low.isNotEmpty() && high.isNotEmpty())
        assertTrue(high[0].durationMs < low[0].durationMs)
    }
}
