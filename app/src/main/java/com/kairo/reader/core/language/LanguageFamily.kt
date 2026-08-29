package com.kairo.reader.core.language

import java.util.Locale

enum class LanguageFamily {
    ENGLISH,
    DEFAULT_NON_ENGLISH,
    CJK,
    RTL,
    UNKNOWN,
}

data class LanguageClassification(val normalizedTag: String?, val primaryLanguage: String?, val family: LanguageFamily,)

object LanguageFamilyClassifier {
    fun classify(languageTag: String?): LanguageClassification {
        val normalizedTag = LanguageTagNormalizer.normalize(languageTag)?.lowercase(Locale.ROOT)
        val primaryLanguage =
            normalizedTag
                ?.substringBefore('-')
                ?.takeIf { it.isNotBlank() }
                ?.canonicalPrimaryLanguage()
        val family =
            when (primaryLanguage) {
                null -> LanguageFamily.UNKNOWN
                "en" -> LanguageFamily.ENGLISH
                "ja", "zh", "ko" -> LanguageFamily.CJK
                "ar", "he" -> LanguageFamily.RTL
                else -> LanguageFamily.DEFAULT_NON_ENGLISH
            }
        return LanguageClassification(
            normalizedTag = normalizedTag,
            primaryLanguage = primaryLanguage,
            family = family,
        )
    }

    private fun String.canonicalPrimaryLanguage(): String =
        when (this) {
            "eng" -> "en"
            "jpn" -> "ja"
            "chi", "cmn", "zho" -> "zh"
            "kor" -> "ko"
            "ara", "arb" -> "ar"
            "heb", "iw" -> "he"
            else -> this
        }
}
