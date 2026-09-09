package com.kairo.reader.core.rsvp.engine

import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.effectiveBlinkMode
import com.kairo.reader.core.model.isMidSentencePunctuation
import com.kairo.reader.core.rsvp.analysis.shouldPreferHold
import com.kairo.reader.core.rsvp.text.isHardBoundary
import com.kairo.reader.core.rsvp.timing.RsvpSessionTimingPolicy
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

    val blinkToken = Token(text = " ", type = TokenType.PUNCTUATION)
    val output = ArrayList<RsvpFrame>(frames.size * 2)

    for (i in frames.indices) {
        val frame = frames[i]
        val next = frames.getOrNull(i + 1)
        output += splitFrameForBlink(frame, next, config, blinkToken)
    }

    frames.clear()
    frames.addAll(output)
}

private fun splitFrameForBlink(
    frame: RsvpFrame,
    next: RsvpFrame?,
    config: RsvpConfig,
    blinkToken: Token,
): List<RsvpFrame> {
    val nextTokens = next?.tokens.orEmpty()
    val firstWord = frame.tokens.singleWordOrNull()
    val nextWord = nextTokens.singleWordOrNull()
    if (firstWord == null || nextWord == null) return listOf(frame)
    val shouldHold =
        frame.tokens.none { it.type == TokenType.PUNCTUATION } &&
            shouldPreferHold(firstWord, nextWord)
    val repeatedWord = firstWord.text.equals(nextWord.text, ignoreCase = true)
    if ((!repeatedWord && shouldHold) || isHardBoundary(frame.tokens, nextWord)) return listOf(frame)

    val floorMs = max(wordFloorMs(firstWord, config), MIN_FRAME_MS)
    val maxBlink = (frame.durationMs - floorMs).coerceAtLeast(0L)
    val punctuationFactor = blinkPunctuationFactor(frame.tokens)
    val blinkMs = min((WORD_SEPARATION_MS * punctuationFactor).roundToLong(), maxBlink)
    return if (blinkMs < MIN_BLINK_MS) {
        listOf(frame)
    } else {
        listOf(
            frame.copy(durationMs = (frame.durationMs - blinkMs).coerceAtLeast(MIN_FRAME_MS)),
            RsvpFrame(
                tokens = listOf(blinkToken),
                isWordSeparation = true,
                isRepeatedWordSeparation = repeatedWord,
                durationMs = blinkMs,
                originalTokenIndex = frame.originalTokenIndex,
                resumeCursor = frame.resumeCursor,
                nextOriginalTokenIndex = frame.nextOriginalTokenIndex,
                displayOriginalStartIndex = frame.displayOriginalStartIndex,
                displayOriginalEndExclusive = frame.displayOriginalEndExclusive,
                displayOriginalStartCharacterOffset = frame.displayOriginalStartCharacterOffset,
                displayOriginalEndCharacterOffset = frame.displayOriginalEndCharacterOffset,
                phraseStartTokenIndex = frame.phraseStartTokenIndex,
                phraseEndTokenIndexExclusive = frame.phraseEndTokenIndexExclusive,
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

private const val WORD_SEPARATION_MS = 20L
private const val MID_SENTENCE_BLINK_FACTOR = 0.55
