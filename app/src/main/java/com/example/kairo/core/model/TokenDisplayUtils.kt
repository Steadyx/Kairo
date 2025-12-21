@file:Suppress("MagicNumber")

package com.example.kairo.core.model

private val openingPunctuationCharsForDisplay = setOf('"', '\u201C', '\u2018', '(', '[', '{')
private val closingPunctuationCharsForDisplay =
    setOf(
        '.',
        ',',
        ';',
        ':',
        '!',
        '?',
        '"',
        '\u201D',
        '\u2019',
        ')',
        ']',
        '}',
        '\u2014',
        '\u2013', // Em-dash — and en-dash –
        '\u2026', // Ellipsis …
    )

private val dashJoinersForDisplay = setOf('\u2014', '\u2013')

private fun Token.singleCharOrNull(): Char? = if (text.length == 1) text[0] else null

private fun Token.isPunctuationIn(chars: Set<Char>): Boolean =
    type == TokenType.PUNCTUATION && singleCharOrNull()?.let(chars::contains) == true

/**
 * Returns whether a space should be inserted before [token] when rendering tokens as human-readable text.
 *
 * This is used by the scrollable Reader view so punctuation spacing stays stable regardless of the original
 * whitespace in the source.
 */
fun shouldInsertSpaceBeforeToken(
    token: Token,
    prevToken: Token?,
    tokenIndexInParagraph: Int,
): Boolean {
    if (tokenIndexInParagraph == 0) return false

    val isClosing = token.isPunctuationIn(closingPunctuationCharsForDisplay)
    val prevWasOpening = prevToken?.isPunctuationIn(openingPunctuationCharsForDisplay) == true
    val prevWasDashJoiner = prevToken?.isPunctuationIn(dashJoinersForDisplay) == true
    val dashNotParagraphStart = prevWasDashJoiner && tokenIndexInParagraph >= 2

    return !(isClosing || prevWasOpening || dashNotParagraphStart)
}

/**
 * Joins [tokens] into a readable string using [shouldInsertSpaceBeforeToken].
 *
 * Paragraph break tokens are treated as paragraph boundaries.
 */
fun joinTokensForDisplay(tokens: List<Token>): String {
    val builder = StringBuilder()
    var paragraphIndex = 0
    var prevNonBreakToken: Token? = null

    for (token in tokens) {
        if (token.type == TokenType.PARAGRAPH_BREAK || token.type == TokenType.PAGE_BREAK) {
            paragraphIndex = 0
            prevNonBreakToken = null
            continue
        }

        if (shouldInsertSpaceBeforeToken(token, prevNonBreakToken, paragraphIndex)) {
            builder.append(' ')
        }
        builder.append(token.text)
        prevNonBreakToken = token
        paragraphIndex++
    }

    return builder.toString()
}
