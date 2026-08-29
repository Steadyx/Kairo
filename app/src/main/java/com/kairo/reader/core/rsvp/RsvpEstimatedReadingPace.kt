package com.kairo.reader.core.rsvp

import com.kairo.reader.core.language.LanguageFamilyClassifier
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.effectiveBlinkMode
import com.kairo.reader.core.model.isSentenceEndingPunctuation
import com.kairo.reader.core.model.wordFloorMsForReadability
import com.kairo.reader.core.rsvp.engine.frameTimingKey
import com.kairo.reader.core.rsvp.text.isOpeningPunctuationChar
import com.kairo.reader.core.rsvp.text.isQuoteOrBracket
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTimingPolicy
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object RsvpEstimatedReadingPace {
    fun estimateWpm(
        config: RsvpConfig,
        sessionTempoMsPerWord: Long? = null,
        fallbackEstimatedWpm: Int = 0,
        languageTag: String? = null,
        paceOptions: RsvpPaceEstimationOptions = RsvpPaceEstimationOptions.LEGACY,
    ): Int {
        val effectiveConfig =
            sessionTempoMsPerWord
                ?.takeIf { it > 0L }
                ?.let { tempoMsPerWord -> config.withLiveTempo(tempoMsPerWord) }
                ?: config
        val cacheKey =
            EstimatedWpmCacheKey(
                timingConfig = effectiveConfig.estimatedWpmTimingKey(),
                paceOptions = paceOptions,
                targetLanguageTag = LanguageFamilyClassifier.classify(languageTag).normalizedTag,
            )
        cachedEstimate(cacheKey)?.let { return it }

        val estimatedWpm =
            runCatching {
                RsvpPaceEstimator.estimateWpm(
                    config = effectiveConfig,
                    options = paceOptions,
                )
            }
                .getOrNull()
        if (estimatedWpm != null) {
            cacheEstimate(cacheKey, estimatedWpm)
            return estimatedWpm
        }

        return fallbackEstimatedWpm
            .takeIf { it > 0 }
            ?: RsvpEffectivePace.estimateWpm(
                config = config,
                sessionTempoMsPerWord = sessionTempoMsPerWord,
            )
    }

    private fun RsvpConfig.withLiveTempo(tempoMsPerWord: Long): RsvpConfig =
        copy(
            tempoMsPerWord = tempoMsPerWord,
            baseWpm =
            (MILLISECONDS_PER_MINUTE / tempoMsPerWord.toDouble())
                .roundToInt()
                .coerceAtLeast(1),
        )

    fun adjustPreviewWpm(
        baseEstimatedWpm: Int,
        baseTempoMsPerWord: Long,
        sessionTempoMsPerWord: Long?,
    ): Int {
        val normalizedBaseEstimatedWpm = baseEstimatedWpm.coerceAtLeast(0)
        val effectiveSessionTempo = sessionTempoMsPerWord?.takeIf { it > 0L }
        if (
            normalizedBaseEstimatedWpm <= 0 ||
            effectiveSessionTempo == null ||
            baseTempoMsPerWord <= 0L
        ) {
            return normalizedBaseEstimatedWpm
        }

        val tempoScale =
            (effectiveSessionTempo.toDouble() / baseTempoMsPerWord.toDouble())
                .coerceIn(TEMPO_SCALE_MIN, TEMPO_SCALE_MAX)
        return (normalizedBaseEstimatedWpm / tempoScale).roundToInt().coerceAtLeast(1)
    }

    fun estimateChapterPreviewWpm(
        config: RsvpConfig,
        frames: List<RsvpFrame>,
        baseTempoMsPerWord: Long,
        sessionTempoMsPerWord: Long?,
        fallbackEstimatedWpm: Int = 0,
        languageTag: String? = null,
        paceOptions: RsvpPaceEstimationOptions = RsvpPaceEstimationOptions.LEGACY,
    ): Int {
        val effectiveTempoMsPerWord =
            sessionTempoMsPerWord
                ?.takeIf { it > 0L }
                ?: baseTempoMsPerWord.takeIf { it > 0L }
                ?: config.tempoMsPerWord
        if (frames.isEmpty() || effectiveTempoMsPerWord <= 0L) {
            return fallbackEstimatedWpm
                .takeIf { it > 0 }
                ?: estimateWpm(
                    config = config,
                    sessionTempoMsPerWord = sessionTempoMsPerWord,
                    languageTag = languageTag,
                    paceOptions = paceOptions,
                )
        }

        val tempoScale =
            if (baseTempoMsPerWord > 0L) {
                (effectiveTempoMsPerWord.toDouble() / baseTempoMsPerWord.toDouble())
                    .coerceIn(TEMPO_SCALE_MIN, TEMPO_SCALE_MAX)
            } else {
                1.0
            }

        var wordCount = 0
        var lastSingleWordOriginalIndex: Int? = null
        var totalMs = 0L
        frames.forEach { frame ->
            wordCount += countPreviewWords(frame, lastSingleWordOriginalIndex)
            lastSingleWordOriginalIndex = nextSingleWordOriginalIndex(frame, lastSingleWordOriginalIndex)
            if (shouldSkipBlinkFrame(frame, config, effectiveTempoMsPerWord, tempoScale)) {
                return@forEach
            }
            val scaledMs =
                (frame.durationMs * tempoScale)
                    .roundToLong()
                    .coerceAtLeast(MIN_FRAME_DELAY_MS)
            val floorMs = frameFloorMs(frame, config, effectiveTempoMsPerWord)
            totalMs += max(scaledMs, floorMs)
        }

        if (wordCount <= 0 || totalMs <= 0L) {
            return fallbackEstimatedWpm
                .takeIf { it > 0 }
                ?: estimateWpm(
                    config = config,
                    sessionTempoMsPerWord = sessionTempoMsPerWord,
                    languageTag = languageTag,
                    paceOptions = paceOptions,
                )
        }

        return ((wordCount * MILLISECONDS_PER_MINUTE) / totalMs.toDouble())
            .roundToInt()
            .coerceAtLeast(1)
    }

    private fun cachedEstimate(key: EstimatedWpmCacheKey): Int? =
        synchronized(estimateCacheLock) {
            estimatedWpmCache[key]
        }

    private fun cacheEstimate(
        key: EstimatedWpmCacheKey,
        estimatedWpm: Int,
    ) {
        synchronized(estimateCacheLock) {
            estimatedWpmCache[key] = estimatedWpm
        }
    }

    private fun RsvpConfig.estimatedWpmTimingKey(): RsvpConfig =
        frameTimingKey().copy(
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
        )
}

