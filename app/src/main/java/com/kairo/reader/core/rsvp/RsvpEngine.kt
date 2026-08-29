package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.splitTokenForRsvp
import com.kairo.reader.core.rsvp.analysis.RsvpTokenAnalysis
import com.kairo.reader.core.rsvp.analysis.analyzeExpandedTokens
import com.kairo.reader.core.rsvp.analysis.nextTokenAfter
import com.kairo.reader.core.rsvp.analysis.recordGivennessWord
import com.kairo.reader.core.rsvp.analysis.shouldKeepFullFocalDuration
import com.kairo.reader.core.rsvp.engine.BoundaryBefore
import com.kairo.reader.core.rsvp.engine.ContextState
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import com.kairo.reader.core.rsvp.engine.FLOW_EMA_ALPHA
import com.kairo.reader.core.rsvp.engine.FLOW_MAX_BOOST
import com.kairo.reader.core.rsvp.engine.FLOW_MAX_SLOWDOWN
import com.kairo.reader.core.rsvp.engine.FLOW_STRENGTH
import com.kairo.reader.core.rsvp.engine.FlowState
import com.kairo.reader.core.rsvp.engine.MAX_ANTICIPATORY_LANDING_BOOST
import com.kairo.reader.core.rsvp.engine.MIN_FOCAL_SUPPORT_COMPRESSION
import com.kairo.reader.core.rsvp.engine.MIN_FRAME_MS
import com.kairo.reader.core.rsvp.engine.OPENING_PUNCTUATION
import com.kairo.reader.core.rsvp.engine.PAGE_BREAK_RETENTION_BOOST
import com.kairo.reader.core.rsvp.engine.PARAGRAPH_BREAK_RETENTION_BOOST
import com.kairo.reader.core.rsvp.engine.PhraseContour
import com.kairo.reader.core.rsvp.engine.ProseState
import com.kairo.reader.core.rsvp.engine.RhythmState
import com.kairo.reader.core.rsvp.engine.SKIPPABLE_BOUNDARY_PUNCTUATION
import com.kairo.reader.core.rsvp.engine.applyBlinkSeparation
import com.kairo.reader.core.rsvp.engine.applySessionRamps
import com.kairo.reader.core.rsvp.engine.buildUnit
import com.kairo.reader.core.rsvp.engine.normalizedForPlayback
import com.kairo.reader.core.rsvp.segmentation.RsvpAtomStream
import com.kairo.reader.core.rsvp.segmentation.RsvpDialogueRole
import com.kairo.reader.core.rsvp.segmentation.RsvpDpSegmenter
import com.kairo.reader.core.rsvp.segmentation.RsvpSegmentationDecision
import com.kairo.reader.core.rsvp.text.boundaryBefore
import com.kairo.reader.core.rsvp.text.boundaryBeforeForPunctuation
import com.kairo.reader.core.rsvp.text.breakMarkerToken
import com.kairo.reader.core.rsvp.text.findFirstWordCursor
import com.kairo.reader.core.rsvp.text.findPrevWord
import com.kairo.reader.core.rsvp.text.isCurrencyPrefixPunctuation
import com.kairo.reader.core.rsvp.text.isOpeningPunctuation
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTier
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTimingPolicy
import com.kairo.reader.core.rsvp.timing.RsvpUnitTimingInput
import com.kairo.reader.core.rsvp.timing.computeUnitDurationMs
import com.kairo.reader.core.rsvp.timing.pageBreakBasePauseMs
import com.kairo.reader.core.rsvp.timing.paragraphBreakBasePauseMs
import com.kairo.reader.core.rsvp.timing.pauseScale
import com.kairo.reader.core.rsvp.timing.speedStrength
import kotlin.math.max

interface RsvpEngine {
    fun generateFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
    ): List<RsvpFrame>

    fun generateFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
        options: RsvpGenerationOptions,
    ): List<RsvpFrame> = generateFrames(tokens, startIndex, config)
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
    override fun generateFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
    ): List<RsvpFrame> =
        generateFrames(
            tokens = tokens,
            startIndex = startIndex,
            config = config,
            options = RsvpGenerationOptions.LEGACY,
        )

    override fun generateFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
        options: RsvpGenerationOptions,
    ): List<RsvpFrame> =
        generateFramesWithNormalizedConfig(
            tokens = tokens,
            startIndex = startIndex,
            config = config.normalizedForPlayback(),
            options = options,
        )
}

