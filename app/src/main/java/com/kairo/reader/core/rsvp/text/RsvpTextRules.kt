package com.kairo.reader.core.rsvp.text

import com.kairo.reader.core.linguistics.ClauseDetector
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.isMidSentencePunctuation
import com.kairo.reader.core.model.isSentenceEndingPunctuation
import com.kairo.reader.core.rsvp.engine.BoundaryBefore
import com.kairo.reader.core.rsvp.engine.CURRENCY_NUMERIC_WORD_REGEX
import com.kairo.reader.core.rsvp.engine.CURRENCY_PREFIX_PUNCTUATION
import com.kairo.reader.core.rsvp.engine.ContextState
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import com.kairo.reader.core.rsvp.engine.OPENING_PUNCTUATION
import com.kairo.reader.core.rsvp.engine.QUOTE_OR_BRACKET_PUNCTUATION
import com.kairo.reader.core.rsvp.engine.SKIPPABLE_BOUNDARY_PUNCTUATION
import com.kairo.reader.core.rsvp.timing.KNOWN_ABBREVIATIONS
import com.kairo.reader.core.rsvp.timing.SENTENCE_STARTERS
import com.kairo.reader.core.rsvp.timing.TITLE_ABBREVIATIONS
import kotlin.math.max

internal fun isClauseLeadPunctuation(
    ch: Char,
    nextToken: Token?,
): Boolean {
    if (ch !in CLAUSE_LEAD_PUNCTUATION) {
        return false
    }
    val nextWord = nextToken?.takeIf { it.type == TokenType.WORD } ?: return false
    val nextLower = nextWord.text.lowercase()
    return ClauseDetector.isClauseBoundary(nextLower) ||
        ClauseDetector.isCoordinatingConjunction(nextLower)
}

private val CLAUSE_LEAD_PUNCTUATION = setOf(',', ';', ':', '\u2014', '\u2013', '-')

internal fun isEmbeddedQuote(
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
            (
                isSentenceEndingPunctuation(nextCh) ||
                    isMidSentencePunctuation(nextCh) ||
                    nextCh == ')' ||
                    nextCh == ']' ||
                    nextCh == '}'
                )

    return adjacentSentencePunct || (prevWord != null && nextToken?.type == TokenType.WORD)
}

internal fun breakMarkerToken(type: TokenType): Token =
    when (type) {
        TokenType.PAGE_BREAK -> Token(text = " ", type = TokenType.PUNCTUATION)
        TokenType.PARAGRAPH_BREAK -> Token(text = " ", type = TokenType.PUNCTUATION)
        else -> Token(text = " ", type = TokenType.PUNCTUATION)
    }

internal fun isOpeningPunctuation(
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

internal fun shouldSkipPunctuationPause(
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

internal fun isOpeningPunctuationChar(ch: Char): Boolean = ch == '"' || ch in OPENING_PUNCTUATION

internal fun updateParentheticalDepthAfterPunctuation(
    currentDepth: Int,
    token: Token,
): Int {
    val ch = token.text.firstOrNull() ?: return currentDepth
    return when (ch) {
        '(', '[', '{' -> currentDepth + 1
        ')', ']', '}' -> max(0, currentDepth - 1)
        else -> currentDepth
    }
}

internal fun isCurrencyPrefixPunctuation(
    token: Token,
    nextToken: Token?,
): Boolean {
    val ch = token.text.firstOrNull() ?: return false
    if (ch !in CURRENCY_PREFIX_PUNCTUATION) return false
    val nextWordText = nextToken?.takeIf { it.type == TokenType.WORD }?.text ?: return false
    return CURRENCY_NUMERIC_WORD_REGEX.matches(nextWordText)
}

internal fun isQuoteOrBracket(ch: Char): Boolean = ch in QUOTE_OR_BRACKET_PUNCTUATION

internal fun isQuoteChar(ch: Char): Boolean =
    ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019'

internal fun isHardBoundaryPunctuation(
    token: Token,
    prevWord: Token?,
    nextToken: Token?,
): Boolean =
    boundaryBeforeForPunctuation(
        token = token,
        prevWord = prevWord,
        nextToken = nextToken,
    ) == BoundaryBefore.SENTENCE

internal fun boundaryBeforeForPunctuation(
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
        ch == '\u2026' -> BoundaryBefore.SENTENCE
        ch == ';' -> BoundaryBefore.CLAUSE
        ch == ':' || ch == '\u2014' || ch == '\u2013' || ch == '-' -> BoundaryBefore.CLAUSE
        ch == ',' && isClauseLeadPunctuation(ch, nextToken) -> BoundaryBefore.CLAUSE
        isSentenceEndingPunctuation(ch) -> BoundaryBefore.SENTENCE
        else -> BoundaryBefore.NONE
    }
}

internal fun isHardBoundary(
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

internal fun findFirstWordCursor(
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

internal fun boundaryBefore(
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

internal fun findPrevWord(
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

internal fun isDecimalPoint(
    prevText: String,
    nextToken: Token?,
): Boolean {
    if (prevText.isEmpty() || !prevText.all { it.isDigit() }) return false
    if (nextToken?.type != TokenType.WORD) return false
    val nextText = nextToken.text
    if (nextText.isEmpty() || !nextText.all { it.isDigit() }) return false
    return nextText.length <= MAX_SEPARATED_DECIMAL_FRACTION_DIGITS
}

private const val MAX_SEPARATED_DECIMAL_FRACTION_DIGITS = 3

internal fun isThousandSeparator(
    prevText: String,
    nextToken: Token?,
): Boolean {
    if (prevText.isEmpty() || nextToken?.type != TokenType.WORD) return false
    if (!prevText.all { it.isDigit() }) return false
    val nextText = nextToken.text
    return nextText.length == THOUSANDS_GROUP_DIGITS && nextText.all { it.isDigit() }
}

internal fun isAbbreviationDot(
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

    // "No." is an abbreviation before a number, but a complete answer before prose.
    if (normalized == "no") return nextWord.firstOrNull()?.isDigit() == true
    val prevLetters = rawPrev.filter { it.isLetter() }
    val canContinueAbbreviation = !isSentenceStarter && (nextStartsLower || nextStartsUpper || nextIsInitial)
    val personalInitial = prevLetters.length == 1 && prevLetters.all(Char::isUpperCase) && nextStartsUpper
    return normalized in TITLE_ABBREVIATIONS ||
        (canContinueAbbreviation && (normalized in KNOWN_ABBREVIATIONS || personalInitial))
}

private const val THOUSANDS_GROUP_DIGITS = 3

internal fun isRhythmBoundaryPunctuation(
    token: Token,
    prevWord: Token?,
    nextToken: Token?,
): Boolean =
    boundaryBeforeForPunctuation(
        token = token,
        prevWord = prevWord,
        nextToken = nextToken,
    ) != BoundaryBefore.NONE
