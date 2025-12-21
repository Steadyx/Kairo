@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "LongMethod",
    "LongParameterList",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "UnreachableCode"
)

package com.example.kairo.core.rsvp

import com.example.kairo.core.linguistics.ClauseDetector
import com.example.kairo.core.linguistics.DialogueAnalyzer
import com.example.kairo.core.model.BlinkMode
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
import kotlin.math.roundToLong

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
@Suppress("LargeClass", "TooManyFunctions")
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
        val flow = FlowState(
            alpha = FLOW_EMA_ALPHA,
            maxBoost = FLOW_MAX_BOOST,
            maxSlowdown = FLOW_MAX_SLOWDOWN,
            strength = FLOW_STRENGTH
        )

        while (cursor < expanded.size) {
            val cursorToken = expanded[cursor].token
            if (cursorToken.type == TokenType.PARAGRAPH_BREAK || cursorToken.type == TokenType.PAGE_BREAK) {
                val nextWordCursor = findFirstWordCursor(expanded, cursor + 1)
                if (nextWordCursor >= expanded.size) break

                val msPerWord = config.tempoMsPerWord.toDouble()
                val pauseScale = pauseScale(msPerWord, config)
                val extraPause = (cursorToken.pauseAfterMs.coerceAtLeast(0L).toDouble()) * pauseScale
                val durationMs = when (cursorToken.type) {
                    TokenType.PAGE_BREAK -> max(pageBreakBasePauseMs(config) * pauseScale, MIN_PAGE_BREAK_MS).toLong()
                    TokenType.PARAGRAPH_BREAK -> max(config.paragraphPauseMs.toDouble() * pauseScale, MIN_PARAGRAPH_BREAK_MS).toLong()
                    else -> 0L
                }.let { base ->
                    (base + extraPause).toLong()
                }.coerceAtLeast(MIN_FRAME_MS)

                frames += RsvpFrame(
                    tokens = listOf(breakMarkerToken(cursorToken.type)),
                    durationMs = durationMs,
                    originalTokenIndex = expanded[nextWordCursor].originalIndex
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

            val (frameTokens, frameOriginalIndex, nextCursor) = buildUnit(
                expandedTokens = expanded,
                startCursor = cursor,
                config = config,
                state = state
            )
            val prevTokenGlobal = expanded.getOrNull(cursor - 1)?.token
            val prevWordGlobal = findPrevWord(expanded, beforeIndex = cursor)
            val nextTokenGlobal = expanded.getOrNull(nextCursor)?.token
            val nextWordGlobal = expanded.getOrNull(findFirstWordCursor(expanded, nextCursor))?.token
            cursor = nextCursor

            val durationMs = computeUnitDurationMs(
                frameTokens = frameTokens,
                config = config,
                contextBefore = contextBefore,
                rhythm = rhythm,
                flow = flow,
                prevToken = prevTokenGlobal,
                prevWord = prevWordGlobal,
                nextToken = nextTokenGlobal,
                nextWord = nextWordGlobal,
                boundaryBefore = boundaryBefore
            )

            frames += RsvpFrame(tokens = frameTokens, durationMs = durationMs, originalTokenIndex = frameOriginalIndex)

            while (cursor < expanded.size && expanded[cursor].token.type != TokenType.WORD &&
                expanded[cursor].token.type != TokenType.PARAGRAPH_BREAK &&
                expanded[cursor].token.type != TokenType.PAGE_BREAK &&
                !(expanded[cursor].token.type == TokenType.PUNCTUATION && isOpeningPunctuation(expanded[cursor].token, state))
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
        state: ContextState
    ): UnitBuildResult {
        val unitTokens = mutableListOf<Token>()
        var cursor = startCursor.coerceIn(0, expandedTokens.lastIndex)
        var firstWordOriginalIndex: Int? = null

        // Allow opening punctuation right before the first word to be part of the unit.
        while (cursor < expandedTokens.size) {
            val token = expandedTokens[cursor].token
            if (token.type == TokenType.PUNCTUATION && isOpeningPunctuation(token, state)) {
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
                val effectiveMaxWords = max(2, config.maxWordsPerUnit)
                val withinLimits = effectiveMaxWords >= 2 && combinedChars <= config.maxCharsPerUnit

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

        // Attach closing punctuation + breaks to this unit.
        // If we hit a hard boundary (sentence end), keep consuming trailing closers like quotes/brackets
        // so we don't strand them on the next unit.
        var hitHardBoundary = false
        while (cursor < expandedTokens.size) {
            val token = expandedTokens[cursor].token
            if (token.type == TokenType.PUNCTUATION) {
                val isOpening = isOpeningPunctuation(token, state)
                if (hitHardBoundary && isOpening) break
                if (!isOpening) {
                    val prevWord = unitTokens.lastOrNull { it.type == TokenType.WORD }
                    unitTokens += token
                    state.consume(token)
                    cursor++

                    val nextToken = expandedTokens.getOrNull(cursor)?.token
                    if (!hitHardBoundary && isHardBoundaryPunctuation(token, prevWord = prevWord, nextToken = nextToken)) {
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
            nextCursor = cursor
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
        boundaryBefore: BoundaryBefore
    ): Long {
        val msPerWord = config.tempoMsPerWord.toDouble()

        val words = frameTokens.filter { it.type == TokenType.WORD }
        val paragraphBreaks = frameTokens.count { it.type == TokenType.PARAGRAPH_BREAK }
        val pageBreaks = frameTokens.count { it.type == TokenType.PAGE_BREAK }
        val firstWordIndex = frameTokens.indexOfFirst { it.type == TokenType.WORD }
        val speedStrength = speedStrength(msPerWord)
        val startBoost = startBoostMultiplier(msPerWord = msPerWord, boundaryBefore = boundaryBefore)
        val clauseConfigStrength = ((config.clausePauseFactor - 1.0) / (DEFAULT_CLAUSE_PAUSE_FACTOR - 1.0)).coerceIn(0.0, 2.0)
        val dialogueEntryBoost = 1.0 + (DIALOGUE_ENTRY_BOOST * speedStrength)
        val speakerTagMultiplier = speakerTagMultiplier(
            wordsInFrame = words,
            prevWord = prevWord,
            nextWord = nextWord,
            config = config
        )

        var duration = 0.0
        var parentheticalDepth = contextBefore.parentheticalDepth
        var inDialogue = contextBefore.inDialogue

        frameTokens.forEachIndexed { index, token ->
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
                    val dialogueMultiplier = if (config.useDialogueDetection && inDialogue) config.dialogueMultiplier else 1.0
                    val contextWordMultiplier =
                        (if (parentheticalDepth > 0) config.parentheticalMultiplier else 1.0) *
                            dialogueMultiplier
                    val boosted = if (index == firstWordIndex) startBoost else 1.0
                    val nextWordText = frameTokens.subList(index + 1, frameTokens.size)
                        .firstOrNull { it.type == TokenType.WORD }
                        ?.text
                        ?: nextWord?.text

                    val clauseMultiplier = if (!config.useClausePausing) {
                        1.0
                    } else {
                        val raw = ClauseDetector.getClausePauseFactor(token.text, nextWordText)
                        1.0 + ((raw - 1.0) * speedStrength * clauseConfigStrength)
                    }

                    val terminalMultiplier = terminalWordMultiplier(
                        wordIndex = index,
                        word = token,
                        frameTokens = frameTokens,
                        nextToken = nextToken,
                        speedStrength = speedStrength
                    )

                    val emphasisMultiplier = emphasisMultiplier(
                        token = token,
                        isFirstWord = index == firstWordIndex,
                        boundaryBefore = boundaryBefore,
                        speedStrength = speedStrength
                    )

                    val dialogueEntryMultiplier = if (config.useDialogueDetection &&
                        !contextBefore.inDialogue &&
                        index == firstWordIndex &&
                        token.isDialogue
                    ) {
                        dialogueEntryBoost
                    } else {
                        1.0
                    }

                    val wordMs = wordDurationMs(token, msPerWord, config) *
                        contextWordMultiplier *
                        boosted *
                        clauseMultiplier *
                        terminalMultiplier *
                        emphasisMultiplier *
                        dialogueEntryMultiplier *
                        speakerTagMultiplier
                    duration += max(wordMs, wordFloorMs(token, config).toDouble())
                    if (token.pauseAfterMs > 0L) {
                        duration += token.pauseAfterMs * pauseScale(msPerWord, config)
                    }
                }
                else -> Unit
            }
        }

        val transitionHold = transitionHoldMs(
            frameTokens = frameTokens,
            firstWord = words.firstOrNull(),
            nextWord = nextWord,
            speedStrength = speedStrength
        )
        if (transitionHold > 0.0) {
            duration += transitionHold
        }

        duration *= multiWordPenalty(words.size)

        val pauseScale = pauseScale(msPerWord, config)
        frameTokens.forEachIndexed { index, token ->
            if (token.type != TokenType.PUNCTUATION) return@forEachIndexed

            val prevTokenInFrame = frameTokens.getOrNull(index - 1)
            val nextTokenInFrame = frameTokens.getOrNull(index + 1)
            if (shouldSkipPunctuationPause(
                    token = token,
                    index = index,
                    firstWordIndex = firstWordIndex,
                    prevToken = prevTokenInFrame,
                    nextToken = nextTokenInFrame
                )
            ) {
                return@forEachIndexed
            }

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
        if (pageBreaks > 0) {
            val scaled = pageBreakBasePauseMs(config) * pauseScale
            duration += max(scaled, MIN_PAGE_BREAK_MS) * pageBreaks
        }

        val hardBoundary = isHardBoundary(frameTokens, nextToken)
        val difficulty = frameDifficulty(words)
        duration *= flow.apply(
            difficulty = difficulty,
            speedStrength = speedStrength,
            isBoundary = hardBoundary
        )

        val smoothed = rhythm.apply(duration, isBoundary = hardBoundary)
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

        var base = when {
            ch == '.' -> {
                when {
                    isDecimalPoint(prevText, nextToken) -> 0.0
                    isAbbreviationDot(prevText, nextToken) -> config.commaPauseMs * 0.35
                    else -> config.sentenceEndPauseMs.toDouble()
                }
            }
            isSentenceEndingPunctuation(ch) -> config.sentenceEndPauseMs.toDouble()
            ch == ',' -> if (isThousandSeparator(prevText, nextToken)) 0.0 else config.commaPauseMs.toDouble()
            ch == ';' -> config.semicolonPauseMs.toDouble()
            ch == ':' -> config.colonPauseMs.toDouble()
            ch == '\u2014' || ch == '\u2013' || ch == '-' -> config.dashPauseMs.toDouble()
            ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}' -> config.parenthesesPauseMs.toDouble()
            ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019' -> config.quotePauseMs.toDouble()
            isMidSentencePunctuation(ch) -> config.commaPauseMs * 0.85
            else -> 0.0
        }

        var floor = when {
            ch == '.' -> {
                if (isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken)) 0.0 else 125.0
            }
            isSentenceEndingPunctuation(ch) -> 125.0
            ch == ',' -> if (isThousandSeparator(prevText, nextToken)) 0.0 else 70.0
            ch == ';' -> 95.0
            ch == ':' -> 85.0
            ch == '\u2014' || ch == '\u2013' || ch == '-' -> 90.0
            ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}' -> 45.0
            ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019' -> 35.0
            else -> 0.0
        }

        if (ch == '.' && isLikelySentenceContinuation(nextToken) &&
            !isDecimalPoint(prevText, nextToken) &&
            !isAbbreviationDot(prevText, nextToken)
        ) {
            base = min(base, config.commaPauseMs * 0.8)
            floor = min(floor, 80.0)
        }

        val speedStrength = speedStrength(msPerWord)
        if (isClauseLeadPunctuation(ch, nextToken)) {
            base += CLAUSE_LEAD_BOOST_MS * speedStrength
        }

        if (isSentenceEndingPunctuation(ch) || ch == '.') {
            if (nextToken?.type == TokenType.PARAGRAPH_BREAK || nextToken?.type == TokenType.PAGE_BREAK) {
                base += SENTENCE_END_BREAK_BOOST_MS * speedStrength
            }
        }

        if (isEmbeddedQuote(ch, prevWord, nextToken)) {
            base *= EMBEDDED_QUOTE_FACTOR
            floor *= EMBEDDED_QUOTE_FACTOR
        }

        val scaled = base * pauseScale
        return max(scaled, floor)
    }

    private fun pauseScale(msPerWord: Double, config: RsvpConfig): Double {
        val ratio = (msPerWord / BASE_MS_PER_WORD_AT_300).coerceIn(0.12, 2.5)
        val scaled = ratio.pow(config.pauseScaleExponent)
        return max(config.minPauseScale, scaled)
    }

    private fun emphasisMultiplier(
        token: Token,
        isFirstWord: Boolean,
        boundaryBefore: BoundaryBefore,
        speedStrength: Double
    ): Double {
        val text = token.text
        if (text.isEmpty()) return 1.0

        val isSentenceStart = isFirstWord && boundaryBefore != BoundaryBefore.NONE
        val letters = text.filter { it.isLetter() }
        val hasDigits = text.any { it.isDigit() }
        val isAcronym = letters.length in 2..5 && letters.isNotEmpty() && letters.all { it.isUpperCase() }
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

    private fun speakerTagMultiplier(
        wordsInFrame: List<Token>,
        prevWord: Token?,
        nextWord: Token?,
        config: RsvpConfig
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
                if (prevText != null && nextText != null) candidates += listOf(prevText, current, nextText)
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
        speedStrength: Double
    ): Double {
        if (firstWord == null || nextWord == null) return 0.0
        if (frameTokens.count { it.type == TokenType.WORD } != 1) return 0.0
        if (frameTokens.any { it.type == TokenType.PUNCTUATION }) return 0.0
        if (!shouldPreferHold(firstWord, nextWord)) return 0.0

        val hold = TRANSITION_HOLD_BASE_MS + (TRANSITION_HOLD_EXTRA_MS * speedStrength)
        return hold.coerceAtLeast(0.0)
    }

    private fun frameDifficulty(words: List<Token>): Double {
        if (words.isEmpty()) return 0.0
        val total = words.sumOf { (1.0 - wordEase(it)) }
        return (total / words.size).coerceIn(0.0, 1.0)
    }

    private fun shouldPreferHold(prev: Token, next: Token): Boolean {
        val prevLower = prev.text.lowercase()
        val nextLower = next.text.lowercase()
        val pairKey = "$prevLower $nextLower"
        val isHinted = pairKey in TIGHT_PAIR_HINTS
        val gluePair = prevLower in GLUE_WORDS && nextLower in GLUE_WORDS &&
            prev.text.length <= 4 && next.text.length <= 4
        val easyPair = wordEase(prev) >= EASY_PAIR_THRESHOLD && wordEase(next) >= EASY_PAIR_THRESHOLD

        return easyPair && (isHinted || gluePair)
    }

    private fun isClauseLeadPunctuation(ch: Char, nextToken: Token?): Boolean {
        if (ch != ',' && ch != ';' && ch != ':' && ch != '\u2014' && ch != '\u2013' && ch != '-') return false
        val nextWord = nextToken?.takeIf { it.type == TokenType.WORD } ?: return false
        val nextLower = nextWord.text.lowercase()
        return ClauseDetector.isClauseBoundary(nextLower) || ClauseDetector.isCoordinatingConjunction(nextLower)
    }

    private fun isLikelySentenceContinuation(nextToken: Token?): Boolean {
        val nextWord = nextToken?.takeIf { it.type == TokenType.WORD } ?: return false
        val firstChar = nextWord.text.firstOrNull() ?: return false
        return firstChar.isLowerCase()
    }

    private fun isEmbeddedQuote(ch: Char, prevWord: Token?, nextToken: Token?): Boolean {
        val isQuote = ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019'
        if (!isQuote) return false

        val nextCh = nextToken?.text?.firstOrNull()
        val nextIsPunct = nextToken?.type == TokenType.PUNCTUATION
        val adjacentSentencePunct = nextIsPunct && nextCh != null &&
            (isSentenceEndingPunctuation(nextCh) || isMidSentencePunctuation(nextCh))

        return adjacentSentencePunct || (prevWord != null && nextToken?.type == TokenType.WORD)
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

    private fun terminalWordMultiplier(
        wordIndex: Int,
        word: Token,
        frameTokens: List<Token>,
        nextToken: Token?,
        speedStrength: Double
    ): Double {
        val punctIndex = (wordIndex + 1 until frameTokens.size).firstOrNull { frameTokens[it].type == TokenType.PUNCTUATION }
            ?: return 1.0
        if (frameTokens.subList(wordIndex + 1, punctIndex).any { it.type == TokenType.WORD }) return 1.0

        val punctToken = frameTokens[punctIndex]
        val ch = punctToken.text.firstOrNull() ?: return 1.0

        val tokenAfterPunct = frameTokens.getOrNull(punctIndex + 1) ?: nextToken

        if (ch == ',' && isThousandSeparator(word.text, tokenAfterPunct)) return 1.0
        if (ch == '.' && isDecimalPoint(word.text, tokenAfterPunct)) return 1.0

        val extra = when {
            isHardBoundaryPunctuation(punctToken, prevWord = word, nextToken = tokenAfterPunct) -> 0.10
            ch == ',' -> 0.06
            ch == ':' -> 0.07
            ch == '\u2014' || ch == '\u2013' || ch == '-' -> 0.06
            else -> 0.0
        }

        return 1.0 + (extra * speedStrength)
    }

    private fun breakMarkerToken(type: TokenType): Token = when (type) {
        TokenType.PAGE_BREAK -> Token(text = "• • •", type = TokenType.PUNCTUATION)
        TokenType.PARAGRAPH_BREAK -> Token(text = " ", type = TokenType.PUNCTUATION)
        else -> Token(text = " ", type = TokenType.PUNCTUATION)
    }

    private fun isOpeningPunctuation(token: Token, state: ContextState): Boolean {
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
        nextToken: Token?
    ): Boolean {
        val ch = token.text.firstOrNull() ?: return true
        if (index < firstWordIndex && isOpeningPunctuationChar(ch)) return true

        val prevIsPunct = prevToken?.type == TokenType.PUNCTUATION
        val nextIsPunct = nextToken?.type == TokenType.PUNCTUATION
        val prevCh = prevToken?.text?.firstOrNull()

        if (isQuoteOrBracket(ch) && (prevIsPunct || nextIsPunct)) return true

        val isSentenceEnd = isSentenceEndingPunctuation(ch) || ch == '.'
        val prevIsSentenceEnd = prevCh != null && (isSentenceEndingPunctuation(prevCh) || prevCh == '.')
        return isSentenceEnd && prevIsSentenceEnd
    }

    private fun isOpeningPunctuationChar(ch: Char): Boolean = ch == '"' || ch in OPENING_PUNCTUATION

    private fun isQuoteOrBracket(ch: Char): Boolean = ch in QUOTE_OR_BRACKET_PUNCTUATION

    private fun isHardBoundaryPunctuation(
        token: Token,
        prevWord: Token?,
        nextToken: Token?
    ): Boolean {
        val ch = token.text.firstOrNull() ?: return false
        val prevText = prevWord?.text.orEmpty()
        return when {
            ch == '.' -> {
                !isDecimalPoint(prevText, nextToken) && !isAbbreviationDot(prevText, nextToken)
            }
            isSentenceEndingPunctuation(ch) -> true
            ch == ';' -> true
            else -> false
        }
    }

    private fun isHardBoundary(tokens: List<Token>, nextToken: Token?): Boolean {
        if (tokens.any { it.type == TokenType.PARAGRAPH_BREAK || it.type == TokenType.PAGE_BREAK }) return true

        for (i in tokens.indices) {
            val token = tokens[i]
            if (token.type != TokenType.PUNCTUATION) continue

            val prevWord = tokens.subList(0, i).lastOrNull { it.type == TokenType.WORD }
            val nextWord = tokens.subList(i + 1, tokens.size).firstOrNull { it.type == TokenType.WORD }
            if (isRhythmBoundaryPunctuation(token, prevWord = prevWord, nextToken = nextWord ?: nextToken)) return true
        }

        return false
    }

    private fun wordFloorMs(word: Token, config: RsvpConfig): Long {
        val letters = word.text.count { it.isLetterOrDigit() }
        return if (letters >= config.longWordChars) config.longWordMinMs else config.minWordMs
    }

    private fun pageBreakBasePauseMs(config: RsvpConfig): Double =
        max(config.paragraphPauseMs.toDouble() * 1.75, config.sentenceEndPauseMs.toDouble() * 1.4)

    private fun startBoostMultiplier(msPerWord: Double, boundaryBefore: BoundaryBefore): Double {
        val strength = speedStrength(msPerWord)
        val maxExtra = when (boundaryBefore) {
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

    private fun findFirstWordCursor(expandedTokens: List<ExpandedToken>, startCursor: Int): Int {
        var cursor = startCursor.coerceAtLeast(0)
        while (cursor < expandedTokens.size && expandedTokens[cursor].token.type != TokenType.WORD) cursor++
        return cursor
    }

    private fun boundaryBefore(expandedTokens: List<ExpandedToken>, wordCursor: Int): BoundaryBefore {
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
                    return if (isHardBoundaryPunctuation(token, prevWord = prevWord, nextToken = nextToken)) {
                        BoundaryBefore.SENTENCE
                    } else {
                        BoundaryBefore.NONE
                    }
                }
                TokenType.WORD -> return BoundaryBefore.NONE
            }
        }
        return BoundaryBefore.NONE
    }

    private fun findPrevWord(expandedTokens: List<ExpandedToken>, beforeIndex: Int): Token? {
        var cursor = beforeIndex - 1
        while (cursor >= 0) {
            val token = expandedTokens[cursor].token
            if (token.type == TokenType.WORD) return token
            cursor--
        }
        return null
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

    private fun applyBlinkSeparation(frames: MutableList<RsvpFrame>, config: RsvpConfig) {
        if (frames.size < 2) return

        val strength = speedStrength(config.tempoMsPerWord.toDouble())
        if (strength < BLINK_START_STRENGTH) return
        val normalizedStrength = ((strength - BLINK_START_STRENGTH) / (1.0 - BLINK_START_STRENGTH))
            .coerceIn(0.0, 1.0)
        val easedStrength = normalizedStrength * normalizedStrength
        val targetBlinkMs = (MIN_BLINK_MS.toDouble() + (BLINK_EXTRA_MS * easedStrength))
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

            if (hasWord && nextHasWord) {
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
                val weight = when (config.blinkMode) {
                    BlinkMode.SUBTLE -> punctuationFactor
                    BlinkMode.ADAPTIVE -> {
                        val ease = (wordEase(firstWord) + wordEase(nextWord ?: firstWord)) * 0.5
                        if (ease >= ADAPTIVE_EASE_THRESHOLD) punctuationFactor else 0.0
                    }
                    BlinkMode.OFF -> 0.0
                }
                val blinkMs = min((targetBlinkMs * weight).roundToLong(), maxBlink)
                if (blinkMs >= MIN_BLINK_MS) {
                    output += frame.copy(durationMs = (frame.durationMs - blinkMs).coerceAtLeast(MIN_FRAME_MS))
                    output += RsvpFrame(tokens = listOf(blinkToken), durationMs = blinkMs, originalTokenIndex = frame.originalTokenIndex)
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
        val complexityScore = (word.complexityMultiplier - 1.0).coerceAtLeast(0.0).coerceIn(0.0, 1.0)

        val difficulty = (lengthScore * 0.35) +
            (syllableScore * 0.25) +
            (rarityScore * 0.25) +
            (complexityScore * 0.15)

        return (1.0 - difficulty).coerceIn(0.0, 1.0)
    }

    private fun blinkPunctuationFactor(tokens: List<Token>): Double {
        val hasMidPause = tokens.any { token ->
            val ch = token.text.firstOrNull() ?: return@any false
            token.type == TokenType.PUNCTUATION && isMidSentencePunctuation(ch)
        }
        return if (hasMidPause) 0.55 else 1.0
    }

    private data class ExpandedToken(val token: Token, val originalIndex: Int)

    private data class UnitBuildResult(
        val tokens: List<Token>,
        val originalWordIndex: Int,
        val nextCursor: Int
    )

    private enum class BoundaryBefore { NONE, SENTENCE, PARAGRAPH, PAGE }

    private class ContextState {
        var parentheticalDepth: Int = 0
            private set
        var straightQuoteOpen: Boolean = false
            private set
        var inDialogue: Boolean = false
            private set

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
                '"' -> straightQuoteOpen = !straightQuoteOpen
                '\u201C', '\u2018' -> Unit
                '\u201D', '\u2019' -> Unit
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

        fun reset() {
            ema = null
        }
    }

    private class FlowState(
        private val alpha: Double,
        private val maxBoost: Double,
        private val maxSlowdown: Double,
        private val strength: Double
    ) {
        private var ema: Double? = null

        fun apply(difficulty: Double, speedStrength: Double, isBoundary: Boolean): Double {
            if (isBoundary) {
                ema = difficulty
                return 1.0
            }

            val prev = ema ?: difficulty
            val delta = difficulty - prev
            val multiplier = (1.0 + (delta * strength * speedStrength))
                .coerceIn(1.0 - maxSlowdown, 1.0 + maxBoost)

            ema = prev + (alpha * (difficulty - prev))
            return multiplier
        }

        fun reset() {
            ema = null
        }
    }

    private fun isDecimalPoint(prevText: String, nextToken: Token?): Boolean {
        if (!prevText.any { it.isDigit() }) return false
        return nextToken?.type == TokenType.WORD && nextToken.text.any { it.isDigit() }
    }

    private fun isThousandSeparator(prevText: String, nextToken: Token?): Boolean {
        if (prevText.isEmpty() || nextToken?.type != TokenType.WORD) return false
        if (!prevText.all { it.isDigit() }) return false
        val nextText = nextToken.text
        return nextText.length == 3 && nextText.all { it.isDigit() }
    }

    private fun isAbbreviationDot(prevWordText: String, nextToken: Token?): Boolean {
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
        nextToken: Token?
    ): Boolean {
        if (isHardBoundaryPunctuation(token, prevWord = prevWord, nextToken = nextToken)) return true
        val ch = token.text.firstOrNull() ?: return false
        return ch == ':' || ch == '\u2014' || ch == '\u2013' || ch == '-'
    }

    private companion object {
        private const val MIN_FRAME_MS = 40L
        private const val MIN_PARAGRAPH_BREAK_MS = 140.0
        private const val MIN_PAGE_BREAK_MS = 240.0
        private const val BASE_MS_PER_WORD_AT_300 = 200.0
        private const val DEFAULT_CLAUSE_PAUSE_FACTOR = 1.25
        private const val MIN_BLINK_MS = 16L
        private const val MAX_BLINK_MS = 22L
        private const val BLINK_EXTRA_MS = 6.0
        private const val BLINK_START_STRENGTH = 0.35
        private const val ADAPTIVE_EASE_THRESHOLD = 0.7
        private const val EASY_PAIR_THRESHOLD = 0.72
        private const val TRANSITION_HOLD_BASE_MS = 6.0
        private const val TRANSITION_HOLD_EXTRA_MS = 10.0
        private const val DIALOGUE_ENTRY_BOOST = 0.06
        private const val NUMBER_EMPHASIS_BOOST = 0.12
        private const val PROPER_NOUN_BOOST = 0.08
        private const val ACRONYM_EMPHASIS_BOOST = 0.10
        private const val MAX_EMPHASIS_MULTIPLIER = 1.25
        private const val CLAUSE_LEAD_BOOST_MS = 20.0
        private const val SENTENCE_END_BREAK_BOOST_MS = 40.0
        private const val EMBEDDED_QUOTE_FACTOR = 0.45
        private const val FLOW_EMA_ALPHA = 0.22
        private const val FLOW_MAX_BOOST = 0.08
        private const val FLOW_MAX_SLOWDOWN = 0.10
        private const val FLOW_STRENGTH = 0.16

        private val OPENING_PUNCTUATION = setOf('(', '[', '{', '\u201C', '\u2018')

        private val QUOTE_OR_BRACKET_PUNCTUATION = setOf(
            '(', ')', '[', ']', '{', '}',
            '"', '\u201C', '\u201D', '\u2018', '\u2019'
        )

        private val SKIPPABLE_BOUNDARY_PUNCTUATION = setOf(
            '(', ')', '[', ']', '{', '}',
            '"', '\u201C', '\u201D', '\u2018', '\u2019'
        )

        private val TITLE_ABBREVIATIONS = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "rev", "fr"
        )

        private val KNOWN_ABBREVIATIONS = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc",
            "e.g", "i.e", "eg", "ie", "no", "vol", "fig", "al",
            "inc", "ltd", "dept", "est", "approx", "misc",
            "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept",
            "oct", "nov", "dec", "u.s", "u.k", "u.n"
        )

        private val SENTENCE_STARTERS = setOf(
            "i", "he", "she", "they", "we", "it", "the", "a", "an", "this", "that", "these", "those"
        )

        private val GLUE_WORDS = setOf(
            "a", "an", "the",
            "of", "to", "in", "on", "at", "by", "for", "with", "from",
            "and", "or", "but", "nor", "yet", "so",
            "as", "if", "than", "then", "that", "which", "who", "whom", "whose",
            "is", "are", "was", "were", "be", "been", "being",
            "not", "no"
        )

        private val TIGHT_PAIR_HINTS = setOf(
            "to the", "in the", "of the", "on the", "at the", "for the",
            "to a", "in a", "of a", "on a", "at a", "for a",
            "to my", "in my", "of my", "on my", "at my",
            "to his", "to her", "to their",
            "as a", "as the", "as if", "as a", "as an"
        )
    }
}
