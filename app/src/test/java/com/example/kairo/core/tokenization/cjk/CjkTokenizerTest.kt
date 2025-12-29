package com.example.kairo.core.tokenization.cjk

import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.ChapterLink
import com.example.kairo.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CjkTokenizerTest {
    private fun chapter(text: String) =
        Chapter(
            index = 0,
            title = null,
            htmlContent = "",
            plainText = text,
        )

    @Test
    fun tokenizesJapaneseIntoCjkChunksAndPunctuation() {
        val tokens = CjkTokenizer().tokenize(chapter("私は猫です。"))
        val texts = tokens.map { it.text }
        val types = tokens.map { it.type }

        assertEquals(listOf("私は", "猫で", "す", "。"), texts)
        assertEquals(
            listOf(TokenType.WORD, TokenType.WORD, TokenType.WORD, TokenType.PUNCTUATION),
            types,
        )
    }

    @Test
    fun keepsJapaneseCommaAndFullStopAsPunctuationTokens() {
        val tokens = CjkTokenizer().tokenize(chapter("よろしく、お願いします。"))
        val punctuation = tokens.filter { it.type == TokenType.PUNCTUATION }.map { it.text }

        assertEquals(listOf("、", "。"), punctuation)
    }

    @Test
    fun tokenizesChineseIntoFixedWidthChunks() {
        val tokens = CjkTokenizer().tokenize(chapter("速度阅读很好。"))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }
        val punctuation = tokens.filter { it.type == TokenType.PUNCTUATION }.map { it.text }

        assertEquals(listOf("速度", "阅读", "很好"), words)
        assertEquals(listOf("。"), punctuation)
    }

    @Test
    fun preservesLatinRunsInsideCjkText() {
        val tokens = CjkTokenizer().tokenize(chapter("CPU处理器快"))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertEquals(listOf("CPU", "处理", "器快"), words)
    }

    @Test
    fun treatsHangulAsWordChunksWhenConfigured() {
        val tokenizer =
            CjkTokenizer(
                CjkSegmenterConfig(
                    maxCjkCharsPerToken = 2,
                    treatHangulAsWord = true,
                ),
            )
        val tokens = tokenizer.tokenize(chapter("한국어 테스트"))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertEquals(listOf("한국어", "테스트"), words)
    }

    @Test
    fun insertsParagraphBreaksBetweenBlocks() {
        val tokens = CjkTokenizer().tokenize(chapter("你好\n\n世界"))

        assertTrue(tokens.any { it.type == TokenType.PARAGRAPH_BREAK })
    }

    @Test
    fun detectsFormFeedAsPageBreak() {
        val tokens = CjkTokenizer().tokenize(chapter("第一章\u000C第二章"))

        assertTrue(tokens.any { it.type == TokenType.PAGE_BREAK })
    }

    @Test
    fun handlesNestedQuotesAndPunctuation() {
        val tokens = CjkTokenizer().tokenize(chapter("他说：「你好！？」"))
        val punctuation = tokens.filter { it.type == TokenType.PUNCTUATION }.map { it.text }

        assertTrue(punctuation.contains("「"))
        assertTrue(punctuation.contains("」"))
        assertTrue(punctuation.contains("！"))
        assertTrue(punctuation.contains("？"))
    }

    @Test
    fun handlesEmojiAndCombiningMarks() {
        val tokens = CjkTokenizer().tokenize(chapter("测试😊e\u0301"))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertTrue(words.any { it.contains("测试") })
        assertTrue(words.any { it.contains("e") })
    }

    @Test
    fun appliesLinksByCharacterPositions() {
        val chapter =
            Chapter(
                index = 0,
                title = null,
                htmlContent = "",
                plainText = "你好世界",
                links =
                    listOf(
                        ChapterLink(
                            startChar = 0,
                            endChar = 2,
                            targetChapterIndex = 3,
                        ),
                    ),
            )
        val tokens = CjkTokenizer().tokenize(chapter)

        assertTrue(tokens.first().linkChapterIndex == 3)
        assertTrue(tokens.drop(1).none { it.linkChapterIndex == 3 })
    }

    @Test
    fun fallsBackToCjkChunkingWhenHangulNotConfiguredAsWord() {
        val tokenizer =
            CjkTokenizer(
                CjkSegmenterConfig(
                    maxCjkCharsPerToken = 2,
                    treatHangulAsWord = false,
                ),
            )
        val tokens = tokenizer.tokenize(chapter("한국어테스트"))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertEquals(listOf("한국", "어테", "스트"), words)
    }

    @Test
    fun tokenizesNumbersIntoWordTokens() {
        val tokens = CjkTokenizer().tokenize(chapter("2024年版本"))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertTrue(words.any { it == "2024" || it.contains("2024") })
    }

    @Test
    fun keepsCjkPunctuationAsSeparateTokens() {
        val tokens = CjkTokenizer().tokenize(chapter("你好—世界。"))
        val punctuation = tokens.filter { it.type == TokenType.PUNCTUATION }.map { it.text }

        assertTrue(punctuation.contains("—"))
        assertTrue(punctuation.contains("。"))
    }
}
