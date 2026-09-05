package com.kairo.reader.core.rsvp.engine

import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.effectiveBlinkMode
import com.kairo.reader.core.model.isMidSentencePunctuation
import com.kairo.reader.core.rsvp.analysis.shouldPreferHold
import com.kairo.reader.core.rsvp.analysis.wordEase
import com.kairo.reader.core.rsvp.text.isHardBoundary
import com.kairo.reader.core.rsvp.timing.RsvpSessionTimingPolicy
import com.kairo.reader.core.rsvp.timing.speedStrength
import com.kairo.reader.core.rsvp.timing.wordFloorMs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

internal fun applyPlaybackEffects(
    frames: MutableList<RsvpFrame>,
    config: RsvpConfig,
) {
    RsvpSessionTimingPolicy.applyInitialSessionRamps(frames = frames, config = config)
    applyBlinkSeparation(frames, config)
}

internal fun applyBlinkSeparation(
    frames: MutableList<RsvpFrame>,
    config: RsvpConfig,
) {
    val blinkMode = config.effectiveBlinkMode()
    // Early exit if blink mode is disabled - no processing needed
    if (blinkMode == BlinkMode.OFF) return
    if (frames.size < 2) return

    val strength = speedStrength(config.tempoMsPerWord.toDouble())
    if (strength < BLINK_START_STRENGTH) return
    val normalizedStrength =
        ((strength - BLINK_START_STRENGTH) / (1.0 - BLINK_START_STRENGTH))
            .coerceIn(0.0, 1.0)
    val easedStrength = normalizedStrength * normalizedStrength
    val targetBlinkMs =
        (MIN_BLINK_MS.toDouble() + (BLINK_EXTRA_MS * easedStrength))
            .roundToLong()
            .coerceIn(MIN_BLINK_MS, MAX_BLINK_MS)

    val blinkToken = Token(text = " ", type = TokenType.PUNCTUATION)
    val output = ArrayList<RsvpFrame>(frames.size * 2)

    for (i in frames.indices) {
        val frame = frames[i]
        val next = frames.getOrNull(i + 1)
        output += splitFrameForBlink(frame, next, config, blinkMode, targetBlinkMs, blinkToken)
    }

    frames.clear()
    frames.addAll(output)
}

private fun splitFrameForBlink(
    frame: RsvpFrame,
    next: RsvpFrame?,
    config: RsvpConfig,
    blinkMode: BlinkMode,
    targetBlinkMs: Long,
    blinkToken: Token,
): List<RsvpFrame> {
    val nextTokens = next?.tokens.orEmpty()
    val firstWord = frame.tokens.singleWordOrNull()
    val nextWord = nextTokens.singleWordOrNull()
    if (firstWord == null || nextWord == null) return listOf(frame)
    val shouldHold =
        frame.tokens.none { it.type == TokenType.PUNCTUATION } &&
            shouldPreferHold(firstWord, nextWord)
    if (shouldHold || isHardBoundary(frame.tokens, nextWord)) return listOf(frame)

    val floorMs = max(wordFloorMs(firstWord, config), MIN_FRAME_MS)
    val maxBlink = (frame.durationMs - floorMs).coerceAtLeast(0L)
    val punctuationFactor = blinkPunctuationFactor(frame.tokens)
    val weight =
        when (blinkMode) {
            BlinkMode.SUBTLE -> punctuationFactor
            BlinkMode.ADAPTIVE -> {
                val ease = (wordEase(firstWord) + wordEase(nextWord)) * BLINK_EASE_AVERAGE_FACTOR
                if (ease >= ADAPTIVE_EASE_THRESHOLD) punctuationFactor else 0.0
            }
            BlinkMode.OFF -> 0.0
        }
    val blinkMs = min((targetBlinkMs * weight).roundToLong(), maxBlink)
    return if (blinkMs < MIN_BLINK_MS) {
        listOf(frame)
    } else {
        listOf(
            frame.copy(durationMs = (frame.durationMs - blinkMs).coerceAtLeast(MIN_FRAME_MS)),
            RsvpFrame(
                tokens = listOf(blinkToken),
                durationMs = blinkMs,
                originalTokenIndex = frame.originalTokenIndex,
                resumeCursor = frame.resumeCursor,
                nextOriginalTokenIndex = frame.nextOriginalTokenIndex,
                displayOriginalStartIndex = frame.displayOriginalStartIndex,
                displayOriginalEndExclusive = frame.displayOriginalEndExclusive,
                displayOriginalStartCharacterOffset = frame.displayOriginalStartCharacterOffset,
                displayOriginalEndCharacterOffset = frame.displayOriginalEndCharacterOffset,
            ),
        )
    }
}

private fun List<Token>.singleWordOrNull(): Token? =
    filter { it.type == TokenType.WORD }.singleOrNull()

internal fun blinkPunctuationFactor(tokens: List<Token>): Double {
    val hasMidPause =
        tokens.any { token ->
            val ch = token.text.firstOrNull() ?: return@any false
            token.type == TokenType.PUNCTUATION && isMidSentencePunctuation(ch)
        }
    return if (hasMidPause) MID_SENTENCE_BLINK_FACTOR else 1.0
}

private const val BLINK_EASE_AVERAGE_FACTOR = 0.5
private const val MID_SENTENCE_BLINK_FACTOR = 0.55
