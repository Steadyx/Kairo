package com.example.kairo.core.tokenization.cjk

import com.example.kairo.core.linguistics.ClauseDetector
import com.example.kairo.core.linguistics.WordAnalyzer
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.calculateOrpIndex

internal object CjkTokenFactory {
    fun word(
        text: String,
        isLatin: Boolean,
    ): Token =
        if (isLatin) {
            val syllables = WordAnalyzer.countSyllables(text)
            val frequency = WordAnalyzer.getFrequencyScore(text)
            val complexity = WordAnalyzer.getComplexityMultiplier(text)
            val isClause = ClauseDetector.isClauseBoundary(text)

            Token(
                text = text,
                type = TokenType.WORD,
                orpIndex = calculateOrpIndex(text),
                syllableCount = syllables,
                frequencyScore = frequency,
                complexityMultiplier = complexity,
                isClauseBoundary = isClause,
            )
        } else {
            Token(
                text = text,
                type = TokenType.WORD,
                orpIndex = 0,
                syllableCount = 1,
                frequencyScore = 0.8,
                complexityMultiplier = 1.0,
            )
        }

    fun punctuation(text: String): Token =
        Token(
            text = text,
            type = TokenType.PUNCTUATION,
            pauseAfterMs = 0L,
        )

    fun paragraphBreak(): Token =
        Token(
            text = "\n",
            type = TokenType.PARAGRAPH_BREAK,
            pauseAfterMs = 0L,
        )

    fun pageBreak(): Token =
        Token(
            text = "\u000C",
            type = TokenType.PAGE_BREAK,
            pauseAfterMs = 0L,
        )
}
