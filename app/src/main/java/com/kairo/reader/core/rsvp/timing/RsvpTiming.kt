package com.kairo.reader.core.rsvp.timing

import com.kairo.reader.core.linguistics.ClauseDetector
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpConfigConstraints as Constraints
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.isSentenceEndingPunctuation
import com.kairo.reader.core.model.wordFloorMsForReadability
import com.kairo.reader.core.rsvp.engine.ADAPTIVE_DIFFICULTY_FLOOR
import com.kairo.reader.core.rsvp.engine.ADAPTIVE_HOLD_MAX_MS
import com.kairo.reader.core.rsvp.engine.BASE_MS_PER_WORD_AT_300
import com.kairo.reader.core.rsvp.engine.BoundaryBefore
import com.kairo.reader.core.rsvp.engine.CLAUSE_BOUNDARY_HOLD_MS
import com.kairo.reader.core.rsvp.engine.CLAUSE_LEAD_BOOST_MS
import com.kairo.reader.core.rsvp.engine.CLAUSE_START_HOLD_FRACTION
import com.kairo.reader.core.rsvp.engine.CLAUSE_START_MIN_HOLD_MS
import com.kairo.reader.core.rsvp.engine.DYNAMISM_EASE_PIVOT
import com.kairo.reader.core.rsvp.engine.DYNAMISM_MAX_SPEEDUP
import com.kairo.reader.core.rsvp.engine.EMBEDDED_QUOTE_FACTOR
import com.kairo.reader.core.rsvp.engine.LANDING_HOLD_SPEED_BOOST
import com.kairo.reader.core.rsvp.engine.MAX_LANDING_HOLD_MS
import com.kairo.reader.core.rsvp.engine.MAX_MIN_PAUSE_SCALE
import com.kairo.reader.core.rsvp.engine.MIN_LANDING_HOLD_MS
import com.kairo.reader.core.rsvp.engine.PAGE_BREAK_SENTENCE_MULTIPLIER_RATIO
import com.kairo.reader.core.rsvp.engine.PARAGRAPH_SENTENCE_MULTIPLIER
import com.kairo.reader.core.rsvp.engine.PARENTHETICAL_HOLD_FRACTION
import com.kairo.reader.core.rsvp.engine.PHRASE_BREAK_HOLD_MS
import com.kairo.reader.core.rsvp.engine.SENTENCE_END_BREAK_BOOST_MS
import com.kairo.reader.core.rsvp.engine.SENTENCE_START_MIN_HOLD_MS
import com.kairo.reader.core.rsvp.engine.SENTENCE_WRAP_UP_LONG_WORDS
import com.kairo.reader.core.rsvp.engine.SENTENCE_WRAP_UP_MAX_FACTOR
import com.kairo.reader.core.rsvp.engine.SENTENCE_WRAP_UP_MIN_FACTOR
import com.kairo.reader.core.rsvp.engine.SENTENCE_WRAP_UP_SHORT_WORDS
import com.kairo.reader.core.rsvp.text.isClauseLeadPunctuation
import com.kairo.reader.core.rsvp.text.isDecimalPoint
import com.kairo.reader.core.rsvp.text.isEmbeddedQuote
import com.kairo.reader.core.rsvp.text.isQuoteChar
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

internal fun adaptiveHoldMs(
    words: List<Token>,
    difficulty: Double,
    config: RsvpConfig,
    speedStrength: Double,
    hardBoundary: Boolean,
    nextWord: Token?,
    clauseConfigStrength: Double,
): Double {
    if (!config.useAdaptiveTiming || words.isEmpty() || nextWord == null) return 0.0

    val difficultyScale =
        (
            (difficulty - ADAPTIVE_DIFFICULTY_FLOOR).coerceAtLeast(0.0) /
                (1.0 - ADAPTIVE_DIFFICULTY_FLOOR)
            )
            .coerceIn(0.0, 1.0)
    var hold = difficultyScale * config.adaptiveDifficultyMaxHoldMs * speedStrength

    if (words.any { it.complexityMultiplier >= config.complexWordThreshold }) {
        hold += config.complexWordHoldMs * speedStrength
    }

    val lastWord = words.lastOrNull()
    if (!hardBoundary && config.useClausePausing && lastWord?.isClauseBoundary == true) {
        hold += CLAUSE_BOUNDARY_HOLD_MS * speedStrength * clauseConfigStrength
    }

    // Add hold for phrase enders to give reader time to process the phrase
    if (lastWord != null && ClauseDetector.isPhraseEnder(lastWord.text)) {
        hold += PHRASE_BREAK_HOLD_MS * speedStrength * PHRASE_END_HOLD_FACTOR
    }

    // Reduce hold if next word has high coherence with current (they belong together)
    val coherence = ClauseDetector.getCoherenceScore(
        lastWord?.text.orEmpty(),
        nextWord.text,
    )
    if (coherence >= COHERENCE_GROUP_THRESHOLD) {
        // Words belong together, reduce the hold to keep them mentally grouped
        hold *= (1.0 - coherence * COHERENCE_HOLD_REDUCTION)
    }

    return hold.coerceAtMost(ADAPTIVE_HOLD_MAX_MS * speedStrength)
}