private data class RsvpGenerationContext(
    val tokens: List<Token>,
    val expanded: List<ExpandedToken>,
    val config: RsvpConfig,
    val options: RsvpGenerationOptions,
    val atomStream: RsvpAtomStream?,
    val analysis: RsvpTokenAnalysis,
    val frames: MutableList<RsvpFrame>,
    val state: ContextState,
    val rhythm: RhythmState,
    val flow: FlowState,
    val prose: ProseState,
)

private fun generateFramesWithNormalizedConfig(
    tokens: List<Token>,
    startIndex: Int,
    config: RsvpConfig,
    options: RsvpGenerationOptions,
): List<RsvpFrame> {
    if (tokens.isEmpty()) return emptyList()

    val analysisStartIndex = resolveAnalysisStartIndex(tokens, startIndex)
    val expanded = buildExpandedTokens(tokens, analysisStartIndex, config)
    val cursor = resolveFirstPlaybackCursor(expanded, startIndex) ?: return emptyList()
    val context =
        RsvpGenerationContext(
            tokens = tokens,
            expanded = expanded,
            config = config,
            options = options,
            atomStream =
            if (options.usesScoredSegmentation(config)) {
                RsvpAtomStream.build(
                    expandedTokens = expanded,
                    languagePolicy = options.languagePolicy,
                    useDialogueDetection = config.useDialogueDetection,
                    useParentheticalAside = config.useParentheticalAside,
                )
            } else {
                null
            },
            analysis = analyzeExpandedTokens(expanded, config),
            frames = mutableListOf(),
            state = ContextState(),
            rhythm = createRhythmState(config),
            flow = createFlowState(),
            prose = createProseState(expanded, cursor, config),
        )

    var nextCursor = cursor
    while (nextCursor < expanded.size) {
        nextCursor = context.appendNextFrame(nextCursor) ?: break
    }

    applySessionRamps(context.frames, config)
    applyBlinkSeparation(context.frames, config)
    return context.frames
}

private fun buildExpandedTokens(
    tokens: List<Token>,
    analysisStartIndex: Int,
    config: RsvpConfig,
): List<ExpandedToken> =
    tokens
        .subList(analysisStartIndex, tokens.size)
        .flatMapIndexed { index, token ->
            var sourceCursor = 0
            splitTokenForRsvp(
                token = token,
                maxChunkLength = config.maxChunkLength,
                subwordChunkPauseMs = config.subwordChunkPauseMs,
            ).map { splitToken ->
                val sourceStart =
                    if (splitToken.text == token.text) {
                        0
                    } else {
                        token.text.indexOf(splitToken.text, startIndex = sourceCursor)
                            .takeIf { it >= 0 }
                            ?: token.text.indexOf(splitToken.text).coerceAtLeast(0)
                    }
                val sourceEnd =
                    (sourceStart + splitToken.text.length).coerceIn(sourceStart, token.text.length)
                sourceCursor = sourceEnd
                ExpandedToken(
                    token = splitToken,
                    originalIndex = analysisStartIndex + index,
                    expandedIndex = -1,
                    sourceCharacterStart = sourceStart,
                    sourceCharacterEndExclusive = sourceEnd,
                )
            }
        }.mapIndexed { expandedIndex, expandedToken ->
            expandedToken.copy(expandedIndex = expandedIndex)
        }

private fun resolveFirstPlaybackCursor(
    expanded: List<ExpandedToken>,
    startIndex: Int,
): Int? {
    if (expanded.isEmpty()) return null

    val startCursor = expanded.indexOfFirst { it.originalIndex >= startIndex }
    val fallbackCursor =
        expanded.indexOfLast { it.originalIndex <= startIndex }
            .coerceAtLeast(0)
    val cursor =
        (if (startCursor == -1) fallbackCursor else startCursor)
            .coerceIn(0, expanded.lastIndex)
    val firstWordCursor = findFirstWordCursor(expanded, cursor)
    if (firstWordCursor >= expanded.size) return null
    return includeLeadingOpeningPunctuation(expanded, firstWordCursor, startIndex)
}

private fun includeLeadingOpeningPunctuation(
    expanded: List<ExpandedToken>,
    initialCursor: Int,
    startIndex: Int,
): Int {
    var cursor = initialCursor
    while (cursor > 0 && shouldIncludePreviousOpeningPunctuation(expanded, cursor, startIndex)) {
        cursor--
    }
    return cursor
}

