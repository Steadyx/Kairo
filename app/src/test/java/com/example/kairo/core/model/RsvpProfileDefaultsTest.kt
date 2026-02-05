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