internal data class EstimatedWpmCacheKey(
    val timingConfig: RsvpConfig,
    val paceOptions: RsvpPaceEstimationOptions,
    val targetLanguageTag: String?,
)

private val estimateCacheLock = Any()
private val estimatedWpmCache =
    object : LinkedHashMap<EstimatedWpmCacheKey, Int>(
        ESTIMATED_WPM_CACHE_SIZE,
        ESTIMATED_WPM_CACHE_LOAD_FACTOR,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<EstimatedWpmCacheKey, Int>?): Boolean =
            size > ESTIMATED_WPM_CACHE_SIZE
    }

private fun countPreviewWords(
    frame: RsvpFrame,
    lastSingleWordOriginalIndex: Int?,
): Int {
    val words = frame.tokens.filter { it.type == TokenType.WORD }
    if (words.isEmpty()) return 0
    if (words.size > 1) return words.size

    val duplicateSplitFrame = frame.originalTokenIndex == lastSingleWordOriginalIndex
    return if (duplicateSplitFrame) 0 else 1
}

private fun nextSingleWordOriginalIndex(
    frame: RsvpFrame,
    current: Int?,
): Int? {
    val wordCount = frame.tokens.count { it.type == TokenType.WORD }
    return when (wordCount) {
        0 -> current
        1 -> frame.originalTokenIndex
        else -> null
    }
}

internal fun frameFloorMs(
    frame: RsvpFrame,
    config: RsvpConfig,
    effectiveTempoMs: Long,
): Long {
    val tokens = frame.tokens
    if (tokens.isEmpty()) return frame.durationMs

    val firstWordIndex = tokens.indexOfFirst { it.type == TokenType.WORD }
    if (firstWordIndex == -1) return frame.durationMs

    val effectiveConfig = config.copy(tempoMsPerWord = effectiveTempoMs)
    var total = 0L
    tokens.forEachIndexed { index, token ->
        when (token.type) {
            TokenType.WORD -> total += config.wordFloorMsForReadability(token, effectiveTempoMs)
            TokenType.PUNCTUATION ->
                total += punctuationFloorMs(tokens, token, index, firstWordIndex, effectiveConfig)
            TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> Unit
        }
    }
    return max(total, MIN_FRAME_DELAY_MS)
}

private fun punctuationFloorMs(
    tokens: List<Token>,
    token: Token,
    index: Int,
    firstWordIndex: Int,
    config: RsvpConfig,
): Long {
    val ch = token.text.firstOrNull() ?: return 0L
    if (index < firstWordIndex && isOpeningPunctuationChar(ch)) return 0L

    val prevToken = tokens.getOrNull(index - 1)
    val nextToken = tokens.getOrNull(index + 1)
    val prevCh = prevToken?.text?.firstOrNull()
    val prevIsSentenceEnd =
        prevCh != null && (isSentenceEndingPunctuation(prevCh) || prevCh == '.')
    val isSentenceEnd = isSentenceEndingPunctuation(ch) || ch == '.'
    if (isSentenceEnd && prevIsSentenceEnd) return 0L
    if (isQuoteOrBracket(ch) &&
        (prevToken?.type == TokenType.PUNCTUATION || nextToken?.type == TokenType.PUNCTUATION)
    ) {
        return 0L
    }

    val prevWord = tokens.subList(0, index).lastOrNull { it.type == TokenType.WORD }
    return RsvpPunctuationTimingPolicy
        .resolvePauseTiming(token, prevWord, nextToken, config)
        .floorMs
        .roundToLong()
}

internal fun shouldSkipBlinkFrame(
    frame: RsvpFrame,
    config: RsvpConfig,
    effectiveTempoMs: Long,
    tempoScale: Double,
): Boolean {
    if (config.effectiveBlinkMode(effectiveTempoMs) != BlinkMode.OFF) return false
    if (effectiveTempoMs >= BLINK_SKIP_TEMPO_MS) return false
    if (frame.tokens.any { it.type == TokenType.WORD }) return false
    if (frame.tokens.size != 1) return false
    val token = frame.tokens.first()
    if (token.type != TokenType.PUNCTUATION || token.text != " ") return false
    val scaledMs = (frame.durationMs * tempoScale).roundToLong()
    return scaledMs <= BLINK_SKIP_MAX_MS
}

private const val TEMPO_SCALE_MIN = 0.1
private const val TEMPO_SCALE_MAX = 4.0
private const val ESTIMATED_WPM_CACHE_SIZE = 32
private const val ESTIMATED_WPM_CACHE_LOAD_FACTOR = 0.75f
private const val MIN_FRAME_DELAY_MS = 1L
private const val BLINK_SKIP_TEMPO_MS = 200L
private const val BLINK_SKIP_MAX_MS = 48L
