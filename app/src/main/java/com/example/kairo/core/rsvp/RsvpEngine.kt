package com.example.kairo.core.rsvp

import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFrame
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.splitHyphenatedToken
import kotlin.math.max
import kotlin.math.min

/**
 * Represents an expanded token with a reference to its original token index.
 * Used internally during frame generation to maintain position tracking.
 */
private data class ExpandedToken(
    val token: Token,
    val originalIndex: Int
)

interface RsvpEngine {
    fun generateFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig
    ): List<RsvpFrame>
}

/**
 * RSVP Engine focused on smooth, grammar-aware pacing.
 *
 * Goals:
 * - Stable token-to-position mapping (no tokenizer rewrites here).
 * - Contextual punctuation handling (abbreviations, decimals, initialisms).
 * - Gentle adaptive timing for complex/rare/long words.
 * - Natural chunking around strong grammatical boundaries.
 */
class DefaultRsvpEngine : RsvpEngine {

    override fun generateFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig
    ): List<RsvpFrame> {
        if (tokens.isEmpty()) return emptyList()

        val expandedTokens = tokens.flatMapIndexed { index, token ->
            splitHyphenatedToken(token).map { splitToken ->
                ExpandedToken(splitToken, index)
            }
        }

        var cursor = expandedTokens.indexOfFirst { it.originalIndex >= startIndex }
        if (cursor == -1) cursor = 0
        cursor = cursor.coerceIn(0, expandedTokens.lastIndex)

        // Skip leading punctuation and realign to a word.
        while (cursor < expandedTokens.size && expandedTokens[cursor].token.type != TokenType.WORD) {
            cursor++
        }
        if (cursor >= expandedTokens.size) return emptyList()

        val frames = mutableListOf<RsvpFrame>()
        val baseMsPerWord = 60_000.0 / max(1, config.baseWpm)

        // Recent non-boundary durations to smooth jitter.
        val recentNonBoundaryDurations = ArrayDeque<Long>(SMOOTHING_WINDOW)

        while (cursor < expandedTokens.size) {
            val expanded = expandedTokens[cursor]
            val token = expanded.token

            val isOpeningPunctuation = token.type == TokenType.PUNCTUATION &&
                token.text.length == 1 &&
                token.text[0] in OPENING_PUNCTUATION

            // Attach closing punctuation to previous frame with contextual pauses.
            if (frames.isNotEmpty() && token.type == TokenType.PUNCTUATION && !isOpeningPunctuation) {
                val last = frames.removeAt(frames.lastIndex)
                val prevWord = last.tokens.lastOrNull { it.type == TokenType.WORD }
                val nextToken = expandedTokens.getOrNull(cursor + 1)?.token
                val extraPause = punctuationPauseMs(
                    punctuation = token.text,
                    prevWord = prevWord,
                    nextToken = nextToken,
                    baseMsPerWord = baseMsPerWord,
                    config = config
                )
                frames += last.copy(
                    tokens = last.tokens + token,
                    durationMs = last.durationMs + extraPause.toLong()
                )
                cursor++
                continue
            }

            // Attach paragraph breaks as pauses to the previous frame.
            if (frames.isNotEmpty() && token.type == TokenType.PARAGRAPH_BREAK) {
                val last = frames.removeAt(frames.lastIndex)
                val extraPause = paragraphPauseMs(baseMsPerWord, config)
                frames += last.copy(
                    tokens = last.tokens + token,
                    durationMs = last.durationMs + extraPause.toLong()
                )
                cursor++
                continue
            }

            val frameTokens = mutableListOf<Token>()
            var frameOriginalIndex = expanded.originalIndex
            var firstWordOriginalIndex: Int? = null
            var words = 0
            var length = 0
            val frameStartCursor = cursor

            while (cursor < expandedTokens.size) {
                val current = expandedTokens[cursor]
                val currentToken = current.token

                frameTokens += currentToken
                length += currentToken.text.length
                if (currentToken.type == TokenType.WORD) {
                    words++
                    if (firstWordOriginalIndex == null) {
                        firstWordOriginalIndex = current.originalIndex
                    }
                }

                cursor++

                val reachedWordLimit = words >= max(1, config.wordsPerFrame)
                val reachedLengthLimit = length >= config.maxChunkLength

                val atClauseBoundary = config.useClausePausing &&
                    currentToken.type == TokenType.WORD &&
                    currentToken.isClauseBoundary &&
                    words > 0

                val prevWordInFrame = frameTokens.lastOrNull { it.type == TokenType.WORD }
                val nextTokenGlobal = expandedTokens.getOrNull(cursor)?.token
                val atStrongPunctuation = currentToken.type == TokenType.PUNCTUATION &&
                    isStrongBoundaryPunctuation(
                        punctuation = currentToken.text,
                        prevWord = prevWordInFrame,
                        nextToken = nextTokenGlobal
                    ) &&
                    words > 0

                val atParagraphBreak = currentToken.type == TokenType.PARAGRAPH_BREAK && words > 0

                if (reachedWordLimit || reachedLengthLimit || atClauseBoundary || atStrongPunctuation || atParagraphBreak) {
                    break
                }
            }

            frameOriginalIndex = firstWordOriginalIndex ?: frameOriginalIndex

            val prevTokenGlobal = expandedTokens.getOrNull(frameStartCursor - 1)?.token
            val nextTokenGlobal = expandedTokens.getOrNull(cursor)?.token

            var durationMs = calculateDurationMs(
                frameTokens = frameTokens,
                baseMsPerWord = baseMsPerWord,
                config = config,
                prevToken = prevTokenGlobal,
                nextToken = nextTokenGlobal
            )

            val hasStrongBoundary =
                frameTokens.any { it.type == TokenType.PARAGRAPH_BREAK } ||
                    frameTokens.withIndex().any { (index, t) ->
                        if (t.type != TokenType.PUNCTUATION) return@any false
                        val prevWord = frameTokens.subList(0, index)
                            .lastOrNull { it.type == TokenType.WORD }
                        val nextWordInFrame = frameTokens.subList(index + 1, frameTokens.size)
                            .firstOrNull { it.type == TokenType.WORD }
                        val next = nextWordInFrame ?: nextTokenGlobal
                        isStrongBoundaryPunctuation(
                            punctuation = t.text,
                            prevWord = prevWord,
                            nextToken = next
                        )
                    }

            val hasAnyPunctuation = frameTokens.any {
                it.type == TokenType.PUNCTUATION || it.type == TokenType.PARAGRAPH_BREAK
            }

            if (!hasAnyPunctuation && !hasStrongBoundary && recentNonBoundaryDurations.isNotEmpty()) {
                val avg = recentNonBoundaryDurations.sum().toDouble() / recentNonBoundaryDurations.size
                val minAllowed = (avg * 0.8).toLong()
                val maxAllowed = (avg * 1.4).toLong()
                durationMs = durationMs.coerceIn(minAllowed, maxAllowed)
            }

            if (!hasAnyPunctuation && !hasStrongBoundary) {
                if (recentNonBoundaryDurations.size == SMOOTHING_WINDOW) {
                    recentNonBoundaryDurations.removeFirst()
                }
                recentNonBoundaryDurations.addLast(durationMs)
            }

            frames += RsvpFrame(frameTokens, durationMs, frameOriginalIndex)
        }

        applyRamping(frames, config)
        return frames
    }

    private fun calculateDurationMs(
        frameTokens: List<Token>,
        baseMsPerWord: Double,
        config: RsvpConfig,
        prevToken: Token?,
        nextToken: Token?
    ): Long {
        val wordTokens = frameTokens.filter { it.type == TokenType.WORD }
        if (wordTokens.isEmpty()) {
            return baseMsPerWord.toLong().coerceAtLeast(40L)
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

            multiplier = multiplier.coerceIn(0.85, 2.0)
            duration += baseMsPerWord * multiplier

            // Micro pause between hyphenated parts ("self-" "aware").
            if (text.endsWith("-")) {
                duration += baseMsPerWord * 0.25
            }
        }

        // Dialogue reads slightly faster.
        if (config.useDialogueDetection && frameTokens.any { it.isDialogue }) {
            duration *= 0.95
        }

        // Small breath at clause boundaries.
        if (config.useClausePausing && wordTokens.any { it.isClauseBoundary }) {
            duration += baseMsPerWord * (config.clausePauseFactor - 1.0) * 0.5
        }

        // Add punctuation/paragraph pauses with context.
        val speedScale = baseMsPerWord / BASE_MS_PER_WORD_AT_300
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
                    var scaledTokenPause = token.pauseAfterMs * speedScale * tokenPauseScale

                    if (ch == '.' && (isDecimalPoint(prevWord?.text.orEmpty(), next) || isAbbreviationDot(prevWord?.text.orEmpty(), next))) {
                        // Avoid huge pauses on abbreviation/decimal dots.
                        scaledTokenPause *= 0.15
                    }
                    if (ch == ',' && isNumericWord(prevWord?.text.orEmpty()) && isNumericWord(next?.text.orEmpty())) {
                        // Thousand separators shouldn't pause.
                        scaledTokenPause = 0.0
                    }

                    duration += max(scaledTokenPause, computedPause)
                }
                TokenType.PARAGRAPH_BREAK -> duration += paragraphPauseMs(baseMsPerWord, config)
                else -> Unit
            }
        }

        return duration.toLong().coerceAtLeast(40L)
    }

    private fun punctuationPauseMs(
        punctuation: String,
        prevWord: Token?,
        nextToken: Token?,
        baseMsPerWord: Double,
        config: RsvpConfig
    ): Double {
        val ch = punctuation.firstOrNull() ?: return 0.0

        val sentencePause = baseMsPerWord * config.punctuationPauseFactor
        // Mid punctuation should be noticeable but not jarring.
        val midPause = baseMsPerWord * (config.punctuationPauseFactor - 1.0) * 0.9
        val tinyPause = midPause * 0.5

        val prevText = prevWord?.text.orEmpty()
        val nextText = nextToken?.text.orEmpty()

        return when (ch) {
            '.' -> {
                when {
                    isDecimalPoint(prevText, nextToken) -> 0.0
                    nextToken?.type == TokenType.PUNCTUATION && nextText == "." ->
                        // Ellipsis series
                        sentencePause * 0.6
                    isAbbreviationDot(prevText, nextToken) ->
                        // Abbreviations / initialisms / lettered forms (e.g., Dr., U.S., e.g.)
                        midPause * 0.25
                    else -> sentencePause
                }
            }
            '!', '?' -> sentencePause
            '\u2026' -> sentencePause * 1.1
            ',' -> {
                if (isNumericWord(prevText) && isNumericWord(nextText)) 0.0 else midPause
            }
            ';' -> midPause * 1.8
            ':' -> {
                if (isNumericWord(prevText) && isNumericWord(nextText)) 0.0 else midPause * 1.6
            }
            '\u2014', '\u2013', '-' -> midPause * 1.5
            ')', ']', '}', '(', '[', '{', '"', '\u201C', '\u201D', '\u2018', '\u2019' -> tinyPause
            else -> 0.0
        }
    }

    private fun paragraphPauseMs(baseMsPerWord: Double, config: RsvpConfig): Double {
        // Scale paragraph pause with speed, but never below the configured minimum.
        return max(config.paragraphPauseMs.toDouble(), baseMsPerWord * 1.2)
    }

    private fun isStrongBoundaryPunctuation(
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

    private fun isDecimalPoint(prevText: String, nextToken: Token?): Boolean {
        if (!prevText.any { it.isDigit() }) return false
        return nextToken?.type == TokenType.WORD && nextToken.text.any { it.isDigit() }
    }

    private fun isNumericWord(text: String): Boolean {
        val digits = NON_DIGIT_REGEX.replace(text, "")
        if (digits.isEmpty()) return false
        // Treat as numeric if it is mostly digits.
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

    /**
     * Applies gradual speed ramping at start and end of reading session.
     * This helps readers ease into the pace and wind down naturally.
     */
    private fun applyRamping(frames: MutableList<RsvpFrame>, config: RsvpConfig) {
        if (frames.isEmpty()) return

        val totalFrames = frames.size

        // Ramp-up: Start slower and gradually reach target speed.
        val rampUpCount = min(config.rampUpFrames, totalFrames / 2)
        for (i in 0 until rampUpCount) {
            val progress = i.toDouble() / rampUpCount
            val multiplier = 1.5 - (0.5 * progress)
            val frame = frames[i]
            frames[i] = frame.copy(durationMs = (frame.durationMs * multiplier).toLong())
        }

        // Apply start delay to first frame.
        val first = frames.first()
        frames[0] = first.copy(durationMs = first.durationMs + config.startDelayMs)

        // Ramp-down: Gradually slow down at the end.
        val rampDownCount = min(config.rampDownFrames, totalFrames / 2)
        val rampDownStart = totalFrames - rampDownCount
        for (i in rampDownStart until totalFrames) {
            val progress = (i - rampDownStart).toDouble() / rampDownCount
            val multiplier = 1.0 + (0.3 * progress)
            val frame = frames[i]
            frames[i] = frame.copy(durationMs = (frame.durationMs * multiplier).toLong())
        }

        // Apply end delay to last frame.
        val last = frames.last()
        frames[frames.lastIndex] = last.copy(durationMs = last.durationMs + config.endDelayMs)
    }

    private companion object {
        private const val BASE_MS_PER_WORD_AT_300 = 200.0
        private const val SMOOTHING_WINDOW = 8
        private val NON_DIGIT_REGEX = Regex("[^0-9]")
        private val OPENING_PUNCTUATION = setOf('(', '[', '{', '"', '\u201C', '\u2018')

        private val KNOWN_ABBREVIATIONS = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc",
            "e.g", "i.e", "eg", "ie", "no", "vol", "fig", "al",
            "inc", "ltd", "dept", "est", "approx", "misc",
            "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept",
            "oct", "nov", "dec", "u.s", "u.k", "u.n"
        )
    }
}
