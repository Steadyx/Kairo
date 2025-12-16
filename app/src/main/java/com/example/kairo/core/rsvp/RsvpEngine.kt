package com.example.kairo.core.rsvp

import com.example.kairo.core.linguistics.ClauseDetector
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFrame
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.isMidSentencePunctuation
import com.example.kairo.core.model.isSentenceEndingPunctuation
import com.example.kairo.core.model.splitHyphenatedToken
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

interface RsvpEngine {
    fun generateFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig
    ): List<RsvpFrame>
}

/**
 * Ground-up redesign focused on comprehension at high WPM.
 *
 * Core idea:
 * - Build *reading units* (1–2 word phrases + attached punctuation) that match language flow.
 * - Compute unit durations via a difficulty model (length/syllables/rarity/complexity) + breath pauses.
 * - Apply context shaping (parentheticals/quotes) and rhythm shaping (EMA smoothing + jitter clamps).
 *
 * The result is a calm, legible cadence where long words and punctuation never "flash" away.
 */
class ComprehensionRsvpEngine : RsvpEngine {

    override fun generateFrames(tokens: List<Token>, startIndex: Int, config: RsvpConfig): List<RsvpFrame> {
        if (tokens.isEmpty()) return emptyList()

        val expanded = tokens.flatMapIndexed { index, token ->
            splitHyphenatedToken(token).map { splitToken ->
                ExpandedToken(splitToken, index)
            }
        }

        var cursor = expanded.indexOfFirst { it.originalIndex >= startIndex }.let { if (it == -1) 0 else it }
        cursor = cursor.coerceIn(0, expanded.lastIndex)

        while (cursor < expanded.size && expanded[cursor].token.type != TokenType.WORD) cursor++
        if (cursor >= expanded.size) return emptyList()

        val frames = mutableListOf<RsvpFrame>()

        val state = ContextState()
        val rhythm = RhythmState(
            smoothingAlpha = config.smoothingAlpha,
            maxSpeedupFactor = config.maxSpeedupFactor,
            maxSlowdownFactor = config.maxSlowdownFactor
        )

        while (cursor < expanded.size) {
            val contextBefore = state.snapshot()

            val (frameTokens, frameOriginalIndex, nextCursor) = buildUnit(
                expandedTokens = expanded,
                startCursor = cursor,
                config = config,
                state = state
            )
            val prevTokenGlobal = expanded.getOrNull(cursor - 1)?.token
            val nextTokenGlobal = expanded.getOrNull(nextCursor)?.token
            cursor = nextCursor

            val durationMs = computeUnitDurationMs(
                frameTokens = frameTokens,
                config = config,
                contextBefore = contextBefore,
                rhythm = rhythm,
                prevToken = prevTokenGlobal,
                nextToken = nextTokenGlobal
            )

            frames += RsvpFrame(tokens = frameTokens, durationMs = durationMs, originalTokenIndex = frameOriginalIndex)

            while (cursor < expanded.size && expanded[cursor].token.type != TokenType.WORD &&
                !(expanded[cursor].token.type == TokenType.PUNCTUATION && isOpeningPunctuation(expanded[cursor].token))
            ) {
                state.consume(expanded[cursor].token)
                cursor++
            }
        }

        applySessionRamps(frames, config)
        return frames
    }