private fun shouldIncludePreviousOpeningPunctuation(
    expanded: List<ExpandedToken>,
    cursor: Int,
    startIndex: Int,
): Boolean {
    val prevExpandedToken = expanded[cursor - 1]
    val prevToken = prevExpandedToken.token
    val nextToken = expanded.getOrNull(cursor)?.token
    val isCurrencyPrefix = isCurrencyPrefixPunctuation(prevToken, nextToken)
    if (prevExpandedToken.originalIndex < startIndex && !isCurrencyPrefix) return false
    val ch = prevToken.text.firstOrNull() ?: return false
    return prevToken.type == TokenType.PUNCTUATION &&
        (ch == '"' || ch in OPENING_PUNCTUATION || isCurrencyPrefix)
}

private fun createRhythmState(config: RsvpConfig): RhythmState =
    RhythmState(
        smoothingAlpha = config.smoothingAlpha,
        maxSpeedupFactor = config.maxSpeedupFactor,
        maxSlowdownFactor = config.maxSlowdownFactor,
    )

private fun createFlowState(): FlowState =
    FlowState(
        alpha = FLOW_EMA_ALPHA,
        maxBoost = FLOW_MAX_BOOST,
        maxSlowdown = FLOW_MAX_SLOWDOWN,
        strength = FLOW_STRENGTH,
    )

private fun createProseState(
    expanded: List<ExpandedToken>,
    playbackCursor: Int,
    config: RsvpConfig,
): ProseState {
    val prose = ProseState()
    var previousWord: Token? = null
    val endExclusive = playbackCursor.coerceIn(0, expanded.size)

    for (index in 0 until endExclusive) {
        val token = expanded[index].token
        when (token.type) {
            TokenType.WORD -> {
                prose.onWordShown()
                if (config.useAdaptiveTiming) {
                    recordGivennessWord(token, prose)
                }
                previousWord = token
            }
            TokenType.PUNCTUATION -> {
                val tier =
                    RsvpPunctuationTimingPolicy.resolveTier(
                        token = token,
                        prevWord = previousWord,
                        nextToken = nextTokenAfter(expanded, index),
                    )
                if (tier == RsvpPunctuationTier.SENTENCE_END) {
                    prose.onSentenceEnd()
                }
            }
            TokenType.PARAGRAPH_BREAK -> {
                prose.onParagraphBreak()
                previousWord = null
            }
            TokenType.PAGE_BREAK -> {
                prose.onPageBreak()
                previousWord = null
            }
        }
    }

    return prose
}

private fun RsvpGenerationContext.appendNextFrame(cursor: Int): Int? {
    val cursorToken = expanded[cursor].token
    return if (cursorToken.isBreakToken()) {
        appendBreakFrame(cursor)
    } else {
        appendReadingFrame(cursor)
    }
}

private fun RsvpGenerationContext.appendBreakFrame(cursor: Int): Int? {
    val nextWordCursor = findFirstWordCursor(expanded, cursor + 1)
    if (nextWordCursor >= expanded.size) return null

    val cursorToken = expanded[cursor].token
    frames +=
        RsvpFrame(
            tokens = listOf(breakMarkerToken(cursorToken.type)),
            durationMs = breakFrameDurationMs(cursorToken, config),
            originalTokenIndex = expanded[cursor].originalIndex,
            resumeCursor = expanded[cursor].expandedIndex,
            nextOriginalTokenIndex = expanded[nextWordCursor].originalIndex,
            displayOriginalStartIndex = expanded[cursor].originalIndex,
            displayOriginalEndExclusive = expanded[cursor].originalIndex + 1,
            displayOriginalStartCharacterOffset = expanded[cursor].sourceCharacterStart,
            displayOriginalEndCharacterOffset = expanded[cursor].sourceCharacterEndExclusive,
        )
    rhythm.reset()
    flow.reset()
    if (cursorToken.type == TokenType.PAGE_BREAK) {
        prose.onPageBreak()
    } else {
        prose.onParagraphBreak()
    }
    return cursor + 1
}

