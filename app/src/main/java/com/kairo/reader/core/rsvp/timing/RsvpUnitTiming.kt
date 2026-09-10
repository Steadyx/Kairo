@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.core.rsvp.timing

import com.kairo.reader.core.linguistics.ClauseDetector
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpConfigConstraints
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.analysis.RsvpThoughtCue
import com.kairo.reader.core.rsvp.analysis.contextShapingMultiplier
import com.kairo.reader.core.rsvp.analysis.emphasisMultiplier
import com.kairo.reader.core.rsvp.analysis.frameDifficulty
import com.kairo.reader.core.rsvp.analysis.givennessGlideMultiplier
import com.kairo.reader.core.rsvp.analysis.isEmDashChar
import com.kairo.reader.core.rsvp.analysis.multiWordPenalty
import com.kairo.reader.core.rsvp.analysis.phraseBoundaryShapeMultiplier
import com.kairo.reader.core.rsvp.analysis.phraseContourMultiplier
import com.kairo.reader.core.rsvp.analysis.prosodyMultiplier
import com.kairo.reader.core.rsvp.analysis.speakerTagMultiplier
import com.kairo.reader.core.rsvp.analysis.terminalWordMultiplier
import com.kairo.reader.core.rsvp.analysis.transitionHoldMs
import com.kairo.reader.core.rsvp.engine.BoundaryBefore
import com.kairo.reader.core.rsvp.engine.CLAUSE_LEAD_HOLD_FRACTION
import com.kairo.reader.core.rsvp.engine.ContextSnapshot
import com.kairo.reader.core.rsvp.engine.DEFAULT_CLAUSE_PAUSE_FACTOR
import com.kairo.reader.core.rsvp.engine.DIALOGUE_ENTRY_BOOST
import com.kairo.reader.core.rsvp.engine.EM_DASH_ASIDE_CONTOUR_FACTOR
import com.kairo.reader.core.rsvp.engine.EM_DASH_ASIDE_PAUSE_FACTOR
import com.kairo.reader.core.rsvp.engine.EM_DASH_INTERRUPTION_PAUSE_FACTOR
import com.kairo.reader.core.rsvp.engine.FlowState
import com.kairo.reader.core.rsvp.engine.MIN_FRAME_MS
import com.kairo.reader.core.rsvp.engine.PAGE_BREAK_RETENTION_BOOST
import com.kairo.reader.core.rsvp.engine.PARAGRAPH_BREAK_RETENTION_BOOST
import com.kairo.reader.core.rsvp.engine.PhraseContour
import com.kairo.reader.core.rsvp.engine.ProseState
import com.kairo.reader.core.rsvp.engine.QUOTE_TRANSITION_HOLD_FRACTION
import com.kairo.reader.core.rsvp.engine.RhythmState
import com.kairo.reader.core.rsvp.engine.SENTENCE_START_HOLD_FRACTION
import com.kairo.reader.core.rsvp.text.boundaryBeforeForPunctuation
import com.kairo.reader.core.rsvp.text.isHardBoundary
import com.kairo.reader.core.rsvp.text.isQuoteChar
import com.kairo.reader.core.rsvp.text.shouldSkipPunctuationPause
import com.kairo.reader.core.rsvp.text.updateParentheticalDepthAfterPunctuation
import kotlin.math.max

internal data class RsvpUnitTimingInput(
    val frameTokens: List<Token>,
    val config: RsvpConfig,
    val contextBefore: ContextSnapshot,
    val rhythm: RhythmState,
    val flow: FlowState,
    val prevToken: Token?,
    val prevWord: Token?,
    val nextToken: Token?,
    val nextWord: Token?,
    val boundaryBefore: BoundaryBefore,
    val focalSuppression: Double = 1.0,
    val anticipatoryLanding: Double = 1.0,
    val emDashAside: Boolean = false,
    val phraseContour: PhraseContour = PhraseContour.NONE,
    val prose: ProseState? = null,
    val pairedEmDashInUnit: Boolean = false,
    val afterPairedEmDash: Boolean = false,
    val rhythmBoundaryStrengthMilli: Int = 0,
    val explicitSpeakerTag: Boolean = false,
    val thoughtCues: List<RsvpThoughtCue> = emptyList(),
)