    private fun buildUnit(
        expandedTokens: List<ExpandedToken>,
        startCursor: Int,
        config: RsvpConfig,
        state: ContextState
    ): UnitBuildResult {
        val unitTokens = mutableListOf<Token>()
        var cursor = startCursor.coerceIn(0, expandedTokens.lastIndex)
        var firstWordOriginalIndex: Int? = null

        // Allow opening punctuation right before the first word to be part of the unit.
        while (cursor < expandedTokens.size) {
            val token = expandedTokens[cursor].token
            if (token.type == TokenType.PUNCTUATION && isOpeningPunctuation(token)) {
                unitTokens += token
                state.consume(token)
                cursor++
            } else {
                break
            }
        }

        // First word is required.
        while (cursor < expandedTokens.size && expandedTokens[cursor].token.type != TokenType.WORD) {
            cursor++
        }
        val firstWord = expandedTokens.getOrNull(cursor) ?: return UnitBuildResult(unitTokens, startCursor, cursor)
        if (firstWord.token.type != TokenType.WORD) return UnitBuildResult(unitTokens, startCursor, cursor)
        unitTokens += firstWord.token
        state.consume(firstWord.token)
        firstWordOriginalIndex = firstWord.originalIndex
        cursor++

        // Optionally add a second word for phrase chunking (only across "soft" boundaries).
        if (config.enablePhraseChunking) {
            val candidateIndex = cursor
            val candidate = expandedTokens.getOrNull(candidateIndex)
            if (candidate != null && candidate.token.type == TokenType.WORD) {
                val combinedChars = unitTokens.sumOf { it.text.length } + candidate.token.text.length
                val withinLimits =
                    config.maxWordsPerUnit >= 2 && combinedChars <= config.maxCharsPerUnit

                val canBridge =
                    withinLimits &&
                        isPhraseChunkCandidate(prev = firstWord.token, next = candidate.token)

                if (canBridge) {
                    unitTokens += candidate.token
                    state.consume(candidate.token)
                    cursor++
                }
            }
        }

        // Attach closing punctuation + paragraph breaks to this unit until a "hard" stop.
        while (cursor < expandedTokens.size) {
            val token = expandedTokens[cursor].token
            if (token.type == TokenType.PUNCTUATION && !isOpeningPunctuation(token)) {
                unitTokens += token
                state.consume(token)
                cursor++
                if (isHardBoundaryPunctuation(token)) break
                continue
            }
            if (token.type == TokenType.PARAGRAPH_BREAK) {
                unitTokens += token
                state.consume(token)
                cursor++
                break
            }
            break
        }

        return UnitBuildResult(
            tokens = unitTokens,
            originalWordIndex = firstWordOriginalIndex ?: startCursor,
            nextCursor = cursor
        )
    }

    private fun computeUnitDurationMs(
        frameTokens: List<Token>,
        config: RsvpConfig,
        contextBefore: ContextSnapshot,
        rhythm: RhythmState,
        prevToken: Token?,
        nextToken: Token?
    ): Long {
        val msPerWord = config.tempoMsPerWord.toDouble()

        val words = frameTokens.filter { it.type == TokenType.WORD }
        val paragraphBreaks = frameTokens.count { it.type == TokenType.PARAGRAPH_BREAK }

        var duration = 0.0
        var parentheticalDepth = contextBefore.parentheticalDepth
        var inDialogue = contextBefore.inDialogue

        frameTokens.forEach { token ->
            when (token.type) {
                TokenType.PUNCTUATION -> {
                    val ch = token.text.firstOrNull()
                    when (ch) {
                        '(', '[', '{' -> parentheticalDepth++
                        ')', ']', '}' -> parentheticalDepth = max(0, parentheticalDepth - 1)
                        '"', '\u201C', '\u201D', '\u2018', '\u2019' -> Unit
                    }
                    inDialogue = token.isDialogue
                }
                TokenType.WORD -> {
                    val contextWordMultiplier =
                        (if (parentheticalDepth > 0) config.parentheticalMultiplier else 1.0) *
                            (if (inDialogue) config.dialogueMultiplier else 1.0)
                    duration += max(wordDurationMs(token, msPerWord, config), config.minWordMs.toDouble()) *
                        contextWordMultiplier
                }
                else -> Unit
            }
        }

        duration *= multiWordPenalty(words.size)

        val pauseScale = pauseScale(msPerWord, config)
        frameTokens.forEachIndexed { index, token ->
            if (token.type != TokenType.PUNCTUATION) return@forEachIndexed

            val prevWordInFrame = frameTokens.subList(0, index).lastOrNull { it.type == TokenType.WORD }
            val nextWordInFrame = frameTokens.subList(index + 1, frameTokens.size).firstOrNull { it.type == TokenType.WORD }

            duration += punctuationPauseMs(
                token = token,
                prevWord = prevWordInFrame ?: prevToken,
                nextToken = nextWordInFrame ?: nextToken,
                msPerWord = msPerWord,
                config = config,
                pauseScale = pauseScale
            )
        }
        if (paragraphBreaks > 0) {
            duration += config.paragraphPauseMs * pauseScale * paragraphBreaks
        }

        val smoothed = rhythm.apply(duration, isBoundary = isHardBoundary(frameTokens))
        return smoothed
            .toLong()
            .coerceAtLeast(MIN_FRAME_MS)
    }

