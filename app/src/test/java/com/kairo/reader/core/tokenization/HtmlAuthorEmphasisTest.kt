package com.kairo.reader.core.tokenization

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlAuthorEmphasisTest {
    @Test
    fun repeatedWordsKeepTheCorrectAuthoredOccurrence() {
        val tokens = tokenize("<p>I said Tuesday, not <em>Tuesday</em> again.</p>", "I said Tuesday, not Tuesday again.")
        assertEquals(listOf(false, true), tokens.filter { it.text == "Tuesday" }.map { it.authorEmphasis })
    }

    @Test
    fun nestedMarkupEntitiesAndQuotesKeepSourcePositionsAndLinks() {
        val html = "<p>She said &quot;<a href='kairo://chapter/3'><em>you <b>can</b></em></a>&quot; &amp; left.</p>"
        val tokens = tokenize(html, "She said \"you can\" & left.")
        val neutral = tokenize(html.replace("<em>", "").replace("</em>", ""), "She said \"you can\" & left.")
        assertEquals(listOf("you", "can"), tokens.filter { it.authorEmphasis }.map { it.text })
        assertEquals(neutral, tokens.map { it.copy(authorEmphasis = false) })
        assertTrue(tokens.filter { it.authorEmphasis }.all { it.linkChapterIndex == 3 })
    }

    @Test
    fun adjacentMarkupDoesNotManufactureWordsOrAccentPartialWords() {
        val tokens = tokenize("<p>An un<em>believ</em>able <strong>result</strong>!</p>", "An unbelievable result!")
        assertEquals(listOf("An", "unbelievable", "result"), tokens.filter { it.type == TokenType.WORD }.map { it.text })
        assertEquals(listOf("result"), tokens.filter { it.authorEmphasis }.map { it.text })
    }

    @Test
    fun decorativeWholeBlockAndLongEmphasisStayNeutral() {
        val examples = listOf(
            "<p>A <i>foreign</i> word.</p>" to "A foreign word.",
            "<p><em>A whole paragraph.</em></p>" to "A whole paragraph.",
            "<p>A <cite><em>book title</em></cite> here.</p>" to "A book title here.",
            "<h2>A <em>heading</em></h2>" to "A heading",
            "<p>A <em>rather long run of five words</em> here.</p>" to "A rather long run of five words here.",
        )
        examples.forEach { (html, plain) -> assertFalse(html, tokenize(html, plain).any { it.authorEmphasis }) }
    }

    @Test
    fun mismatchedHtmlCannotAccentAnotherOccurrence() {
        val tokens = tokenize("<p>Tuesday and <em>Tuesday</em>.</p>", "Tuesday.")
        assertFalse(tokens.any { it.authorEmphasis })
        assertEquals(listOf("Tuesday", "."), tokens.map { it.text })
    }

    @Test
    fun semanticEmphasisWorksAcrossTokenizerFamilies() {
        for ((language, plain, accented) in listOf(
            Triple("fr", "Je veux ceci.", "ceci"),
            Triple("ar", "أنا أريد هذا.", "هذا"),
            Triple("zh", "今天晴天。", "晴天"),
        )) {
            val html = "<p>${plain.replace(accented, "<em>$accented</em>")}</p>"
            val tokens = tokenize(html, plain, language)
            assertTrue(language, tokens.any { it.authorEmphasis })
            assertEquals(language, accented, tokens.filter { it.authorEmphasis }.joinToString("") { it.text })
        }
    }

    @Test
    fun paragraphBoundariesAndNumberNormalizationDoNotMoveEmphasis() {
        val html = "<p>First.</p><p>I meant <em>50 %</em>, not 20 %.</p>"
        val plain = "First.\n\nI meant 50 %, not 20 %."
        val tokens = tokenize(html, plain)
        assertEquals(listOf("50%"), tokens.filter { it.authorEmphasis }.map { it.text })
        assertEquals(1, tokens.count { it.type == TokenType.PARAGRAPH_BREAK })
        assertEquals(0L, tokens.first { it.type == TokenType.PARAGRAPH_BREAK }.pauseAfterMs)
    }

    @Test
    fun manyAccentsRemainOrderedAndDoNotChangeTokenCount() {
        val html = "<p>" + List(500) { "word <em>again</em>. " }.joinToString("") + "</p>"
        val tokens = tokenize(html, List(500) { "word again. " }.joinToString(""))
        assertEquals(1500, tokens.size)
        assertEquals(500, tokens.count { it.authorEmphasis })
    }

    private fun tokenize(html: String, plain: String, language: String = "en"): List<Token> =
        TokenizerRegistry.resolve(language).tokenize(Chapter(0, null, html, plain))
}
