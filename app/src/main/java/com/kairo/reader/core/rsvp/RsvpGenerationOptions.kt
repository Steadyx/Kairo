package com.kairo.reader.core.rsvp

import com.kairo.reader.core.language.LanguageFamily
import com.kairo.reader.core.language.LanguageFamilyClassifier

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

/** Language changes the scoring evidence, never the segmentation implementation. */
data class RsvpGenerationOptions(val languagePolicy: RsvpLanguagePolicy = RsvpLanguagePolicy.UNKNOWN,) {
    // The estimator currently has one English sample. Do not apply another language's
    // rules to it; add representative samples before expanding estimation policies.
    fun asPaceEstimationOptions(): RsvpPaceEstimationOptions = RsvpPaceEstimationOptions.DEFAULT

    companion object {
        val DEFAULT = RsvpGenerationOptions()

        fun fromLanguageTag(languageTag: String?): RsvpGenerationOptions =
            RsvpGenerationOptions(RsvpLanguagePolicy.fromLanguageTag(languageTag))
    }
}

data class RsvpPaceEstimationOptions(val sampleLanguagePolicy: RsvpLanguagePolicy = RsvpLanguagePolicy.ENGLISH,) {
    fun asGenerationOptions(): RsvpGenerationOptions =
        RsvpGenerationOptions(languagePolicy = RsvpLanguagePolicy.ENGLISH)

    companion object {
        val DEFAULT = RsvpPaceEstimationOptions()
    }
}
