package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.tokenization.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTextPositionResolverTest {
    @Test
    fun normalizedWordsResolveToTheirOriginalOccurrence() {
        val sources = listOf(
            "co\u00ADoperate alpha cooperate beta",
            "co\u2060operate alpha cooperate beta",
            "co\uFEFFoperate alpha cooperate beta",
            "well\u2011known alpha well-known beta",
            "20 ° C alpha 20°C beta",
            "− 35 alpha -35 beta",
            "$ 20 alpha $20 beta",
        )
        sources.forEach { source ->
            val tokens = Tokenizer().tokenize(
                Chapter(0, null, "", source),
            )
            val expected = tokens.indexOfFirst { it.text == "alpha" }
            assertEquals(source, expected, ReaderTextPositionResolver.resolveTokenIndex(source, tokens, source.indexOf("alpha")))
            assertEquals(
                source,
                tokens.indexOfFirst { it.type == TokenType.WORD },
                ReaderTextPositionResolver.resolveTokenIndex(source, tokens, 1),
            )
        }
    }

    @Test
    fun resolvesAuthoredFragmentOffsetToItsFirstWordToken() {
        val plainText = "The Arrival\n\nFirst chapter.\n\nThe Crossing\n\nSecond chapter."
        val tokens =
            listOf(
                word("The"),
                word("Arrival"),
                paragraphBreak(),
                word("First"),
                word("chapter"),
                punctuation("."),
                paragraphBreak(),
                word("The"),
                word("Crossing"),
                paragraphBreak(),
                word("Second"),
                word("chapter"),
                punctuation("."),
            )

        val resolved =
            ReaderTextPositionResolver.resolveTokenIndex(
                plainText = plainText,
                tokens = tokens,
                characterOffset = plainText.indexOf("The Crossing"),
            )

        assertEquals(7, resolved)
    }

    private fun word(text: String) = Token(text = text, type = TokenType.WORD)

    private fun punctuation(text: String) = Token(text = text, type = TokenType.PUNCTUATION)

    private fun paragraphBreak() = Token(text = "\n", type = TokenType.PARAGRAPH_BREAK)
}