    private fun wordDurationMs(word: Token, msPerWord: Double, config: RsvpConfig): Double {
        val text = word.text
        val letters = text.count { it.isLetterOrDigit() }.coerceAtLeast(1)

        val lengthCurve = run {
            val x = ((letters - 4).coerceAtLeast(0) / 10.0)
            1.0 + config.lengthStrength * (x.pow(config.lengthExponent))
        }

        val complexityComponent =
            1.0 + (max(0.0, word.complexityMultiplier - 1.0) * config.complexityStrength)

        val rarityExtra = (1.0 - word.frequencyScore).coerceIn(0.0, 1.0) * config.rarityExtraMaxMs
        val syllableExtra = max(0, word.syllableCount - 1) * config.syllableExtraMs

        var duration = (msPerWord * lengthCurve * complexityComponent) + rarityExtra + syllableExtra

        if (letters >= config.longWordChars) {
            duration = max(duration, config.longWordMinMs.toDouble())
        }

        if (text.endsWith("-")) {
            duration += msPerWord * 0.25
        }

        return duration
    }

    private fun punctuationPauseMs(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
        msPerWord: Double,
        config: RsvpConfig,
        pauseScale: Double
    ): Double {
        val ch = token.text.firstOrNull() ?: return 0.0
        val prevText = prevWord?.text.orEmpty()
        val nextText = nextToken?.text.orEmpty()

        val base = when {
            ch == '.' -> {
                when {
                    isDecimalPoint(prevText, nextToken) -> 0.0
                    isAbbreviationDot(prevText, nextToken) -> config.commaPauseMs * 0.35
                    else -> config.sentenceEndPauseMs.toDouble()
                }
            }
            isSentenceEndingPunctuation(ch) -> config.sentenceEndPauseMs.toDouble()
            ch == ',' -> config.commaPauseMs.toDouble()
            ch == ';' -> config.semicolonPauseMs.toDouble()
            ch == ':' -> config.colonPauseMs.toDouble()
            ch == '\u2014' || ch == '\u2013' || ch == '-' -> config.dashPauseMs.toDouble()
            ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}' -> config.parenthesesPauseMs.toDouble()
            ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019' -> config.quotePauseMs.toDouble()
            isMidSentencePunctuation(ch) -> (config.commaPauseMs * 0.85).toDouble()
            else -> 0.0
        }

