package com.kairo.reader.core.rsvp.engine

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.analysis.RsvpReadingDemand
import kotlin.math.max

internal data class ExpandedToken(
    val token: Token,
    val originalIndex: Int,
    val expandedIndex: Int,
    val sourceCharacterStart: Int,
    val sourceCharacterEndExclusive: Int,
    val readingDemand: RsvpReadingDemand? = null,
)

internal data class PhraseContour(val preBoundaryWeight: Double, val restartWeight: Double,) {
    companion object {
        val NONE = PhraseContour(preBoundaryWeight = 0.0, restartWeight = 0.0)
    }
}

internal data class UnitBuildResult(val tokens: List<Token>, val originalWordIndex: Int, val nextCursor: Int,)

internal enum class BoundaryBefore {
    NONE,
    CLAUSE,
    SENTENCE,
    PARAGRAPH,
    PAGE,
    ;

    fun isMajorStart(): Boolean =
        this == SENTENCE || this == PARAGRAPH || this == PAGE
}

internal class ContextState {
    var parentheticalDepth: Int = 0
        private set
    var straightQuoteOpen: Boolean = false
        private set
    var inDialogue: Boolean = false
        private set

    fun snapshot(): ContextSnapshot =
        ContextSnapshot(
            parentheticalDepth = parentheticalDepth,
            inDialogue = inDialogue,
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
            '"' -> straightQuoteOpen = !straightQuoteOpen
            '\u201C', '\u2018' -> Unit
            '\u201D', '\u2019' -> Unit
        }
        inDialogue = token.isDialogue
    }
}

internal data class ContextSnapshot(val parentheticalDepth: Int, val inDialogue: Boolean,)

/**
 * Sequential prose memory carried across frames during generation.
 *
 * Tracks how deep into the current sentence the reader is (for sentence wrap-up pauses) and
 * phrase timing. Frames are generated in
 * reading order, so this state is deterministic for a given token stream.
 */
internal class ProseState {
    var wordsInSentence: Int = 0
        private set

    fun onWordShown() {
        wordsInSentence++
    }

    fun onSentenceEnd() {
        wordsInSentence = 0
    }

    fun onParagraphBreak() {
        wordsInSentence = 0
    }

    fun onPageBreak() {
        wordsInSentence = 0
    }
}

internal class RhythmState {
    private var ema: Double? = null
    private val smoothingAlpha: Double
    private val maxSpeedupFactor: Double
    private val maxSlowdownFactor: Double

    constructor(
        smoothingAlpha: Double,
        maxSpeedupFactor: Double,
        maxSlowdownFactor: Double,
    ) {
        this.smoothingAlpha = smoothingAlpha.coerceIn(0.0, 1.0)
        this.maxSpeedupFactor = maxSpeedupFactor.coerceAtLeast(1.0)
        this.maxSlowdownFactor = maxSlowdownFactor.coerceAtLeast(1.0)
    }

    fun apply(
        rawMs: Double,
        isBoundary: Boolean,
        boundaryStrengthMilli: Int = 0,
    ): Double {
        val boundaryStrength = boundaryStrengthMilli.coerceIn(0, RHYTHM_BOUNDARY_SCALE)
        if (isBoundary || boundaryStrength == RHYTHM_BOUNDARY_SCALE) {
            ema = rawMs
            return rawMs
        }

        val prev = ema
        val next =
            if (prev == null) {
                rawMs
            } else {
                val effectiveAlpha =
                    if (boundaryStrength == 0) {
                        smoothingAlpha
                    } else {
                        val partialReseed =
                            (1.0 - smoothingAlpha) *
                                PARTIAL_BOUNDARY_RESEED_MAX *
                                boundaryStrength /
                                RHYTHM_BOUNDARY_SCALE
                        smoothingAlpha + partialReseed
                    }
                val mixed = prev + (effectiveAlpha * (rawMs - prev))
                val minAllowed = prev / maxSpeedupFactor
                val maxAllowed = prev * maxSlowdownFactor
                mixed.coerceIn(minAllowed, maxAllowed)
            }

        ema = next
        return next
    }

    fun reset() {
        ema = null
    }
}

private const val RHYTHM_BOUNDARY_SCALE = 1000
private const val PARTIAL_BOUNDARY_RESEED_MAX = 0.25
