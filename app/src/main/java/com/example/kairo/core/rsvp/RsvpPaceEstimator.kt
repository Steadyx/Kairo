package com.example.kairo.core.rsvp

import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.tokenization.Tokenizer
import kotlin.math.roundToInt

/**
 * Produces an estimated WPM for a given RSVP profile by running the engine on a representative
 * sample passage and measuring total words / total time.
 *
 * This is intentionally opinionated:
 * - WPM is a *derived* metric (the engine is not driven by a fixed WPM interval).
 * - We disable session ramp + start/end delays so the estimate reflects steady-state pacing.
 */
object RsvpPaceEstimator {
    private val tokenizer = Tokenizer()
    private val engine = ComprehensionRsvpEngine()

    fun estimateWpm(config: RsvpConfig): Int {
        val steadyConfig = config.copy(
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0
        )

        val tokens = tokenizer.tokenize(
            Chapter(
                index = 0,
                title = "Sample",
                htmlContent = "",
                plainText = SAMPLE_TEXT
            )
        )

        val wordCount = tokens.count { it.type == TokenType.WORD }.coerceAtLeast(1)
        val frames = engine.generateFrames(tokens, startIndex = 0, config = steadyConfig)
        val totalMs = frames.sumOf { it.durationMs }.coerceAtLeast(1L)

        val wpm = (wordCount * 60_000.0) / totalMs.toDouble()
        return wpm.roundToInt().coerceAtLeast(1)
    }

    private const val SAMPLE_TEXT =
        "Kairo is built for calm comprehension, even at high speed. " +
        "When a sentence turns—unexpectedly—your eyes should not feel rushed. " +
        "Short words flow; longer words (especially technical ones) slow slightly. " +
        "We pause at commas, breathe at semicolons, and settle at full stops. " +
        "A parenthetical aside (like this) should read naturally, not abruptly. " +
        "\"Quoted dialogue\" can move a bit faster, but remains legible."
}

