package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpPaceEstimatorTest {
    @Test
    fun lowerTempoProducesHigherEstimatedWpm() {
        val slow = RsvpPaceEstimator.estimateWpm(RsvpConfig(tempoMsPerWord = 160L))
        val fast = RsvpPaceEstimator.estimateWpm(RsvpConfig(tempoMsPerWord = 90L))

        assertTrue("Expected fast($fast) > slow($slow)", fast > slow)
    }

    @Test
    fun paceCacheRetainsConfigurationAndTargetLanguageIdentity() {
        val config = RsvpConfig(enablePhraseChunking = true, maxWordsPerUnit = 3)
        val options = RsvpPaceEstimationOptions.DEFAULT
        assertTrue(RsvpEstimatedReadingPace.estimateWpm(config, paceOptions = options) > 0)
        val english = EstimatedWpmCacheKey(config, options, targetLanguageTag = "en")
        assertNotEquals(english, EstimatedWpmCacheKey(config, options, targetLanguageTag = "fr"))
        assertNotEquals(english, EstimatedWpmCacheKey(config.copy(maxWordsPerUnit = 1), options, targetLanguageTag = "en"))
    }

    @Test
    fun unsupportedSamplePolicyStillUsesTheActualEnglishSample() {
        val config =
            RsvpConfig(
                enablePhraseChunking = true,
                maxWordsPerUnit = 3,
                maxCharsPerUnit = 24,
            )
        val defaultEstimate = RsvpPaceEstimator.estimateWpm(config)
        val normalizedEstimate =
            RsvpPaceEstimator.estimateWpm(
                config,
                RsvpPaceEstimationOptions(
                    sampleLanguagePolicy = RsvpLanguagePolicy.DEFAULT_NON_ENGLISH,

                ),
            )

        assertEquals(defaultEstimate, normalizedEstimate)
    }
}