private class RsvpUnitTimingContext(input: RsvpUnitTimingInput) {
    val frameTokens = input.frameTokens
    val config = input.config
    val contextBefore = input.contextBefore
    val rhythm = input.rhythm
    val flow = input.flow
    val prevToken = input.prevToken
    val prevWord = input.prevWord
    val nextToken = input.nextToken
    val nextWord = input.nextWord
    val boundaryBefore = input.boundaryBefore
    val focalSuppression = input.focalSuppression
    val anticipatoryLanding = input.anticipatoryLanding
    val emDashAside = input.emDashAside
    val phraseContour = input.phraseContour
    val prose = input.prose
    val pairedEmDashInUnit = input.pairedEmDashInUnit
    val afterPairedEmDash = input.afterPairedEmDash
    val rhythmBoundaryStrengthMilli = input.rhythmBoundaryStrengthMilli
    val explicitSpeakerTag = input.explicitSpeakerTag
    val thoughtCues = input.thoughtCues
    val msPerWord = config.tempoMsPerWord.toDouble()
    val pauseScale = pauseScale(msPerWord, config)
    val clausePauseScale =
        pauseScale(
            msPerWord = msPerWord,
            config = config,
            extraRetention = CLAUSE_PUNCTUATION_RETENTION_BOOST,
        )
    val sentencePauseScale =
        pauseScale(
            msPerWord = msPerWord,
            config = config,
            extraRetention = STRONG_PUNCTUATION_RETENTION_BOOST,
        )
    val paragraphPauseScale =
        pauseScale(
            msPerWord = msPerWord,
            config = config,
            extraRetention = PARAGRAPH_BREAK_RETENTION_BOOST,
        )
    val pagePauseScale =
        pauseScale(
            msPerWord = msPerWord,
            config = config,
            extraRetention = PAGE_BREAK_RETENTION_BOOST,
        )

    val words = frameTokens.filter { it.type == TokenType.WORD }
    val paragraphBreaks = frameTokens.count { it.type == TokenType.PARAGRAPH_BREAK }
    val pageBreaks = frameTokens.count { it.type == TokenType.PAGE_BREAK }
    val firstWordIndex = frameTokens.indexOfFirst { it.type == TokenType.WORD }
    val speedStrength = speedStrength(msPerWord)
    val expressionStrength = expressionStrength(msPerWord)
    val prosodyStrength =
        if (config.useProsodyPacing) {
            config.prosodyStrength.coerceIn(
                RsvpConfigConstraints.MIN_PROSODY_STRENGTH,
                RsvpConfigConstraints.MAX_PROSODY_STRENGTH,
            )
        } else {
            0.0
        }
    val firstWord = words.firstOrNull()
    val boundaryForBoost =
        if (boundaryBefore == BoundaryBefore.NONE &&
            prevToken?.type == TokenType.PUNCTUATION &&
            boundaryBeforeForPunctuation(
                token = prevToken,
                prevWord = prevWord,
                nextToken = firstWord ?: nextToken,
            ) != BoundaryBefore.NONE
        ) {
            boundaryBeforeForPunctuation(
                token = prevToken,
                prevWord = prevWord,
                nextToken = firstWord ?: nextToken,
            )
        } else {
            boundaryBefore
        }
    val startBoost =
        startBoostMultiplier(msPerWord = msPerWord, boundaryBefore = boundaryForBoost)
    val clauseConfigStrength = (
        (config.clausePauseFactor - 1.0) /
            (DEFAULT_CLAUSE_PAUSE_FACTOR - 1.0)
        ).coerceIn(0.0, 2.0)
    val dialogueEntryBoost = 1.0 + (DIALOGUE_ENTRY_BOOST * expressionStrength)

    // Phrase-arc shaping at grammatical boundaries: single-word, punctuation-free frames only,
    // mirroring the breath in transitionHoldMs (punctuation frames are shaped by the contour
    // machinery instead).
    val phraseShapeMultiplier =
        if (config.useProsodyPacing &&
            words.size == 1 &&
            firstWord != null &&
            frameTokens.none { it.type == TokenType.PUNCTUATION }
        ) {
            phraseBoundaryShapeMultiplier(
                word = firstWord,
                prevWord = prevWord,
                nextWord = nextWord,
                boundaryBefore = boundaryForBoost,
                speedStrength = expressionStrength,
                prosodyStrength = prosodyStrength,
            )
        } else {
            1.0
        }
    val speakerTagMultiplier =
        speakerTagMultiplier(
            wordsInFrame = words,
            prevWord = prevWord,
            nextWord = nextWord,
            config = config,
            speedStrength = speedStrength,
            explicitSpeakerTag = explicitSpeakerTag,
        )
}