        val floor = when {
            ch == '.' -> {
                if (isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken)) 0.0 else 125.0
            }
            isSentenceEndingPunctuation(ch) -> 125.0
            ch == ',' -> 70.0
            ch == ';' -> 95.0
            ch == ':' -> 85.0
            ch == '\u2014' || ch == '\u2013' || ch == '-' -> 90.0
            ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}' -> 45.0
            ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019' -> 35.0
            else -> 0.0
        }

        val scaled = base * pauseScale
        return max(scaled, floor)
    }

    private fun pauseScale(msPerWord: Double, config: RsvpConfig): Double {
        val ratio = (msPerWord / BASE_MS_PER_WORD_AT_300).coerceIn(0.12, 2.5)
        val scaled = ratio.pow(config.pauseScaleExponent)
        return max(config.minPauseScale, scaled)
    }

    private fun multiWordPenalty(wordCount: Int): Double = when (wordCount) {
        0, 1 -> 1.0
        2 -> 1.12
        else -> 1.2
    }

    private fun isPhraseChunkCandidate(prev: Token, next: Token): Boolean {
        val prevLower = prev.text.lowercase()
        val nextLower = next.text.lowercase()

        if (prev.isClauseBoundary || next.isClauseBoundary) return false
        if (ClauseDetector.isCoordinatingConjunction(prevLower)) return false

        val glue = prevLower in GLUE_WORDS || nextLower in GLUE_WORDS
        val bothShort = prev.text.length <= 4 && next.text.length <= 7
        val bothCommon = prev.frequencyScore >= 0.7 && next.frequencyScore >= 0.7

        return (glue && bothShort) || (bothShort && bothCommon)
    }

    private fun isOpeningPunctuation(token: Token): Boolean {
        val ch = token.text.firstOrNull() ?: return false
        return ch in OPENING_PUNCTUATION
    }

    private fun isHardBoundaryPunctuation(token: Token): Boolean {
        val ch = token.text.firstOrNull() ?: return false
        return isSentenceEndingPunctuation(ch) || ch == ';' || ch == '\u2026'
    }

    private fun isHardBoundary(tokens: List<Token>): Boolean {
        if (tokens.any { it.type == TokenType.PARAGRAPH_BREAK }) return true
        return tokens.any { it.type == TokenType.PUNCTUATION && isHardBoundaryPunctuation(it) }
    }

    private fun applySessionRamps(frames: MutableList<RsvpFrame>, config: RsvpConfig) {
        if (frames.isEmpty()) return

        val total = frames.size
        val rampUp = min(config.rampUpFrames, total / 2)
        for (i in 0 until rampUp) {
            val progress = i.toDouble() / rampUp.coerceAtLeast(1)
            val multiplier = 1.35 - (0.35 * progress)
            frames[i] = frames[i].copy(durationMs = (frames[i].durationMs * multiplier).toLong())
        }

        frames[0] = frames[0].copy(durationMs = frames[0].durationMs + config.startDelayMs)

        val rampDown = min(config.rampDownFrames, total / 2)
        val start = total - rampDown
        for (i in start until total) {
            val progress = (i - start).toDouble() / rampDown.coerceAtLeast(1)
            val multiplier = 1.0 + (0.25 * progress)
            frames[i] = frames[i].copy(durationMs = (frames[i].durationMs * multiplier).toLong())
        }

        frames[frames.lastIndex] = frames.last().copy(durationMs = frames.last().durationMs + config.endDelayMs)
    }

    private data class ExpandedToken(val token: Token, val originalIndex: Int)

    private data class UnitBuildResult(
        val tokens: List<Token>,
        val originalWordIndex: Int,
        val nextCursor: Int
    )

    private class ContextState {
        var parentheticalDepth: Int = 0
            private set
        var quoteDepth: Int = 0
            private set
        var inDialogue: Boolean = false
            private set

        val inParenthetical: Boolean get() = parentheticalDepth > 0

        fun snapshot(): ContextSnapshot = ContextSnapshot(
            parentheticalDepth = parentheticalDepth,
            inDialogue = inDialogue
        )

        fun consume(token: Token) {
            if (token.type == TokenType.WORD) {
                if (token.isDialogue) inDialogue = true
                return
            }
            if (token.type != TokenType.PUNCTUATION) return

            val ch = token.text.firstOrNull() ?: return
            when (ch) {
                '(', '[', '{' -> parentheticalDepth++
                ')', ']', '}' -> parentheticalDepth = max(0, parentheticalDepth - 1)
                '"', '\u201C', '\u201D', '\u2018', '\u2019' -> quoteDepth = (quoteDepth + 1).coerceAtMost(8)
            }
            inDialogue = token.isDialogue
        }
    }

    private data class ContextSnapshot(
        val parentheticalDepth: Int,
        val inDialogue: Boolean
    )

    private class RhythmState {
        private var ema: Double? = null
        private val smoothingAlpha: Double
        private val maxSpeedupFactor: Double
        private val maxSlowdownFactor: Double

        constructor(
            smoothingAlpha: Double,
            maxSpeedupFactor: Double,
            maxSlowdownFactor: Double
        ) {
            this.smoothingAlpha = smoothingAlpha.coerceIn(0.0, 1.0)
            this.maxSpeedupFactor = maxSpeedupFactor.coerceAtLeast(1.0)
            this.maxSlowdownFactor = maxSlowdownFactor.coerceAtLeast(1.0)
        }

        fun apply(rawMs: Double, isBoundary: Boolean): Double {
            if (isBoundary) {
                ema = rawMs
                return rawMs
            }

            val prev = ema
            val next = if (prev == null) {
                rawMs
            } else {
                val mixed = prev + (smoothingAlpha * (rawMs - prev))
                val minAllowed = prev / maxSpeedupFactor
                val maxAllowed = prev * maxSlowdownFactor
                mixed.coerceIn(minAllowed, maxAllowed)
            }

            ema = next
            return next
        }
    }

    private fun isDecimalPoint(prevText: String, nextToken: Token?): Boolean {
        if (!prevText.any { it.isDigit() }) return false
        return nextToken?.type == TokenType.WORD && nextToken.text.any { it.isDigit() }
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

    private companion object {
        private const val MIN_FRAME_MS = 40L
        private const val BASE_MS_PER_WORD_AT_300 = 200.0

        private val OPENING_PUNCTUATION = setOf('(', '[', '{', '"', '\u201C', '\u2018')

        private val KNOWN_ABBREVIATIONS = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc",
            "e.g", "i.e", "eg", "ie", "no", "vol", "fig", "al",
            "inc", "ltd", "dept", "est", "approx", "misc",
            "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept",
            "oct", "nov", "dec", "u.s", "u.k", "u.n"
        )

        private val GLUE_WORDS = setOf(
            "a", "an", "the",
            "of", "to", "in", "on", "at", "by", "for", "with", "from",
            "and", "or", "but", "nor", "yet", "so",
            "as", "if", "than", "then", "that", "which", "who", "whom", "whose",
            "is", "are", "was", "were", "be", "been", "being",
            "not", "no"
        )
    }
}
