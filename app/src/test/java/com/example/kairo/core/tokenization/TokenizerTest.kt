package com.example.kairo.core.tokenization

import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenizerTest {
    private val tokenizer = Tokenizer()

    private fun chapter(text: String) =
        Chapter(
            index = 0,
            title = null,
            htmlContent = "",
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
}
