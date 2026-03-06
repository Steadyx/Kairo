@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "LongMethod",
    "LongParameterList",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "UnreachableCode",
)

package com.example.kairo.core.rsvp

import com.example.kairo.core.linguistics.ClauseDetector
import com.example.kairo.core.linguistics.DialogueAnalyzer
import com.example.kairo.core.model.BlinkMode
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFrame
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.effectiveBlinkMode
import com.example.kairo.core.model.isMidSentencePunctuation
import com.example.kairo.core.model.isSentenceEndingPunctuation
import com.example.kairo.core.model.splitTokenForRsvp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

interface RsvpEngine {
    fun generateFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
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
@Suppress("LargeClass", "TooManyFunctions")
class ComprehensionRsvpEngine : RsvpEngine {
    override fun generateFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
    ): List<RsvpFrame> {
        if (tokens.isEmpty()) return emptyList()

        val expanded =
            tokens.flatMapIndexed { index, token ->
                splitTokenForRsvp(
                    token = token,
                    maxChunkLength = config.maxChunkLength,
                    subwordChunkPauseMs = config.subwordChunkPauseMs,
                ).map { splitToken ->
                    ExpandedToken(splitToken, index, -1)
                }
            }.mapIndexed { expandedIndex, expandedToken ->
                expandedToken.copy(expandedIndex = expandedIndex)
            }

        val startCursor = expanded.indexOfFirst { it.originalIndex >= startIndex }
        val fallbackCursor =
            expanded.indexOfLast { it.originalIndex <= startIndex }
                .coerceAtLeast(0)
        var cursor = if (startCursor == -1) fallbackCursor else startCursor
        cursor = cursor.coerceIn(0, expanded.lastIndex)
        val firstWordCursor = findFirstWordCursor(expanded, cursor)
        if (firstWordCursor >= expanded.size) return emptyList()
        cursor = firstWordCursor
        while (cursor > 0) {
            val prevExpandedToken = expanded[cursor - 1]
            val prevToken = prevExpandedToken.token
            val nextToken = expanded.getOrNull(cursor)?.token
            val isCurrencyPrefix = isCurrencyPrefixPunctuation(prevToken, nextToken)
            if (prevExpandedToken.originalIndex < startIndex && !isCurrencyPrefix) break
            val ch = prevToken.text.firstOrNull() ?: break
            val isLeadingOpening =
                prevToken.type == TokenType.PUNCTUATION &&
                    (ch == '"' ||
                        ch in OPENING_PUNCTUATION ||
                        isCurrencyPrefix)
            if (!isLeadingOpening) break
            cursor--
        }

        val frames = mutableListOf<RsvpFrame>()

        val state = ContextState()
        val rhythm =
            RhythmState(
                smoothingAlpha = config.smoothingAlpha,
                maxSpeedupFactor = config.maxSpeedupFactor,
                maxSlowdownFactor = config.maxSlowdownFactor,
            )
        val flow =
            FlowState(
                alpha = FLOW_EMA_ALPHA,
                maxBoost = FLOW_MAX_BOOST,
                maxSlowdown = FLOW_MAX_SLOWDOWN,
                strength = FLOW_STRENGTH,
            )

        while (cursor < expanded.size) {
            val cursorToken = expanded[cursor].token
            if (cursorToken.type == TokenType.PARAGRAPH_BREAK ||
                cursorToken.type == TokenType.PAGE_BREAK
            ) {
                val nextWordCursor = findFirstWordCursor(expanded, cursor + 1)
                if (nextWordCursor >= expanded.size) break

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
                val paragraphFloor = paragraphBase * config.minPauseScale
                val pageFloor = pageBreakBasePauseMs(config) * config.minPauseScale
                val extraPause =
                    (cursorToken.pauseAfterMs.coerceAtLeast(0L).toDouble()) *
                        when (cursorToken.type) {
                            TokenType.PAGE_BREAK -> pagePauseScale
                            TokenType.PARAGRAPH_BREAK -> paragraphPauseScale
                            else -> paragraphPauseScale
                        }
                val durationMs =
                    when (cursorToken.type) {
                        TokenType.PAGE_BREAK -> max(
                            pageBreakBasePauseMs(config) * pagePauseScale,
                            pageFloor
                        ).toLong()
                        TokenType.PARAGRAPH_BREAK -> max(
                            paragraphBase * paragraphPauseScale,
                            paragraphFloor
                        ).toLong()
                        else -> 0L
                    }.let { base ->
                        (base + extraPause).toLong()
                    }.coerceAtLeast(MIN_FRAME_MS)

                frames +=
                    RsvpFrame(
                        tokens = listOf(breakMarkerToken(cursorToken.type)),
                        durationMs = durationMs,
                        originalTokenIndex = expanded[cursor].originalIndex,
                        resumeCursor = expanded[cursor].expandedIndex,
                    )
                rhythm.reset()
                flow.reset()
                cursor++
                continue
            }

            val contextBefore = state.snapshot()
            val wordCursor = findFirstWordCursor(expanded, cursor)
            if (wordCursor >= expanded.size) break
            val boundaryBefore = boundaryBefore(expanded, wordCursor)

            val frameStartCursor = cursor
            val (frameTokens, frameOriginalIndex, nextCursor) =
                buildUnit(
                    expandedTokens = expanded,
                    startCursor = cursor,
                    config = config,
                    state = state,
                )
            val prevTokenGlobal = expanded.getOrNull(cursor - 1)?.token
            val prevWordGlobal = findPrevWord(expanded, beforeIndex = cursor)
            val nextTokenGlobal = expanded.getOrNull(nextCursor)?.token
            val nextWordGlobal = expanded.getOrNull(
                findFirstWordCursor(expanded, nextCursor)
            )?.token
            cursor = nextCursor

            val durationMs =
                computeUnitDurationMs(
                    frameTokens = frameTokens,
                    config = config,
                    contextBefore = contextBefore,
                    rhythm = rhythm,
                    flow = flow,
                    prevToken = prevTokenGlobal,
                    prevWord = prevWordGlobal,
                    nextToken = nextTokenGlobal,
                    nextWord = nextWordGlobal,
                    boundaryBefore = boundaryBefore,
                )

            frames +=
                RsvpFrame(
                    tokens = frameTokens,
                    durationMs = durationMs,
                    originalTokenIndex = frameOriginalIndex,
                    resumeCursor = expanded[frameStartCursor].expandedIndex,
                )

            while (cursor < expanded.size &&
                expanded[cursor].token.type != TokenType.WORD &&
                expanded[cursor].token.type != TokenType.PARAGRAPH_BREAK &&
                expanded[cursor].token.type != TokenType.PAGE_BREAK &&
                !(
                    expanded[cursor].token.type == TokenType.PUNCTUATION &&
                        isOpeningPunctuation(
                            token = expanded[cursor].token,
                            state = state,
                            nextToken = expanded.getOrNull(cursor + 1)?.token,
                        )
                    )
            ) {
                state.consume(expanded[cursor].token)
                cursor++
            }
        }

        applySessionRamps(frames, config)
        applyBlinkSeparation(frames, config)
        return frames
    }

