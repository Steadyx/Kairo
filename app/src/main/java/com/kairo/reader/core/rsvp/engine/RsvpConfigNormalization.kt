package com.kairo.reader.core.rsvp.engine

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpConfigConstraints as Constraints
import com.kairo.reader.core.model.RsvpContextAssistMode

internal fun RsvpConfig.normalizedForPlayback(): RsvpConfig {
    val safeMinWordMs = minWordMs.coerceAtLeast(1L)
    return copy(
        tempoMsPerWord = tempoMsPerWord.coerceAtLeast(1L),
        minWordMs = safeMinWordMs,
        longWordMinMs = longWordMinMs.coerceAtLeast(safeMinWordMs),
        longWordChars = longWordChars.coerceAtLeast(1),
        syllableExtraMs = syllableExtraMs.coerceAtLeast(0L),
        rarityExtraMaxMs = rarityExtraMaxMs.coerceAtLeast(0L),
        complexityStrength = complexityStrength.coerceAtLeast(0.0),
        lengthStrength = lengthStrength.coerceAtLeast(0.0),
        lengthExponent = lengthExponent.coerceAtLeast(Constraints.MIN_LENGTH_EXPONENT),
        maxWordsPerUnit = maxWordsPerUnit.coerceAtLeast(1),
        maxCharsPerUnit = maxCharsPerUnit.coerceAtLeast(1),
        subwordChunkPauseMs = subwordChunkPauseMs.coerceAtLeast(0L),
        commaPauseMs = commaPauseMs.coerceAtLeast(0L),
        periodPauseMs = periodPauseMs.coerceAtLeast(0L),
        semicolonPauseMs = semicolonPauseMs.coerceAtLeast(0L),
        colonPauseMs = colonPauseMs.coerceAtLeast(0L),
        dashPauseMs = dashPauseMs.coerceAtLeast(0L),
        parenthesesPauseMs = parenthesesPauseMs.coerceAtLeast(0L),
        quotePauseMs = quotePauseMs.coerceAtLeast(0L),
        sentenceEndPauseMs = sentenceEndPauseMs.coerceAtLeast(0L),
        paragraphPauseMs = paragraphPauseMs.coerceAtLeast(0L),
        paragraphPauseMultiplier =
        paragraphPauseMultiplier.coerceIn(
            Constraints.MIN_PARAGRAPH_PAUSE_MULTIPLIER,
            Constraints.MAX_PARAGRAPH_PAUSE_MULTIPLIER,
        ),
        pageBreakPauseMultiplier =
        pageBreakPauseMultiplier.coerceIn(
            Constraints.MIN_PAGE_BREAK_PAUSE_MULTIPLIER,
            Constraints.MAX_PAGE_BREAK_PAUSE_MULTIPLIER,
        ),
        pauseScaleExponent = pauseScaleExponent.coerceAtLeast(0.0),
        minPauseScale = minPauseScale.coerceIn(0.0, MAX_MIN_PAUSE_SCALE),
        startDelayMs = startDelayMs.coerceAtLeast(0L),
        endDelayMs = endDelayMs.coerceAtLeast(0L),
        rampUpFrames = rampUpFrames.coerceAtLeast(0),
        rampDownFrames = rampDownFrames.coerceAtLeast(0),
        maxChunkLength = maxChunkLength.coerceAtLeast(0),
        punctuationPauseFactor = punctuationPauseFactor.coerceAtLeast(0.0),
        adaptiveDifficultyMaxHoldMs = adaptiveDifficultyMaxHoldMs.coerceAtLeast(0L),
        complexWordHoldMs = complexWordHoldMs.coerceAtLeast(0L),
        complexWordThreshold = complexWordThreshold.coerceAtLeast(0.0),
        clausePauseFactor = clausePauseFactor.coerceAtLeast(1.0),
        parentheticalMultiplier = parentheticalMultiplier.coerceAtLeast(0.0),
        dialogueMultiplier = dialogueMultiplier.coerceAtLeast(0.0),
        smoothingAlpha = smoothingAlpha.coerceIn(0.0, 1.0),
        maxSpeedupFactor = maxSpeedupFactor.coerceAtLeast(1.0),
        maxSlowdownFactor = maxSlowdownFactor.coerceAtLeast(1.0),
        focalSupportCompression = focalSupportCompression.coerceIn(MIN_FOCAL_SUPPORT_COMPRESSION, 1.0),
        anticipatoryLandingBoost = anticipatoryLandingBoost.coerceIn(1.0, MAX_ANTICIPATORY_LANDING_BOOST),
        dialoguePunctuationScale =
        dialoguePunctuationScale.coerceIn(
            Constraints.MIN_DIALOGUE_PUNCTUATION_SCALE,
            Constraints.MAX_DIALOGUE_PUNCTUATION_SCALE,
        ),
        parentheticalAsideMultiplier =
        parentheticalAsideMultiplier.coerceIn(
            Constraints.MIN_PARENTHETICAL_ASIDE_MULTIPLIER,
            Constraints.MAX_PARENTHETICAL_ASIDE_MULTIPLIER,
        ),
    )
}

internal fun RsvpConfig.frameTimingKey(): RsvpConfig =
    copy(
        baseWpm = 0,
        orpEnabled = false,
        orpHighlightEnabled = false,
        orpGuideEnabled = false,
        orpGuideBrightness = 0.0,
        orpGuideThickness = 0.0,
        contextAssistMode = RsvpContextAssistMode.OFF,
        useRegressionAdaptivePacing = false,
    )
