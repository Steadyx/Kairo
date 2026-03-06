package com.example.kairo.core.model

enum class RsvpReadabilityMode { STANDARD, HIGH_SPEED, EXTREME }

fun readabilityModeForTempoMs(tempoMs: Long): RsvpReadabilityMode =
    when {
        tempoMs <= EXTREME_SPEED_TEMPO_MS -> RsvpReadabilityMode.EXTREME
        tempoMs <= HIGH_SPEED_TEMPO_MS -> RsvpReadabilityMode.HIGH_SPEED
        else -> RsvpReadabilityMode.STANDARD
    }

fun RsvpConfig.readabilityMode(tempoMs: Long = this.tempoMsPerWord): RsvpReadabilityMode =
    readabilityModeForTempoMs(tempoMs)

fun RsvpConfig.effectiveBlinkMode(tempoMs: Long = this.tempoMsPerWord): BlinkMode =
    when {
        blinkMode == BlinkMode.OFF -> BlinkMode.OFF
        readabilityMode(tempoMs) == RsvpReadabilityMode.EXTREME -> BlinkMode.ADAPTIVE
        else -> blinkMode
    }

fun RsvpConfig.prefersSimplifiedOrpDisplay(tempoMs: Long = this.tempoMsPerWord): Boolean =
    readabilityMode(tempoMs) == RsvpReadabilityMode.EXTREME

fun RsvpConfig.prefersOrpWindowing(tempoMs: Long = this.tempoMsPerWord): Boolean =
    readabilityMode(tempoMs) != RsvpReadabilityMode.STANDARD

fun RsvpConfig.wordFloorMsForReadability(
    word: Token,
    tempoMs: Long = this.tempoMsPerWord,
): Long {
    val base =
        if (word.isSubwordChunk) {
            longWordMinMs
        } else {
            val letters = word.text.count { it.isLetterOrDigit() }
            if (letters >= longWordChars) longWordMinMs else minWordMs
        }
    val bonus =
        when (readabilityMode(tempoMs)) {
            RsvpReadabilityMode.EXTREME -> if (word.isSubwordChunk) 18L else 8L
            RsvpReadabilityMode.HIGH_SPEED -> if (word.isSubwordChunk) 10L else 4L
            RsvpReadabilityMode.STANDARD -> 0L
        }
    return base + bonus
}

private const val HIGH_SPEED_TEMPO_MS = 82L
private const val EXTREME_SPEED_TEMPO_MS = 62L