    private fun buildUnit(
        expandedTokens: List<ExpandedToken>,
        startCursor: Int,
        config: RsvpConfig,
        state: ContextState,
    ): UnitBuildResult {
        val unitTokens = mutableListOf<Token>()
        var cursor = startCursor.coerceIn(0, expandedTokens.lastIndex)
        var firstWordOriginalIndex: Int? = null

        // Allow opening punctuation right before the first word to be part of the unit.
        while (cursor < expandedTokens.size) {
            val token = expandedTokens[cursor].token
            val nextToken = expandedTokens.getOrNull(cursor + 1)?.token
            val isLeadingQuote =
                token.type == TokenType.PUNCTUATION &&
                    token.text.firstOrNull()?.let { isQuoteChar(it) } == true &&
                    nextToken?.type == TokenType.WORD
            if (token.type == TokenType.PUNCTUATION &&
                (isOpeningPunctuation(token, state, nextToken) || isLeadingQuote)
            ) {
                unitTokens += token
                state.consume(token)
                cursor++
            } else {
                break
            }
        }

        // First word is required.
        while (cursor < expandedTokens.size &&
            expandedTokens[cursor].token.type != TokenType.WORD
        ) {
            cursor++
        }
        val firstWord =
            expandedTokens.getOrNull(cursor)
                ?: return UnitBuildResult(unitTokens, startCursor, cursor)
        if (firstWord.token.type !=
            TokenType.WORD
        ) {
            return UnitBuildResult(unitTokens, startCursor, cursor)
        }
        unitTokens += firstWord.token
        state.consume(firstWord.token)
        firstWordOriginalIndex = firstWord.originalIndex
        cursor++

        // Optionally add more words for phrase chunking (only across "soft" boundaries).
        val maxWordsInUnit =
            if (config.enablePhraseChunking) {
                config.maxWordsPerUnit.coerceAtLeast(1)
            } else {
                config.maxWordsPerUnit.coerceAtLeast(1)
            }
        val maxCharsInUnit = config.maxCharsPerUnit.coerceAtLeast(1)
        if (config.enablePhraseChunking && maxWordsInUnit > 1) {
            var wordsInUnit = 1
            var wordCharsInUnit = firstWord.token.text.length
            while (wordsInUnit < maxWordsInUnit) {
                val candidate = expandedTokens.getOrNull(cursor) ?: break
                if (candidate.token.type != TokenType.WORD) break

                val combinedWordChars = wordCharsInUnit + candidate.token.text.length
                val withinLimits = combinedWordChars <= maxCharsInUnit
                val prevWord = unitTokens.lastOrNull { it.type == TokenType.WORD } ?: break
                val canBridge =
                    withinLimits &&
                        isPhraseChunkCandidate(prev = prevWord, next = candidate.token)

                if (!canBridge) break

                unitTokens += candidate.token
                state.consume(candidate.token)
                cursor++
                wordsInUnit++
                wordCharsInUnit = combinedWordChars
            }
        }

        // Attach closing punctuation + breaks to this unit.
        // If we hit a hard boundary (sentence end), keep consuming trailing closers like quotes/brackets
        // so we don't strand them on the next unit.
        var hitHardBoundary = false
        while (cursor < expandedTokens.size) {
            val token = expandedTokens[cursor].token
            if (token.type == TokenType.PUNCTUATION) {
                val ch = token.text.firstOrNull()
                val nextToken = expandedTokens.getOrNull(cursor + 1)?.token
                val isQuote = ch != null && isQuoteChar(ch)
                val isOpening = isOpeningPunctuation(token, state, nextToken)
                val isExplicitOpeningQuote = ch == '\u201C' || ch == '\u2018'

                // Opening quotes that precede a word should stay with that word,
                // even if a sentence-ending punctuation token came right before.
                val quoteFollowedByWord =
                    isQuote &&
                        nextToken?.type == TokenType.WORD &&
                        (isExplicitOpeningQuote || (ch == '"' && isOpening))
                if (quoteFollowedByWord) {
                    break
                }

                if (hitHardBoundary && isOpening) break
                if (!isOpening) {
                    val prevWord = unitTokens.lastOrNull { it.type == TokenType.WORD }
                    unitTokens += token
                    state.consume(token)
                    cursor++

                    val nextTokenAfter = expandedTokens.getOrNull(cursor)?.token
                    if (!hitHardBoundary &&
                        isHardBoundaryPunctuation(token, prevWord = prevWord, nextToken = nextTokenAfter)
                    ) {
                        hitHardBoundary = true
                    }
                    continue
                }
                break
            }
            if (token.type == TokenType.PARAGRAPH_BREAK || token.type == TokenType.PAGE_BREAK) {
                break
            }
            break
        }

        return UnitBuildResult(
            tokens = unitTokens,
            originalWordIndex = firstWordOriginalIndex ?: startCursor,
            nextCursor = cursor,
        )
    }

    private fun computeUnitDurationMs(
        frameTokens: List<Token>,
        config: RsvpConfig,
        contextBefore: ContextSnapshot,
        rhythm: RhythmState,
        flow: FlowState,
        prevToken: Token?,
        prevWord: Token?,
        nextToken: Token?,
        nextWord: Token?,
        boundaryBefore: BoundaryBefore,
    ): Long {
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
        val prosodyStrength =
            if (config.useProsodyPacing) {
                config.prosodyStrength.coerceIn(0.0, 1.6)
            } else {
                0.0
            }
        val boundaryForBoost =
            if (boundaryBefore == BoundaryBefore.NONE &&
                prevToken?.type == TokenType.PUNCTUATION &&
                boundaryBeforeForPunctuation(
                    token = prevToken,
                    prevWord = prevWord,
                    nextToken = nextToken,
                ) != BoundaryBefore.NONE
            ) {
                boundaryBeforeForPunctuation(
                    token = prevToken,
                    prevWord = prevWord,
                    nextToken = nextToken,
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
        val dialogueEntryBoost = 1.0 + (DIALOGUE_ENTRY_BOOST * speedStrength)
        val speakerTagMultiplier =
            speakerTagMultiplier(
                wordsInFrame = words,
                prevWord = prevWord,
                nextWord = nextWord,
                config = config,
            )

        var duration = 0.0
        var parentheticalDepth = contextBefore.parentheticalDepth
        var inDialogue = contextBefore.inDialogue
        var enteredDialogue = false
        var exitedDialogue = false
        var sawParentheticalWord = false

        frameTokens.forEachIndexed { index, token ->
            when (token.type) {
                TokenType.PUNCTUATION -> {
                    val ch = token.text.firstOrNull()
                    val wasInDialogue = inDialogue
                    when (ch) {
                        '(', '[', '{' -> parentheticalDepth++
                        ')', ']', '}' -> parentheticalDepth = max(0, parentheticalDepth - 1)
                        '"', '\u201C', '\u201D', '\u2018', '\u2019' -> Unit
                    }
                    inDialogue = token.isDialogue
                    if (config.useDialogueDetection && ch != null && isQuoteChar(ch)) {
                        if (!wasInDialogue && inDialogue) {
                            enteredDialogue = true
                        } else if (wasInDialogue && !inDialogue) {
                            exitedDialogue = true
                        }
                    }
                }
                TokenType.WORD -> {
                    val dialogueMultiplier = if (config.useDialogueDetection &&
                        inDialogue
                    ) {
                        config.dialogueMultiplier
                    } else {
                        1.0
                    }
                    val contextWordMultiplier =
                        (if (parentheticalDepth > 0) config.parentheticalMultiplier else 1.0) *
                            dialogueMultiplier
                    if (parentheticalDepth > 0) {
                        sawParentheticalWord = true
                    }
                    val boosted = if (index == firstWordIndex) startBoost else 1.0
                    val nextWordText =
                        frameTokens
                            .subList(index + 1, frameTokens.size)
                            .firstOrNull { it.type == TokenType.WORD }
                            ?.text
                            ?: nextWord?.text
                    val prevWordText =
                        frameTokens
                            .subList(0, index)
                            .lastOrNull { it.type == TokenType.WORD }
                            ?.text
                            ?: prevWord?.text

                    val clauseMultiplier =
                        if (!config.useClausePausing) {
                            1.0
                        } else {
                            val raw = ClauseDetector.getClausePauseFactor(token.text, nextWordText)
                            1.0 + ((raw - 1.0) * speedStrength * clauseConfigStrength)
                        }

                    val terminalMultiplier =
                        terminalWordMultiplier(
                            wordIndex = index,
                            word = token,
                            frameTokens = frameTokens,
                            nextToken = nextToken,
                            speedStrength = speedStrength,
                        )

                    val emphasisMultiplier =
                        emphasisMultiplier(
                            token = token,
                            isFirstWord = index == firstWordIndex,
                            boundaryBefore = boundaryBefore,
                            speedStrength = speedStrength,
                        )
                    val prosodyMultiplier =
                        prosodyMultiplier(
                            token = token,
                            prevWordText = prevWordText,
                            nextWordText = nextWordText,
                            isFirstWord = index == firstWordIndex,
                            boundaryBefore = boundaryBefore,
                            speedStrength = speedStrength,
                            prosodyStrength = prosodyStrength,
                        )

                    val dialogueEntryMultiplier =
                        if (config.useDialogueDetection &&
                            !contextBefore.inDialogue &&
                            index == firstWordIndex &&
                            token.isDialogue
                        ) {
                            dialogueEntryBoost
                        } else {
                            1.0
                        }

                    val wordMs =
                        wordDurationMs(token, msPerWord, config) *
                            contextWordMultiplier *
                            boosted *
                            clauseMultiplier *
                            terminalMultiplier *
                            emphasisMultiplier *
                            prosodyMultiplier *
                            dialogueEntryMultiplier *
                            speakerTagMultiplier
                    duration += max(wordMs, wordFloorMs(token, config).toDouble())
                    if (token.pauseAfterMs > 0L) {
                        duration += token.pauseAfterMs * pauseScale
                    }
                }
                else -> Unit
            }
        }

        val transitionHold =
            transitionHoldMs(
                frameTokens = frameTokens,
                firstWord = words.firstOrNull(),
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

        val smoothedWordDuration = rhythm.apply(duration, isBoundary = hardBoundary)

        // Now add punctuation pauses on top of the smoothed word duration.
        // These pauses are intentionally NOT smoothed so they remain prominent.
        var totalDuration = smoothedWordDuration
        if (words.isNotEmpty()) {
            when (boundaryForBoost) {
                BoundaryBefore.SENTENCE -> {
                    totalDuration +=
                        max(config.sentenceEndPauseMs, config.periodPauseMs) *
                            sentencePauseScale *
                            SENTENCE_START_HOLD_FRACTION
                }
                BoundaryBefore.CLAUSE -> {
                    totalDuration += clauseStartHoldMs(config = config, pauseScale = clausePauseScale)
                }
                BoundaryBefore.PARAGRAPH, BoundaryBefore.PAGE, BoundaryBefore.NONE -> Unit
            }
            totalDuration += boundaryStartMicroHoldMs(
                msPerWord = msPerWord,
                speedStrength = speedStrength,
                boundaryBefore = boundaryForBoost,
            )
        }
        frameTokens.forEachIndexed { index, token ->
            if (token.type != TokenType.PUNCTUATION) return@forEachIndexed

            val prevTokenInFrame = frameTokens.getOrNull(index - 1)
            val nextTokenInFrame = frameTokens.getOrNull(index + 1)
            if (shouldSkipPunctuationPause(
                    token = token,
                    index = index,
                    firstWordIndex = firstWordIndex,
                    prevToken = prevTokenInFrame,
                    nextToken = nextTokenInFrame,
                )
            ) {
                return@forEachIndexed
            }

            val prevWordInFrame = frameTokens.subList(0, index).lastOrNull {
                it.type ==
                    TokenType.WORD
            }
            val nextWordInFrame = frameTokens.subList(index + 1, frameTokens.size).firstOrNull {
                it.type ==
                    TokenType.WORD
            }

            totalDuration +=
                punctuationPauseMs(
                    token = token,
                    prevWord = prevWordInFrame ?: prevToken,
                    nextToken = nextWordInFrame ?: nextToken,
                    msPerWord = msPerWord,
                    config = config,
                )
        }
        if (config.usePunctuationLandingHold) {
            totalDuration +=
                punctuationLandingHoldMs(
                    frameTokens = frameTokens,
                    nextToken = nextToken,
                    msPerWord = msPerWord,
                    speedStrength = speedStrength,
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
                totalDuration +=
                    config.commaPauseMs * pauseScale * CLAUSE_LEAD_HOLD_FRACTION * clauseConfigStrength
            }
        }
        if (config.useDialogueDetection && (enteredDialogue || exitedDialogue)) {
            val quoteHold = config.quotePauseMs * pauseScale * QUOTE_TRANSITION_HOLD_FRACTION
            if (enteredDialogue) totalDuration += quoteHold
            if (exitedDialogue) totalDuration += quoteHold
        }
        if (sawParentheticalWord) {
            totalDuration =
                max(
                    totalDuration,
                    smoothedWordDuration * config.parentheticalMultiplier.coerceAtLeast(1.0),
                )
            totalDuration += parentheticalHoldMs(msPerWord = msPerWord, config = config)
        }

        return totalDuration
            .toLong()
            .coerceAtLeast(MIN_FRAME_MS)
    }

    private fun adaptiveHoldMs(
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
            ((difficulty - ADAPTIVE_DIFFICULTY_FLOOR).coerceAtLeast(0.0) /
                (1.0 - ADAPTIVE_DIFFICULTY_FLOOR))
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
            hold += PHRASE_BREAK_HOLD_MS * speedStrength * 0.6
        }

        // Reduce hold if next word has high coherence with current (they belong together)
        val coherence = ClauseDetector.getCoherenceScore(
            lastWord?.text.orEmpty(),
            nextWord.text,
        )
        if (coherence >= 0.5) {
            // Words belong together, reduce the hold to keep them mentally grouped
            hold *= (1.0 - coherence * 0.4)
        }

        return hold.coerceAtMost(ADAPTIVE_HOLD_MAX_MS * speedStrength)
    }

    private fun wordDurationMs(
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
                val ratio = (chunkLetters.toDouble() / fullLetters.toDouble()).coerceIn(0.2, 1.0)
                val scaledSyllables =
                    max(1.0, word.syllableCount.toDouble() * ratio).roundToLong().toInt()
                chunkLetters to scaledSyllables
            } else {
                fullLetters to word.syllableCount
            }

        val lengthCurve =
            run {
                val x = ((letters - 4).coerceAtLeast(0) / 10.0)
                1.0 + config.lengthStrength * (x.pow(config.lengthExponent))
            }

        val complexityComponent =
            1.0 + (max(0.0, word.complexityMultiplier - 1.0) * config.complexityStrength)

        val rarityExtra = (1.0 - word.frequencyScore).coerceIn(0.0, 1.0) * config.rarityExtraMaxMs
        val syllableExtra = max(0, syllables - 1) * config.syllableExtraMs

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
    ): Double {
        val ch = token.text.firstOrNull() ?: return 0.0
        val prevText = prevWord?.text.orEmpty()
        val prevIsNumeric = prevWord?.text?.all { it.isDigit() } == true

        if (ch == '.' && isDecimalPoint(prevText, nextToken)) {
            return 0.0
        }

        var base =
            when {
                ch == '.' ->
                    if (isAbbreviationDot(prevText, nextToken)) {
                        config.commaPauseMs * 0.35
                    } else {
                        config.periodPauseMs.toDouble()
                    }
                ch == '\u2026' -> ellipsisPauseBaseMs(nextToken = nextToken, config = config)
                isSentenceEndingPunctuation(ch) -> config.sentenceEndPauseMs.toDouble()
                ch == ',' -> if (isThousandSeparator(
                        prevText,
                        nextToken
                    )
                ) {
                    0.0
                } else {
                    config.commaPauseMs.toDouble()
                }
                ch == ';' -> config.semicolonPauseMs.toDouble()
                ch == ':' -> config.colonPauseMs.toDouble()
                ch == '\u2014' || ch == '\u2013' || ch == '-' -> config.dashPauseMs.toDouble()
                ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}' -> config.parenthesesPauseMs.toDouble()
                ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019' -> config.quotePauseMs.toDouble()
                isMidSentencePunctuation(ch) -> config.commaPauseMs * 0.85
                else -> 0.0
            }

        var floor =
            when {
                ch == '.' -> {
                    if (isDecimalPoint(prevText, nextToken) ||
                        isAbbreviationDot(prevText, nextToken)
                    ) {
                        0.0
                    } else {
                        config.periodPauseMs * config.minPauseScale
                    }
                }
                ch == '\u2026' ->
                    ellipsisPauseBaseMs(nextToken = nextToken, config = config) * config.minPauseScale
                isSentenceEndingPunctuation(ch) -> config.sentenceEndPauseMs * config.minPauseScale
                ch == ',' ->
                    if (isThousandSeparator(prevText, nextToken)) {
                        0.0
                    } else {
                        config.commaPauseMs * config.minPauseScale
                    }
                ch == ';' -> config.semicolonPauseMs * config.minPauseScale
                ch == ':' -> config.colonPauseMs * config.minPauseScale
                ch == '\u2014' || ch == '\u2013' || ch == '-' ->
                    config.dashPauseMs * config.minPauseScale
                ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}' ->
                    config.parenthesesPauseMs * config.minPauseScale
                ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019' ->
                    config.quotePauseMs * config.minPauseScale
                isMidSentencePunctuation(ch) -> (config.commaPauseMs * 0.85) * config.minPauseScale
                else -> 0.0
            }

