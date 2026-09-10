package com.kairo.reader.core.rsvp.timing

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpConfigConstraints as Constraints
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.isMidSentencePunctuation
import com.kairo.reader.core.model.isSentenceEndingPunctuation
import com.kairo.reader.core.rsvp.text.isAbbreviationDot
import com.kairo.reader.core.rsvp.text.isClauseLeadPunctuation
import com.kairo.reader.core.rsvp.text.isDecimalPoint
import com.kairo.reader.core.rsvp.text.isThousandSeparator
import kotlin.math.max
import kotlin.math.min

internal data class RsvpPunctuationPauseTiming(val baseMs: Double, val floorMs: Double, val scaleRetentionBoost: Double,)

internal data class RsvpBoundaryContour(val landingHoldWeight: Double, val tailLiftWeight: Double,)

internal enum class RsvpPunctuationTier {
    SENTENCE_END,
    CLAUSE_BREAK,
    SOFT_SEPARATOR,
    NONE,
}

internal object RsvpPunctuationTimingPolicy {
    fun resolvePauseTiming(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
        config: RsvpConfig,
    ): RsvpPunctuationPauseTiming {
        val ch = token.text.firstOrNull() ?: return ZERO_PAUSE_TIMING
        val prevText = prevWord?.text.orEmpty()
        val tier = resolveTier(token = token, prevWord = prevWord, nextToken = nextToken)

        if (tier == RsvpPunctuationTier.NONE) {
            return ZERO_PAUSE_TIMING
        }

        val base =
            pauseBaseMs(
                ch = ch,
                tier = tier,
                prevText = prevText,
                nextToken = nextToken,
                config = config,
            ) ?: return ZERO_PAUSE_TIMING

        val floor =
            pauseFloorMs(
                ch = ch,
                tier = tier,
                prevText = prevText,
                nextToken = nextToken,
                config = config,
            )

        val scaleRetentionBoost =
            scaleRetentionBoost(ch = ch, tier = tier, nextToken = nextToken)

        val breathingScale = punctuationBreathingScale(config)

        return RsvpPunctuationPauseTiming(
            baseMs = base * breathingScale,
            floorMs = floor * breathingScale,
            scaleRetentionBoost = scaleRetentionBoost,
        )
    }

    fun boundaryLandingWeight(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): Double {
        return resolveBoundaryContour(
            token = token,
            prevWord = prevWord,
            nextToken = nextToken,
        ).landingHoldWeight
    }

    fun boundaryTailLiftWeight(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): Double {
        return resolveBoundaryContour(
            token = token,
            prevWord = prevWord,
            nextToken = nextToken,
        ).tailLiftWeight
    }

    internal fun resolveBoundaryContour(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): RsvpBoundaryContour {
        val ch = token.text.firstOrNull() ?: return ZERO_BOUNDARY_CONTOUR
        val prevText = prevWord?.text.orEmpty()
        val tier = resolveTier(token = token, prevWord = prevWord, nextToken = nextToken)
        if (tier == RsvpPunctuationTier.NONE) return ZERO_BOUNDARY_CONTOUR
        if (ch == '.' &&
            (isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken))
        ) {
            return ZERO_BOUNDARY_CONTOUR
        }
        if (ch == ',' && isThousandSeparator(prevText, nextToken)) {
            return ZERO_BOUNDARY_CONTOUR
        }

        val contourStrength =
            boundaryTierContourWeight(
                token = token,
                prevWord = prevWord,
                nextToken = nextToken,
                tier = tier,
            )
        if (contourStrength <= 0.0) return ZERO_BOUNDARY_CONTOUR

        val landingHoldWeight = landingHoldWeight(tier, ch) * contourStrength
        val tailLiftWeight = tailLiftWeight(tier, ch, prevText, nextToken) * contourStrength

