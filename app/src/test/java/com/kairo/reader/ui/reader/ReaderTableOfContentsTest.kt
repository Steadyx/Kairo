package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.TableOfContentsEntry
import com.kairo.reader.core.model.TableOfContentsTarget
import com.kairo.reader.core.tokenization.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTableOfContentsTest {
    @Test
    fun activeEntryTracksFragmentsWithinTheSameSpineDocument() {
        val plainText = "The Arrival\n\nFirst chapter.\n\nThe Crossing\n\nSecond chapter."
        val chapter =
            Chapter(
                index = 0,
                title = "Story",
                htmlContent = "<h1>The Arrival</h1><p>First chapter.</p><h1>The Crossing</h1><p>Second chapter.</p>",
                plainText = plainText,
            )
        val tokens = Tokenizer().tokenize(chapter)
        val chapterData = requireNotNull(ReaderChapterProcessor().process(chapter, tokens, wordsPerPage = 100))
        val entries =
            listOf(
                TableOfContentsEntry(
                    label = "The Arrival",
                    depth = 0,
                    target = TableOfContentsTarget(chapterIndex = 0, characterOffset = 0),
                ),
                TableOfContentsEntry(
                    label = "The Crossing",
                    depth = 0,
                    target =
                    TableOfContentsTarget(
                        chapterIndex = 0,
                        characterOffset = plainText.indexOf("The Crossing"),
                    ),
                ),
            )
        val crossingToken = tokens.indexOfFirst { it.text == "Crossing" }

        val active =
            resolveActiveTableOfContentsEntry(
                entries = entries,
                chapterIndex = 0,
                focusIndex = crossingToken,
                chapterData = chapterData,
            )

        assertEquals("The Crossing", active?.label)
    }
}
