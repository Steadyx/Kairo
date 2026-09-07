package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpGenerationOptionsTest {
    private val eligibleConfig =
        RsvpConfig(
            enablePhraseChunking = true,
            maxWordsPerUnit = 3,
        )

    @Test
    fun defaultsRemainLegacyAndConservative() {
        assertEquals(RsvpLanguagePolicy.UNKNOWN, RsvpGenerationOptions().languagePolicy)
        assertEquals(
            RsvpSegmentationStrategy.LEGACY_GREEDY,
            RsvpGenerationOptions().segmentationStrategy,
        )
    }

    @Test
    fun releaseRolloutUsesScoredEnglishAndKeepsOtherLanguagesConservative() {
        RsvpLanguagePolicy.entries.forEach { policy ->
            assertEquals(
                if (policy == RsvpLanguagePolicy.ENGLISH) {
                    RsvpSegmentationStrategy.SCORED_DP_V2
                } else {
                    RsvpSegmentationStrategy.LEGACY_GREEDY
                },
                RsvpSegmentationRolloutResolver.resolve(
                    languagePolicy = policy,
                    config = eligibleConfig,
                    isDebugBuild = false,
                ).segmentationStrategy,
            )
        }
    }

    @Test
    fun debugRolloutUsesExplicitLanguagePoliciesWithinTheirSupportedWidths() {
        assertEquals(
            RsvpSegmentationStrategy.SCORED_DP_V2,
            RsvpSegmentationRolloutResolver.resolve(
                languageTag = "en-GB",
                config = eligibleConfig,
                isDebugBuild = true,
            ).segmentationStrategy,
        )
        assertEquals(
            RsvpSegmentationStrategy.SCORED_DP_V2,
            RsvpSegmentationRolloutResolver.resolve(
                languageTag = "eng",
                config = eligibleConfig,
                isDebugBuild = true,
            ).segmentationStrategy,
        )
        listOf("fr", "ja", "ar").forEach { languageTag ->
            assertEquals(
                RsvpSegmentationStrategy.SCORED_DP_V2,
                RsvpSegmentationRolloutResolver.resolve(
                    languageTag = languageTag,
                    config = eligibleConfig.copy(maxWordsPerUnit = 2),
                    isDebugBuild = true,
                ).segmentationStrategy,
            )
        }
        assertEquals(
            RsvpSegmentationStrategy.SCORED_DP_V2,
            RsvpSegmentationRolloutResolver.resolve(
                languageTag = "en",
                config = eligibleConfig.copy(enablePhraseChunking = false),
                isDebugBuild = true,
            ).segmentationStrategy,
        )

        val ineligible =
            listOf(
                RsvpSegmentationRolloutResolver.resolve("fr", eligibleConfig, true),
                RsvpSegmentationRolloutResolver.resolve(null, eligibleConfig, true),
                RsvpSegmentationRolloutResolver.resolve(
                    "fr",
                    eligibleConfig.copy(maxWordsPerUnit = 3),
                    true,
                ),
                RsvpSegmentationRolloutResolver.resolve(
                    "en",
                    eligibleConfig.copy(maxWordsPerUnit = 4),
                    true,
                ),
            )
        assertEquals(
            List(ineligible.size) { RsvpSegmentationStrategy.LEGACY_GREEDY },
            ineligible.map(RsvpGenerationOptions::segmentationStrategy),
        )
    }
}