        return balanceBoundaryContour(
            tier = tier,
            landingHoldWeight = landingHoldWeight,
            tailLiftWeight = tailLiftWeight,
        )
    }

    private fun landingHoldWeight(
        tier: RsvpPunctuationTier,
        character: Char,
    ): Double =
        when {
            tier == RsvpPunctuationTier.SENTENCE_END && character == '\u2026' -> ELLIPSIS_LANDING_HOLD_WEIGHT
            tier == RsvpPunctuationTier.SENTENCE_END -> STRONG_LANDING_HOLD_WEIGHT
            tier == RsvpPunctuationTier.CLAUSE_BREAK && character == ';' -> SEMICOLON_LANDING_HOLD_WEIGHT
            tier == RsvpPunctuationTier.CLAUSE_BREAK -> CLAUSE_LANDING_HOLD_WEIGHT
            else -> 0.0
        }

    private fun tailLiftWeight(
        tier: RsvpPunctuationTier,
        character: Char,
        previousText: String,
        nextToken: Token?,
    ): Double =
        when {
            tier == RsvpPunctuationTier.SENTENCE_END && isPeriodPunctuation(character) ->
                periodTailLift(character, previousText, nextToken)
            tier == RsvpPunctuationTier.SENTENCE_END && character == '\u2026' -> ELLIPSIS_TAIL_LIFT
            tier == RsvpPunctuationTier.SENTENCE_END && isQuestionPunctuation(character) -> QUESTION_TAIL_LIFT
            tier == RsvpPunctuationTier.SENTENCE_END && isExclamationPunctuation(character) -> EXCLAMATION_TAIL_LIFT
            tier == RsvpPunctuationTier.SENTENCE_END -> SENTENCE_TAIL_LIFT
            tier == RsvpPunctuationTier.CLAUSE_BREAK && isSemicolonPunctuation(character) -> SEMICOLON_TAIL_LIFT
            tier == RsvpPunctuationTier.CLAUSE_BREAK && isColonPunctuation(character) -> COLON_TAIL_LIFT
            tier == RsvpPunctuationTier.CLAUSE_BREAK && isDashPunctuation(character) -> DASH_TAIL_LIFT
            tier == RsvpPunctuationTier.CLAUSE_BREAK && isCommaPunctuation(character) ->
                if (isClauseLeadPunctuation(',', nextToken)) CLAUSE_LEAD_COMMA_TAIL_LIFT else COMMA_TAIL_LIFT
            else -> 0.0
        }

    private fun periodTailLift(
        character: Char,
        previousText: String,
        nextToken: Token?,
    ): Double =
        when {
            character == '.' &&
                (isDecimalPoint(previousText, nextToken) || isAbbreviationDot(previousText, nextToken)) -> 0.0
            else -> PERIOD_TAIL_LIFT
        }

    private fun boundaryTierContourWeight(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
        tier: RsvpPunctuationTier,
    ): Double {
        val ch = token.text.firstOrNull() ?: return 0.0
        val prevText = prevWord?.text.orEmpty()
        return when (tier) {
            RsvpPunctuationTier.SENTENCE_END ->
                when (ch) {
                    in PERIOD_PUNCTUATION -> {
                        when {
                            ch == '.' &&
                                (isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken)) -> 0.0
                            else -> PERIOD_CONTOUR
                        }
                    }
                    '\u2026' -> 1.0
                    in QUESTION_PUNCTUATION -> QUESTION_CONTOUR
                    in EXCLAMATION_PUNCTUATION -> EXCLAMATION_CONTOUR
                    else -> SENTENCE_CONTOUR
                }
            RsvpPunctuationTier.CLAUSE_BREAK ->
                when {
                    isSemicolonPunctuation(ch) -> SEMICOLON_CONTOUR
                    isColonPunctuation(ch) -> COLON_CONTOUR
                    isDashPunctuation(ch) -> DASH_CONTOUR
                    isCommaPunctuation(ch) ->
                        if (isClauseLeadPunctuation(',', nextToken)) {
                            CLAUSE_LEAD_COMMA_CONTOUR
                        } else {
                            COMMA_CONTOUR
                        }
                    else -> 0.0
                }
            RsvpPunctuationTier.SOFT_SEPARATOR -> 0.0
            RsvpPunctuationTier.NONE -> 0.0
        }
    }

    private fun balanceBoundaryContour(
        tier: RsvpPunctuationTier,
        landingHoldWeight: Double,
        tailLiftWeight: Double,
    ): RsvpBoundaryContour {
        if (landingHoldWeight <= 0.0 || tailLiftWeight <= 0.0) {
            return RsvpBoundaryContour(
                landingHoldWeight = landingHoldWeight,
                tailLiftWeight = tailLiftWeight,
            )
        }

        val overlapPressure =
            when (tier) {
                RsvpPunctuationTier.SENTENCE_END -> SENTENCE_CONTOUR_OVERLAP_PRESSURE
                RsvpPunctuationTier.CLAUSE_BREAK -> CLAUSE_CONTOUR_OVERLAP_PRESSURE
                RsvpPunctuationTier.SOFT_SEPARATOR, RsvpPunctuationTier.NONE -> 0.0
            }

        if (overlapPressure <= 0.0) {
            return RsvpBoundaryContour(
                landingHoldWeight = landingHoldWeight,
                tailLiftWeight = tailLiftWeight,
            )
        }

        val dampening =
            min(
                MAX_CONTOUR_DAMPENING,
                landingHoldWeight.coerceIn(0.0, MAX_DAMPENED_LANDING_WEIGHT) * overlapPressure,
            )
        return RsvpBoundaryContour(
            landingHoldWeight = landingHoldWeight,
            tailLiftWeight = tailLiftWeight * (1.0 - dampening),
        )
    }

    private fun ellipsisPauseBaseMs(
        nextToken: Token?,
        config: RsvpConfig,
    ): Double {
        val nextWord = nextToken?.takeIf { it.type == TokenType.WORD }?.text
        val nextStartsSentenceLike =
            nextWord?.firstOrNull { it.isLetter() }?.isUpperCase() == true ||
                nextWord?.lowercase() in SENTENCE_STARTERS
        val breakAfterEllipsis =
            nextToken?.type == TokenType.PARAGRAPH_BREAK || nextToken?.type == TokenType.PAGE_BREAK
        return if (nextStartsSentenceLike || breakAfterEllipsis || nextWord == null) {
            max(
                config.commaPauseMs * ELLIPSIS_SENTENCE_COMMA_FACTOR,
                config.periodPauseMs * ELLIPSIS_PERIOD_FACTOR,
            )
        } else {
            config.commaPauseMs * ELLIPSIS_INLINE_COMMA_FACTOR
        }
    }

    private fun punctuationTier(ch: Char): RsvpPunctuationTier =
        when {
            isSentenceEndingPunctuation(ch) -> RsvpPunctuationTier.SENTENCE_END
            ch == '\u2026' -> RsvpPunctuationTier.SENTENCE_END
            isCommaPunctuation(ch) ||
                isSemicolonPunctuation(ch) ||
                isColonPunctuation(ch) ||
                isDashPunctuation(ch) -> RsvpPunctuationTier.CLAUSE_BREAK
            ch in listOf('(', ')', '[', ']', '{', '}', '"', '\u201C', '\u201D', '\u2018', '\u2019') ->
                RsvpPunctuationTier.SOFT_SEPARATOR
            isMidSentencePunctuation(ch) -> RsvpPunctuationTier.SOFT_SEPARATOR
            else -> RsvpPunctuationTier.NONE
        }

    private fun punctuationBreathingScale(config: RsvpConfig): Double =
        config.punctuationPauseFactor.coerceIn(
            Constraints.MIN_PUNCTUATION_PAUSE_FACTOR,
            Constraints.MAX_PUNCTUATION_PAUSE_FACTOR,
        )

    private fun pauseBaseMs(
        ch: Char,
        tier: RsvpPunctuationTier,
        prevText: String,
        nextToken: Token?,
        config: RsvpConfig,
    ): Double? =
        when (tier) {
            RsvpPunctuationTier.SENTENCE_END ->
                sentencePauseBaseMs(ch = ch, prevText = prevText, nextToken = nextToken, config = config)
            RsvpPunctuationTier.CLAUSE_BREAK ->
                clausePauseBaseMs(ch = ch, prevText = prevText, nextToken = nextToken, config = config)
            RsvpPunctuationTier.SOFT_SEPARATOR ->
                softSeparatorPauseBaseMs(ch = ch, config = config)
            RsvpPunctuationTier.NONE -> null
        }

    private fun pauseFloorMs(
        ch: Char,
        tier: RsvpPunctuationTier,
        prevText: String,
        nextToken: Token?,
        config: RsvpConfig,
    ): Double =
        when (tier) {
            RsvpPunctuationTier.SENTENCE_END ->
                sentencePauseFloorMs(ch = ch, prevText = prevText, nextToken = nextToken, config = config)
            RsvpPunctuationTier.CLAUSE_BREAK ->
                clausePauseFloorMs(ch = ch, prevText = prevText, nextToken = nextToken, config = config)
            RsvpPunctuationTier.SOFT_SEPARATOR ->
                softSeparatorPauseFloorMs(ch = ch, config = config)
            RsvpPunctuationTier.NONE -> 0.0
        }

    private fun sentencePauseBaseMs(
        ch: Char,
        prevText: String,
        nextToken: Token?,
        config: RsvpConfig,
    ): Double? =
        when {
            isPeriodPunctuation(ch) ->
                when {
                    ch == '.' &&
                        (isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken)) -> null
                    ch == '.' -> config.periodPauseMs.toDouble()
                    else -> max(config.periodPauseMs, config.sentenceEndPauseMs).toDouble()
                }
            ch == '\u2026' -> ellipsisPauseBaseMs(nextToken = nextToken, config = config)
            isQuestionPunctuation(ch) -> config.sentenceEndPauseMs * QUESTION_PAUSE_FACTOR
            isExclamationPunctuation(ch) -> config.sentenceEndPauseMs * EXCLAMATION_PAUSE_FACTOR
            else -> config.sentenceEndPauseMs.toDouble()
        }

    private fun sentencePauseFloorMs(
        ch: Char,
        prevText: String,
        nextToken: Token?,
        config: RsvpConfig,
    ): Double =
        (sentencePauseBaseMs(ch, prevText, nextToken, config) ?: 0.0) * config.minPauseScale

    private fun clausePauseBaseMs(
        ch: Char,
        prevText: String,
        nextToken: Token?,
        config: RsvpConfig,
    ): Double? =
        when {
            isCommaPunctuation(ch) ->
                if (ch == ',' && isThousandSeparator(prevText, nextToken)) {
                    null
                } else {
                    val clauseFactor =
                        if (isClauseLeadPunctuation(',', nextToken)) {
                            CLAUSE_LEADING_COMMA_FACTOR
                        } else {
                            1.0
                        }
                    config.commaPauseMs * COMMA_BREATH_FACTOR * clauseFactor
                }
            isSemicolonPunctuation(ch) -> config.semicolonPauseMs.toDouble()
            isColonPunctuation(ch) -> config.colonPauseMs.toDouble()
            isDashPunctuation(ch) -> config.dashPauseMs.toDouble()
            else -> null
        }

    private fun clausePauseFloorMs(
        ch: Char,
        prevText: String,
        nextToken: Token?,
        config: RsvpConfig,
    ): Double =
        (clausePauseBaseMs(ch, prevText, nextToken, config) ?: 0.0) * config.minPauseScale

    private fun softSeparatorPauseBaseMs(
        ch: Char,
        config: RsvpConfig,
    ): Double? =
        when {
            isParenthesisPunctuation(ch) -> config.parenthesesPauseMs.toDouble()
            isQuotePunctuation(ch) -> config.quotePauseMs.toDouble()
            isMidSentencePunctuation(ch) -> config.commaPauseMs * SOFT_SEPARATOR_COMMA_FACTOR
            else -> null
        }

    private fun softSeparatorPauseFloorMs(
        ch: Char,
        config: RsvpConfig,
    ): Double =
        when {
            isParenthesisPunctuation(ch) -> config.parenthesesPauseMs * config.minPauseScale
            isQuotePunctuation(ch) -> config.quotePauseMs * config.minPauseScale
            isMidSentencePunctuation(ch) ->
                (config.commaPauseMs * SOFT_SEPARATOR_COMMA_FACTOR) * config.minPauseScale
            else -> 0.0
        }

    private fun scaleRetentionBoost(
        ch: Char,
        tier: RsvpPunctuationTier,
        nextToken: Token?,
    ): Double =
        when (tier) {
            RsvpPunctuationTier.SENTENCE_END ->
                if (ch == '\u2026') ELLIPSIS_RETENTION_BOOST else STRONG_PUNCTUATION_RETENTION_BOOST
            RsvpPunctuationTier.CLAUSE_BREAK ->
                when {
                    isSemicolonPunctuation(ch) -> SEMICOLON_RETENTION_BOOST
                    isColonPunctuation(ch) || isDashPunctuation(ch) ->
                        CLAUSE_PUNCTUATION_RETENTION_BOOST
                    isCommaPunctuation(ch) ->
                        if (isClauseLeadPunctuation(',', nextToken)) {
                            CLAUSE_PUNCTUATION_RETENTION_BOOST
                        } else {
                            COMMA_RETENTION_BOOST
                        }
                    else -> 0.0
                }
            RsvpPunctuationTier.SOFT_SEPARATOR ->
                when {
                    isQuotePunctuation(ch) -> QUOTE_RETENTION_BOOST
                    isParenthesisPunctuation(ch) -> PARENTHESIS_RETENTION_BOOST
                    isMidSentencePunctuation(ch) -> COMMA_RETENTION_BOOST
                    else -> 0.0
                }
            RsvpPunctuationTier.NONE -> 0.0
        }

    private fun isParenthesisPunctuation(ch: Char): Boolean =
        ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}'

    private fun isQuotePunctuation(ch: Char): Boolean =
        ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019'

    private fun isCommaPunctuation(ch: Char): Boolean = ch in COMMA_PUNCTUATION

    private fun isSemicolonPunctuation(ch: Char): Boolean = ch in SEMICOLON_PUNCTUATION

    private fun isColonPunctuation(ch: Char): Boolean = ch in COLON_PUNCTUATION

    private fun isDashPunctuation(ch: Char): Boolean = ch in DASH_PUNCTUATION

    private fun isPeriodPunctuation(ch: Char): Boolean = ch in PERIOD_PUNCTUATION

    private fun isQuestionPunctuation(ch: Char): Boolean = ch in QUESTION_PUNCTUATION

    private fun isExclamationPunctuation(ch: Char): Boolean = ch in EXCLAMATION_PUNCTUATION

    internal fun resolveTier(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): RsvpPunctuationTier {
        val ch = token.text.firstOrNull() ?: return RsvpPunctuationTier.NONE
        val prevText = prevWord?.text.orEmpty()
        return when (punctuationTier(ch)) {
            RsvpPunctuationTier.SENTENCE_END ->
                when {
                    ch == '.' &&
                        (isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken)) ->
                        RsvpPunctuationTier.NONE
                    else -> RsvpPunctuationTier.SENTENCE_END
                }
            RsvpPunctuationTier.CLAUSE_BREAK -> {
                if (ch == ',' && isThousandSeparator(prevText, nextToken)) {
                    RsvpPunctuationTier.NONE
                } else {
                    RsvpPunctuationTier.CLAUSE_BREAK
                }
            }
            RsvpPunctuationTier.SOFT_SEPARATOR -> RsvpPunctuationTier.SOFT_SEPARATOR
            RsvpPunctuationTier.NONE -> RsvpPunctuationTier.NONE
        }
    }

    private const val ELLIPSIS_INLINE_COMMA_FACTOR = 1.25
    private const val ELLIPSIS_SENTENCE_COMMA_FACTOR = 1.35
    private const val ELLIPSIS_PERIOD_FACTOR = 0.84
    private const val COMMA_RETENTION_BOOST = 0.10
    private const val QUOTE_RETENTION_BOOST = 0.07
    private const val PARENTHESIS_RETENTION_BOOST = 0.08
    private const val SEMICOLON_RETENTION_BOOST = 0.18
    private const val ELLIPSIS_RETENTION_BOOST = 0.26
    private const val COMMA_BREATH_FACTOR = 1.10
    private const val CLAUSE_LEADING_COMMA_FACTOR = 1.16
    private const val SOFT_SEPARATOR_COMMA_FACTOR = 0.35
    private const val QUESTION_PAUSE_FACTOR = 1.08
    private const val EXCLAMATION_PAUSE_FACTOR = 0.96
    private const val CLAUSE_LANDING_HOLD_WEIGHT = 0.18
    private const val SEMICOLON_LANDING_HOLD_WEIGHT = 0.20
    private const val STRONG_LANDING_HOLD_WEIGHT = 0.22
    private const val ELLIPSIS_LANDING_HOLD_WEIGHT = 0.24

    private const val PERIOD_TAIL_LIFT = 1.34
    private const val ELLIPSIS_TAIL_LIFT = 1.40
    private const val QUESTION_TAIL_LIFT = 1.34
    private const val EXCLAMATION_TAIL_LIFT = 1.18
    private const val SENTENCE_TAIL_LIFT = 1.26
    private const val SEMICOLON_TAIL_LIFT = 0.64
    private const val COLON_TAIL_LIFT = 0.68
    private const val DASH_TAIL_LIFT = 0.74
    private const val CLAUSE_LEAD_COMMA_TAIL_LIFT = 0.36
    private const val COMMA_TAIL_LIFT = 0.24

    private const val PERIOD_CONTOUR = 0.92
    private const val QUESTION_CONTOUR = 0.94
    private const val EXCLAMATION_CONTOUR = 0.84
    private const val SENTENCE_CONTOUR = 0.88
    private const val SEMICOLON_CONTOUR = 0.72
    private const val COLON_CONTOUR = 0.76
    private const val DASH_CONTOUR = 0.82
    private const val CLAUSE_LEAD_COMMA_CONTOUR = 0.42
    private const val COMMA_CONTOUR = 0.24

    private const val SENTENCE_CONTOUR_OVERLAP_PRESSURE = 0.48
    private const val CLAUSE_CONTOUR_OVERLAP_PRESSURE = 0.32
    private const val MAX_CONTOUR_DAMPENING = 0.16
    private const val MAX_DAMPENED_LANDING_WEIGHT = 0.35

    private val ZERO_BOUNDARY_CONTOUR = RsvpBoundaryContour(0.0, 0.0)

    private val ZERO_PAUSE_TIMING = RsvpPunctuationPauseTiming(0.0, 0.0, 0.0)

    private val COMMA_PUNCTUATION = setOf(',', '\u3001', '\uFF0C', '\u060C')
    private val SEMICOLON_PUNCTUATION = setOf(';', '\uFF1B', '\u061B')
    private val COLON_PUNCTUATION = setOf(':', '\uFF1A')
    private val DASH_PUNCTUATION = setOf('\u2014', '\u2013', '-')
    private val PERIOD_PUNCTUATION = setOf('.', '\u3002', '\uFF61', '\u06D4', '\u05C3')
    private val QUESTION_PUNCTUATION = setOf('?', '\uFF1F', '\u061F')
    private val EXCLAMATION_PUNCTUATION = setOf('!', '\uFF01')
}

