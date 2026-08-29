package com.kairo.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ReaderContentBlockParserImageLimitTest {
    @Test
    fun acceptsExactly256MixedLocalCandidatesAndExcludesGuaranteedNonLocalSources() {
        val directPath = "kairo_epub_assets/book/images/duplicate.png"
        val fallbackPaths = List(128) { index -> "kairo_epub_assets/book/images/fallback-$index.png" }
        val html =
            buildString {
                repeat(100) { index ->
                    append("<img src='data:image/png;base64,$index'>")
                    append("<img src='http://example.invalid/$index.png'>")
                    append("<svg><image href='https://example.invalid/$index.png'/></svg>")
                }
                repeat(128) {
                    append("<img src='$directPath'>")
                }
                repeat(128) {
                    append("<svg><image xlink:href='relative-duplicate.png'/></svg>")
                }
            }

        val imageBlocks =
            buildReaderBlocks(
                htmlContent = html,
                paragraphs = emptyList(),
                imagePaths = fallbackPaths,
            ).filterIsInstance<ReaderImageBlock>()

        assertEquals(256, MAX_READER_CHAPTER_LOCAL_IMAGE_CANDIDATES)
        assertEquals(256, imageBlocks.size)
        assertEquals(List(128) { directPath }, imageBlocks.take(128).map { it.imagePath })
        assertEquals(fallbackPaths, imageBlocks.drop(128).map { it.imagePath })
    }

    @Test
    fun candidate257ThrowsDedicatedSafeErrorAcrossImgAndSvgDuplicates() {
        val attackerPath = "kairo_epub_assets/private/attacker-controlled-name.png"
        val html =
            buildString {
                repeat(129) {
                    append("<img src='kairo_epub_assets/book/images/duplicate.png'>")
                }
                repeat(127) {
                    append("<svg><image href='kairo_epub_assets/book/images/duplicate.svg'/></svg>")
                }
                append("<svg><image href='$attackerPath'/></svg>")
            }

        val exception =
            assertThrows(ReaderChapterImageLimitException::class.java) {
                buildReaderBlocks(
                    htmlContent = html,
                    paragraphs = emptyList(),
                    imagePaths = emptyList(),
                )
            }

        assertEquals("This chapter contains too many images to display.", exception.message)
        assertFalse(exception.message.orEmpty().contains(attackerPath))
    }

    @Test
    fun imagePathsOnlyFallbackEnforcesTheSame256CandidateBoundary() {
        val acceptedPaths = List(256) { index -> "kairo_epub_assets/book/images/$index.png" }

        val accepted =
            buildReaderBlocks(
                htmlContent = "",
                paragraphs = emptyList(),
                imagePaths = acceptedPaths,
            )
        val exception =
            assertThrows(ReaderChapterImageLimitException::class.java) {
                buildReaderBlocks(
                    htmlContent = "",
                    paragraphs = emptyList(),
                    imagePaths = acceptedPaths + "kairo_epub_assets/book/images/overflow.png",
                )
            }

        assertEquals(256, accepted.filterIsInstance<ReaderImageBlock>().size)
        assertEquals(READER_CHAPTER_IMAGE_LIMIT_MESSAGE, exception.message)
    }
}
