package com.example.kairo.core.rsvp

import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFrame
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal object RsvpPacing {

    fun playbackDelayMs(
        frames: List<RsvpFrame>,
        frameIndex: Int,
        config: RsvpConfig
    ): Long {
        val frame = frames.getOrNull(frameIndex) ?: return 30L
        val prevToken = frames.getOrNull(frameIndex - 1)?.tokens?.lastOrNull()
        val nextToken = frames.getOrNull(frameIndex + 1)?.tokens?.firstOrNull()

        val baseMsPerWord = baseMsPerWord(config)
        var duration = calculateFrameDurationMs(
            frameTokens = frame.tokens,
            baseMsPerWord = baseMsPerWord,
            config = config,
            prevToken = prevToken,
            nextToken = nextToken
        )

        duration = (duration * rampMultiplier(frameIndex, frames.size, config)).toLong()

        if (frameIndex == 0) duration += config.startDelayMs
        if (frameIndex == frames.lastIndex) duration += config.endDelayMs

        return duration.coerceAtLeast(30L)
    }

    fun applyRamping(frames: MutableList<RsvpFrame>, config: RsvpConfig) {
        if (frames.isEmpty()) return

        for (i in frames.indices) {
            val multiplier = rampMultiplier(i, frames.size, config)
            if (multiplier != 1.0) {
                val frame = frames[i]
                frames[i] = frame.copy(durationMs = (frame.durationMs * multiplier).toLong())
            }
        }

        val first = frames.first()
        frames[0] = first.copy(durationMs = first.durationMs + config.startDelayMs)

        val last = frames.last()
        frames[frames.lastIndex] = last.copy(durationMs = last.durationMs + config.endDelayMs)
    }

    fun calculateFrameDurationMs(
        frameTokens: List<Token>,
        config: RsvpConfig,
        prevToken: Token?,
        nextToken: Token?
    ): Long = calculateFrameDurationMs(
        frameTokens = frameTokens,
        baseMsPerWord = baseMsPerWord(config),
        config = config,
        prevToken = prevToken,
        nextToken = nextToken
    )

    fun calculateFrameDurationMs(
        frameTokens: List<Token>,
        baseMsPerWord: Double,
        config: RsvpConfig,
        prevToken: Token?,
        nextToken: Token?
    ): Long {
        val wordTokens = frameTokens.filter { it.type == TokenType.WORD }
        if (wordTokens.isEmpty()) {
            return baseMsPerWord.toLong().coerceAtLeast(MIN_FRAME_MS)
        }

        var duration = 0.0

        for (word in wordTokens) {
            val text = word.text

            var multiplier = if (config.useAdaptiveTiming) {
                val raw = word.complexityMultiplier
                if (raw > config.complexWordThreshold) raw else 1.0 + (raw - 1.0) * 0.5
            } else {
                1.0
            }

            if (!config.useAdaptiveTiming && text.length >= 8) {
                multiplier *= config.longWordMultiplier
            }

            if (isNumericWord(text)) multiplier *= 1.15
            if (isAllCapsAcronym(text)) {
                multiplier *= if (text.length <= 4) 1.05 else 1.12
            }

            multiplier = multiplier.coerceIn(0.85, 2.35)

            val computed = baseMsPerWord * multiplier
            val floor = wordFloorMs(word, baseMsPerWord)
            duration += max(computed, floor)

            if (text.endsWith("-")) {
                duration += baseMsPerWord * 0.25
            }
        }

        if (config.useDialogueDetection && frameTokens.any { it.isDialogue }) {
            duration *= 0.95
        }

        if (config.useClausePausing && wordTokens.any { it.isClauseBoundary }) {
            duration += baseMsPerWord * (config.clausePauseFactor - 1.0) * 0.5
        }

        val pauseScale = pauseScale(baseMsPerWord)

        frameTokens.forEachIndexed { index, token ->
            when (token.type) {
                TokenType.PUNCTUATION -> {
                    val prevWord = frameTokens.subList(0, index)
                        .lastOrNull { it.type == TokenType.WORD }
                        ?: prevToken
                    val nextWordInFrame = frameTokens.subList(index + 1, frameTokens.size)
                        .firstOrNull { it.type == TokenType.WORD }
                    val next = nextWordInFrame ?: nextToken

                    val computedPause = punctuationPauseMs(
                        punctuation = token.text,
                        prevWord = prevWord,
                        nextToken = next,
                        baseMsPerWord = baseMsPerWord,
                        config = config
                    )

                    val ch = token.text.firstOrNull()
                    val tokenPauseScale = when (ch) {
                        ')', ']', '}', '(', '[', '{',
                        '"', '\u201C', '\u201D', '\u2018', '\u2019' -> 0.5
                        else -> 1.0
                    }

                    var scaledTokenPause = token.pauseAfterMs * pauseScale * tokenPauseScale

                    if (ch == '.' && (isDecimalPoint(prevWord?.text.orEmpty(), next) || isAbbreviationDot(prevWord?.text.orEmpty(), next))) {
                        scaledTokenPause *= 0.15
                    }
                    if (ch == ',' && isNumericWord(prevWord?.text.orEmpty()) && isNumericWord(next?.text.orEmpty())) {
                        scaledTokenPause = 0.0
                    }

                    duration += max(scaledTokenPause, computedPause)
                }
                TokenType.PARAGRAPH_BREAK -> duration += paragraphPauseMs(baseMsPerWord, config)
                else -> Unit
            }
        }

        return duration.toLong().coerceAtLeast(MIN_FRAME_MS)
    }

    fun punctuationPauseMs(
        punctuation: String,
        prevWord: Token?,
        nextToken: Token?,
        baseMsPerWord: Double,
        config: RsvpConfig
    ): Double {
        val ch = punctuation.firstOrNull() ?: return 0.0

        val sentencePause = baseMsPerWord * config.punctuationPauseFactor
        val midPause = baseMsPerWord * (config.punctuationPauseFactor - 1.0) * 0.9
        val tinyPause = midPause * 0.5

        val prevText = prevWord?.text.orEmpty()
        val nextText = nextToken?.text.orEmpty()

        val minPause = minPunctuationPauseMs(
            ch = ch,
            prevText = prevText,
            nextToken = nextToken
        )

        val computed = when (ch) {
            '.' -> {
                when {
                    isDecimalPoint(prevText, nextToken) -> 0.0
                    nextToken?.type == TokenType.PUNCTUATION && nextText == "." -> sentencePause * 0.6
                    isAbbreviationDot(prevText, nextToken) -> midPause * 0.25
                    else -> sentencePause
                }
            }
            '!', '?' -> sentencePause
            '\u2026' -> sentencePause * 1.1
            ',' -> if (isNumericWord(prevText) && isNumericWord(nextText)) 0.0 else midPause
            ';' -> midPause * 1.8
            ':' -> if (isNumericWord(prevText) && isNumericWord(nextText)) 0.0 else midPause * 1.6
            '\u2014', '\u2013', '-' -> midPause * 1.5
            ')', ']', '}', '(', '[', '{', '"', '\u201C', '\u201D', '\u2018', '\u2019' -> tinyPause
            else -> 0.0
        }

        return max(computed, minPause)
    }

    fun paragraphPauseMs(baseMsPerWord: Double, config: RsvpConfig): Double {
        return max(config.paragraphPauseMs.toDouble(), baseMsPerWord * 1.2)
    }

    fun isStrongBoundaryPunctuation(
        punctuation: String,
        prevWord: Token?,
        nextToken: Token?
    ): Boolean {
        val ch = punctuation.firstOrNull() ?: return false
        val prevText = prevWord?.text.orEmpty()
        val nextText = nextToken?.text.orEmpty()

        return when (ch) {
            '.', '!', '?', '\u2026' ->
                !(ch == '.' && isAbbreviationDot(prevText, nextToken)) &&
                    !isDecimalPoint(prevText, nextToken)
            ';', '\u2014', '\u2013' -> true
            ':' -> !(isNumericWord(prevText) && isNumericWord(nextText))
            else -> false
        }
    }

    private fun rampMultiplier(frameIndex: Int, totalFrames: Int, config: RsvpConfig): Double {
        if (totalFrames <= 1) return 1.0

        val rampUpCount = min(config.rampUpFrames, totalFrames / 2)
        if (rampUpCount > 0 && frameIndex in 0 until rampUpCount) {
            val progress = frameIndex.toDouble() / rampUpCount
            return 1.5 - (0.5 * progress)
        }

        val rampDownCount = min(config.rampDownFrames, totalFrames / 2)
        val rampDownStart = totalFrames - rampDownCount
        if (rampDownCount > 0 && frameIndex in rampDownStart until totalFrames) {
            val progress = (frameIndex - rampDownStart).toDouble() / rampDownCount
            return 1.0 + (0.3 * progress)
        }

        return 1.0
    }

    private fun baseMsPerWord(config: RsvpConfig): Double = 60_000.0 / max(1, config.baseWpm)

    private fun pauseScale(baseMsPerWord: Double): Double {
        val ratio = (baseMsPerWord / BASE_MS_PER_WORD_AT_300).coerceIn(0.12, 2.5)
        return ratio.pow(0.6)
    }

    private fun wordFloorMs(token: Token, baseMsPerWord: Double): Double {
        val text = token.text
        val cleanedLength = text.count { it.isLetterOrDigit() }.let { if (it > 0) it else text.length }

        val minForLength = when {
            cleanedLength <= 6 -> 0.0
            cleanedLength <= 8 -> max(0.0, baseMsPerWord * 1.05)
            cleanedLength <= 10 -> 90.0
            cleanedLength <= 12 -> 110.0
            else -> 130.0
        }

        val syllableFloor = when {
            token.syllableCount >= 5 -> 125.0
            token.syllableCount == 4 -> 110.0
            else -> 0.0
        }

        val numericFloor = if (isNumericWord(text)) 95.0 else 0.0

        val acronymRelax = if (isAllCapsAcronym(text)) 0.0 else 1.0

        return max(
            max(minForLength, max(syllableFloor, numericFloor)) * acronymRelax,
            MIN_WORD_MS
        )
    }

    private fun minPunctuationPauseMs(ch: Char, prevText: String, nextToken: Token?): Double {
        return when (ch) {
            '.', '!', '?' -> {
                if (ch == '.' && (isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken))) {
                    0.0
                } else {
                    125.0
                }
            }
            '\u2026' -> 155.0
            ',' -> 70.0
            ';' -> 95.0
            ':' -> 85.0
            '\u2014', '\u2013', '-' -> 90.0
            ')', ']', '}', '(', '[', '{', '"', '\u201C', '\u201D', '\u2018', '\u2019' -> 35.0
            else -> 0.0
        }
    }

    private fun isDecimalPoint(prevText: String, nextToken: Token?): Boolean {
        if (!prevText.any { it.isDigit() }) return false
        return nextToken?.type == TokenType.WORD && nextToken.text.any { it.isDigit() }
    }

    private fun isNumericWord(text: String): Boolean {
        val digits = NON_DIGIT_REGEX.replace(text, "")
        if (digits.isEmpty()) return false
        return digits.length >= min(1, text.length / 2)
    }

    private fun isAllCapsAcronym(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        return letters.length >= 2 && letters.all { it.isUpperCase() }
    }

    private fun isAbbreviationDot(prevWordText: String, nextToken: Token?): Boolean {
        val rawPrev = prevWordText.trim()
        if (rawPrev.isEmpty()) return false

        val normalized = rawPrev.trimEnd('.', ',', ';', ':').lowercase()
        if (normalized in KNOWN_ABBREVIATIONS) return true

        val prevLetters = rawPrev.filter { it.isLetter() }
        if (prevLetters.length == 1) return true
        if (prevLetters.length <= 3 && prevLetters.all { it.isUpperCase() }) return true

        val nextText = nextToken?.text?.trim().orEmpty()
        val nextLetters = nextText.filter { it.isLetter() }

        if (nextToken?.type == TokenType.WORD &&
            nextLetters.length == 1 &&
            nextLetters.all { it.isUpperCase() } &&
            prevLetters.length <= 3 &&
            prevLetters.any { it.isUpperCase() }
        ) {
            return true
        }

        return false
    }

    private const val BASE_MS_PER_WORD_AT_300 = 200.0
    private const val MIN_FRAME_MS = 40L
    private const val MIN_WORD_MS = 45.0
    private val NON_DIGIT_REGEX = Regex("[^0-9]")

    private val KNOWN_ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc",
        "e.g", "i.e", "eg", "ie", "no", "vol", "fig", "al",
        "inc", "ltd", "dept", "est", "approx", "misc",
        "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept",
        "oct", "nov", "dec", "u.s", "u.k", "u.n"
    )
}

