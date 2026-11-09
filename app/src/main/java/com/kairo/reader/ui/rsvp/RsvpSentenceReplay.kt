package com.kairo.reader.ui.rsvp

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTier
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTimingPolicy

internal fun findReplaySentenceStartTokenIndex(tokens: List<Token>, currentTokenIndex: Int): Int {
    if (tokens.isEmpty()) return 0
    val current = currentTokenIndex.coerceIn(tokens.indices)
    var start = current
    while (start > 0 && !isSentenceBoundary(tokens, start - 1)) start--
    return (start..current).firstOrNull { tokens[it].type == TokenType.WORD } ?: current
}

private fun isSentenceBoundary(tokens: List<Token>, index: Int): Boolean {
    val token = tokens[index]
    if (token.type == TokenType.PARAGRAPH_BREAK || token.type == TokenType.PAGE_BREAK) return true
    if (token.type != TokenType.PUNCTUATION) return false
    val previousWord = (index - 1 downTo 0).firstOrNull { tokens[it].type == TokenType.WORD }?.let(tokens::get)
    return RsvpPunctuationTimingPolicy.resolveTier(token, previousWord, tokens.getOrNull(index + 1)) ==
        RsvpPunctuationTier.SENTENCE_END
}
