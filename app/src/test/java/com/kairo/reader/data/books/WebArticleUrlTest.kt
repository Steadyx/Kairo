package com.kairo.reader.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WebArticleUrlTest {
    @Test
    fun normalizePreservesBalancedParenthesesInsideSharedLinks() {
        val url = "https://en.wikipedia.org/wiki/Function_(mathematics)"
        listOf(url, "Read ($url).", "[$url]", "$url!", "<$url>").forEach { source ->
            assertEquals(url, WebArticleUrl.normalize(source))
        }
        assertEquals("https://example.com/a_(b_(c))", WebArticleUrl.normalize("https://example.com/a_(b_(c))"))
    }

    @Test
    fun normalizeAddsHttpsAndStableSlash() {
        assertEquals(
            "https://example.com/story",
            WebArticleUrl.normalize("example.com/story"),
        )
    }

    @Test
    fun normalizeRemovesFragmentForStableImports() {
        assertEquals(
            "https://example.com/story?utm=test",
            WebArticleUrl.normalize("https://Example.com/story?utm=test#comments"),
        )
    }

    @Test
    fun normalizeRemovesTrackingParamsButKeepsContentParams() {
        assertEquals(
            "https://example.com/story?id=42",
            WebArticleUrl.normalize(
                "https://example.com/story?utm_source=share&id=42&fbclid=abc&ref=home&at_medium=social",
            ),
        )
    }

    @Test
    fun extractFirstWebUrlFromSharedText() {
        assertEquals(
            "https://example.com/story",
            WebArticleUrl.extractFirstWebUrl("Read this: https://example.com/story."),
        )
    }

    @Test
    fun extractBestWebUrlPrefersArticleOverSocialProfile() {
        val sharedText =
            """
            Follow BBC: https://www.facebook.com/bbc
            Read the article: https://www.bbc.co.uk/news/articles/cx2k9x0xv01o?utm_source=share
            """.trimIndent()

        assertEquals(
            "https://www.bbc.co.uk/news/articles/cx2k9x0xv01o?utm_source=share",
            WebArticleUrl.extractBestWebUrl(sharedText),
        )
    }

    @Test
    fun normalizeRejectsNonWebLinks() {
        try {
            WebArticleUrl.normalize("ftp://example.com/story")
            fail("Expected non-web links to be rejected")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("http"))
        }
    }
}
