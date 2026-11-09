package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpCustomProfile
import com.kairo.reader.data.preferences.RsvpProfileJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpRhythmCalibrationTest {
    @Test
    fun keepingAllChoicesIsLossless() {
        val config = RsvpConfig(tempoMsPerWord = 312, difficultWordSupport = 1.7)
        assertEquals(config, applyRsvpRhythmChoices(config, List(3) { RsvpRhythmChoice.KEEP }))
    }

    @Test
    fun calibrationChangesOnlyChosenDimensionsAndPreservesLatestSettings() {
        val latest = RsvpConfig(tempoMsPerWord = 312, minWordMs = 85, difficultWordSupport = 1.7, maxChunkLength = 0)
        val result = applyRsvpRhythmChoices(latest, listOf(RsvpRhythmChoice.KEEP, RsvpRhythmChoice.GENTLE))
        assertEquals(
            latest.copy(useProsodyPacing = true, useFocalStress = true, prosodyStrength = 0.45, focalSupportCompression = 0.98),
            result
        )
    }

    @Test
    fun choicesRoundTripThroughExistingProfileStorage() {
        val config = applyRsvpRhythmChoices(RsvpConfig(), List(3) { RsvpRhythmChoice.EXPRESSIVE })
        val codec = RsvpProfileJsonCodec()
        val profile = RsvpCustomProfile("user:rhythm", "My rhythm", config, 1L)
        assertEquals(listOf(profile), codec.parseCustomProfiles(codec.encodeCustomProfiles(listOf(profile))))
    }

    @Test
    fun variantsLeaveSpeedAndRecognitionFloorsAlone() {
        val config = RsvpConfig(tempoMsPerWord = 235, minWordMs = 80, longWordMinMs = 160)
        for (aspect in RsvpRhythmAspect.entries) {
            val a = rhythmCandidate(config, aspect, RsvpRhythmChoice.GENTLE)
            val b = rhythmCandidate(config, aspect, RsvpRhythmChoice.EXPRESSIVE)
            assertTrue(a != b)
            for (candidate in listOf(a, b)) {
                assertEquals(config.tempoMsPerWord, candidate.tempoMsPerWord)
                assertEquals(config.minWordMs, candidate.minWordMs)
                assertEquals(config.longWordMinMs, candidate.longWordMinMs)
                assertEquals(config.difficultWordSupport, candidate.difficultWordSupport, 0.0)
            }
        }
    }
}
