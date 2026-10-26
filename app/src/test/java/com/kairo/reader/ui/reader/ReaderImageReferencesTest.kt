package com.kairo.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderImageReferencesTest {
    @Test
    fun repeatedDocumentImagesResolveTheirOwnPaths() {
        val a = "kairo_docx_assets/book/images/a.png"
        val b = "kairo_docx_assets/book/images/b.png"
        val html = "<img src='$a'><img src='$a'><img src='$b'>"
        val blocks = buildReaderBlocks(html, emptyList(), listOf(a, b))
        assertEquals(listOf(a, a, b), blocks.filterIsInstance<ReaderImageBlock>().map { it.imagePath })
    }
}
