package com.kairo.reader.core.rsvp

import com.kairo.reader.core.language.LanguageFamily
import com.kairo.reader.core.language.LanguageFamilyClassifier
import com.kairo.reader.core.model.RsvpConfig

enum class RsvpLanguagePolicy {
    ENGLISH,
    DEFAULT_NON_ENGLISH,
    CJK,
    RTL,
    UNKNOWN,
    ;

    companion object {
        fun fromLanguageTag(languageTag: String?): RsvpLanguagePolicy =
            when (LanguageFamilyClassifier.classify(languageTag).family) {
                LanguageFamily.ENGLISH -> ENGLISH
                LanguageFamily.DEFAULT_NON_ENGLISH -> DEFAULT_NON_ENGLISH
                LanguageFamily.CJK -> CJK
                LanguageFamily.RTL -> RTL
                LanguageFamily.UNKNOWN -> UNKNOWN
            }
    }
}

enum class RsvpSegmentationStrategy {
    LEGACY_GREEDY,
    SCORED_DP_V2,
}

data class RsvpGenerationOptions(
    val languagePolicy: RsvpLanguagePolicy = RsvpLanguagePolicy.UNKNOWN,
    val segmentationStrategy: RsvpSegmentationStrategy = RsvpSegmentationStrategy.LEGACY_GREEDY,
) {
    fun asPaceEstimationOptions(): RsvpPaceEstimationOptions =
        if (languagePolicy == RsvpLanguagePolicy.ENGLISH) {
            RsvpPaceEstimationOptions(segmentationStrategy = segmentationStrategy)
        } else {
            // The estimator currently uses an English sample. Until there are representative
            // samples for each language family, do not apply a non-English policy to that text.
            RsvpPaceEstimationOptions.LEGACY
        }

    companion object {
        val LEGACY = RsvpGenerationOptions()
    }
}

internal fun RsvpGenerationOptions.usesScoredSegmentation(config: RsvpConfig): Boolean =
    segmentationStrategy == RsvpSegmentationStrategy.SCORED_DP_V2 &&
        when (languagePolicy) {
            RsvpLanguagePolicy.ENGLISH ->
                config.maxWordsPerUnit in SUPPORTED_ENGLISH_SCORED_WORD_COUNTS
            RsvpLanguagePolicy.DEFAULT_NON_ENGLISH,
            RsvpLanguagePolicy.CJK,
            RsvpLanguagePolicy.RTL ->
                config.maxWordsPerUnit in SUPPORTED_NON_ENGLISH_SCORED_WORD_COUNTS
            RsvpLanguagePolicy.UNKNOWN -> false
        }

data class RsvpPaceEstimationOptions(
    val sampleLanguagePolicy: RsvpLanguagePolicy = RsvpLanguagePolicy.ENGLISH,
    val segmentationStrategy: RsvpSegmentationStrategy = RsvpSegmentationStrategy.LEGACY_GREEDY,
) {
    fun asGenerationOptions(): RsvpGenerationOptions =
        if (sampleLanguagePolicy == RsvpLanguagePolicy.ENGLISH) {
            RsvpGenerationOptions(
                languagePolicy = sampleLanguagePolicy,
                segmentationStrategy = segmentationStrategy,
            )
        } else {
            // The fallback still reads the English estimator sample; only grouping is legacy.
            RsvpGenerationOptions(languagePolicy = RsvpLanguagePolicy.ENGLISH)
        }

    companion object {
        val LEGACY = RsvpPaceEstimationOptions()
    }
}

object RsvpSegmentationRolloutResolver {
    fun resolve(
        languageTag: String?,
        config: RsvpConfig,
        isDebugBuild: Boolean,
    ): RsvpGenerationOptions =
        resolve(
            languagePolicy = RsvpLanguagePolicy.fromLanguageTag(languageTag),
            config = config,
            isDebugBuild = isDebugBuild,
        )

    fun resolve(
        languagePolicy: RsvpLanguagePolicy,
        config: RsvpConfig,
        isDebugBuild: Boolean,
    ): RsvpGenerationOptions {
        val scoredOptions =
            RsvpGenerationOptions(
                languagePolicy = languagePolicy,
                segmentationStrategy = RsvpSegmentationStrategy.SCORED_DP_V2,
            )
        val strategy =
            if ((isDebugBuild || languagePolicy == RsvpLanguagePolicy.ENGLISH) &&
                scoredOptions.usesScoredSegmentation(config)
            ) {
                RsvpSegmentationStrategy.SCORED_DP_V2
            } else {
                RsvpSegmentationStrategy.LEGACY_GREEDY
            }
        return RsvpGenerationOptions(
            languagePolicy = languagePolicy,
            segmentationStrategy = strategy,
        )
    }
}

private val SUPPORTED_ENGLISH_SCORED_WORD_COUNTS = 1..3
private val SUPPORTED_NON_ENGLISH_SCORED_WORD_COUNTS = 1..2