private fun breakFrameDurationMs(
    token: Token,
    config: RsvpConfig,
): Long {
    val msPerWord = config.tempoMsPerWord.toDouble()
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
    val paragraphBase = paragraphBreakBasePauseMs(config)
    val base =
        when (token.type) {
            TokenType.PAGE_BREAK ->
                max(
                    pageBreakBasePauseMs(config) * pagePauseScale,
                    pageBreakBasePauseMs(config) * config.minPauseScale,
                )
            TokenType.PARAGRAPH_BREAK ->
                max(paragraphBase * paragraphPauseScale, paragraphBase * config.minPauseScale)
            else -> 0.0
        }
    val extraPause =
        token.pauseAfterMs.coerceAtLeast(0L).toDouble() *
            when (token.type) {
                TokenType.PAGE_BREAK -> pagePauseScale
                else -> paragraphPauseScale
            }
    return (base + extraPause).toLong().coerceAtLeast(MIN_FRAME_MS)
}

private fun RsvpGenerationContext.appendReadingFrame(cursor: Int): Int? {
    val wordCursor = findFirstWordCursor(expanded, cursor)
    if (wordCursor >= expanded.size) return null
    val frameStartCursor = cursor
    val scoredSelection = selectScoredFrame(cursor)
    val contextBefore = state.snapshot()
    val (frameTokens, frameOriginalIndex, nextCursor) =
        buildUnit(
            expandedTokens = expanded,
            startCursor = cursor,
            config = config,
            state = state,
            selectedWordCursors = scoredSelection?.selectedWordCursors,
        )

    val durationMs =
        computeUnitDurationMs(
            RsvpUnitTimingInput(
                frameTokens = frameTokens,
                config = config,
                contextBefore = contextBefore,
                rhythm = rhythm,
                flow = flow,
                prevToken = expanded.getOrNull(cursor - 1)?.token,
                prevWord = findPrevWord(expanded, beforeIndex = cursor),
                nextToken = expanded.getOrNull(nextCursor)?.token,
                nextWord = expanded.getOrNull(findFirstWordCursor(expanded, nextCursor))?.token,
                boundaryBefore = boundaryBefore(expanded, wordCursor),
                focalSuppression = focalSuppression(wordCursor),
                anticipatoryLanding = anticipatoryLanding(wordCursor),
                emDashAside = config.useParentheticalAside && wordCursor in analysis.emDashAsideIndices,
                phraseContour = analysis.phraseContours[wordCursor] ?: PhraseContour.NONE,
                prose = prose,
                pairedEmDashInUnit =
                (frameStartCursor until nextCursor).any { it in analysis.pairedEmDashIndices },
                afterPairedEmDash = followsPairedEmDash(wordCursor),
                rhythmBoundaryStrengthMilli =
                scoredSelection?.boundaryStrengthBeforeMilli ?: 0,
                explicitSpeakerTag =
                scoredSelection?.dialogueRole == RsvpDialogueRole.SPEAKER_TAG,
            ),
        )

    frames +=
        RsvpFrame(
            tokens = frameTokens,
            durationMs = durationMs,
            originalTokenIndex = frameOriginalIndex,
            resumeCursor = expanded[frameStartCursor].expandedIndex,
            nextOriginalTokenIndex = nextOriginalTokenIndex(nextCursor),
            displayOriginalStartIndex = expanded[frameStartCursor].originalIndex,
            displayOriginalEndExclusive =
            displayOriginalEndExclusive(
                frameStartCursor = frameStartCursor,
                nextCursor = nextCursor,
            ),
            displayOriginalStartCharacterOffset =
            expanded[frameStartCursor].sourceCharacterStart,
            displayOriginalEndCharacterOffset =
            expanded.getOrNull(nextCursor - 1)?.sourceCharacterEndExclusive,
        )

    return consumeContextPunctuation(nextCursor)
}

private fun RsvpGenerationContext.selectScoredFrame(cursor: Int): RsvpSegmentationDecision? {
    if (!options.usesScoredSegmentation(config)) return null
    val atoms = atomStream ?: return null
    return RsvpDpSegmenter
        .selectWordCount(
            atomStream = atoms,
            startCursor = cursor,
            config = config,
            languagePolicy = options.languagePolicy,
        )
}

/**
 * Whether the boundary punctuation directly before this word is the closing (or opening) dash of
 * a paired aside — mirrors the back-scan in [boundaryBefore], skipping quotes/brackets.
 */
private fun RsvpGenerationContext.followsPairedEmDash(wordCursor: Int): Boolean {
    var cursor = wordCursor - 1
    while (cursor >= 0) {
        val token = expanded[cursor].token
        if (token.type != TokenType.PUNCTUATION) return false
        if (cursor in analysis.pairedEmDashIndices) return true
        val ch = token.text.firstOrNull() ?: return false
        if (ch !in SKIPPABLE_BOUNDARY_PUNCTUATION) return false
        cursor--
    }
    return false
}

