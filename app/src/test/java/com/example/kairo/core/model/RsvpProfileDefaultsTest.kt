package com.example.kairo.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpProfileDefaultsTest {
    @Test
    fun builtInProfilesHavePhraseChunkingOffByDefault() {
        RsvpProfile.entries.forEach { profile ->
            val config = profile.defaultConfig()
            assertFalse(
                "Expected phrase chunking off for ${profile.name}",
                config.enablePhraseChunking,
            )
        }
    }

    @Test
    fun builtInProfilesEnablePunctuationLandingByDefault() {
        RsvpProfile.entries.forEach { profile ->
            val config = profile.defaultConfig()
            assertTrue(
                "Expected punctuation landing on for ${profile.name}",
                config.usePunctuationLandingHold,
            )
        }
    }

    @Test
    fun builtInProfilesMaintainReadablePauseHierarchy() {
        RsvpProfile.entries.forEach { profile ->
            val config = profile.defaultConfig()
            assertTrue("Expected comma pause for ${profile.name}", config.commaPauseMs > 0L)
            assertTrue(
                "Expected sentence pauses to exceed comma pauses for ${profile.name}",
                config.periodPauseMs > config.commaPauseMs &&
                    config.sentenceEndPauseMs > config.commaPauseMs,
            )
            assertTrue(
                "Expected paragraph pauses to exceed sentence pauses for ${profile.name}",
                config.paragraphPauseMs > config.periodPauseMs,
            )
            assertTrue(
                "Expected long-word floor to exceed base floor for ${profile.name}",
                config.longWordMinMs > config.minWordMs,
            )
            assertTrue(
                "Expected pause floor to stay within a readable range for ${profile.name}",
                config.minPauseScale in 0.65..0.9,
            )
        }
    }

    @Test
    fun builtInProfilesDefineDistinctProsodyStrengths() {
        val distinctProsodyStrengths =
            RsvpProfile.entries
                .map { it.defaultConfig().prosodyStrength }
                .toSet()
        assertTrue(
            "Expected profiles to provide meaningful cadence variation",
            distinctProsodyStrengths.size >= 4,
        )
    }
}
