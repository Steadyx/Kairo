package com.kairo.reader.data.preferences

import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.RsvpCustomProfile
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpPreferenceCodecsTest {
    private val profileCodec = RsvpProfileJsonCodec()
    private val preferenceCodec = RsvpConfigPreferenceCodec(PrefKeys, profileCodec)

    @Test
    fun customProfileJsonRoundTripsEveryConfigField() {
        val config = distinctiveConfig()
        val profile =
            RsvpCustomProfile(
                id = "user:test",
                name = "Test profile",
                config = config,
                updatedAtMs = 1234L,
            )

        val decoded = profileCodec.parseCustomProfiles(profileCodec.encodeCustomProfiles(listOf(profile)))

        assertEquals(listOf(profile), decoded)
    }

    @Test
    fun malformedCustomProfileJsonReportsDiagnosticAndFallsBack() {
        val errors = mutableListOf<Throwable>()
        val codec = RsvpProfileJsonCodec(errors::add)

        assertTrue(codec.parseCustomProfiles("{not-json").isEmpty())
        assertEquals(1, errors.size)
    }

    @Test
    fun dataStoreCodecRoundTripsEveryConfigField() {
        val expected = distinctiveConfig()
        val preferences = mutablePreferencesOf()

        preferenceCodec.writeRsvpConfig(preferences, expected)
        val actual = preferenceCodec.readRsvpConfig(preferences, RsvpConfig())

        assertEquals(expected.copy(baseWpm = (60_000.0 / expected.tempoMsPerWord).toInt()), actual)
    }

    @Test
    fun obsoleteFieldsInExistingPreferencesAndProfilesAreIgnored() {
        val config = distinctiveConfig()
        val preferences = mutablePreferencesOf()
        preferenceCodec.writeRsvpConfig(preferences, config)
        val expected = preferenceCodec.readRsvpConfig(preferences, RsvpConfig())
        preferences[intPreferencesKey("words_per_frame")] = 99
        preferences[doublePreferencesKey("long_word_multiplier")] = 99.0
        assertEquals(expected, preferenceCodec.readRsvpConfig(preferences, RsvpConfig()))
        val profile = RsvpCustomProfile("user:legacy", "Legacy", config, 1L)
        val json = JSONArray(profileCodec.encodeCustomProfiles(listOf(profile)))
        json.getJSONObject(0).getJSONObject("config").put("wordsPerFrame", 99).put("longWordMultiplier", 99.0)
        assertEquals(listOf(profile), profileCodec.parseCustomProfiles(json.toString()))
    }

    private fun distinctiveConfig(): RsvpConfig =
        RsvpConfig(
            tempoMsPerWord = 137L,
            minWordMs = 41L,
            longWordMinMs = 171L,
            longWordChars = 11,
            syllableExtraMs = 17L,
            rarityExtraMaxMs = 73L,
            complexityStrength = 0.63,
            lengthStrength = 0.42,
            lengthExponent = 1.31,
            enablePhraseChunking = true,
            maxWordsPerUnit = 3,
            maxCharsPerUnit = 19,
            subwordChunkPauseMs = 29L,
            contextAssistMode = RsvpContextAssistMode.FULL_CLAUSE,
            useRegressionAdaptivePacing = true,
            commaPauseMs = 51L,
            periodPauseMs = 211L,
            semicolonPauseMs = 83L,
            colonPauseMs = 79L,
            dashPauseMs = 89L,
            parenthesesPauseMs = 97L,
            quotePauseMs = 61L,
            sentenceEndPauseMs = 191L,
            paragraphPauseMs = 307L,
            paragraphPauseMultiplier = 1.4,
            pageBreakPauseMultiplier = 2.7,
            pauseScaleExponent = 0.61,
            minPauseScale = 0.58,
            usePunctuationLandingHold = false,
            parentheticalMultiplier = 1.17,
            dialogueMultiplier = 0.93,
            smoothingAlpha = 0.71,
            maxSpeedupFactor = 1.24,
            maxSlowdownFactor = 1.47,
            useProsodyPacing = true,
            prosodyStrength = 1.2,
            orpEnabled = false,
            orpHighlightEnabled = false,
            orpGuideEnabled = false,
            orpGuideBrightness = 1.3,
            orpGuideThickness = 1.7,
            startDelayMs = 31L,
            endDelayMs = 37L,
            rampUpFrames = 4,
            rampDownFrames = 5,
            useAdaptiveTiming = false,
            adaptiveDifficultyMaxHoldMs = 101L,
            complexWordHoldMs = 109L,
            complexWordThreshold = 1.33,
            maxChunkLength = 23,
            punctuationPauseFactor = 1.21,
            useClausePausing = false,
            clausePauseFactor = 1.27,
            useFocalStress = false,
            focalSupportCompression = 0.87,
            useAnticipatoryLanding = false,
            anticipatoryLandingBoost = 1.11,
            dialoguePunctuationScale = 0.72,
            useParentheticalAside = true,
            parentheticalAsideMultiplier = 0.78,
            blinkMode = BlinkMode.SUBTLE,
        )
}
