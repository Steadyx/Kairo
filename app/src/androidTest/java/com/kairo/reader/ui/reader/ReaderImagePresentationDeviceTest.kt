package com.kairo.reader.ui.reader

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.data.local.toDomain
import com.kairo.reader.data.local.toEntity
import com.kairo.reader.ui.navigation.buildReaderPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderImagePresentationDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun separateCoverKeepsAnImageOnlyChapterImage() {
        val diagram = "fixture/diagram.png"
        val data = ChapterData(
            tokens = emptyList(), plainText = "", paragraphs = emptyList(),
            blocks = listOf(ReaderImageBlock(diagram, 0)), firstWordIndex = -1,
            imagePaths = listOf(diagram), pages = emptyList(),
            wordCountByToken = IntArray(0), totalWords = 0,
        )
        lateinit var rendered: ReaderRenderState
        composeRule.setContent {
            rendered = rememberReaderRenderState(0, 0, null, byteArrayOf(1), data)
            Text("Fixture")
        }
        composeRule.runOnIdle {
            assertEquals(diagram, rendered.fullScreenTitlePageImagePath)
        }
    }

    @Test
    fun separateCoverDoesNotHideChapterIllustration() {
        val diagram = "fixture/diagram.png"
        val token = Token("text", TokenType.WORD)
        val paragraph = Paragraph(listOf(token), 0)
        val data = ChapterData(
            tokens = listOf(token),
            plainText = "text",
            paragraphs = listOf(paragraph),
            blocks = listOf(ReaderParagraphBlock(paragraph), ReaderImageBlock(diagram, 0, 0)),
            firstWordIndex = 0,
            imagePaths = listOf(diagram),
            pages = listOf(ChapterPage(0, 0, 0, 1)),
            wordCountByToken = intArrayOf(1),
            totalWords = 1,
        )
        lateinit var rendered: ReaderRenderState
        composeRule.setContent {
            rendered = rememberReaderRenderState(0, 0, null, byteArrayOf(1), data)
            Text("Fixture")
        }
        composeRule.runOnIdle {
            assertTrue(rendered.isCoverChapter)
            assertEquals(listOf(diagram), rendered.displayBlocks.filterIsInstance<ReaderImageBlock>().map { it.imagePath })
        }
    }

    @Test
    fun laterPageImageIsNotDuplicatedInFirstPageHeader() {
        val paths = listOf("fixture/diagram.png")
        val tokens = listOf(Token("first", TokenType.WORD), Token("second", TokenType.WORD))
        val paragraphs = listOf(Paragraph(listOf(tokens[0]), 0), Paragraph(listOf(tokens[1]), 1))
        val data = ChapterData(
            tokens = tokens,
            plainText = "first second",
            paragraphs = paragraphs,
            blocks = listOf(ReaderParagraphBlock(paragraphs[0]), ReaderParagraphBlock(paragraphs[1]), ReaderImageBlock(paths[0], 0, 1)),
            firstWordIndex = 0,
            imagePaths = paths,
            pages = listOf(ChapterPage(0, 0, 0, 1), ChapterPage(1, 1, 1, 1)),
            wordCountByToken = intArrayOf(1, 2),
            totalWords = 2,
        )
        lateinit var first: ReaderRenderState
        lateinit var second: ReaderRenderState
        composeRule.setContent {
            first = rememberReaderRenderState(1, 0, 0, null, data)
            second = rememberReaderRenderState(1, 1, 1, null, data)
            Text("Fixture")
        }
        composeRule.runOnIdle {
            assertTrue(first.headerCarouselImages.isEmpty())
            assertTrue(first.displayBlocks.filterIsInstance<ReaderImageBlock>().isEmpty())
            assertEquals(paths, second.displayBlocks.filterIsInstance<ReaderImageBlock>().map { it.imagePath })
        }
    }

    @Test
    fun imagePageSurvivesPositionRoundTrip() {
        val paths = listOf("fixture/a.png", "fixture/b.png", "fixture/c.png")
        val data = ChapterData(
            tokens = emptyList(), plainText = "", paragraphs = emptyList(),
            blocks = paths.mapIndexed { index, path -> ReaderImageBlock(path, index) },
            firstWordIndex = -1, imagePaths = paths, pages = emptyList(),
            wordCountByToken = IntArray(0), totalWords = 0,
        )
        val position = requireNotNull(
            buildReaderPosition(BookId("book"), ReaderUiState(chapterIndex = 2, pageIndexOverride = 2, chapterData = data))
        ).toEntity().toDomain()
        lateinit var reopened: ReaderRenderState
        composeRule.setContent {
            reopened = rememberReaderRenderState(2, position.tokenIndex, position.imagePageIndex, null, data)
            Text("Fixture")
        }
        composeRule.runOnIdle {
            assertEquals(2, reopened.resolvedPageIndex)
            assertEquals(paths[2], reopened.currentPage?.imagePath)
        }
    }
}