internal fun wordDurationMs(
    word: Token,
    msPerWord: Double,
    config: RsvpConfig,
): Double {
    val text = word.text
    val fullLetters = text.count { it.isLetterOrDigit() }.coerceAtLeast(1)
    val (letters, syllables) =
        if (word.isSubwordChunk &&
            word.highlightStart != null &&
            word.highlightEndExclusive != null &&
            word.highlightEndExclusive > word.highlightStart &&
            word.highlightEndExclusive <= text.length
        ) {
            val chunkText = text.substring(word.highlightStart, word.highlightEndExclusive)
            val chunkLetters = chunkText.count { it.isLetterOrDigit() }.coerceAtLeast(1)
            val ratio =
                (chunkLetters.toDouble() / fullLetters.toDouble())
                    .coerceIn(MIN_SUBWORD_LENGTH_RATIO, 1.0)
            val scaledSyllables =
                max(1.0, word.syllableCount.toDouble() * ratio).roundToLong().toInt()
            chunkLetters to scaledSyllables
        } else {
            fullLetters to word.syllableCount
        }

    val lengthCurve =
        run {
            val x =
                (
                    (letters - LENGTH_CURVE_BASE_CHARS).coerceAtLeast(0) /
                        LENGTH_CURVE_CHAR_SCALE
                    )
            1.0 + config.lengthStrength * (x.pow(config.lengthExponent))
        }

    val complexityComponent =
        1.0 + (max(0.0, word.complexityMultiplier - 1.0) * config.complexityStrength)

    val rarityExtra = (1.0 - word.frequencyScore).coerceIn(0.0, 1.0) * config.rarityExtraMaxMs
    val syllableExtra = max(0, syllables - 1) * config.syllableExtraMs

    var duration = (msPerWord * lengthCurve * complexityComponent) + rarityExtra + syllableExtra

    // Dynamism: let easy, predictable words glide below the baseline tempo so the cadence rises
    // and falls with difficulty rather than only ever adding time. Hard words are untouched (they
    // already earned length/rarity time). Mid-word continuations (hyphen/subword) are left alone,
    // and the unit-level word floor downstream keeps every frame readable.
    if (!word.isSubwordChunk && !text.endsWith("-")) {
        val ease = word.frequencyScore.coerceIn(0.0, 1.0)
        if (ease > DYNAMISM_EASE_PIVOT) {
            val t = (ease - DYNAMISM_EASE_PIVOT) / (1.0 - DYNAMISM_EASE_PIVOT)
            val compressionStrength = speedStrength(msPerWord)
            duration *= 1.0 - (DYNAMISM_MAX_SPEEDUP * t * compressionStrength)
        }
    }

    if (letters >= config.longWordChars) {
        duration = max(duration, config.longWordMinMs.toDouble())
    }

    if (text.endsWith("-")) {
        duration += msPerWord * HYPHEN_CONTINUATION_HOLD_FACTOR
    }

    return duration
}

internal fun punctuationPauseMs(
    token: Token,
    prevWord: Token?,
    nextToken: Token?,
    msPerWord: Double,
    config: RsvpConfig,
    insideAside: Boolean = false,
    insideDialogue: Boolean = false,
): Double {
    val ch = token.text.firstOrNull() ?: return 0.0
    val prevText = prevWord?.text.orEmpty()

    if (ch == '.' && isDecimalPoint(prevText, nextToken)) {
        return 0.0
    }

    val timing = RsvpPunctuationTimingPolicy.resolvePauseTiming(token, prevWord, nextToken, config)
    var base = timing.baseMs
    var floor = timing.floorMs
    val speedStrength = speedStrength(msPerWord)
    if (isClauseLeadPunctuation(ch, nextToken)) {
        base += CLAUSE_LEAD_BOOST_MS * speedStrength
    }

    if (isSentenceEndingPunctuation(ch) || ch == '.') {
        if (nextToken?.type == TokenType.PARAGRAPH_BREAK ||
            nextToken?.type == TokenType.PAGE_BREAK
        ) {
            base += SENTENCE_END_BREAK_BOOST_MS * speedStrength
        }
    }

    if (isEmbeddedQuote(ch, prevWord, nextToken)) {
        base *= EMBEDDED_QUOTE_FACTOR
        floor *= EMBEDDED_QUOTE_FACTOR
    }

    val punctuationScale =
        pauseScale(
            msPerWord = msPerWord,
            config = config,
            extraRetention = timing.scaleRetentionBoost,
        )
    val dialogueScale =
        if (config.useDialogueDetection && insideDialogue && !isQuoteChar(ch)) {
            config.dialoguePunctuationScale.coerceIn(
                Constraints.MIN_DIALOGUE_PUNCTUATION_SCALE,
                Constraints.MAX_DIALOGUE_PUNCTUATION_SCALE,
            )
        } else {
            1.0
        }
    val asideScale =
        if (insideAside && !isQuoteChar(ch)) {
            config.parentheticalAsideMultiplier.coerceIn(
                Constraints.MIN_PARENTHETICAL_ASIDE_MULTIPLIER,
                Constraints.MAX_PARENTHETICAL_ASIDE_MULTIPLIER,
            )
        } else {
            1.0
        }
    val scaled = base * punctuationScale * dialogueScale * asideScale
    return max(scaled, floor)
}

