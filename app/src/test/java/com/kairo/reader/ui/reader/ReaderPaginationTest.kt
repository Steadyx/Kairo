package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPaginationTest {
    @Test
    fun pagesKeepOpeningPunctuationAndFocusOnWords() {
        listOf("(" to ")", "[" to "]", "“" to "”").forEach { (opening, closing) ->
            val tokens = listOf(punctuation(opening)) + words("Hello", "world") + punctuation(closing) +
                Token(text = "\n", type = TokenType.PAGE_BREAK) +
                punctuation(opening) + words("Next", "page") + punctuation(closing)
            val pages = buildChapterPages(tokens, wordsPerPage = 2)
            val blocks = listOf(ReaderParagraphBlock(Paragraph(tokens, 0)))
            assertEquals(2, pages.size)
            pages.forEach { page ->
                val displayed = sliceBlocksForPage(blocks, page).filterIsInstance<ReaderParagraphBlock>()
                    .flatMap { it.paragraph.tokens }.map { it.text }
                assertEquals(opening, displayed.first())
                assertEquals(closing, displayed.last())
                assertEquals(TokenType.WORD, tokens[page.focusTokenIndex].type)
            }
        }
    }

    @Test
    fun buildChapterPages_usesEarlierSentenceBoundaryInsteadOfCuttingMidSentence() {
        val tokens =
            words("one", "two", "three", "four", "five", "six") +
                punctuation(".") +
                words(
                    "seven",
                    "eight",
                    "nine",
                    "ten",
                    "eleven",
                    "twelve",
                    "thirteen",
                    "fourteen",
                    "fifteen",
                    "sixteen",
                    "seventeen",
                    "eighteen",
                )

        val pages = buildChapterPages(tokens, wordsPerPage = 10)

        assertEquals(2, pages.size)
        assertEquals(6, pages[0].wordCount)
        assertEquals(".", tokens[pages[0].endTokenIndex].text)
        assertEquals("seven", tokens[pages[1].startTokenIndex].text)
    }

    @Test
    fun buildChapterPages_usesEarlierParagraphBoundaryInsteadOfCrossingIntoNextParagraph() {
        val tokens =
            words("one", "two", "three", "four", "five", "six") +
                paragraphBreak() +
                words(
                    "seven",
                    "eight",
                    "nine",
                    "ten",
                    "eleven",
                    "twelve",
                    "thirteen",
                    "fourteen",
                    "fifteen",
                    "sixteen",
                    "seventeen",
                    "eighteen",
                )

        val pages = buildChapterPages(tokens, wordsPerPage = 10)

        assertEquals(2, pages.size)
        assertEquals(6, pages[0].wordCount)
        assertEquals("six", tokens[pages[0].endTokenIndex].text)
        assertEquals("seven", tokens[pages[1].startTokenIndex].text)
    }

    @Test
    fun buildChapterPages_ignoresInlinePhysicalPageBreakInsideSentence() {
        val tokens =
            words("one", "two", "three", "four") +
                pageBreak() +
                words("five", "six") +
                punctuation(".") +
                words("seven", "eight", "nine", "ten")

        val pages = buildChapterPages(tokens, wordsPerPage = 4)

        assertEquals(2, pages.size)
        assertEquals(6, pages[0].wordCount)
        assertEquals(".", tokens[pages[0].endTokenIndex].text)
        assertEquals("seven", tokens[pages[1].startTokenIndex].text)
    }

    @Test
    fun buildVisualChapterPages_keepsInlineImageOnAnchoredTextPage() {
        val tokens = words("one", "two", "three", "four")
        val textPages =
            listOf(
                ChapterPage(index = 0, startTokenIndex = 0, endTokenIndex = 1, wordCount = 2),
                ChapterPage(index = 1, startTokenIndex = 2, endTokenIndex = 3, wordCount = 2),
            )
        val imageBlock =
            ReaderImageBlock(
                imagePath = "images/full-page.jpg",
                index = 0,
                anchorTokenIndex = 2,
            )
        val blocks =
            listOf(
                ReaderParagraphBlock(Paragraph(tokens = tokens.subList(0, 2), startIndex = 0)),
                imageBlock,
                ReaderParagraphBlock(Paragraph(tokens = tokens.subList(2, 4), startIndex = 2)),
            )

        val pages = buildVisualChapterPages(textPages, blocks, tokens)

        assertEquals(2, pages.size)
        assertEquals(ChapterPageKind.TEXT, pages[0].kind)
        assertEquals(ChapterPageKind.TEXT, pages[1].kind)
        assertTrue(sliceBlocksForPage(blocks, pages[0]).none { it is ReaderImageBlock })
        assertEquals(
            listOf(imageBlock, blocks[2]),
            sliceBlocksForPage(blocks, pages[1]),
        )
    }

    @Test
    fun buildVisualChapterPages_preservesImageOrderWhenTextPageCrossesImageBlock() {
        val tokens = words("one", "two", "three", "four")
        val textPages =
            listOf(
                ChapterPage(index = 0, startTokenIndex = 0, endTokenIndex = 3, wordCount = 4),
            )
        val imageBlock =
            ReaderImageBlock(
                imagePath = "images/full-page.jpg",
                index = 0,
                anchorTokenIndex = 2,
            )
        val firstParagraph =
            ReaderParagraphBlock(Paragraph(tokens = tokens.subList(0, 2), startIndex = 0))
        val secondParagraph =
            ReaderParagraphBlock(Paragraph(tokens = tokens.subList(2, 4), startIndex = 2))
        val blocks = listOf(firstParagraph, imageBlock, secondParagraph)

        val pages = buildVisualChapterPages(textPages, blocks, tokens)

        assertEquals(1, pages.size)
        assertEquals(ChapterPageKind.TEXT, pages[0].kind)
        assertEquals(0, pages[0].startTokenIndex)
        assertEquals(3, pages[0].endTokenIndex)
        assertEquals(
            listOf(firstParagraph, imageBlock, secondParagraph),
            sliceBlocksForPage(blocks, pages[0]),
        )
    }

    @Test
    fun buildVisualChapterPages_createsPageForImageOnlyChapter() {
        val imageBlock = ReaderImageBlock(imagePath = "images/plate.jpg", index = 0)

        val pages = buildVisualChapterPages(
            textPages = emptyList(),
            blocks = listOf(imageBlock),
            tokens = emptyList(),
        )

        assertEquals(1, pages.size)
        assertEquals(ChapterPageKind.IMAGE, pages[0].kind)
        assertEquals(0, pages[0].focusTokenIndex)
        assertEquals(listOf(imageBlock), sliceBlocksForPage(listOf(imageBlock), pages[0]))
    }

    @Test
    fun buildVisualChapterPages_createsOnePagePerImageForImageOnlyChapter() {
        val firstImage = ReaderImageBlock(imagePath = "images/plate-1.jpg", index = 0)
        val secondImage = ReaderImageBlock(imagePath = "images/plate-2.jpg", index = 1)
        val blocks = listOf(firstImage, secondImage)

        val pages = buildVisualChapterPages(
            textPages = emptyList(),
            blocks = blocks,
            tokens = emptyList(),
        )

        assertEquals(2, pages.size)
        assertEquals(ChapterPageKind.IMAGE, pages[0].kind)
        assertEquals(ChapterPageKind.IMAGE, pages[1].kind)
        assertEquals(listOf(firstImage), sliceBlocksForPage(blocks, pages[0]))
        assertEquals(listOf(secondImage), sliceBlocksForPage(blocks, pages[1]))
    }

    @Test
    fun buildVisualChapterPages_createsBlankPageForPageBreakOnlyChapter() {
        val tokens = listOf(Token(text = "\u000C", type = TokenType.PAGE_BREAK))

        val pages = buildVisualChapterPages(
            textPages = emptyList(),
            blocks = emptyList(),
            tokens = tokens,
        )

        assertEquals(1, pages.size)
        assertEquals(ChapterPageKind.BLANK, pages[0].kind)
        assertEquals(emptyList<ReaderBlock>(), sliceBlocksForPage(emptyList(), pages[0]))
    }

    private fun words(vararg texts: String): List<Token> =
        texts.map { text -> Token(text = text, type = TokenType.WORD) }

    private fun punctuation(text: String): Token = Token(text = text, type = TokenType.PUNCTUATION)

    private fun paragraphBreak(): Token = Token(text = "\n\n", type = TokenType.PARAGRAPH_BREAK)

    private fun pageBreak(): Token = Token(text = "\u000C", type = TokenType.PAGE_BREAK)
}
