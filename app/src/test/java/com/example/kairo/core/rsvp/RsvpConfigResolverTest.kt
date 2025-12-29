package com.example.kairo.core.rsvp

import com.example.kairo.core.model.RsvpConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpConfigResolverTest {
    @Test
    fun keepsEnglishConfigUnchanged() {
        val base =
            RsvpConfig(
                tempoMsPerWord = 100L,
                minWordMs = 45L,
                longWordMinMs = 120L,
            )

        val resolved = RsvpConfigResolver.resolve(base, "en")

        assertEquals(base, resolved)
    }

    @Test
    fun appliesCjkAdjustments() {
        val base =
            RsvpConfig(
                tempoMsPerWord = 100L,
                minWordMs = 40L,
                longWordMinMs = 100L,
            )

        val resolved = RsvpConfigResolver.resolve(base, "ja")

        assertEquals(135L, resolved.tempoMsPerWord)
        assertEquals(65L, resolved.minWordMs)
        assertEquals(140L, resolved.longWordMinMs)
    }

    @Test
    fun appliesRtlAdjustments() {
        val base =
            RsvpConfig(
                tempoMsPerWord = 100L,
                minWordMs = 40L,
                longWordMinMs = 100L,
            )

        val resolved = RsvpConfigResolver.resolve(base, "ar")

        assertEquals(120L, resolved.tempoMsPerWord)
        assertEquals(55L, resolved.minWordMs)
        assertEquals(130L, resolved.longWordMinMs)
    }
}