internal fun punctuationLandingHoldMs(
    frameTokens: List<Token>,
    nextToken: Token?,
    msPerWord: Double,
    speedStrength: Double,
): Double {
    val nextWordExists =
        nextToken?.type == TokenType.WORD ||
            frameTokens.any { it.type == TokenType.PARAGRAPH_BREAK || it.type == TokenType.PAGE_BREAK }
    if (!nextWordExists) return 0.0

    val weight =
        frameTokens
            .mapIndexedNotNull { index, token ->
                if (token.type != TokenType.PUNCTUATION) return@mapIndexedNotNull null
                val prevWord =
                    frameTokens
                        .subList(0, index)
                        .lastOrNull { it.type == TokenType.WORD }
                boundaryLandingWeight(
                    token = token,
                    prevWord = prevWord,
                    nextToken = nextToken,
                )
            }.maxOrNull()
            ?: return 0.0
    if (weight <= 0.0) return 0.0

    val base = (msPerWord * weight).coerceIn(MIN_LANDING_HOLD_MS, MAX_LANDING_HOLD_MS)
    val speedAdjusted = base * (1.0 + (speedStrength * LANDING_HOLD_SPEED_BOOST))
    return speedAdjusted.coerceAtMost(MAX_LANDING_HOLD_MS)
}

internal fun boundaryLandingWeight(
    token: Token,
    prevWord: Token?,
    nextToken: Token?,
): Double {
    return RsvpPunctuationTimingPolicy.boundaryLandingWeight(
        token = token,
        prevWord = prevWord,
        nextToken = nextToken,
    )
}

internal fun pauseScale(
    msPerWord: Double,
    config: RsvpConfig,
    extraRetention: Double = 0.0,
): Double {
    val minPauseScale = config.minPauseScale.coerceIn(0.0, MAX_MIN_PAUSE_SCALE)
    val pauseScaleExponent = config.pauseScaleExponent.coerceAtLeast(0.0)
    val ratio =
        (msPerWord / BASE_MS_PER_WORD_AT_300).coerceIn(
            MIN_PAUSE_TEMPO_RATIO,
            MAX_PAUSE_TEMPO_RATIO,
        )
    val compressed = ratio.pow(pauseScaleExponent)
    val preservedFloor =
        (minPauseScale + extraRetention)
            .coerceIn(minPauseScale, MAX_MIN_PAUSE_SCALE)
    val scaled = preservedFloor + ((1.0 - preservedFloor) * compressed)
    return scaled.coerceIn(minPauseScale, MAX_SCALED_PAUSE)
}

internal fun wordFloorMs(
    word: Token,
    config: RsvpConfig,
): Long = config.wordFloorMsForReadability(word)

internal fun pageBreakBasePauseMs(config: RsvpConfig): Double =
    max(
        config.paragraphPauseMs.toDouble() * config.pageBreakPauseMultiplier,
        max(config.sentenceEndPauseMs.toDouble(), config.periodPauseMs.toDouble()) *
            (config.pageBreakPauseMultiplier * PAGE_BREAK_SENTENCE_MULTIPLIER_RATIO),
    )

internal fun paragraphBreakBasePauseMs(config: RsvpConfig): Double =
    max(
        config.paragraphPauseMs.toDouble() * config.paragraphPauseMultiplier,
        config.sentenceEndPauseMs.toDouble() * PARAGRAPH_SENTENCE_MULTIPLIER,
    )

internal fun boundaryStartMicroHoldMs(
    msPerWord: Double,
    speedStrength: Double,
    boundaryBefore: BoundaryBefore,
): Double {
    if (msPerWord > MAX_BOUNDARY_MICRO_HOLD_TEMPO_MS) return 0.0
    return when (boundaryBefore) {
        BoundaryBefore.SENTENCE -> SENTENCE_START_MIN_HOLD_MS * speedStrength
        BoundaryBefore.CLAUSE -> CLAUSE_START_MIN_HOLD_MS * speedStrength
        BoundaryBefore.PARAGRAPH, BoundaryBefore.PAGE, BoundaryBefore.NONE -> 0.0
    }
}