        if (ch == '.' &&
            isLikelySentenceContinuation(nextToken) &&
            !prevIsNumeric &&
            !isDecimalPoint(prevText, nextToken) &&
            !isAbbreviationDot(prevText, nextToken)
        ) {
            base = min(base, config.commaPauseMs * 0.8)
            floor = min(floor, (config.commaPauseMs * 0.8) * config.minPauseScale)
        }

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
                extraRetention = punctuationRetentionBoost(ch = ch, nextToken = nextToken),
            )
        val scaled = base * punctuationScale
        return max(scaled, floor)
    }

    private fun punctuationLandingHoldMs(
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
                .asSequence()
                .filter { it.type == TokenType.PUNCTUATION }
                .map { token ->
                    boundaryLandingWeight(
                        token = token,
                        nextToken = nextToken,
                    )
                }.maxOrNull()
                ?: return 0.0
        if (weight <= 0.0) return 0.0

        val base = (msPerWord * weight).coerceIn(MIN_LANDING_HOLD_MS, MAX_LANDING_HOLD_MS)
        val speedAdjusted = base * (1.0 + (speedStrength * LANDING_HOLD_SPEED_BOOST))
        return speedAdjusted.coerceAtMost(MAX_LANDING_HOLD_MS)
    }

    private fun boundaryLandingWeight(
        token: Token,
        nextToken: Token?,
    ): Double {
        val ch = token.text.firstOrNull() ?: return 0.0
        val contourStrength =
            boundaryContourWeight(
                token = token,
                prevWord = null,
                nextToken = nextToken,
            )
        if (contourStrength <= 0.0) return 0.0
        val base =
            when {
                ch == '\u2026' -> ELLIPSIS_LANDING_HOLD_WEIGHT
                ch == '.' || isSentenceEndingPunctuation(ch) -> STRONG_LANDING_HOLD_WEIGHT
                ch == ';' -> SEMICOLON_LANDING_HOLD_WEIGHT
                else -> CLAUSE_LANDING_HOLD_WEIGHT
            }
        return base * contourStrength
    }

    private fun boundaryContourWeight(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): Double {
        val ch = token.text.firstOrNull() ?: return 0.0
        val prevText = prevWord?.text.orEmpty()
        return when {
            ch == '.' -> {
                when {
                    isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken) -> 0.0
                    isLikelySentenceContinuation(nextToken) -> 0.55
                    else -> 0.92
                }
            }
            ch == '\u2026' -> 1.0
            isSentenceEndingPunctuation(ch) -> 0.88
            ch == ';' -> 0.74
            ch == ':' -> 0.60
            ch == '\u2014' || ch == '\u2013' || ch == '-' -> 0.56
            ch == ',' && isClauseLeadPunctuation(ch, nextToken) -> 0.42
            else -> 0.0
        }
    }

    private fun pauseScale(
        msPerWord: Double,
        config: RsvpConfig,
        extraRetention: Double = 0.0,
    ): Double {
        val ratio = (msPerWord / BASE_MS_PER_WORD_AT_300).coerceIn(0.12, 2.5)
        val compressed = ratio.pow(config.pauseScaleExponent)
        val preservedFloor =
            (config.minPauseScale + extraRetention)
                .coerceIn(config.minPauseScale, 0.97)
        val scaled = preservedFloor + ((1.0 - preservedFloor) * compressed)
        return scaled.coerceIn(config.minPauseScale, 1.35)
    }

    private fun punctuationRetentionBoost(
        ch: Char,
        nextToken: Token?,
    ): Double =
        when {
            ch == '\u2026' -> ELLIPSIS_RETENTION_BOOST
            ch == '.' || isSentenceEndingPunctuation(ch) -> STRONG_PUNCTUATION_RETENTION_BOOST
            ch == ';' -> SEMICOLON_RETENTION_BOOST
            ch == ':' || ch == '\u2014' || ch == '\u2013' || ch == '-' ->
                CLAUSE_PUNCTUATION_RETENTION_BOOST
            ch == ',' && isClauseLeadPunctuation(ch, nextToken) ->
                CLAUSE_PUNCTUATION_RETENTION_BOOST
            ch == ',' -> COMMA_RETENTION_BOOST
            ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019' ->
                QUOTE_RETENTION_BOOST
            ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}' ->
                PARENTHESIS_RETENTION_BOOST
            isMidSentencePunctuation(ch) -> COMMA_RETENTION_BOOST
            else -> 0.0
        }

    private fun ellipsisPauseBaseMs(
        nextToken: Token?,
        config: RsvpConfig,
    ): Double {
        val nextWord = nextToken?.takeIf { it.type == TokenType.WORD }?.text
        val nextStartsSentenceLike =
            nextWord?.filter { it.isLetter() }?.firstOrNull()?.isUpperCase() == true ||
                nextWord?.lowercase() in SENTENCE_STARTERS
        val breakAfterEllipsis =
            nextToken?.type == TokenType.PARAGRAPH_BREAK || nextToken?.type == TokenType.PAGE_BREAK
        return if (nextStartsSentenceLike || breakAfterEllipsis || nextWord == null) {
            max(config.commaPauseMs * 1.15, config.periodPauseMs * 0.72)
        } else {
            config.commaPauseMs * 1.15
        }
    }

    private fun emphasisMultiplier(
        token: Token,
        isFirstWord: Boolean,
        boundaryBefore: BoundaryBefore,
        speedStrength: Double,
    ): Double {
        val text = token.text
        if (text.isEmpty()) return 1.0

        val isSentenceStart = isFirstWord && boundaryBefore.isMajorStart()
        val letters = text.filter { it.isLetter() }
        val hasDigits = text.any { it.isDigit() }
        val isAcronym =
            letters.length in 2..5 && letters.isNotEmpty() && letters.all { it.isUpperCase() }
        val startsUpper = letters.firstOrNull()?.isUpperCase() == true
        val hasLower = letters.any { it.isLowerCase() }
        val isProper = startsUpper && hasLower && !isSentenceStart

        var multiplier = 1.0
        if (hasDigits) {
            multiplier *= 1.0 + (NUMBER_EMPHASIS_BOOST * speedStrength)
        }
        if (isAcronym) {
            multiplier *= 1.0 + (ACRONYM_EMPHASIS_BOOST * speedStrength)
        }
        if (isProper) {
            multiplier *= 1.0 + (PROPER_NOUN_BOOST * speedStrength)
        }

        return multiplier.coerceIn(1.0, MAX_EMPHASIS_MULTIPLIER)
    }

    private fun prosodyMultiplier(
        token: Token,
        prevWordText: String?,
        nextWordText: String?,
        isFirstWord: Boolean,
        boundaryBefore: BoundaryBefore,
        speedStrength: Double,
        prosodyStrength: Double,
    ): Double {
        if (prosodyStrength <= 0.0) return 1.0
        if (token.isSubwordChunk) return 1.0

        val wordLower = normalizeWord(token.text)
        if (wordLower.isEmpty()) return 1.0

        val prevLower = prevWordText?.let(::normalizeWord)?.takeIf { it.isNotEmpty() }
        val nextLower = nextWordText?.let(::normalizeWord)?.takeIf { it.isNotEmpty() }

        val isFunction = isFunctionWord(wordLower)
        val isAnchor = isSemanticAnchor(wordLower)
        val effectiveStrength = speedStrength * prosodyStrength
        var multiplier = 1.0

        val canGlide =
            isFunction &&
                !isAnchor &&
                boundaryBefore == BoundaryBefore.NONE &&
                nextLower != null
        if (canGlide) {
            val coherence = ClauseDetector.getCoherenceScore(wordLower, nextLower)
            val glideStrength =
                if (coherence >= FUNCTION_BRIDGE_COHERENCE_THRESHOLD ||
                    isFunctionBridgePair(wordLower, nextLower)
                ) {
                    FUNCTION_WORD_GLIDE_STRONG
                } else {
                    FUNCTION_WORD_GLIDE_LIGHT
                }
            multiplier -= glideStrength * effectiveStrength
        }

        if (isAnchor) {
            multiplier += SEMANTIC_ANCHOR_BOOST * effectiveStrength
        }

        val surroundedByFunctionWords =
            !isFunction &&
                (
                    (prevLower != null && isFunctionWord(prevLower)) ||
                        (nextLower != null && isFunctionWord(nextLower))
                    )
        if (surroundedByFunctionWords) {
            multiplier += CONTENT_WORD_STRESS_BOOST * effectiveStrength
        }

        // Keep sentence starts crisp; don't compress them when they start with function words.
        if (isFirstWord && boundaryBefore.isMajorStart() && isFunction) {
            multiplier = max(multiplier, 1.0)
        }

        return multiplier.coerceIn(MIN_PROSODY_MULTIPLIER, MAX_PROSODY_MULTIPLIER)
    }

    private fun isFunctionBridgePair(
        currentLower: String,
        nextLower: String,
    ): Boolean {
        if (!isFunctionWord(currentLower)) return false
        if (isFunctionWord(nextLower)) return false
        if (isSemanticAnchor(currentLower)) return false

        val coherence = ClauseDetector.getCoherenceScore(currentLower, nextLower)
        return coherence >= FUNCTION_BRIDGE_COHERENCE_THRESHOLD ||
            currentLower in FUNCTION_BRIDGE_WORDS
    }

    private fun isSemanticAnchor(wordLower: String): Boolean = wordLower in SEMANTIC_ANCHOR_WORDS

    private fun isFunctionWord(wordLower: String): Boolean = wordLower in FUNCTION_WORDS

    private fun normalizeWord(text: String): String =
        text.lowercase().trim('"', '\'', '\u2018', '\u2019')

    private fun speakerTagMultiplier(
        wordsInFrame: List<Token>,
        prevWord: Token?,
        nextWord: Token?,
        config: RsvpConfig,
    ): Double {
        if (!config.useDialogueDetection) return 1.0

        if (wordsInFrame.isEmpty()) return 1.0
        if (wordsInFrame.any { it.isDialogue }) return 1.0
        if (wordsInFrame.size > 3) return 1.0

        val prevText = prevWord?.text
        val nextText = nextWord?.text
        val hasSpeakerVerb =
            wordsInFrame.any { DialogueAnalyzer.isSpeakerVerb(it.text) } ||
                (prevText != null && DialogueAnalyzer.isSpeakerVerb(prevText)) ||
                (nextText != null && DialogueAnalyzer.isSpeakerVerb(nextText))
        if (!hasSpeakerVerb) return 1.0

        val frameTexts = wordsInFrame.map { it.text }
        val candidates = mutableListOf<List<String>>()

        when (frameTexts.size) {
            1 -> {
                val current = frameTexts[0]
                if (prevText != null) candidates += listOf(prevText, current)
                if (nextText != null) candidates += listOf(current, nextText)
                if (prevText != null &&
                    nextText != null
                ) {
                    candidates += listOf(prevText, current, nextText)
                }
            }
            else -> {
                candidates += frameTexts
                if (prevText != null) candidates += listOf(prevText) + frameTexts
                if (nextText != null) candidates += frameTexts + listOf(nextText)
            }
        }

        val matchesTag = candidates.any { DialogueAnalyzer.isSpeakerTag(it) }
        return if (matchesTag) DialogueAnalyzer.SPEAKER_TAG_MULTIPLIER else 1.0
    }

    private fun transitionHoldMs(
        frameTokens: List<Token>,
        firstWord: Token?,
        nextWord: Token?,
        speedStrength: Double,
        prosodyStrength: Double,
    ): Double {
        if (firstWord == null || nextWord == null) return 0.0
        if (frameTokens.count { it.type == TokenType.WORD } != 1) return 0.0
        if (frameTokens.any { it.type == TokenType.PUNCTUATION }) return 0.0

        val firstLower = firstWord.text.lowercase()
        val nextLower = nextWord.text.lowercase()

        // Function words should glide into content words with minimal visual gap.
        if (prosodyStrength > 0.0 && isFunctionBridgePair(firstLower, nextLower)) {
            return FUNCTION_BRIDGE_HOLD_MS * speedStrength * prosodyStrength
        }

        // Check for tight pair patterns that should stay together mentally
        if (shouldPreferHold(firstWord, nextWord)) {
            val hold = TRANSITION_HOLD_BASE_MS + (TRANSITION_HOLD_EXTRA_MS * speedStrength)
            return hold.coerceAtLeast(0.0)
        }

        // Add coherence hold when current word "leads into" next word
        // This helps readers anticipate and process the next word
        if (isCoherencePair(firstLower, nextLower)) {
            return COHERENCE_HOLD_MS * speedStrength
        }

        // Add a small hold before phrase breaks for natural reading rhythm
        if (isPhraseBreakBefore(firstLower, nextLower, nextWord)) {
            return PHRASE_BREAK_HOLD_MS * speedStrength
        }

        return 0.0
    }

    private fun isCoherencePair(
        currentLower: String,
        nextLower: String,
    ): Boolean {
        // Article followed by adjective or noun - reader anticipates the noun
        if (currentLower in setOf("a", "an", "the") && nextLower.length > 2) {
            return true
        }
        // Possessive followed by noun
        if (currentLower in setOf("my", "your", "his", "her", "its", "our", "their") &&
            nextLower.length > 2
        ) {
            return true
        }
        // Adverb modifiers that lead into verbs/adjectives
        if (currentLower in setOf("very", "quite", "rather", "too", "so", "really", "just") &&
            nextLower.length > 2
        ) {
            return true
        }
        // Modal verbs leading into main verb
        if (currentLower in setOf("will", "would", "can", "could", "should", "must", "may", "might") &&
            nextLower !in setOf("be", "have", "not", "the", "a", "an")
        ) {
            return true
        }
        return false
    }

    private fun isPhraseBreakBefore(
        currentLower: String,
        nextLower: String,
        nextWord: Token,
    ): Boolean {
        // Before clause starters, add a micro-pause for comprehension
        if (nextLower in setOf(
                "which",
                "who",
                "whom",
                "whose",
                "that",
                "where",
                "when",
                "because",
                "although",
                "though",
                "unless",
                "until",
                "while",
                "after",
                "before",
                "if",
            )
        ) {
            return true
        }
        // Before coordinating conjunctions in longer sentences
        if (nextLower in setOf("and", "but", "or", "yet", "so") &&
            currentLower.length > 3 &&
            !nextWord.isClauseBoundary
        ) {
            return true
        }
        return false
    }

    private fun frameDifficulty(words: List<Token>): Double {
        if (words.isEmpty()) return 0.0
        val total = words.sumOf { (1.0 - wordEase(it)) }
        return (total / words.size).coerceIn(0.0, 1.0)
    }

    private fun shouldPreferHold(
        prev: Token,
        next: Token,
    ): Boolean {
        val prevLower = prev.text.lowercase()
        val nextLower = next.text.lowercase()
        val pairKey = "$prevLower $nextLower"
        val isHinted = pairKey in TIGHT_PAIR_HINTS
        val gluePair =
            prevLower in GLUE_WORDS &&
                nextLower in GLUE_WORDS &&
                prev.text.length <= 4 &&
                next.text.length <= 4
        val easyPair =
            wordEase(prev) >= EASY_PAIR_THRESHOLD && wordEase(next) >= EASY_PAIR_THRESHOLD

        // Check coherence score for high-coherence pairs
        val coherence = ClauseDetector.getCoherenceScore(prevLower, nextLower)
        val highCoherence = coherence >= 0.65 && prev.text.length <= 5 && next.text.length <= 6

        return (easyPair && (isHinted || gluePair)) || highCoherence
    }

    private fun isClauseLeadPunctuation(
        ch: Char,
        nextToken: Token?,
    ): Boolean {
        if (ch != ',' &&
            ch != ';' &&
            ch != ':' &&
            ch != '\u2014' &&
            ch != '\u2013' &&
            ch != '-'
        ) {
            return false
        }
        val nextWord = nextToken?.takeIf { it.type == TokenType.WORD } ?: return false
        val nextLower = nextWord.text.lowercase()
        return ClauseDetector.isClauseBoundary(nextLower) ||
            ClauseDetector.isCoordinatingConjunction(nextLower)
    }

    private fun isLikelySentenceContinuation(nextToken: Token?): Boolean {
        val nextWord = nextToken?.takeIf { it.type == TokenType.WORD } ?: return false
        val firstChar = nextWord.text.firstOrNull() ?: return false
        return firstChar.isLowerCase()
    }

    private fun isEmbeddedQuote(
        ch: Char,
        prevWord: Token?,
        nextToken: Token?,
    ): Boolean {
        val isQuote =
            ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019'
        if (!isQuote) return false

        val nextCh = nextToken?.text?.firstOrNull()
        val nextIsPunct = nextToken?.type == TokenType.PUNCTUATION
        val adjacentSentencePunct =
            nextIsPunct &&
                nextCh != null &&
                (isSentenceEndingPunctuation(nextCh) || isMidSentencePunctuation(nextCh))

        return adjacentSentencePunct || (prevWord != null && nextToken?.type == TokenType.WORD)
    }

    private fun multiWordPenalty(wordCount: Int): Double =
        when (wordCount) {
            0, 1 -> 1.0
            2 -> 1.06  // Reduced from 1.12 for smoother rhythm
            else -> 1.12
        }

    private fun isPhraseChunkCandidate(
        prev: Token,
        next: Token,
    ): Boolean {
        val prevLower = prev.text.lowercase()
        val nextLower = next.text.lowercase()

        if (prev.isClauseBoundary || next.isClauseBoundary) return false
        if (ClauseDetector.isCoordinatingConjunction(prevLower)) return false

        // Use coherence scoring to determine if words should be chunked together
        val coherenceScore = ClauseDetector.getCoherenceScore(prevLower, nextLower)
        if (coherenceScore >= 0.7) {
            // High coherence pairs should always be chunked if short enough
            val bothShort = prev.text.length <= 5 && next.text.length <= 8
            if (bothShort) return true
        }

        val glue = prevLower in GLUE_WORDS || nextLower in GLUE_WORDS
        val bothShort = prev.text.length <= 4 && next.text.length <= 7
        val bothCommon = prev.frequencyScore >= 0.7 && next.frequencyScore >= 0.7

        // Also consider tight pair hints
        val pairKey = "$prevLower $nextLower"
        if (pairKey in TIGHT_PAIR_HINTS && bothShort) return true

        return (glue && bothShort) || (bothShort && bothCommon)
    }

    private fun terminalWordMultiplier(
        wordIndex: Int,
        word: Token,
        frameTokens: List<Token>,
        nextToken: Token?,
        speedStrength: Double,
    ): Double {
        val punctIndex =
            (wordIndex + 1 until frameTokens.size).firstOrNull {
                frameTokens[it].type ==
                    TokenType.PUNCTUATION
            }
                ?: return 1.0
        if (frameTokens.subList(wordIndex + 1, punctIndex).any {
                it.type == TokenType.WORD
            }
        ) {
            return 1.0
        }

        val punctToken = frameTokens[punctIndex]
        val ch = punctToken.text.firstOrNull() ?: return 1.0

        val tokenAfterPunct = frameTokens.getOrNull(punctIndex + 1) ?: nextToken

        if (ch == ',' && isThousandSeparator(word.text, tokenAfterPunct)) return 1.0
        if (ch == '.' && isDecimalPoint(word.text, tokenAfterPunct)) return 1.0

        val contourStrength =
            boundaryTailLiftWeight(
                token = punctToken,
                prevWord = word,
                nextToken = tokenAfterPunct,
            )
        if (contourStrength <= 0.0) return 1.0

        val effectiveStrength = max(speedStrength, MIN_BOUNDARY_TAIL_LIFT_STRENGTH)
        val extra = MAX_BOUNDARY_TAIL_LIFT * contourStrength

        return 1.0 + (extra * effectiveStrength)
    }

    private fun boundaryTailLiftWeight(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): Double {
        val ch = token.text.firstOrNull() ?: return 0.0
        val prevText = prevWord?.text.orEmpty()
        return when {
            ch == '.' ->
                when {
                    isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken) -> 0.0
                    isLikelySentenceContinuation(nextToken) -> 1.18
                    else -> 1.34
                }
            ch == '\u2026' -> 1.40
            isSentenceEndingPunctuation(ch) -> 1.26
            ch == ';' -> 0.62
            ch == ':' -> 0.58
            ch == '\u2014' || ch == '\u2013' || ch == '-' -> 0.46
            ch == ',' && isClauseLeadPunctuation(ch, nextToken) -> 0.24
            else -> 0.0
        }
    }

    private fun breakMarkerToken(type: TokenType): Token =
        when (type) {
            TokenType.PAGE_BREAK -> Token(text = "• • •", type = TokenType.PUNCTUATION)
            TokenType.PARAGRAPH_BREAK -> Token(text = " ", type = TokenType.PUNCTUATION)
            else -> Token(text = " ", type = TokenType.PUNCTUATION)
        }

    private fun isOpeningPunctuation(
        token: Token,
        state: ContextState,
        nextToken: Token?,
    ): Boolean {
        if (isCurrencyPrefixPunctuation(token, nextToken)) return true
        val ch = token.text.firstOrNull() ?: return false
        return when (ch) {
            '"' -> !state.straightQuoteOpen
            else -> ch in OPENING_PUNCTUATION
        }
    }

    private fun shouldSkipPunctuationPause(
        token: Token,
        index: Int,
        firstWordIndex: Int,
        prevToken: Token?,
        nextToken: Token?,
    ): Boolean {
        val ch = token.text.firstOrNull() ?: return true
        if (index < firstWordIndex &&
            (isOpeningPunctuationChar(ch) || isCurrencyPrefixPunctuation(token, nextToken))
        ) {
            return true
        }

        val prevIsPunct = prevToken?.type == TokenType.PUNCTUATION
        val nextIsPunct = nextToken?.type == TokenType.PUNCTUATION
        val prevCh = prevToken?.text?.firstOrNull()

        if (isQuoteOrBracket(ch) && (prevIsPunct || nextIsPunct)) return true

        val isSentenceEnd = isSentenceEndingPunctuation(ch) || ch == '.'
        val prevIsSentenceEnd =
            prevCh != null && (isSentenceEndingPunctuation(prevCh) || prevCh == '.')
        return isSentenceEnd && prevIsSentenceEnd
    }

    private fun isOpeningPunctuationChar(ch: Char): Boolean = ch == '"' || ch in OPENING_PUNCTUATION

    private fun isCurrencyPrefixPunctuation(
        token: Token,
        nextToken: Token?,
    ): Boolean {
        val ch = token.text.firstOrNull() ?: return false
        if (ch !in CURRENCY_PREFIX_PUNCTUATION) return false
        val nextWordText = nextToken?.takeIf { it.type == TokenType.WORD }?.text ?: return false
        return CURRENCY_NUMERIC_WORD_REGEX.matches(nextWordText)
    }

    private fun isQuoteOrBracket(ch: Char): Boolean = ch in QUOTE_OR_BRACKET_PUNCTUATION

    private fun isQuoteChar(ch: Char): Boolean =
        ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019'

    private fun isHardBoundaryPunctuation(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): Boolean =
        boundaryBeforeForPunctuation(
            token = token,
            prevWord = prevWord,
            nextToken = nextToken,
        ) == BoundaryBefore.SENTENCE

    private fun boundaryBeforeForPunctuation(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): BoundaryBefore {
        val ch = token.text.firstOrNull() ?: return BoundaryBefore.NONE
        val prevText = prevWord?.text.orEmpty()
        return when {
            ch == '.' -> {
                if (!isDecimalPoint(prevText, nextToken) && !isAbbreviationDot(prevText, nextToken)) {
                    BoundaryBefore.SENTENCE
                } else {
                    BoundaryBefore.NONE
                }
            }
            ch == '\u2026' -> BoundaryBefore.CLAUSE
            ch == ';' -> BoundaryBefore.CLAUSE
            ch == ':' || ch == '\u2014' || ch == '\u2013' || ch == '-' -> BoundaryBefore.CLAUSE
            ch == ',' && isClauseLeadPunctuation(ch, nextToken) -> BoundaryBefore.CLAUSE
            isSentenceEndingPunctuation(ch) -> BoundaryBefore.SENTENCE
            else -> BoundaryBefore.NONE
        }
    }

    private fun isHardBoundary(
        tokens: List<Token>,
        nextToken: Token?,
    ): Boolean {
        if (tokens.any {
                it.type == TokenType.PARAGRAPH_BREAK || it.type == TokenType.PAGE_BREAK
            }
        ) {
            return true
        }

        for (i in tokens.indices) {
            val token = tokens[i]
            if (token.type != TokenType.PUNCTUATION) continue

            val prevWord = tokens.subList(0, i).lastOrNull { it.type == TokenType.WORD }
            val nextWord = tokens.subList(i + 1, tokens.size).firstOrNull {
                it.type ==
                    TokenType.WORD
            }
            if (isRhythmBoundaryPunctuation(
                    token,
                    prevWord = prevWord,
                    nextToken =
                    nextWord ?: nextToken
                )
            ) {
                return true
            }
        }

        return false
    }

    private fun wordFloorMs(
        word: Token,
        config: RsvpConfig,
    ): Long {
        if (word.isSubwordChunk) return config.longWordMinMs
        val letters = word.text.count { it.isLetterOrDigit() }
        return if (letters >= config.longWordChars) config.longWordMinMs else config.minWordMs
    }

    private fun pageBreakBasePauseMs(config: RsvpConfig): Double =
        max(
            config.paragraphPauseMs.toDouble() * 1.75,
            max(config.sentenceEndPauseMs.toDouble(), config.periodPauseMs.toDouble()) * 1.4,
        )

    private fun paragraphBreakBasePauseMs(config: RsvpConfig): Double =
        max(config.paragraphPauseMs.toDouble(), config.sentenceEndPauseMs.toDouble() * 0.7)

    private fun boundaryStartMicroHoldMs(
        msPerWord: Double,
        speedStrength: Double,
        boundaryBefore: BoundaryBefore,
    ): Double {
        if (msPerWord > 110.0) return 0.0
        return when (boundaryBefore) {
            BoundaryBefore.SENTENCE -> SENTENCE_START_MIN_HOLD_MS * speedStrength
            BoundaryBefore.CLAUSE -> CLAUSE_START_MIN_HOLD_MS * speedStrength
            BoundaryBefore.PARAGRAPH, BoundaryBefore.PAGE, BoundaryBefore.NONE -> 0.0
        }
    }

    private fun clauseStartHoldMs(
        config: RsvpConfig,
        pauseScale: Double,
    ): Double {
        val base =
            max(
                config.commaPauseMs.toDouble(),
                max(
                    config.semicolonPauseMs.toDouble() * 0.72,
                    max(
                        config.colonPauseMs.toDouble() * 0.78,
                        config.dashPauseMs.toDouble() * 0.78,
                    ),
                ),
            )
        return base * pauseScale * CLAUSE_START_HOLD_FRACTION
    }

    private fun parentheticalHoldMs(
        msPerWord: Double,
        config: RsvpConfig,
    ): Double {
        val multiplierDelta = (config.parentheticalMultiplier - 1.0).coerceAtLeast(0.0)
        if (multiplierDelta <= 0.0) return 0.0
        return msPerWord * multiplierDelta * PARENTHETICAL_HOLD_FRACTION
    }

    private fun startBoostMultiplier(
        msPerWord: Double,
        boundaryBefore: BoundaryBefore,
    ): Double {
        val strength = speedStrength(msPerWord)
        val maxExtra =
            when (boundaryBefore) {
                BoundaryBefore.CLAUSE -> 0.05
                BoundaryBefore.SENTENCE -> 0.10
                BoundaryBefore.PARAGRAPH -> 0.16
                BoundaryBefore.PAGE -> 0.22
                BoundaryBefore.NONE -> 0.0
            }

        return 1.0 + (maxExtra * strength)
    }

    private fun speedStrength(msPerWord: Double): Double {
        val speedFactor = (BASE_MS_PER_WORD_AT_300 / msPerWord).coerceIn(1.0, 3.5)
        return ((speedFactor - 1.0) / 2.5).coerceIn(0.0, 1.0)
    }

    private fun findFirstWordCursor(
        expandedTokens: List<ExpandedToken>,
        startCursor: Int,
    ): Int {
        var cursor = startCursor.coerceAtLeast(0)
        while (cursor < expandedTokens.size &&
            expandedTokens[cursor].token.type != TokenType.WORD
        ) {
            cursor++
        }
        return cursor
    }

    private fun boundaryBefore(
        expandedTokens: List<ExpandedToken>,
        wordCursor: Int,
    ): BoundaryBefore {
        if (wordCursor <= 0 || wordCursor >= expandedTokens.size) return BoundaryBefore.NONE
        val nextToken = expandedTokens[wordCursor].token

        var cursor = wordCursor - 1
        while (cursor >= 0) {
            val token = expandedTokens[cursor].token
            when (token.type) {
                TokenType.PAGE_BREAK -> return BoundaryBefore.PAGE
                TokenType.PARAGRAPH_BREAK -> return BoundaryBefore.PARAGRAPH
                TokenType.PUNCTUATION -> {
                    val ch = token.text.firstOrNull()
                    if (ch != null && ch in SKIPPABLE_BOUNDARY_PUNCTUATION) {
                        cursor--
                        continue
                    }
                    val prevWord = findPrevWord(expandedTokens, beforeIndex = cursor)
                    return boundaryBeforeForPunctuation(
                        token = token,
                        prevWord = prevWord,
                        nextToken = nextToken,
                    )
                }
                TokenType.WORD -> return BoundaryBefore.NONE
            }
        }
        return BoundaryBefore.NONE
    }

    private fun findPrevWord(
        expandedTokens: List<ExpandedToken>,
        beforeIndex: Int,
    ): Token? {
        var cursor = beforeIndex - 1
        while (cursor >= 0) {
            val token = expandedTokens[cursor].token
            if (token.type == TokenType.WORD) return token
            cursor--
        }
        return null
    }

    private fun applySessionRamps(
        frames: MutableList<RsvpFrame>,
        config: RsvpConfig,
    ) {
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

        frames[frames.lastIndex] =
            frames.last().copy(durationMs = frames.last().durationMs + config.endDelayMs)
    }

    private fun applyBlinkSeparation(
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
            val hasWord = frame.tokens.any { it.type == TokenType.WORD }
            val nextTokens = next?.tokens.orEmpty()
            val nextHasWord = nextTokens.any { it.type == TokenType.WORD }
            val wordCount = frame.tokens.count { it.type == TokenType.WORD }
            val nextWordCount = nextTokens.count { it.type == TokenType.WORD }

            if (hasWord && nextHasWord) {
                // Keep chunked phrase units visually stable by avoiding injected blink frames
                // between multi-word frames.
                if (wordCount != 1 || nextWordCount != 1) {
                    output += frame
                    continue
                }
                val firstWord = frame.tokens.firstOrNull { it.type == TokenType.WORD }
                if (firstWord == null) {
                    output += frame
                    continue
                }
                val nextWord = nextTokens.firstOrNull { it.type == TokenType.WORD }
                if (nextWord != null &&
                    frame.tokens.none { it.type == TokenType.PUNCTUATION } &&
                    shouldPreferHold(firstWord, nextWord)
                ) {
                    output += frame
                    continue
                }
                if (isHardBoundary(frame.tokens, nextWord)) {
                    output += frame
                    continue
                }
                val floorMs = max(wordFloorMs(firstWord, config), MIN_FRAME_MS)
                val maxBlink = (frame.durationMs - floorMs).coerceAtLeast(0L)
                val punctuationFactor = blinkPunctuationFactor(frame.tokens)
                val weight =
                    when (blinkMode) {
                        BlinkMode.SUBTLE -> punctuationFactor
                        BlinkMode.ADAPTIVE -> {
                            val ease = (wordEase(firstWord) + wordEase(nextWord ?: firstWord)) * 0.5
                            if (ease >= ADAPTIVE_EASE_THRESHOLD) punctuationFactor else 0.0
                        }
                        BlinkMode.OFF -> 0.0
                    }
                val blinkMs = min((targetBlinkMs * weight).roundToLong(), maxBlink)
                if (blinkMs >= MIN_BLINK_MS) {
                    output +=
                        frame.copy(
                            durationMs = (frame.durationMs - blinkMs).coerceAtLeast(MIN_FRAME_MS)
                        )
                    output +=
                        RsvpFrame(
                            tokens = listOf(blinkToken),
                            durationMs = blinkMs,
                            originalTokenIndex = frame.originalTokenIndex,
                            resumeCursor = frame.resumeCursor,
                        )
                    continue
                }
            }

            output += frame
        }

        frames.clear()
        frames.addAll(output)
    }

    private fun wordEase(word: Token): Double {
        val letters = word.text.count { it.isLetterOrDigit() }.coerceAtLeast(1)
        val lengthScore = ((letters - 4).coerceAtLeast(0) / 8.0).coerceIn(0.0, 1.0)
        val syllableScore = ((word.syllableCount - 1).coerceAtLeast(0) / 4.0).coerceIn(0.0, 1.0)
        val rarityScore = (1.0 - word.frequencyScore).coerceIn(0.0, 1.0)
        val complexityScore = (word.complexityMultiplier - 1.0).coerceAtLeast(
            0.0
        ).coerceIn(0.0, 1.0)

        val difficulty =
            (lengthScore * 0.35) +
                (syllableScore * 0.25) +
                (rarityScore * 0.25) +
                (complexityScore * 0.15)

        return (1.0 - difficulty).coerceIn(0.0, 1.0)
    }

    private fun blinkPunctuationFactor(tokens: List<Token>): Double {
        val hasMidPause =
            tokens.any { token ->
                val ch = token.text.firstOrNull() ?: return@any false
                token.type == TokenType.PUNCTUATION && isMidSentencePunctuation(ch)
            }
        return if (hasMidPause) 0.55 else 1.0
    }

    private data class ExpandedToken(
        val token: Token,
        val originalIndex: Int,
        val expandedIndex: Int,
    )

    private data class UnitBuildResult(val tokens: List<Token>, val originalWordIndex: Int, val nextCursor: Int,)

    private enum class BoundaryBefore {
        NONE,
        CLAUSE,
        SENTENCE,
        PARAGRAPH,
        PAGE,
        ;

        fun isMajorStart(): Boolean =
            this == SENTENCE || this == PARAGRAPH || this == PAGE
    }

    private class ContextState {
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

    private data class ContextSnapshot(val parentheticalDepth: Int, val inDialogue: Boolean,)

    private class RhythmState {
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
        ): Double {
            if (isBoundary) {
                ema = rawMs
                return rawMs
            }

            val prev = ema
            val next =
                if (prev == null) {
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

        fun reset() {
            ema = null
        }
    }

    private class FlowState(
        private val alpha: Double,
        private val maxBoost: Double,
        private val maxSlowdown: Double,
        private val strength: Double,
    ) {
        private var ema: Double? = null

        fun apply(
            difficulty: Double,
            speedStrength: Double,
            isBoundary: Boolean,
        ): Double {
            if (isBoundary) {
                ema = difficulty
                return 1.0
            }

            val prev = ema ?: difficulty
            val delta = difficulty - prev

            // Gentle flow adjustment - reduce variation for smoother cadence
            val multiplier =
                (1.0 + (delta * strength * speedStrength))
                    .coerceIn(1.0 - maxSlowdown, 1.0 + maxBoost)

            ema = prev + (alpha * (difficulty - prev))
            return multiplier
        }

        fun reset() {
            ema = null
        }
    }

    private fun isDecimalPoint(
        prevText: String,
        nextToken: Token?,
    ): Boolean {
        if (!prevText.any { it.isDigit() }) return false
        val nextText = nextToken?.text ?: return false
        return nextText.any { it.isDigit() }
    }

    private fun isThousandSeparator(
        prevText: String,
        nextToken: Token?,
    ): Boolean {
        if (prevText.isEmpty() || nextToken?.type != TokenType.WORD) return false
        if (!prevText.all { it.isDigit() }) return false
        val nextText = nextToken.text
        return nextText.length == 3 && nextText.all { it.isDigit() }
    }

    private fun isAbbreviationDot(
        prevWordText: String,
        nextToken: Token?,
    ): Boolean {
        val rawPrev = prevWordText.trim()
        if (rawPrev.isEmpty()) return false

        val normalized = rawPrev.trimEnd('.', ',', ';', ':').lowercase()
        val nextWord = nextToken?.takeIf { it.type == TokenType.WORD }?.text
        if (nextWord == null) return false

        val nextLetters = nextWord.filter { it.isLetter() }
        val nextFirst = nextLetters.firstOrNull()
        val nextStartsLower = nextFirst?.isLowerCase() == true
        val nextStartsUpper = nextFirst?.isUpperCase() == true
        val isSentenceStarter = nextWord.lowercase() in SENTENCE_STARTERS
        val nextIsInitial = nextLetters.length == 1 && nextLetters.all { it.isUpperCase() }

        if (normalized in TITLE_ABBREVIATIONS) return true

        if (normalized in KNOWN_ABBREVIATIONS) {
            return nextStartsLower || (nextStartsUpper && !isSentenceStarter) || nextIsInitial
        }

        val prevLetters = rawPrev.filter { it.isLetter() }
        if (prevLetters.isEmpty()) return false
        if (prevLetters.length == 1) {
            return nextStartsLower || (nextStartsUpper && !isSentenceStarter) || nextIsInitial
        }
        if (prevLetters.length <= 3 && prevLetters.all { it.isUpperCase() }) {
            return nextStartsLower || (nextStartsUpper && !isSentenceStarter) || nextIsInitial
        }

        return false
    }

    private fun isRhythmBoundaryPunctuation(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): Boolean =
        boundaryBeforeForPunctuation(
            token = token,
            prevWord = prevWord,
            nextToken = nextToken,
        ) != BoundaryBefore.NONE

    private companion object {
        private const val MIN_FRAME_MS = 40L
        private const val BASE_MS_PER_WORD_AT_300 = 200.0
        private const val DEFAULT_CLAUSE_PAUSE_FACTOR = 1.25
        private const val MIN_BLINK_MS = 16L
        private const val MAX_BLINK_MS = 22L
        private const val BLINK_EXTRA_MS = 6.0
        private const val BLINK_START_STRENGTH = 0.35
        private const val ADAPTIVE_EASE_THRESHOLD = 0.7
        private const val ADAPTIVE_DIFFICULTY_FLOOR = 0.35
        private const val CLAUSE_BOUNDARY_HOLD_MS = 55.0
        private const val ADAPTIVE_HOLD_MAX_MS = 160.0
        private const val EASY_PAIR_THRESHOLD = 0.72
        private const val TRANSITION_HOLD_BASE_MS = 4.0
        private const val TRANSITION_HOLD_EXTRA_MS = 6.0
        private const val COHERENCE_HOLD_MS = 5.0
        private const val PHRASE_BREAK_HOLD_MS = 8.0
        private const val FUNCTION_BRIDGE_HOLD_MS = 2.0
        private const val CLAUSE_START_HOLD_FRACTION = 0.18
        private const val SENTENCE_START_HOLD_FRACTION = 0.24
        private const val CLAUSE_START_MIN_HOLD_MS = 4.0
        private const val SENTENCE_START_MIN_HOLD_MS = 6.0
        private const val PARENTHETICAL_HOLD_FRACTION = 0.50
        private const val CLAUSE_LEAD_HOLD_FRACTION = 0.28
        private const val QUOTE_TRANSITION_HOLD_FRACTION = 0.55
        private const val DIALOGUE_ENTRY_BOOST = 0.08
        private const val NUMBER_EMPHASIS_BOOST = 0.12
        private const val PROPER_NOUN_BOOST = 0.08
        private const val ACRONYM_EMPHASIS_BOOST = 0.10
        private const val MAX_EMPHASIS_MULTIPLIER = 1.25
        private const val FUNCTION_WORD_GLIDE_STRONG = 0.09
        private const val FUNCTION_WORD_GLIDE_LIGHT = 0.05
        private const val CONTENT_WORD_STRESS_BOOST = 0.05
        private const val SEMANTIC_ANCHOR_BOOST = 0.10
        private const val FUNCTION_BRIDGE_COHERENCE_THRESHOLD = 0.65
        private const val MIN_PROSODY_MULTIPLIER = 0.88
        private const val MAX_PROSODY_MULTIPLIER = 1.18
        private const val CLAUSE_LEAD_BOOST_MS = 20.0
        private const val SENTENCE_END_BREAK_BOOST_MS = 40.0
        private const val EMBEDDED_QUOTE_FACTOR = 0.45
        private const val FLOW_EMA_ALPHA = 0.25
        private const val FLOW_MAX_BOOST = 0.04
        private const val FLOW_MAX_SLOWDOWN = 0.05
        private const val FLOW_STRENGTH = 0.12
        private const val MAX_BOUNDARY_TAIL_LIFT = 0.12
        private const val MIN_BOUNDARY_TAIL_LIFT_STRENGTH = 0.35
        private const val MIN_LANDING_HOLD_MS = 8.0
        private const val MAX_LANDING_HOLD_MS = 30.0
        private const val LANDING_HOLD_SPEED_BOOST = 0.35
        private const val CLAUSE_LANDING_HOLD_WEIGHT = 0.18
        private const val SEMICOLON_LANDING_HOLD_WEIGHT = 0.20
        private const val STRONG_LANDING_HOLD_WEIGHT = 0.22
        private const val ELLIPSIS_LANDING_HOLD_WEIGHT = 0.24
        private const val COMMA_RETENTION_BOOST = 0.03
        private const val QUOTE_RETENTION_BOOST = 0.04
        private const val PARENTHESIS_RETENTION_BOOST = 0.05
        private const val CLAUSE_PUNCTUATION_RETENTION_BOOST = 0.10
        private const val SEMICOLON_RETENTION_BOOST = 0.12
        private const val STRONG_PUNCTUATION_RETENTION_BOOST = 0.18
        private const val ELLIPSIS_RETENTION_BOOST = 0.20
        private const val PARAGRAPH_BREAK_RETENTION_BOOST = 0.22
        private const val PAGE_BREAK_RETENTION_BOOST = 0.26

        private val OPENING_PUNCTUATION = setOf('(', '[', '{', '\u201C', '\u2018')
        private val CURRENCY_PREFIX_PUNCTUATION = setOf('$', '€', '£', '¥')
        private val CURRENCY_NUMERIC_WORD_REGEX = Regex("""\d+(?:[.,]\d+)*""")

        private val QUOTE_OR_BRACKET_PUNCTUATION =
            setOf(
                '(',
                ')',
                '[',
                ']',
                '{',
                '}',
                '"',
                '\u201C',
                '\u201D',
                '\u2018',
                '\u2019',
            )

        private val SKIPPABLE_BOUNDARY_PUNCTUATION =
            setOf(
                '(',
                ')',
                '[',
                ']',
                '{',
                '}',
                '"',
                '\u201C',
                '\u201D',
                '\u2018',
                '\u2019',
            )

        private val TITLE_ABBREVIATIONS =
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

        private val KNOWN_ABBREVIATIONS =
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

        private val SENTENCE_STARTERS =
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

        private val GLUE_WORDS =
            setOf(
                // Articles
                "a",
                "an",
                "the",
                // Prepositions
                "of",
                "to",
                "in",
                "on",
                "at",
                "by",
                "for",
                "with",
                "from",
                "into",
                "onto",
                "upon",
                "about",
                "over",
                "under",
                "through",
                "between",
                "among",
                "against",
                "toward",
                "towards",
                // Conjunctions
                "and",
                "or",
                "but",
                "nor",
                "yet",
                "so",
                "as",
                "if",
                "than",
                "then",
                // Relative pronouns
                "that",
                "which",
                "who",
                "whom",
                "whose",
                // Auxiliary verbs
                "is",
                "are",
                "was",
                "were",
                "be",
                "been",
                "being",
                "has",
                "have",
                "had",
                "do",
                "does",
                "did",
                "will",
                "would",
                "can",
                "could",
                "shall",
                "should",
                "may",
                "might",
                "must",
                // Common pronouns
                "i",
                "me",
                "my",
                "we",
                "us",
                "our",
                "you",
                "your",
                "he",
                "him",
                "his",
                "she",
                "her",
                "it",
                "its",
                "they",
                "them",
                "their",
                // Negation
                "not",
                "no",
                // Common short words that flow naturally
                "all",
                "any",
                "some",
                "each",
                "every",
                "both",
                "few",
                "more",
                "most",
                "other",
                "such",
                "own",
                "same",
                "just",
                "only",
                "very",
                "too",
                "also",
                "still",
                "even",
                "now",
                "here",
                "there",
                "when",
                "where",
                "how",
                "why",
                "what",
                "this",
                "these",
                "those",
            )

        private val FUNCTION_WORDS =
            setOf(
                "a",
                "an",
                "the",
                "of",
                "to",
                "in",
                "on",
                "at",
                "by",
                "for",
                "with",
                "from",
                "into",
                "onto",
                "upon",
                "about",
                "over",
                "under",
                "through",
                "between",
                "among",
                "against",
                "toward",
                "towards",
                "and",
                "or",
                "nor",
                "yet",
                "so",
                "as",
                "if",
                "than",
                "then",
                "that",
                "which",
                "who",
                "whom",
                "whose",
                "is",
                "are",
                "was",
                "were",
                "be",
                "been",
                "being",
                "has",
                "have",
                "had",
                "do",
                "does",
                "did",
                "will",
                "would",
                "can",
                "could",
                "shall",
                "should",
                "may",
                "might",
                "must",
                "i",
                "me",
                "my",
                "we",
                "us",
                "our",
                "you",
                "your",
                "he",
                "him",
                "his",
                "she",
                "her",
                "it",
                "its",
                "they",
                "them",
                "their",
            )

        private val FUNCTION_BRIDGE_WORDS =
            setOf(
                "a",
                "an",
                "the",
                "of",
                "to",
                "in",
                "on",
                "at",
                "for",
                "with",
                "from",
                "by",
                "my",
                "your",
                "his",
                "her",
                "our",
                "their",
            )

        private val SEMANTIC_ANCHOR_WORDS =
            setOf(
                "not",
                "no",
                "never",
                "none",
                "nothing",
                "nobody",
                "neither",
                "nor",
                "only",
                "even",
                "except",
                "unless",
                "until",
                "however",
                "but",
                "yet",
                "despite",
                "although",
                "though",
            )

        private val TIGHT_PAIR_HINTS =
            setOf(
                // Article + preposition patterns
                "to the",
                "in the",
                "of the",
                "on the",
                "at the",
                "for the",
                "with the",
                "from the",
                "by the",
                "into the",
                "through the",
                "over the",
                "under the",
                "about the",
                "to a",
                "in a",
                "of a",
                "on a",
                "at a",
                "for a",
                "with a",
                "from a",
                "by a",
                "into a",
                "to an",
                "in an",
                "of an",
                "on an",
                // Possessive patterns
                "to my",
                "in my",
                "of my",
                "on my",
                "at my",
                "for my",
                "with my",
                "to his",
                "in his",
                "of his",
                "on his",
                "at his",
                "for his",
                "with his",
                "to her",
                "in her",
                "of her",
                "on her",
                "at her",
                "for her",
                "with her",
                "to their",
                "in their",
                "of their",
                "on their",
                "at their",
                "for their",
                "with their",
                "to our",
                "in our",
                "of our",
                "on our",
                "to your",
                "in your",
                "of your",
                // Comparative/conjunction patterns
                "as a",
                "as an",
                "as the",
                "as if",
                "so that",
                "such a",
                "such an",
                "but the",
                "and the",
                "or the",
                "and a",
                "or a",
                // Common verb patterns
                "is the",
                "is a",
                "is an",
                "was the",
                "was a",
                "was an",
                "are the",
                "were the",
                "has the",
                "has a",
                "had the",
                "had a",
                "have the",
                "have a",
                "will be",
                "would be",
                "can be",
                "could be",
                "should be",
                "must be",
                "may be",
                "might be",
                // Common idiom starters
                "it was",
                "it is",
                "there is",
                "there was",
                "there are",
                "there were",
                "this is",
                "that is",
                "what is",
                "who is",
                "i am",
                "i was",
                "i have",
                "i had",
                "i would",
                "i could",
                "he was",
                "he had",
                "he is",
                "she was",
                "she had",
                "she is",
                "they are",
                "they were",
                "they have",
                "they had",
                "we are",
                "we were",
                "we have",
                "we had",
                "you are",
                "you were",
                "you have",
                // Time expressions
                "at last",
                "at once",
                "at first",
                "of course",
                "in fact",
                "for now",
                "by now",
                "so far",
                "as yet",
                "no more",
                "no less",
                "not yet",
                // Common two-word phrases
                "all the",
                "all of",
                "one of",
                "some of",
                "most of",
                "each of",
                "many of",
                "much of",
                "none of",
                "part of",
                "out of",
                "up to",
                "due to",
                "next to",
                "close to",
                "back to",
                "down to",
            )
    }
}
