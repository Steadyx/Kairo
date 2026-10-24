package com.kairo.reader.core.text

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.nearestWordIndex

object TokenTextPositionResolver {
    fun resolveTokenIndex(
        plainText: String,
        tokens: List<Token>,
        characterOffset: Int,
    ): Int {
        if (tokens.isEmpty()) return 0
        val targetOffset = characterOffset.coerceIn(0, plainText.length)
        if (targetOffset == 0) return tokens.nearestWordIndex(0)
        val source = canonicalText(plainText)
        var searchFrom = 0
        var lastWordIndex = tokens.nearestWordIndex(0)
        tokens.forEachIndexed { index, token ->
            if (token.type == TokenType.PARAGRAPH_BREAK || token.type == TokenType.PAGE_BREAK) {
                return@forEachIndexed
            }
            val text = canonicalText(token.text).text
            if (text.isEmpty()) return@forEachIndexed
            val start = source.text.indexOf(text, startIndex = searchFrom)
            if (start < 0) return@forEachIndexed
            val endExclusive = start + text.length
            if (source.offsets[start] >= targetOffset || targetOffset <= source.offsets[endExclusive - 1]) {
                return tokens.nearestWordIndex(index)
            }
            if (token.type == TokenType.WORD) lastWordIndex = index
            searchFrom = endExclusive
        }
        return tokens.nearestWordIndex(lastWordIndex)
    }

    // Match tokenizer substitutions while retaining offsets into the unmodified source.
    private fun canonicalText(source: String): PositionedText {
        val text = StringBuilder(source.length)
        val offsets = IntArray(source.length)
        source.forEachIndexed { index, char ->
            val canonical = canonicalChar(char)
            val mojibake = char == 'Â' && source.getOrNull(index + 1) in DEGREE_SIGNS
            val repeatedDot = char == '.' &&
                source.getOrNull(index - 1) == '.' &&
                (source.getOrNull(index - 2) == '.' || source.getOrNull(index + 1) == '.')
            val doubleDash = char == '-' &&
                source.getOrNull(index - 1) == '-' &&
                source.getOrNull(index - 2) != '-' &&
                source.getOrNull(index + 1) != '-'
            if (canonical != null && !mojibake && !repeatedDot && !doubleDash) {
                offsets[text.length] = index
                text.append(canonical)
            }
        }
        return PositionedText(text.toString(), offsets)
    }

    private fun canonicalChar(char: Char): Char? =
        when (char) {
            '\u00AD', '\u2060', '\uFEFF', '\u200B' -> null
            '“', '”' -> '"'
            '‘', '’' -> '\''
            '…' -> '.'
            '−', '–', '—', '‐', '‑', '‒', '﹣', '－' -> '-'
            'º' -> '°'
            else -> char.takeUnless { it.isWhitespace() || Character.isSpaceChar(it) }
        }

    private val DEGREE_SIGNS = setOf('°', 'º')

    private data class PositionedText(val text: String, val offsets: IntArray,)
}