internal fun clauseStartHoldMs(
    config: RsvpConfig,
    pauseScale: Double,
): Double {
    val base =
        max(
            config.commaPauseMs.toDouble(),
            max(
                config.semicolonPauseMs.toDouble() * SEMICOLON_CLAUSE_START_FACTOR,
                max(
                    config.colonPauseMs.toDouble() * COLON_CLAUSE_START_FACTOR,
                    config.dashPauseMs.toDouble() * DASH_CLAUSE_START_FACTOR,
                ),
            ),
        )
    return base * pauseScale * CLAUSE_START_HOLD_FRACTION
}

internal fun parentheticalHoldMs(
    msPerWord: Double,
    config: RsvpConfig,
): Double {
    val multiplierDelta = (config.parentheticalMultiplier - 1.0).coerceAtLeast(0.0)
    if (multiplierDelta <= 0.0) return 0.0
    return msPerWord * multiplierDelta * PARENTHETICAL_HOLD_FRACTION
}

internal fun startBoostMultiplier(
    msPerWord: Double,
    boundaryBefore: BoundaryBefore,
): Double {
    val strength = speedStrength(msPerWord)
    val maxExtra =
        when (boundaryBefore) {
            BoundaryBefore.CLAUSE -> CLAUSE_START_BOOST
            BoundaryBefore.SENTENCE -> SENTENCE_START_BOOST
            BoundaryBefore.PARAGRAPH -> PARAGRAPH_START_BOOST
            BoundaryBefore.PAGE -> PAGE_START_BOOST
            BoundaryBefore.NONE -> 0.0
        }

    return 1.0 + (maxExtra * strength)
}

/**
 * Sentence wrap-up: scales the sentence-end pause by how many words the sentence held.
 * Every sentence keeps its full stop; longer sentences earn additional integration time.
 */
internal fun sentenceWrapUpFactor(wordsInSentence: Int): Double {
    val t =
        (
            (wordsInSentence - SENTENCE_WRAP_UP_SHORT_WORDS) /
                (SENTENCE_WRAP_UP_LONG_WORDS - SENTENCE_WRAP_UP_SHORT_WORDS)
            )
            .coerceIn(0.0, 1.0)
    return SENTENCE_WRAP_UP_MIN_FACTOR +
        ((SENTENCE_WRAP_UP_MAX_FACTOR - SENTENCE_WRAP_UP_MIN_FACTOR) * t)
}

internal fun speedStrength(msPerWord: Double): Double {
    val speedFactor =
        (BASE_MS_PER_WORD_AT_300 / msPerWord).coerceIn(1.0, MAX_SPEED_STRENGTH_FACTOR)
    return ((speedFactor - 1.0) / SPEED_STRENGTH_FACTOR_RANGE).coerceIn(0.0, 1.0)
}

// Expression remains audible at comfortable tempos; speed-only compensation still starts at 300 WPM.
internal fun expressionStrength(msPerWord: Double): Double =
    BASE_EXPRESSION_STRENGTH + (1.0 - BASE_EXPRESSION_STRENGTH) * speedStrength(msPerWord)

private const val BASE_EXPRESSION_STRENGTH = 0.30

private const val PHRASE_END_HOLD_FACTOR = 0.6
private const val COHERENCE_GROUP_THRESHOLD = 0.5
private const val COHERENCE_HOLD_REDUCTION = 0.4
private const val MIN_SUBWORD_LENGTH_RATIO = 0.2
private const val LENGTH_CURVE_BASE_CHARS = 4
private const val LENGTH_CURVE_CHAR_SCALE = 10.0
private const val HYPHEN_CONTINUATION_HOLD_FACTOR = 0.25
private const val MIN_PAUSE_TEMPO_RATIO = 0.12
private const val MAX_PAUSE_TEMPO_RATIO = 2.5
private const val MAX_SCALED_PAUSE = 1.35
private const val MAX_BOUNDARY_MICRO_HOLD_TEMPO_MS = 110.0
private const val SEMICOLON_CLAUSE_START_FACTOR = 0.72
private const val COLON_CLAUSE_START_FACTOR = 0.78
private const val DASH_CLAUSE_START_FACTOR = 0.78
private const val CLAUSE_START_BOOST = 0.05
private const val SENTENCE_START_BOOST = 0.10
private const val PARAGRAPH_START_BOOST = 0.16
private const val PAGE_START_BOOST = 0.22
private const val MAX_SPEED_STRENGTH_FACTOR = 3.5
private const val SPEED_STRENGTH_FACTOR_RANGE = 2.5
