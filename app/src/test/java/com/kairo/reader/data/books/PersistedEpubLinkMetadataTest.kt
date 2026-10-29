package com.kairo.reader.data.books

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.tokenization.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistedEpubLinkMetadataTest {
    @Test
    fun generatedMetadataReplacesUntrustedAttributesOnSelfClosingTags() {
        assertEquals(
            """<a href="kairo://chapter/3" data-kairo-aux="1" data-kairo-offset="4"/>""",
            addEpubLinkAttributes(
                """<a data-kairo-aux="0" data-kairo-offset="999" href="kairo://chapter/3"/>""",
                isAuxiliary = true,
                offset = 4,
            ),
        )
    }

    @Test
    fun annotatesOldLinksWithoutChangingIdsAssetsOrOtherMarkup() {
        val rewriter = EpubContentRewriter()
        val noteHtml = """<p>First note.</p><p id="second">Second note.</p>"""
        val noteText = rewriter.extractPlainText(noteHtml)
        val chapterHtml = """<p>Read <a href="kairo://chapter/3#second">2</a> now.</p><img src="kairo_epub_assets/old/image.png">"""
        val chapters = listOf(
            Chapter(0, null, chapterHtml, "Read 2 now."),
            Chapter(3, null, noteHtml, noteText),
        )
        val result = requireNotNull(annotatePersistedEpubLinks(chapters, mapOf(0 to true, 3 to false)))
        assertEquals(noteHtml, result[3])
        val rewritten = requireNotNull(result[0])
        assertTrue(rewritten.contains("kairo://chapter/3#second"))
        assertTrue(rewritten.contains("kairo_epub_assets/old/image.png"))
        val token = Tokenizer().tokenize(chapters[0].copy(htmlContent = rewritten)).first { it.text == "2" }
        assertEquals(3, token.linkChapterIndex)
        assertEquals(noteText.indexOf("Second note"), token.linkCharacterOffset)
    }

    @Test
    fun ambiguousFragmentLeavesExistingContentUntouched() {
        val chapters = listOf(
            Chapter(0, null, """<p><a href="kairo://chapter/1#missing">note</a></p>""", "note"),
            Chapter(1, null, "<p>Note.</p>", "Note."),
        )
        assertNull(annotatePersistedEpubLinks(chapters, mapOf(0 to true, 1 to false)))
    }
}
