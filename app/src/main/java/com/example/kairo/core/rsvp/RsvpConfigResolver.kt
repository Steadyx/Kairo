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
            normalized.startsWith("ar") -> baseConfig.withRtlAdjustments()
            normalized.startsWith("he") -> baseConfig.withRtlAdjustments()
            else -> baseConfig
        }
    }

    fun toBaseTempoMs(
        tempoMsPerWord: Long,
        languageTag: String?,
    ): Long {
        val normalized = LanguageTagNormalizer.normalize(languageTag)?.lowercase()
        val multiplier =
            when {
                normalized == null -> 1.0
                normalized.startsWith("ja") -> CJK_TEMPO_MULTIPLIER
                normalized.startsWith("zh") -> CJK_TEMPO_MULTIPLIER
                normalized.startsWith("ko") -> CJK_TEMPO_MULTIPLIER
                normalized.startsWith("ar") -> RTL_TEMPO_MULTIPLIER
                normalized.startsWith("he") -> RTL_TEMPO_MULTIPLIER
                else -> 1.0
            }
        if (multiplier == 1.0) return tempoMsPerWord
        return (tempoMsPerWord / multiplier).roundToLong().coerceAtLeast(1L)
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

private fun RsvpConfig.withRtlAdjustments(): RsvpConfig =
    copy(
        tempoMsPerWord = (tempoMsPerWord * RTL_TEMPO_MULTIPLIER).roundToLong(),
        minWordMs = max(minWordMs, RTL_MIN_WORD_MS),
        longWordMinMs = max(longWordMinMs, RTL_LONG_WORD_MIN_MS),
    )

private const val RTL_TEMPO_MULTIPLIER = 1.2
private const val RTL_MIN_WORD_MS = 55L
private const val RTL_LONG_WORD_MIN_MS = 130L
