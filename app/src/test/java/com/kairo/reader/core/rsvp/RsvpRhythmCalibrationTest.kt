package com.kairo.reader.core.rsvp

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpCustomProfile
import com.kairo.reader.data.preferences.PrefKeys
import com.kairo.reader.data.preferences.RsvpConfigPreferenceCodec
import com.kairo.reader.data.preferences.RsvpProfileJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpRhythmCalibrationTest {
    @Test
    fun savedRhythmRecognizesBothPresetsAfterPreferencesReload() {
        val codec = RsvpConfigPreferenceCodec(PrefKeys, RsvpProfileJsonCodec())
        val prefs = mutablePreferencesOf()
        val gentle = RsvpRhythmChoice.GENTLE
        val strong = RsvpRhythmChoice.EXPRESSIVE
        for (choices in listOf(listOf(strong, gentle, strong), listOf(gentle, strong, gentle))) {
            codec.writeRsvpConfig(prefs, applyRsvpRhythmChoices(RsvpConfig(), choices))
            val restored = codec.readRsvpConfig(prefs)
            assertEquals(choices, RsvpRhythmAspect.entries.map { savedRhythmChoice(restored, it) })
        }
    }

    @Test
    fun fineTuningOnlyMarksTheAffectedAspectAsCustom() {
        val strong = RsvpRhythmChoice.EXPRESSIVE
        val preset = applyRsvpRhythmChoices(RsvpConfig(), RsvpRhythmAspect.entries.map { strong })
        val keep = RsvpRhythmChoice.KEEP
        assertEquals(keep, savedRhythmChoice(preset.copy(clausePauseFactor = 1.25), RsvpRhythmAspect.LANDINGS))
        assertEquals(keep, savedRhythmChoice(preset.copy(useFocalStress = false), RsvpRhythmAspect.VARIATION))
        val customRestart = preset.copy(rampUpFrames = 4, tempoMsPerWord = 312)
        assertEquals(listOf(strong, strong, keep), RsvpRhythmAspect.entries.map { savedRhythmChoice(customRestart, it) })
    }

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
        val codec = RsvpProfileJsonCodec()
        for (choice in RsvpRhythmChoice.entries) {
            val config = applyRsvpRhythmChoices(RsvpConfig(), List(3) { choice })
            val profile = RsvpCustomProfile("user:rhythm", "My rhythm", config, 1L)
            assertEquals(listOf(profile), codec.parseCustomProfiles(codec.encodeCustomProfiles(listOf(profile))))
        }
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
