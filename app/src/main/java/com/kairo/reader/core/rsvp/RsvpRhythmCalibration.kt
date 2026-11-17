package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpConfigConstraints

internal enum class RsvpRhythmAspect { LANDINGS, VARIATION, RESTART }
internal enum class RsvpRhythmChoice { KEEP, GENTLE, EXPRESSIVE }

internal fun savedRhythmChoice(config: RsvpConfig, aspect: RsvpRhythmAspect): RsvpRhythmChoice =
    RsvpRhythmChoice.entries.firstOrNull { choice ->
        choice != RsvpRhythmChoice.KEEP && rhythmCandidate(config, aspect, choice) == config
    } ?: RsvpRhythmChoice.KEEP

/** Applies only explicitly chosen rhythm fields, leaving speed, readability and other preferences intact. */
internal fun applyRsvpRhythmChoices(config: RsvpConfig, choices: List<RsvpRhythmChoice>): RsvpConfig =
    choices.take(RsvpRhythmAspect.entries.size).foldIndexed(config) { index, current, choice ->
        rhythmCandidate(current, RsvpRhythmAspect.entries[index], choice)
    }

internal fun rhythmCandidate(config: RsvpConfig, aspect: RsvpRhythmAspect, choice: RsvpRhythmChoice): RsvpConfig {
    if (choice == RsvpRhythmChoice.KEEP) return config
    val expressive = choice == RsvpRhythmChoice.EXPRESSIVE
    return when (aspect) {
        RsvpRhythmAspect.LANDINGS -> config.copy(
            useAnticipatoryLanding = true,
            anticipatoryLandingBoost = if (expressive) EXPRESSIVE_LANDING else GENTLE_LANDING,
            clausePauseFactor = if (expressive) EXPRESSIVE_CLAUSE else RsvpConfigConstraints.MIN_CLAUSE_PAUSE_FACTOR,
        )
        RsvpRhythmAspect.VARIATION -> config.copy(
            useProsodyPacing = true,
            useFocalStress = true,
            prosodyStrength = if (expressive) EXPRESSIVE_PROSODY else GENTLE_PROSODY,
            focalSupportCompression = if (expressive) EXPRESSIVE_COMPRESSION else GENTLE_COMPRESSION,
        )
        RsvpRhythmAspect.RESTART -> config.copy(
            startDelayMs = if (expressive) EXPRESSIVE_START_MS else GENTLE_START_MS,
            rampUpFrames = if (expressive) EXPRESSIVE_RAMP_WORDS else GENTLE_RAMP_WORDS,
        )
    }
}

private const val GENTLE_LANDING = 1.02
private const val EXPRESSIVE_LANDING = 1.12
private const val EXPRESSIVE_CLAUSE = 1.4
private const val GENTLE_PROSODY = 0.45
private const val EXPRESSIVE_PROSODY = 1.1
private const val GENTLE_COMPRESSION = 0.98
private const val EXPRESSIVE_COMPRESSION = 0.90
private const val GENTLE_START_MS = 120L
private const val EXPRESSIVE_START_MS = 350L
private const val GENTLE_RAMP_WORDS = 2
private const val EXPRESSIVE_RAMP_WORDS = 6
