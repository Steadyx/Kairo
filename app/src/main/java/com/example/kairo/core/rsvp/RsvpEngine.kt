package com.example.kairo.core.rsvp

import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFrame
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.splitHyphenatedToken
import kotlin.math.max

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
                val extraPause = RsvpPacing.punctuationPauseMs(
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
                val extraPause = RsvpPacing.paragraphPauseMs(baseMsPerWord, config)
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
                    RsvpPacing.isStrongBoundaryPunctuation(
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

            var durationMs = RsvpPacing.calculateFrameDurationMs(
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
                        RsvpPacing.isStrongBoundaryPunctuation(
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

        RsvpPacing.applyRamping(frames, config)
        return frames
    }

    private companion object {
        private const val SMOOTHING_WINDOW = 8
        private val OPENING_PUNCTUATION = setOf('(', '[', '{', '"', '\u201C', '\u2018')
    }
}
