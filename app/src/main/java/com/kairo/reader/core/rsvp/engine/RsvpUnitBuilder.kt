package com.kairo.reader.core.rsvp.engine

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.analysis.isPhraseChunkCandidate
import com.kairo.reader.core.rsvp.segmentation.visibleCodePointCount
import com.kairo.reader.core.rsvp.text.isOpeningPunctuation
import com.kairo.reader.core.rsvp.text.isQuoteChar

internal fun buildUnit(
    expandedTokens: List<ExpandedToken>,
    startCursor: Int,
    config: RsvpConfig,
    state: ContextState,
    selectedWordCursors: List<Int>? = null,
): UnitBuildResult {
    val cursor = UnitCursor(expandedTokens, state, startCursor)
    cursor.consumeLeadingPunctuation()
    val firstWord = cursor.consumeFirstWord()
        ?: return UnitBuildResult(cursor.unitTokens, startCursor, cursor.index)
    if (selectedWordCursors == null) {
        cursor.consumePhraseWords(firstWord.token, config)
    } else {
        cursor.consumeSelectedPhraseWords(firstWord, config, selectedWordCursors)
    }
    cursor.consumeTrailingPunctuation()
    return UnitBuildResult(
        tokens = cursor.unitTokens,
        originalWordIndex = firstWord.originalIndex,
        nextCursor = cursor.index,
    )
}

private class UnitCursor(private val expandedTokens: List<ExpandedToken>, private val state: ContextState, startCursor: Int,) {
    val unitTokens = mutableListOf<Token>()
    var index = startCursor.coerceIn(0, expandedTokens.lastIndex)
        private set

    fun consumeLeadingPunctuation() {
        var scanning = true
        while (index < expandedTokens.size && scanning) {
            val token = expandedTokens[index].token
            val nextToken = expandedTokens.getOrNull(index + 1)?.token
            val isLeadingQuote =
                token.type == TokenType.PUNCTUATION &&
                    token.text.firstOrNull()?.let(::isQuoteChar) == true &&
                    nextToken?.type == TokenType.WORD
            scanning =
                token.type == TokenType.PUNCTUATION &&
                (isOpeningPunctuation(token, state, nextToken) || isLeadingQuote)
            if (scanning) consume(token)
        }
    }

    fun consumeFirstWord(): ExpandedToken? {
        while (index < expandedTokens.size && expandedTokens[index].token.type != TokenType.WORD) {
            index += 1
        }
        val firstWord = expandedTokens.getOrNull(index) ?: return null
        consume(firstWord.token)
        return firstWord
    }

    fun consumePhraseWords(
        firstWord: Token,
        config: RsvpConfig,
    ) {
        val maxWords = config.maxWordsPerUnit.coerceAtLeast(1)
        if (!config.enablePhraseChunking || maxWords <= 1) return
        val maxChars = config.maxCharsPerUnit.coerceAtLeast(1)
        var words = 1
        var characters = firstWord.text.length
        var canContinue = true
        while (words < maxWords && canContinue) {
            val candidate = expandedTokens.getOrNull(index)?.token
            val previousWord = unitTokens.lastOrNull { it.type == TokenType.WORD }
            val combinedCharacters = characters + (candidate?.text?.length ?: 0)
            canContinue =
                candidate?.type == TokenType.WORD &&
                previousWord != null &&
                combinedCharacters <= maxChars &&
                isPhraseChunkCandidate(previousWord, candidate)
            if (canContinue && candidate != null) {
                consume(candidate)
                words += 1
                characters = combinedCharacters
            }
        }
    }

    fun consumeSelectedPhraseWords(
        firstWord: ExpandedToken,
        config: RsvpConfig,
        selectedWordCursors: List<Int>,
    ) {
        if (selectedWordCursors.firstOrNull() != firstWord.expandedIndex) return
        val targetWords =
            selectedWordCursors.size
                .coerceAtLeast(1)
                .coerceAtMost(config.maxWordsPerUnit.coerceAtLeast(1))
        if (!config.enablePhraseChunking || targetWords <= 1) return
        val maxChars = config.maxCharsPerUnit.coerceAtLeast(1)
        var words = 1
        var characters = visibleCodePointCount(firstWord.token.text)
        while (words < targetWords) {
            val candidateExpanded = expandedTokens.getOrNull(index) ?: return
            if (candidateExpanded.expandedIndex != selectedWordCursors[words]) return
            val candidate = candidateExpanded.token
            val previousWord = unitTokens.lastOrNull { it.type == TokenType.WORD } ?: return
            val combinedCharacters = characters + visibleCodePointCount(candidate.text)
            val canConsume =
                candidate.type == TokenType.WORD &&
                    combinedCharacters <= maxChars &&
                    !previousWord.isSubwordChunk &&
                    !candidate.isSubwordChunk &&
                    !previousWord.text.endsWith("-")
            if (!canConsume) return
            consume(candidate)
            words += 1
            characters = combinedCharacters
        }
    }

    fun consumeTrailingPunctuation() {
        var scanning = true
        while (index < expandedTokens.size && scanning) {
            val token = expandedTokens[index].token
            val nextToken = expandedTokens.getOrNull(index + 1)?.token
            val isOpening = isOpeningPunctuation(token, state, nextToken)
            scanning = token.type == TokenType.PUNCTUATION && shouldAttach(token, nextToken, isOpening)
            if (scanning) {
                consume(token)
            }
        }
    }

    private fun shouldAttach(
        token: Token,
        nextToken: Token?,
        isOpening: Boolean,
    ): Boolean {
        val character = token.text.firstOrNull()
        val isQuote = character?.let(::isQuoteChar) == true
        val isExplicitOpeningQuote = character == '\u201C' || character == '\u2018'
        val quoteFollowedByWord =
            isQuote &&
                nextToken?.type == TokenType.WORD &&
                (isExplicitOpeningQuote || (character == '"' && isOpening))
        return !quoteFollowedByWord && !isOpening
    }

    private fun consume(token: Token) {
        unitTokens += token
        state.consume(token)
        index += 1
    }
}
