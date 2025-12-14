package com.example.kairo.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenDisplayTest {

    private fun word(text: String) = Token(text = text, type = TokenType.WORD)
    private fun punct(text: String) = Token(text = text, type = TokenType.PUNCTUATION)

    @Test
    fun joinsEmDashWithoutSpaceAfterWhenMidParagraph() {
        val tokens = listOf(
            word("the"),
            word("dog"),
            punct("—"),
            word("ran")
        )

        assertEquals("the dog—ran", joinTokensForDisplay(tokens))
    }

    @Test
    fun keepsSpaceAfterEmDashWhenLeadingBullet() {
        val tokens = listOf(
            punct("—"),
            word("item")
        )

        assertEquals("— item", joinTokensForDisplay(tokens))
    }

    @Test
    fun joinsEmDashTightAfterOpeningQuote() {
        val tokens = listOf(
            punct("\u201C"), // “
            punct("—"),
            word("No"),
            punct("\u201D")  // ”
        )

        assertEquals("“—No”", joinTokensForDisplay(tokens))
    }
}

