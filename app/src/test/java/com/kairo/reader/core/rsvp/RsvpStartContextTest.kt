package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpStartContextTest : ComprehensionRsvpTestBase() {
    @Test
    fun startingAfterCommaInsideParenthesesRetainsAsideTiming() {
        val tokens = listOf(w("Earlier"), p("("), w("aside"), p(","), w("continued"), p(")"))
        val config = stableConfig.copy(useParentheticalAside = true, parentheticalAsideMultiplier = 0.75)
        val inside = engine.generateFrames(tokens, 4, config).first()
        val outside = engine.generateFrames(tokens.drop(4), 0, config).first()

        assertTrue("The resumed word should retain aside compression", inside.durationMs < outside.durationMs)
    }

    @Test
    fun parenthesesBeforeAnalysisWindowStillShapeResumedWord() {
        val tokens = listOf(p("(")) + List(300) { w("aside") } + listOf(w("continued"), p(")"))
        val config = stableConfig.copy(useParentheticalAside = true, parentheticalAsideMultiplier = 0.75)
        val inside = engine.generateFrames(tokens, 301, config).first()
        val outside = engine.generateFrames(tokens.drop(301), 0, config).first()

        assertTrue(inside.durationMs < outside.durationMs)
    }

    @Test
    fun straightClosingQuoteAfterResumeDoesNotBecomeOpeningQuote() {
        val tokens = listOf(p("\""), w("hello"), w("there"), p("\""), w("outside"))
        val frame = engine.generateFrames(tokens, 2, stableConfig).first()

        assertTrue(frame.tokens.any { it.type == TokenType.PUNCTUATION && it.text == "\"" })
    }
}
