package com.kairo.reader.core.text

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.tokenization.HtmlChapterLinkApplier
import com.kairo.reader.data.books.mobi.MobiContentProcessor
import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlEntitiesTest {
    @Test
    fun literalAmpersandsDoNotHideSubsequentEntities() {
        assertEquals("Fish & chips 😀", HtmlEntities.decode("Fish & chips &#x1F600;"))
        assertEquals("&&& 😀 & trailing", HtmlEntities.decode("&&& &#x1F600; & trailing"))
    }

    @Test
    fun supplementaryCharactersInLinkLabelsStillMatchTheirTokens() {
        val tokens = mutableListOf(Token("𠀀", TokenType.WORD), Token("chapter", TokenType.WORD))
        HtmlChapterLinkApplier.apply(
            tokens = tokens,
            html = "<a href=\"kairo://chapter/2\">&#x20000; chapter</a>",
            normalizeInlineText = { it },
            tokenizeInlineText = { it.split(" ") },
            minimumRomanPageNumberLength = 2,
        )
        assertEquals(listOf(2, 2), tokens.map { it.linkChapterIndex })
    }

    @Test
    fun numericEntitiesPreserveFullUnicodeCodePointsInMobiContent() {
        val html = "<p>&#x1F600; &#128512; &#X20000; &#131072;</p>"
        val expected = "😀 😀 𠀀 𠀀"
        assertEquals(expected, MobiContentProcessor().extractPlainText(html))
    }

    @Test
    fun invalidNumericEntitiesArePreservedRatherThanTruncatedOrDropped() {
        val invalid = "&#x110000; &#xD800; &#0; &#99999999999999; &unknown;"
        assertEquals(invalid, HtmlEntities.decode(invalid))
    }

    @Test
    fun decodingIsSinglePassAndPreservesNamedEntityCase() {
        assertEquals("&lt; Ä ä † ‡", HtmlEntities.decode("&amp;lt; &Auml; &auml; &dagger; &Dagger;"))
    }
}
