package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.tokenization.Tokenizer
import kotlin.math.max
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

    fun estimateWpm(
        config: RsvpConfig,
        options: RsvpPaceEstimationOptions = RsvpPaceEstimationOptions.DEFAULT,
    ): Int {
        val steadyConfig =
            config.copy(
                startDelayMs = 0L,
                endDelayMs = 0L,
                rampUpFrames = 0,
                rampDownFrames = 0,
            )

        val tokens =
            tokenizer.tokenize(
                Chapter(
                    index = 0,
                    title = "Sample",
                    htmlContent = "",
                    plainText = SAMPLE_TEXT,
                ),
            )

        val wordCount = tokens.count { it.type == TokenType.WORD }.coerceAtLeast(1)
        val frames =
            engine.generateFrames(
                tokens = tokens,
                startIndex = 0,
                config = steadyConfig,
                options = options.asGenerationOptions(),
            )
        val totalMs =
            frames.sumOf { frame ->
                if (shouldSkipBlinkFrame(
                        frame = frame,
                        config = steadyConfig,
                        effectiveTempoMs = steadyConfig.tempoMsPerWord,
                        tempoScale = STEADY_TEMPO_SCALE,
                    )
                ) {
                    0L
                } else {
                    max(
                        frame.durationMs,
                        frameFloorMs(
                            frame = frame,
                            config = steadyConfig,
                            effectiveTempoMs = steadyConfig.tempoMsPerWord,
                        ),
                    )
                }
            }.coerceAtLeast(1L)

        val wpm = (wordCount * MS_PER_MINUTE) / totalMs.toDouble()
        return wpm.roundToInt().coerceAtLeast(1)
    }

    private const val MS_PER_MINUTE = 60_000.0
    private const val STEADY_TEMPO_SCALE = 1.0
    private const val SAMPLE_TEXT =
        "Kairo is built for calm comprehension, even at high speed. " +
            "When a sentence turns—unexpectedly—your eyes should not feel rushed. " +
            "Short words flow; longer words (especially technical ones) slow slightly. " +
            "We pause at commas, breathe at semicolons, and settle at full stops. " +
            "A parenthetical aside (like this) should read naturally, not abruptly. " +
            "\"Quoted dialogue\" can move a bit faster, but remains legible."
}
