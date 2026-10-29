package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTimedReadingLauncherTest {
    @Test
    fun dragMovesInEitherDirectionAcrossPunctuationAndParagraphBreaks() {
        val tokens = listOf(
            Token("First", TokenType.WORD),
            Token(".", TokenType.PUNCTUATION),
            Token("\n", TokenType.PARAGRAPH_BREAK),
            Token("Second", TokenType.WORD),
            Token(",", TokenType.PUNCTUATION),
            Token("Third", TokenType.WORD),
        )
        assertEquals(0, tokens.moveFocusByWords(3, -1))
        assertEquals(3, tokens.moveFocusByWords(0, 1))
        assertEquals(5, tokens.moveFocusByWords(0, 2))
        assertEquals(0, tokens.moveFocusByWords(5, -3))
        assertEquals(5, tokens.moveFocusByWords(5, 1))
    }
}