private fun RsvpGenerationContext.focalSuppression(wordCursor: Int): Double {
    val word = expanded[wordCursor].token
    return if (config.useFocalStress &&
        wordCursor !in analysis.focalWordIndices &&
        !shouldKeepFullFocalDuration(word)
    ) {
        val target = config.focalSupportCompression.coerceIn(MIN_FOCAL_SUPPORT_COMPRESSION, 1.0)
        val compressionStrength = speedStrength(config.tempoMsPerWord.toDouble())
        1.0 + ((target - 1.0) * compressionStrength)
    } else {
        1.0
    }
}

private fun RsvpGenerationContext.anticipatoryLanding(wordCursor: Int): Double =
    if (config.useAnticipatoryLanding && wordCursor in analysis.landingWordIndices) {
        config.anticipatoryLandingBoost.coerceIn(1.0, MAX_ANTICIPATORY_LANDING_BOOST)
    } else {
        1.0
    }

private fun RsvpGenerationContext.nextOriginalTokenIndex(nextCursor: Int): Int {
    val nextFrameWordCursor = findFirstWordCursor(expanded, nextCursor)
    return if (nextFrameWordCursor < expanded.size) {
        expanded[nextFrameWordCursor].originalIndex
    } else {
        tokens.size
    }
}

private fun RsvpGenerationContext.displayOriginalEndExclusive(
    frameStartCursor: Int,
    nextCursor: Int,
): Int =
    (frameStartCursor until nextCursor)
        .maxOfOrNull { cursor -> expanded[cursor].originalIndex + 1 }
        ?: (expanded.getOrNull(frameStartCursor)?.originalIndex?.plus(1) ?: tokens.size)

private fun RsvpGenerationContext.consumeContextPunctuation(cursor: Int): Int {
    var nextCursor = cursor
    while (nextCursor < expanded.size && shouldConsumeContextToken(nextCursor)) {
        state.consume(expanded[nextCursor].token)
        nextCursor++
    }
    return nextCursor
}

private fun RsvpGenerationContext.shouldConsumeContextToken(cursor: Int): Boolean {
    val token = expanded[cursor].token
    if (token.type == TokenType.WORD ||
        token.type == TokenType.PARAGRAPH_BREAK ||
        token.type == TokenType.PAGE_BREAK
    ) {
        return false
    }
    return token.type != TokenType.PUNCTUATION ||
        !isOpeningPunctuation(
            token = token,
            state = state,
            nextToken = expanded.getOrNull(cursor + 1)?.token,
        )
}

private fun Token.isBreakToken(): Boolean =
    type == TokenType.PARAGRAPH_BREAK || type == TokenType.PAGE_BREAK

internal fun resolveAnalysisStartIndex(
    tokens: List<Token>,
    startIndex: Int,
): Int {
    if (tokens.isEmpty() || startIndex <= 0) return 0

    val safeStartIndex = startIndex.coerceIn(0, tokens.lastIndex)
    val lowerBound = (safeStartIndex - START_CONTEXT_TOKEN_LIMIT).coerceAtLeast(0)
    var cursor = safeStartIndex - 1
    while (cursor >= lowerBound) {
        val token = tokens[cursor]
        when (token.type) {
            TokenType.PAGE_BREAK,
            TokenType.PARAGRAPH_BREAK -> return cursor + 1
            TokenType.PUNCTUATION -> {
                val boundary =
                    boundaryBeforeForPunctuation(
                        token = token,
                        prevWord = findPreviousTokenWord(tokens, beforeIndex = cursor),
                        nextToken = tokens.getOrNull(cursor + 1),
                    )
                if (boundary != BoundaryBefore.NONE) return cursor + 1
            }
            TokenType.WORD -> Unit
        }
        cursor--
    }
    return lowerBound
}

private fun findPreviousTokenWord(
    tokens: List<Token>,
    beforeIndex: Int,
): Token? {
    var cursor = beforeIndex - 1
    while (cursor >= 0) {
        val token = tokens[cursor]
        if (token.type == TokenType.WORD) return token
        cursor--
    }
    return null
}

private const val START_CONTEXT_TOKEN_LIMIT = 240