private data class FrameWordTiming(
    val duration: Double,
    val expressionMs: Double,
    val enteredDialogue: Boolean,
    val exitedDialogue: Boolean,
    val sawParentheticalWord: Boolean,
)

private class FrameWordContext(contextBefore: ContextSnapshot) {
    var parentheticalDepth = contextBefore.parentheticalDepth
    var inDialogue = contextBefore.inDialogue
    var enteredDialogue = false
    var exitedDialogue = false
    var sawParentheticalWord = false
    var wordOrdinal = 0
    var expressionMs = 0.0
}

private fun computeFrameWordTiming(context: RsvpUnitTimingContext): FrameWordTiming {
    val state = FrameWordContext(context.contextBefore)
    var duration = 0.0
    context.frameTokens.forEachIndexed { index, token ->
        when (token.type) {
            TokenType.PUNCTUATION -> state.acceptPunctuation(token, context.config)
            TokenType.WORD -> {
                duration += wordDurationContribution(context, state, index, token)
                context.prose?.onWordShown()
                if (token.pauseAfterMs > 0L) duration += token.pauseAfterMs * context.pauseScale
            }
            else -> Unit
        }
    }
    return FrameWordTiming(
        duration = duration,
        expressionMs = state.expressionMs,
        enteredDialogue = state.enteredDialogue,
        exitedDialogue = state.exitedDialogue,
        sawParentheticalWord = state.sawParentheticalWord,
    )
}

private fun FrameWordContext.acceptPunctuation(
    token: Token,
    config: RsvpConfig,
) {
    val character = token.text.firstOrNull()
    val wasInDialogue = inDialogue
    parentheticalDepth =
        when (character) {
            '(', '[', '{' -> parentheticalDepth + 1
            ')', ']', '}' -> max(0, parentheticalDepth - 1)
            else -> parentheticalDepth
        }
    inDialogue = token.isDialogue
    if (config.useDialogueDetection && character?.let(::isQuoteChar) == true) {
        enteredDialogue = enteredDialogue || (!wasInDialogue && inDialogue)
        exitedDialogue = exitedDialogue || (wasInDialogue && !inDialogue)
    }
}

