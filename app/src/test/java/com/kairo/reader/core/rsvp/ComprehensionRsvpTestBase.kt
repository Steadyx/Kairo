package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType

abstract class ComprehensionRsvpTestBase {
    protected val engine = ComprehensionRsvpEngine()

    protected fun englishFrames(tokens: List<Token>, startIndex: Int, config: RsvpConfig) =
        engine.generateFrames(tokens, startIndex, config, RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH))

    protected fun w(text: String) =
        Token(
            text = text,
            type = TokenType.WORD,
            frequencyScore = 1.0,
            complexityMultiplier = 1.0,
            syllableCount = 1,
        )

    protected fun p(text: String) = Token(text = text, type = TokenType.PUNCTUATION)

    protected fun pageBreak() = Token(text = "\u000C", type = TokenType.PAGE_BREAK)

    protected val stableConfig =
        RsvpConfig(
            tempoMsPerWord = 200L,
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
            smoothingAlpha = 1.0,
            maxSpeedupFactor = 1000.0,
            maxSlowdownFactor = 1000.0,
            enablePhraseChunking = false,
        )

    protected val punctuationConfig =
        stableConfig.copy(
            rarityExtraMaxMs = 0L,
            syllableExtraMs = 0L,
            complexityStrength = 0.0,
            lengthStrength = 0.0,
            lengthExponent = 1.0,
        )
}
