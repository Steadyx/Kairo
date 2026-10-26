package com.kairo.reader.data.books

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.tokenization.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EpubLinkedChapterResolverTest {
    @Test
    fun numericFootnoteLinksRetainTheirTargetOffset() {
        val rewriter = EpubContentRewriter()
        val html = rewriter.rewriteHtmlAnchorHrefs(
            html = """<p>See <a data-kairo-offset="999" data-kairo-aux="0" href="notes.xhtml#second">2</a>.</p>""",
            baseDir = "book",
            chapterIndexByPathLower = mapOf("book/notes.xhtml" to 2),
            anchorOffsetsByPath = mapOf("book/notes.xhtml" to mapOf("second" to 17)),
            auxiliaryPaths = setOf("book/notes.xhtml"),
        )
        val tokens = Tokenizer().tokenize(Chapter(0, null, html, "See 2."))
        val note = tokens.first { it.text == "2" }
        assertEquals(2, note.linkChapterIndex)
        assertEquals(17, note.linkCharacterOffset)
    }

    @Test
    fun linkedDocumentMissingFromLoadedTextIsNotSilentlyOmitted() {
        val source = """<p>Chapter <a href="notes.xhtml">note</a></p>"""
        val linked = findLinkedAuxiliaryChapters(
            readingPaths = listOf("chapter.xhtml"),
            manifestPaths = setOf("chapter.xhtml", "notes.xhtml"),
            readHtml = { if (it == "chapter.xhtml") source else null },
            contentRewriter = EpubContentRewriter(),
        )
        assertEquals(listOf("notes.xhtml"), linked)
        val rewriter = EpubContentRewriter()
        val builder = EpubChapterBuilder(rewriter, EpubNavigationClassifier(rewriter))
        assertThrows(IllegalArgumentException::class.java) {
            builder.buildWithResult(
                zipTextEntries = mapOf("chapter.xhtml" to source.toByteArray()),
                imageRelativePathByEpubPathLower = emptyMap(),
                preferredChapterPathsLower = listOf("chapter.xhtml") + linked,
                decodedTextEntries = emptyMap(),
                chapterImageSrcsByPathLower = emptyMap(),
                includeZipFallback = false,
                auxiliaryPathsLower = linked.toSet(),
            )
        }
    }

    @Test
    fun followsLinkedManifestDocumentsButNotUnrelatedFiles() {
        val documents = mapOf(
            "book/chapter.xhtml" to """<p>Read <a href="notes.xhtml#one">note</a>.</p>""",
            "book/notes.xhtml" to """<p>Note <a href="extra.xhtml#two">more</a></p>""",
            "book/extra.xhtml" to """<p>Extra <a href="chapter.xhtml">back</a></p>""",
            "book/unrelated.xhtml" to "<p>Not linked.</p>",
        )
        val linked = findLinkedAuxiliaryChapters(
            readingPaths = listOf("book/chapter.xhtml"),
            manifestPaths = documents.keys,
            readHtml = documents::get,
            contentRewriter = EpubContentRewriter(),
        )
        assertEquals(listOf("book/notes.xhtml", "book/extra.xhtml"), linked)
    }

    @Test
    fun tocCanReachAuxiliaryDocumentNotLinkedFromReadingText() {
        val linked = findLinkedAuxiliaryChapters(
            readingPaths = listOf("book/chapter.xhtml"),
            manifestPaths = setOf("book/chapter.xhtml", "book/notes.xhtml"),
            navigationHrefs = listOf("book" to "notes.xhtml#one"),
            readHtml = { "<p>Content</p>" },
            contentRewriter = EpubContentRewriter(),
        )
        assertEquals(listOf("book/notes.xhtml"), linked)
    }
}