private fun wordDurationContribution(
    context: RsvpUnitTimingContext,
    state: FrameWordContext,
    index: Int,
    token: Token,
): Double {
    val config = context.config
    val thoughtCue = context.thoughtCues.getOrNull(state.wordOrdinal++)
    val protectedEmphasis = config.useProsodyPacing && thoughtCue?.protectedEmphasis == true
    val inAside = config.useParentheticalAside && (state.parentheticalDepth > 0 || context.emDashAside)
    val dialogueMultiplier =
        if (config.useDialogueDetection && state.inDialogue) {
            contextShapingMultiplier(config.dialogueMultiplier, context.expressionStrength)
        } else {
            1.0
        }
    val parentheticalMultiplier =
        when {
            inAside ->
                config.parentheticalAsideMultiplier.coerceIn(
                    RsvpConfigConstraints.MIN_PARENTHETICAL_ASIDE_MULTIPLIER,
                    RsvpConfigConstraints.MAX_PARENTHETICAL_ASIDE_MULTIPLIER,
                )
            state.parentheticalDepth > 0 -> config.parentheticalMultiplier
            else -> 1.0
        }
    if (state.parentheticalDepth > 0 && !inAside) state.sawParentheticalWord = true
    val nextWordText =
        context.frameTokens
            .subList(index + 1, context.frameTokens.size)
            .firstOrNull { it.type == TokenType.WORD }
            ?.text
            ?: context.nextWord?.text
    val previousWordText =
        context.frameTokens
            .subList(0, index)
            .lastOrNull { it.type == TokenType.WORD }
            ?.text
            ?: context.prevWord?.text
    val clauseMultiplier =
        if (config.useClausePausing) {
            val raw = ClauseDetector.getClausePauseFactor(token.text, nextWordText)
            1.0 + ((raw - 1.0) * context.speedStrength * context.clauseConfigStrength)
        } else {
            1.0
        }
    val terminalMultiplier =
        terminalWordMultiplier(
            wordIndex = index,
            word = token,
            frameTokens = context.frameTokens,
            nextToken = context.nextToken,
            speedStrength = context.speedStrength,
            emDashContourScale = if (context.pairedEmDashInUnit) EM_DASH_ASIDE_CONTOUR_FACTOR else 1.0,
        )
    val emphasis =
        emphasisMultiplier(token, index == context.firstWordIndex, context.boundaryBefore, context.speedStrength)
    val prosody =
        prosodyMultiplier(
            token,
            previousWordText,
            nextWordText,
            index == context.firstWordIndex,
            context.boundaryBefore,
            context.expressionStrength,
            context.prosodyStrength,
        )
    val dialogueEntry =
        if (config.useDialogueDetection &&
            !context.contextBefore.inDialogue &&
            index == context.firstWordIndex &&
            token.isDialogue
        ) {
            context.dialogueEntryBoost
        } else {
            1.0
        }
    val givenness =
        if (config.useAdaptiveTiming && context.prose != null) {
            givennessGlideMultiplier(token, context.prose, context.speedStrength)
        } else {
            1.0
        }
    val duration =
        wordDurationMs(token, context.msPerWord, config) *
            parentheticalMultiplier *
            dialogueMultiplier *
            context.speakerTagMultiplier
    val expression = coordinateRsvpExpression(
        if (index == context.firstWordIndex) context.startBoost else 1.0,
        clauseMultiplier,
        terminalMultiplier,
        emphasis,
        if (protectedEmphasis) max(1.0, prosody) else prosody,
        dialogueEntry,
        if (protectedEmphasis) 1.0 else context.focalSuppression,
        context.anticipatoryLanding,
        if (config.useProsodyPacing) phraseContourMultiplier(context.phraseContour, context.expressionStrength) else 1.0,
        context.phraseShapeMultiplier,
        if (protectedEmphasis) 1.0 else givenness,
        if (protectedEmphasis) 1.0 + (THOUGHT_EMPHASIS * context.prosodyStrength) else 1.0,
    )
    val baseline = max(duration, wordFloorMs(token, config).toDouble())
    state.expressionMs += baseline * (expression - 1.0)
    return baseline
}

/** Bound overlapping automatic cues while leaving explicit difficulty and pause settings intact. */
internal fun coordinateRsvpExpression(vararg multipliers: Double): Double =
    (1.0 + multipliers.sumOf { it - 1.0 }).coerceIn(MIN_EXPRESSION, MAX_EXPRESSION)

private const val MIN_EXPRESSION = 0.82
private const val MAX_EXPRESSION = 1.4
private const val THOUGHT_EMPHASIS = 0.08

private class FramePunctuationContext(contextBefore: ContextSnapshot) {
    var parentheticalDepth = contextBefore.parentheticalDepth
    var inDialogue = contextBefore.inDialogue
}

private fun punctuationPauseDuration(context: RsvpUnitTimingContext): Double {
    val state = FramePunctuationContext(context.contextBefore)
    var duration = 0.0
    context.frameTokens.forEachIndexed { index, token ->
        when (token.type) {
            TokenType.WORD -> if (token.isDialogue) state.inDialogue = true
            TokenType.PUNCTUATION -> {
                val previousToken = context.frameTokens.getOrNull(index - 1)
                val nextToken = context.frameTokens.getOrNull(index + 1)
                val skip =
                    shouldSkipPunctuationPause(
                        token = token,
                        index = index,
                        firstWordIndex = context.firstWordIndex,
                        prevToken = previousToken,
                        nextToken = nextToken,
                    )
                if (!skip) duration += punctuationPauseForToken(context, state, index, token, nextToken)
                state.parentheticalDepth = updateParentheticalDepthAfterPunctuation(state.parentheticalDepth, token)
                state.inDialogue = token.isDialogue
            }
            else -> Unit
        }
    }
    return duration
}

