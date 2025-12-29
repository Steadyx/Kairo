package com.example.kairo.core.rsvp

import com.example.kairo.core.language.LanguageTagNormalizer
import com.example.kairo.core.model.RsvpConfig
import kotlin.math.max
import kotlin.math.roundToLong

object RsvpConfigResolver {
    fun resolve(
        baseConfig: RsvpConfig,
        languageTag: String?,
    ): RsvpConfig {
        val normalized = LanguageTagNormalizer.normalize(languageTag)?.lowercase()
        return when {
            normalized == null -> baseConfig
            normalized.startsWith("ja") -> baseConfig.withCjkAdjustments()
            normalized.startsWith("zh") -> baseConfig.withCjkAdjustments()
            normalized.startsWith("ko") -> baseConfig.withCjkAdjustments()
            else -> baseConfig
        }
    }
}

private fun RsvpConfig.withCjkAdjustments(): RsvpConfig =
    copy(
        tempoMsPerWord = (tempoMsPerWord * CJK_TEMPO_MULTIPLIER).roundToLong(),
        minWordMs = max(minWordMs, CJK_MIN_WORD_MS),
        longWordMinMs = max(longWordMinMs, CJK_LONG_WORD_MIN_MS),
    )

private const val CJK_TEMPO_MULTIPLIER = 1.35
private const val CJK_MIN_WORD_MS = 65L
private const val CJK_LONG_WORD_MIN_MS = 140L