internal const val CLAUSE_PUNCTUATION_RETENTION_BOOST = 0.10
internal const val STRONG_PUNCTUATION_RETENTION_BOOST = 0.18

internal val TITLE_ABBREVIATIONS =
    setOf(
        "mr",
        "mrs",
        "ms",
        "dr",
        "prof",
        "sr",
        "jr",
        "st",
        "rev",
        "fr",
    )

internal val KNOWN_ABBREVIATIONS =
    setOf(
        "mr",
        "mrs",
        "ms",
        "dr",
        "prof",
        "sr",
        "jr",
        "st",
        "vs",
        "etc",
        "e.g",
        "i.e",
        "eg",
        "ie",
        "no",
        "vol",
        "fig",
        "al",
        "inc",
        "ltd",
        "dept",
        "est",
        "approx",
        "misc",
        "jan",
        "feb",
        "mar",
        "apr",
        "jun",
        "jul",
        "aug",
        "sep",
        "sept",
        "oct",
        "nov",
        "dec",
        "u.s",
        "u.k",
        "u.n",
    )

internal val SENTENCE_STARTERS =
    setOf(
        "i",
        "he",
        "she",
        "they",
        "we",
        "it",
        "the",
        "a",
        "an",
        "this",
        "that",
        "these",
        "those",
    )