private fun punctuationPauseForToken(
    context: RsvpUnitTimingContext,
    state: FramePunctuationContext,
    index: Int,
    token: Token,
    nextTokenInFrame: Token?,
): Double {
    val punctuationCharacter = token.text.firstOrNull()
    val previousWord =
        context.frameTokens.subList(0, index).lastOrNull { it.type == TokenType.WORD }
            ?: context.prevWord
    val nextWord =
        context.frameTokens.subList(index + 1, context.frameTokens.size).firstOrNull { it.type == TokenType.WORD }
            ?: context.nextToken
    val insideAside =
        context.config.useParentheticalAside &&
            (
                state.parentheticalDepth > 0 ||
                    context.emDashAside ||
                    punctuationCharacter in CLOSING_ASIDE_PUNCTUATION
                )
    var pause =
        punctuationPauseMs(
            token = token,
            prevWord = previousWord,
            nextToken = nextWord,
            msPerWord = context.msPerWord,
            config = context.config,
            insideAside = insideAside,
            insideDialogue = context.config.useDialogueDetection && (state.inDialogue || token.isDialogue),
        )
    if (punctuationCharacter?.let(::isEmDashChar) == true) {
        val tokenAfterDash = nextTokenInFrame ?: context.nextToken
        val interruption =
            tokenAfterDash == null ||
                tokenAfterDash.type == TokenType.PARAGRAPH_BREAK ||
                tokenAfterDash.type == TokenType.PAGE_BREAK ||
                (
                    tokenAfterDash.type == TokenType.PUNCTUATION &&
                        tokenAfterDash.text.firstOrNull()?.let(::isQuoteChar) == true
                    )
        pause *=
            when {
                interruption -> EM_DASH_INTERRUPTION_PAUSE_FACTOR
                context.pairedEmDashInUnit -> EM_DASH_ASIDE_PAUSE_FACTOR
                else -> 1.0
            }
    }
    val tier = RsvpPunctuationTimingPolicy.resolveTier(token, previousWord, nextWord)
    if (tier == RsvpPunctuationTier.SENTENCE_END && context.prose != null) {
        if (context.config.useAdaptiveTiming) pause *= sentenceWrapUpFactor(context.prose.wordsInSentence)
        context.prose.onSentenceEnd()
    }
    // Strong boundaries retain their floor after aside and sentence shaping.
    // Embedded quotes keep their deliberately lighter separator timing.
    val floor = if (tier == RsvpPunctuationTier.SENTENCE_END || tier == RsvpPunctuationTier.CLAUSE_BREAK) {
        RsvpPunctuationTimingPolicy.resolvePauseTiming(token, previousWord, nextWord, context.config).floorMs
    } else {
        0.0
    }
    return max(pause, floor)
}

private val CLOSING_ASIDE_PUNCTUATION = setOf(')', ']', '}')

internal data class RsvpUnitTiming(val durationMs: Long, val punctuationHoldMs: Long)

internal fun computeUnitDurationMs(input: RsvpUnitTimingInput): Long = computeUnitTiming(input).durationMs

