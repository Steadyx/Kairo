package com.kairo.reader.ui.rsvp

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.text.isQuoteOrBracket
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTier
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTimingPolicy

internal fun findReplaySentenceStartTokenIndex(tokens: List<Token>, currentTokenIndex: Int): Int {
    if (tokens.isEmpty()) return 0
    val current = findWordAtOrBefore(tokens, currentTokenIndex.coerceIn(tokens.indices))
    if (current < 0) return tokens.indexOfFirst { it.type == TokenType.WORD }.coerceAtLeast(0)
    var start = current
    while (start > 0 && !isSentenceBoundary(tokens, start - 1)) start--
    return (start..current).firstOrNull { tokens[it].type == TokenType.WORD } ?: current
}

private fun isSentenceBoundary(tokens: List<Token>, index: Int): Boolean {
    val token = tokens[index]
    if (token.type == TokenType.PARAGRAPH_BREAK || token.type == TokenType.PAGE_BREAK) return true
    if (token.type != TokenType.PUNCTUATION) return false
    if (token.text == "…") {
        val next = (index + 1 until tokens.size).firstOrNull { candidate ->
            tokens[candidate].type != TokenType.PUNCTUATION || !tokens[candidate].text.all(::isQuoteOrBracket)
        }?.let(tokens::get)
        // A lowercase continuation is a hesitation, even when the ellipsis carries a strong pause.
        return next?.type != TokenType.WORD || next.text.firstOrNull(Char::isLetter)?.isUpperCase() == true
    }
    val previousWord = tokens.getOrNull(index - 1)?.takeIf { it.type == TokenType.WORD }
    return RsvpPunctuationTimingPolicy.resolveTier(token, previousWord, tokens.getOrNull(index + 1)) ==
        RsvpPunctuationTier.SENTENCE_END
}
