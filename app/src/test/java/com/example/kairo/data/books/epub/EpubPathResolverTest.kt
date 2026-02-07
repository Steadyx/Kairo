package com.example.kairo.data.books.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubPathResolverTest {
    @Test
    fun decodeUrlPathPreservesPlusCharacters() {
        val decoded = EpubPathResolver.decodeUrlPath("OPS/Chapter+1%2B2.xhtml")

        assertEquals("OPS/Chapter+1+2.xhtml", decoded)
    }

    @Test
    fun resolveOpfPathWithoutFallbackReturnsNullWhenContainerPathMissing() {
        val resolved =
            EpubPathResolver.resolveOpfPath(
                rawPath = "OPS/content.opf",
                availableEntriesLower = setOf("oebps/content.opf"),
                allowFallback = false,
            )

        assertNull(resolved)
    }

    @Test
    fun resolveOpfPathWithFallbackSelectsPreferredOpf() {
        val resolved =
            EpubPathResolver.resolveOpfPath(
                rawPath = null,
                availableEntriesLower = setOf(
                    "oebps/package.opf",
                    "opf/content.opf",
                ),
            )

        assertEquals("opf/content.opf", resolved)
    }
}
