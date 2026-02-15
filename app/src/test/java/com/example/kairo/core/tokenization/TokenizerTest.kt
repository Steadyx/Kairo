package com.example.kairo.core.tokenization

import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.joinTokensForDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenizerTest {
    private val tokenizer = Tokenizer()

    private fun chapter(text: String, htmlContent: String = "") =
        Chapter(
            index = 0,
            title = null,
            htmlContent = htmlContent,
            plainText = text,
        )

    @Test
    fun normalizesTemperatureSpacingAndMojibake() {
        val tokens = tokenizer.tokenize(chapter("It was 20 ° C outside. Then 68 Â°F."))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertTrue(words.contains("20°C"))
        assertTrue(words.contains("68°F"))
    }

    @Test
    fun keepsPercentagesAsSingleWordToken() {
        val tokens = tokenizer.tokenize(chapter("Battery at 50 % now."))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertTrue(words.contains("50%"))
        // Ensure percent sign isn't dropped.
        assertEquals("50%", words.first { it.contains("%") })
    }

    @Test
    fun keepsCurrencyAmountsAsSingleWordToken() {
        val tokens = tokenizer.tokenize(chapter("It costs \$ 20 or €1,000 or £5.99."))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }
        val currencyPunctuation =
            tokens
                .filter { it.type == TokenType.PUNCTUATION }
                .map { it.text }
                .filter { it == "\$" || it == "€" || it == "£" }

        assertTrue(words.contains("\$20"))
        assertTrue(words.contains("€1,000"))
        assertTrue(words.contains("£5.99"))
        assertTrue(currencyPunctuation.isEmpty())
    }

    @Test
    fun keepsNegativeTemperaturesAsSingleWordToken() {
        val tokens = tokenizer.tokenize(
            chapter("A sentence like this -35c and –35c and ‑35c and -10°C.")
        )
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertTrue(words.contains("-35c"))
        assertTrue(words.contains("–35c") || words.contains("-35c"))
        assertTrue(words.contains("‑35c") || words.contains("-35c"))
        assertTrue(words.contains("-10°C"))
    }

    @Test
    fun detectsFormFeedAsPageBreak() {
        val tokens = tokenizer.tokenize(chapter("Hello\u000CWorld"))
        assertTrue(tokens.any { it.type == TokenType.PAGE_BREAK })
    }

    @Test
    fun detectsSceneBreakMarkersAsPageBreak() {
        val tokens = tokenizer.tokenize(chapter("Hello\n\n***\n\nWorld"))
        assertTrue(tokens.any { it.type == TokenType.PAGE_BREAK })
    }

    @Test
    fun normalizesAsciiEllipsisToSingleToken() {
        val tokens = tokenizer.tokenize(chapter("Wait... now."))
        val ellipsisCount = tokens.count { it.type == TokenType.PUNCTUATION && it.text == "\u2026" }
        assertEquals(1, ellipsisCount)
    }

    @Test
    fun normalizesDoubleHyphenToEmDash() {
        val tokens = tokenizer.tokenize(chapter("Hello--world"))
        val emDashCount = tokens.count { it.type == TokenType.PUNCTUATION && it.text == "\u2014" }
        assertEquals(1, emDashCount)
    }

    @Test
    fun keepsStandaloneSingleLetterIWhenStrippingPageNumbers() {
        val tokens =
            tokenizer.tokenize(
                chapter(
                    text = "Indiana and\nI\nwalked home.",
                    htmlContent = "<a href=\"kairo://chapter/1\">toc</a>",
                ),
            )
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertTrue(words.contains("I"))
        assertEquals(listOf("Indiana", "and", "I", "walked", "home"), words)
    }

    @Test
    fun removesSoftHyphensAndWordJoiners() {
        val tokens = tokenizer.tokenize(chapter("sport\u00ADing\u2060 is fun\uFEFF."))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertTrue(words.contains("sporting"))
        assertTrue(words.contains("is"))
        assertTrue(words.contains("fun"))
    }

    @Test
    fun normalizesNonBreakingHyphenToAsciiHyphen() {
        val tokens = tokenizer.tokenize(chapter("military\u2011grade response"))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertTrue(words.contains("military-grade"))
    }

    @Test
    fun normalizesStraightDoubleQuotesToCurlyByDialogueState() {
        val tokens = tokenizer.tokenize(chapter("guy.\"Hello there\""))
        val punctuation = tokens.filter { it.type == TokenType.PUNCTUATION }.map { it.text }

        assertEquals(listOf(".", "\u201C", "\u201D"), punctuation)
    }

    @Test
    fun openingCurlyQuoteAfterSentencePunctuationKeepsWordAttachmentInReaderDisplay() {
        val tokens = tokenizer.tokenize(chapter("guy.\"Hello there\""))

        assertEquals("guy. \u201CHello there\u201D", joinTokensForDisplay(tokens))
    }
}