internal fun computeUnitTiming(input: RsvpUnitTimingInput): RsvpUnitTiming =
    with(RsvpUnitTimingContext(input)) {
        val wordTiming = computeFrameWordTiming(this)
        var duration = wordTiming.duration

        val transitionHold =
            transitionHoldMs(
                frameTokens = frameTokens,
                firstWord = firstWord,
                nextWord = nextWord,
                speedStrength = speedStrength,
                prosodyStrength = prosodyStrength,
            )
        if (transitionHold > 0.0) {
            duration += transitionHold
        }

        duration *= multiWordPenalty(words.size)

        // Apply flow and rhythm smoothing to word duration BEFORE adding punctuation pauses.
        // This ensures punctuation pauses are not reduced by the smoothing algorithms.
        val hardBoundary = isHardBoundary(frameTokens, nextToken)
        val difficulty = frameDifficulty(words)
        duration *=
            flow.apply(
                difficulty = difficulty,
                speedStrength = speedStrength,
                isBoundary = hardBoundary,
            )

        val smoothedWordDuration =
            rhythm.apply(
                rawMs = duration,
                isBoundary = hardBoundary,
                boundaryStrengthMilli = rhythmBoundaryStrengthMilli,
            )

        // Now add punctuation pauses on top of the smoothed word duration.
        // These pauses are intentionally NOT smoothed so they remain prominent.
        // Smooth the underlying beat, then restore the planned expression so the EMA cannot
        // flatten a contrast or drag its emphasis onto the following word.
        val expression =
            if (wordTiming.duration > 0.0) 1.0 + wordTiming.expressionMs / wordTiming.duration else 1.0
        var totalDuration = max(
            smoothedWordDuration * expression,
            words.sumOf { wordFloorMs(it, config).toDouble() },
        )
        var integrationHold = thoughtCues.sumOf { it.integrationHoldMs }
        if (words.isNotEmpty()) {
            when (boundaryForBoost) {
                BoundaryBefore.SENTENCE -> {
                    totalDuration +=
                        max(config.sentenceEndPauseMs, config.periodPauseMs) *
                        sentencePauseScale *
                        SENTENCE_START_HOLD_FRACTION
                }
                BoundaryBefore.CLAUSE -> {
                    val clauseHold = clauseStartHoldMs(config = config, pauseScale = clausePauseScale)
                    // Resuming the main clause after a closing aside dash is a pick-up, not a fresh
                    // clause start — keep it light so the aside reads as one dip.
                    totalDuration +=
                        if (afterPairedEmDash) clauseHold * EM_DASH_ASIDE_PAUSE_FACTOR else clauseHold
                }
                BoundaryBefore.PARAGRAPH, BoundaryBefore.PAGE, BoundaryBefore.NONE -> Unit
            }
            totalDuration += boundaryStartMicroHoldMs(
                msPerWord = msPerWord,
                speedStrength = speedStrength,
                boundaryBefore = boundaryForBoost,
            )
        }
        val punctuationHold = punctuationPauseDuration(this)
        totalDuration += punctuationHold
        if (config.usePunctuationLandingHold) {
            integrationHold = max(
                integrationHold,
                punctuationLandingHoldMs(
                    frameTokens = frameTokens,
                    nextToken = nextToken,
                    msPerWord = msPerWord,
                    speedStrength = speedStrength,
                ),
            )
        }
        if (paragraphBreaks > 0) {
            totalDuration += config.paragraphPauseMs * paragraphPauseScale * paragraphBreaks
        }
        if (pageBreaks > 0) {
            val base = pageBreakBasePauseMs(config)
            val floor = base * config.minPauseScale
            val scaled = base * pagePauseScale
            totalDuration += max(scaled, floor) * pageBreaks
        }
        if (paragraphBreaks == 0 && pageBreaks == 0) {
            totalDuration +=
                adaptiveHoldMs(
                    words = words,
                    difficulty = difficulty,
                    config = config,
                    speedStrength = speedStrength,
                    hardBoundary = hardBoundary,
                    nextWord = nextWord,
                    clauseConfigStrength = clauseConfigStrength,
                )
            if (!hardBoundary &&
                config.useClausePausing &&
                nextWord?.isClauseBoundary == true
            ) {
                integrationHold = max(
                    integrationHold,
                    config.commaPauseMs * pauseScale * CLAUSE_LEAD_HOLD_FRACTION * clauseConfigStrength,
                )
            }
        }
        // Landing, thought integration, and clause anticipation describe the same breath.
        // Preserve the strongest cue while explicit punctuation and difficulty holds remain additive.
        totalDuration += integrationHold
        if (config.useDialogueDetection && (wordTiming.enteredDialogue || wordTiming.exitedDialogue)) {
            val quoteHold = config.quotePauseMs * pauseScale * QUOTE_TRANSITION_HOLD_FRACTION
            if (wordTiming.enteredDialogue) totalDuration += quoteHold
            if (wordTiming.exitedDialogue) totalDuration += quoteHold
        }
        if (wordTiming.sawParentheticalWord) {
            totalDuration =
                max(
                    totalDuration,
                    smoothedWordDuration * config.parentheticalMultiplier.coerceAtLeast(1.0),
                )
            totalDuration += parentheticalHoldMs(msPerWord = msPerWord, config = config)
        }

        return RsvpUnitTiming(
            durationMs = totalDuration.toLong().coerceAtLeast(MIN_FRAME_MS),
            punctuationHoldMs = (punctuationHold + if (punctuationHold > 0.0) integrationHold else 0.0).toLong(),
        )
    }
