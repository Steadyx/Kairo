package com.kairo.reader.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

internal class EpubImportIntegrityTest : EpubParserTestBase() {
    @Test
    fun missingRequiredChapterCannotProduceSuccessfulPartialBook() {
        val entries = mapOf("chapter1.xhtml" to "<p>First readable chapter.</p>".toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            invokeBuildFallbackChapters(
                zipTextEntries = entries,
                imageRelativePathByEpubPathLower = emptyMap(),
                preferredChapterPathsLower = listOf("chapter1.xhtml", "oversized.xhtml"),
            )
        }
    }

    @Test
    fun completeReadingOrderStillImports() {
        val entries = mapOf("chapter1.xhtml" to "<p>First readable chapter.</p>".toByteArray())
        val chapters = invokeBuildFallbackChapters(entries, emptyMap(), listOf("chapter1.xhtml"))
        assertEquals(1, chapters.size)
    }
}
