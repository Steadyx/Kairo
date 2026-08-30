package com.kairo.reader.core.rsvp.analysis

import com.kairo.reader.core.linguistics.ClauseDetector
import com.kairo.reader.core.linguistics.DialogueAnalyzer
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.engine.ACRONYM_EMPHASIS_BOOST
import com.kairo.reader.core.rsvp.engine.AUXILIARY_BRIDGE_MAX_CHARS
import com.kairo.reader.core.rsvp.engine.AUXILIARY_BRIDGE_WORDS
import com.kairo.reader.core.rsvp.engine.AUXILIARY_CONTENT_MAX_CHARS
import com.kairo.reader.core.rsvp.engine.AUXILIARY_CONTENT_MIN_FREQUENCY
import com.kairo.reader.core.rsvp.engine.BoundaryBefore
import com.kairo.reader.core.rsvp.engine.COHERENCE_HOLD_MS
import com.kairo.reader.core.rsvp.engine.CONTENT_WORD_STRESS_BOOST
import com.kairo.reader.core.rsvp.engine.EASY_PAIR_THRESHOLD
import com.kairo.reader.core.rsvp.engine.FUNCTION_BRIDGE_COHERENCE_THRESHOLD
import com.kairo.reader.core.rsvp.engine.FUNCTION_BRIDGE_HOLD_MS
import com.kairo.reader.core.rsvp.engine.FUNCTION_BRIDGE_WORDS
import com.kairo.reader.core.rsvp.engine.FUNCTION_WORDS
import com.kairo.reader.core.rsvp.engine.FUNCTION_WORD_GLIDE_LIGHT
import com.kairo.reader.core.rsvp.engine.FUNCTION_WORD_GLIDE_STRONG
import com.kairo.reader.core.rsvp.engine.GIVENNESS_GLIDE
import com.kairo.reader.core.rsvp.engine.GIVENNESS_MIN_CHARS
import com.kairo.reader.core.rsvp.engine.GLUE_WORDS
import com.kairo.reader.core.rsvp.engine.MAX_BOUNDARY_TAIL_LIFT
import com.kairo.reader.core.rsvp.engine.MAX_EMPHASIS_MULTIPLIER
import com.kairo.reader.core.rsvp.engine.MAX_PROSODY_MULTIPLIER
import com.kairo.reader.core.rsvp.engine.MIN_BOUNDARY_TAIL_LIFT_STRENGTH
import com.kairo.reader.core.rsvp.engine.MIN_PROSODY_MULTIPLIER
import com.kairo.reader.core.rsvp.engine.NUMBER_EMPHASIS_BOOST
import com.kairo.reader.core.rsvp.engine.PHRASE_BREAK_HOLD_MS
import com.kairo.reader.core.rsvp.engine.PHRASE_BREATH_BASE
import com.kairo.reader.core.rsvp.engine.PHRASE_ONSET_LIFT
import com.kairo.reader.core.rsvp.engine.PHRASE_PRE_BOUNDARY_LIFT
import com.kairo.reader.core.rsvp.engine.PRONOUN_BRIDGE_MAX_CHARS
import com.kairo.reader.core.rsvp.engine.PRONOUN_BRIDGE_WORDS
import com.kairo.reader.core.rsvp.engine.PROPER_NOUN_BOOST
import com.kairo.reader.core.rsvp.engine.ProseState
import com.kairo.reader.core.rsvp.engine.SEMANTIC_ANCHOR_BOOST
import com.kairo.reader.core.rsvp.engine.SEMANTIC_ANCHOR_WORDS
import com.kairo.reader.core.rsvp.engine.TIGHT_PAIR_HINTS
import com.kairo.reader.core.rsvp.engine.TRANSITION_HOLD_BASE_MS
import com.kairo.reader.core.rsvp.engine.TRANSITION_HOLD_EXTRA_MS
import com.kairo.reader.core.rsvp.text.isDecimalPoint
import com.kairo.reader.core.rsvp.text.isThousandSeparator
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTimingPolicy
import kotlin.math.max

internal fun emphasisMultiplier(
    token: Token,
    isFirstWord: Boolean,
    boundaryBefore: BoundaryBefore,
    speedStrength: Double,
): Double {
    val text = token.text
    if (text.isEmpty()) return 1.0

    val isSentenceStart = isFirstWord && boundaryBefore.isMajorStart()
    val letters = text.filter { it.isLetter() }
    val hasDigits = text.any { it.isDigit() }
    val isAcronym =
        letters.length in 2..5 && letters.isNotEmpty() && letters.all { it.isUpperCase() }
    val startsUpper = letters.firstOrNull()?.isUpperCase() == true
    val hasLower = letters.any { it.isLowerCase() }
    val isProper = startsUpper && hasLower && !isSentenceStart

    var multiplier = 1.0
    if (hasDigits) {
        multiplier *= 1.0 + (NUMBER_EMPHASIS_BOOST * speedStrength)
    }
    if (isAcronym) {
        multiplier *= 1.0 + (ACRONYM_EMPHASIS_BOOST * speedStrength)
    }
    if (isProper) {
        multiplier *= 1.0 + (PROPER_NOUN_BOOST * speedStrength)
    }

    return multiplier.coerceIn(1.0, MAX_EMPHASIS_MULTIPLIER)
}

internal fun prosodyMultiplier(
    token: Token,
    prevWordText: String?,
    nextWordText: String?,
    isFirstWord: Boolean,
    boundaryBefore: BoundaryBefore,
    speedStrength: Double,
    prosodyStrength: Double,
): Double {
    if (prosodyStrength <= 0.0) return 1.0
    if (token.isSubwordChunk) return 1.0

    val wordLower = normalizeWord(token.text)
    if (wordLower.isEmpty()) return 1.0

    val prevLower = prevWordText?.let(::normalizeWord)?.takeIf { it.isNotEmpty() }
    val nextLower = nextWordText?.let(::normalizeWord)?.takeIf { it.isNotEmpty() }

    val isFunction = isFunctionWord(wordLower)
    val isAnchor = isSemanticAnchor(wordLower)
    val effectiveStrength = speedStrength * prosodyStrength
    var multiplier = 1.0

    val canGlide =
        isFunction &&
            !isAnchor &&
            boundaryBefore == BoundaryBefore.NONE &&
            nextLower != null
    if (canGlide) {
        val coherence = ClauseDetector.getCoherenceScore(wordLower, nextLower)
        val glideStrength =
            if (coherence >= FUNCTION_BRIDGE_COHERENCE_THRESHOLD ||
                isFunctionBridgePair(wordLower, nextLower)
            ) {
                FUNCTION_WORD_GLIDE_STRONG
            } else {
                FUNCTION_WORD_GLIDE_LIGHT
            }
        multiplier -= glideStrength * effectiveStrength
    }

    if (isAnchor) {
        multiplier += SEMANTIC_ANCHOR_BOOST * effectiveStrength
    }

    val surroundedByFunctionWords =
        !isFunction &&
            (
                (prevLower != null && isFunctionWord(prevLower)) ||
                    (nextLower != null && isFunctionWord(nextLower))
                )
    if (surroundedByFunctionWords) {
        multiplier += CONTENT_WORD_STRESS_BOOST * effectiveStrength
    }

    // Keep sentence starts crisp; don't compress them when they start with function words.
    if (isFirstWord && boundaryBefore.isMajorStart() && isFunction) {
        multiplier = max(multiplier, 1.0)
    }

    return multiplier.coerceIn(MIN_PROSODY_MULTIPLIER, MAX_PROSODY_MULTIPLIER)
}

internal fun isFunctionBridgePair(
    currentLower: String,
    nextLower: String,
): Boolean {
    if (!isFunctionWord(currentLower)) return false
    if (isFunctionWord(nextLower)) return false
    if (isSemanticAnchor(currentLower)) return false

    val coherence = ClauseDetector.getCoherenceScore(currentLower, nextLower)
    return coherence >= FUNCTION_BRIDGE_COHERENCE_THRESHOLD ||
        currentLower in FUNCTION_BRIDGE_WORDS
}

internal fun isSemanticAnchor(wordLower: String): Boolean = wordLower in SEMANTIC_ANCHOR_WORDS

internal fun isFunctionWord(wordLower: String): Boolean = wordLower in FUNCTION_WORDS

internal fun normalizeWord(text: String): String =
    text.lowercase().trim('"', '\'', '\u2018', '\u2019')

internal fun shouldKeepFullFocalDuration(token: Token): Boolean =
    token.isSubwordChunk || token.text.endsWith("-")

internal fun speakerTagMultiplier(
    wordsInFrame: List<Token>,
    prevWord: Token?,
    nextWord: Token?,
    config: RsvpConfig,
    speedStrength: Double,
    explicitSpeakerTag: Boolean = false,
): Double {
    if (!config.useDialogueDetection) return 1.0

    if (wordsInFrame.isEmpty()) return 1.0
    if (wordsInFrame.any { it.isDialogue }) return 1.0
    if (wordsInFrame.size > MAX_SPEAKER_TAG_FRAME_WORDS) return 1.0

    val prevText = prevWord?.text
    val nextText = nextWord?.text
    val hasSpeakerVerb =
        explicitSpeakerTag ||
            wordsInFrame.any { DialogueAnalyzer.isSpeakerVerb(it.text) } ||
            (prevText != null && DialogueAnalyzer.isSpeakerVerb(prevText)) ||
            (nextText != null && DialogueAnalyzer.isSpeakerVerb(nextText))
    if (!hasSpeakerVerb) return 1.0

    val frameTexts = wordsInFrame.map { it.text }
    val candidates = mutableListOf<List<String>>()

    when (frameTexts.size) {
        1 -> {
            val current = frameTexts[0]
            if (prevText != null) candidates += listOf(prevText, current)
            if (nextText != null) candidates += listOf(current, nextText)
            if (prevText != null &&
                nextText != null
            ) {
                candidates += listOf(prevText, current, nextText)
            }
        }
        else -> {
            candidates += frameTexts
            if (prevText != null) candidates += listOf(prevText) + frameTexts
            if (nextText != null) candidates += frameTexts + listOf(nextText)
        }
    }

    val matchesTag = explicitSpeakerTag || candidates.any { DialogueAnalyzer.isSpeakerTag(it) }
    return if (matchesTag) {
        val dialogueSpeedStrength =
            max(
                MIN_DIALOGUE_SPEED_STRENGTH,
                speedStrength + (speedStrength * speedStrength),
            )
        contextShapingMultiplier(
            targetMultiplier = DialogueAnalyzer.SPEAKER_TAG_MULTIPLIER,
            speedStrength = dialogueSpeedStrength,
        )
    } else {
        1.0
    }
}

internal fun contextShapingMultiplier(
    targetMultiplier: Double,
    speedStrength: Double,
): Double {
    if (targetMultiplier == 1.0) return 1.0
    val effectStrength = speedStrength.coerceIn(0.0, 1.0)
    return 1.0 + ((targetMultiplier - 1.0) * effectStrength)
}

internal fun transitionHoldMs(
    frameTokens: List<Token>,
    firstWord: Token?,
    nextWord: Token?,
    speedStrength: Double,
    prosodyStrength: Double,
): Double {
    if (firstWord == null || nextWord == null) return 0.0
    if (frameTokens.count { it.type == TokenType.WORD } != 1) return 0.0
    if (frameTokens.any { it.type == TokenType.PUNCTUATION }) return 0.0

    val firstLower = firstWord.text.lowercase()
    val nextLower = nextWord.text.lowercase()

    // Function words should glide into content words with minimal visual gap.
    if (prosodyStrength > 0.0 && isFunctionBridgePair(firstLower, nextLower)) {
        return FUNCTION_BRIDGE_HOLD_MS * speedStrength * prosodyStrength
    }

    // Check for tight pair patterns that should stay together mentally
    if (shouldPreferHold(firstWord, nextWord)) {
        val hold = TRANSITION_HOLD_BASE_MS + (TRANSITION_HOLD_EXTRA_MS * speedStrength)
        return hold.coerceAtLeast(0.0)
    }

    // Add coherence hold when current word "leads into" next word
    // This helps readers anticipate and process the next word
    if (isCoherencePair(firstLower, nextLower)) {
        return COHERENCE_HOLD_MS * speedStrength
    }

    // Breath before a grammatical phrase boundary (clause-starter / joining conjunction) that
    // carries no punctuation. Kept present even at moderate speed via PHRASE_BREATH_BASE so the
    // phrasing of the inner voice is actually felt, not just a sliver at very high WPM.
    if (isPhraseBreakBefore(firstLower, nextLower, nextWord)) {
        return PHRASE_BREAK_HOLD_MS * (PHRASE_BREATH_BASE + ((1.0 - PHRASE_BREATH_BASE) * speedStrength))
    }

    return 0.0
}

internal fun isCoherencePair(
    currentLower: String,
    nextLower: String,
): Boolean {
    // Article followed by adjective or noun - reader anticipates the noun
    if (currentLower in setOf("a", "an", "the") && nextLower.length > 2) {
        return true
    }
    // Possessive followed by noun
    if (currentLower in setOf("my", "your", "his", "her", "its", "our", "their") &&
        nextLower.length > 2
    ) {
        return true
    }
    // Adverb modifiers that lead into verbs/adjectives
    if (currentLower in setOf("very", "quite", "rather", "too", "so", "really", "just") &&
        nextLower.length > 2
    ) {
        return true
    }
    // Modal verbs leading into main verb
    if (currentLower in setOf("will", "would", "can", "could", "should", "must", "may", "might") &&
        nextLower !in setOf("be", "have", "not", "the", "a", "an")
    ) {
        return true
    }
    return false
}

/**
 * Phrase-arc shaping for a single-word frame at a grammatical (punctuation-free) phrase boundary.
 *
 * Uses the same [isPhraseBreakBefore] detector as the breath in [transitionHoldMs], so the arc and
 * the breath always fire together: the word closing an intonation unit stretches slightly
 * (pre-boundary lengthening) and the clause-starter opening the next one gets a small onset lift
 * instead of being swallowed by the function-word glide. Punctuation-adjacent words are excluded —
 * the punctuation contour machinery already shapes those.
 */
internal fun phraseBoundaryShapeMultiplier(
    word: Token,
    prevWord: Token?,
    nextWord: Token?,
    boundaryBefore: BoundaryBefore,
    speedStrength: Double,
    prosodyStrength: Double,
): Double {
    if (prosodyStrength <= 0.0 || word.isSubwordChunk) return 1.0
    val wordLower = word.text.lowercase()
    if (wordLower.isEmpty()) return 1.0

    val presence = PHRASE_BREATH_BASE + ((1.0 - PHRASE_BREATH_BASE) * speedStrength)
    var multiplier = 1.0

    if (nextWord != null && isPhraseBreakBefore(wordLower, nextWord.text.lowercase(), nextWord)) {
        multiplier *= 1.0 + (PHRASE_PRE_BOUNDARY_LIFT * presence * prosodyStrength)
    }
    if (prevWord != null &&
        boundaryBefore == BoundaryBefore.NONE &&
        isPhraseBreakBefore(prevWord.text.lowercase(), wordLower, word)
    ) {
        multiplier *= 1.0 + (PHRASE_ONSET_LIFT * presence * prosodyStrength)
    }
    return multiplier
}

/**
 * Given/new glide: words shown recently read lighter on re-mention.
 *
 * Only substantial non-function words are tracked, so the within-phrase evenness of glue words is
 * untouched. Mutates [prose] (records this word as seen) — call exactly once per displayed word.
 */
internal fun givennessGlideMultiplier(
    token: Token,
    prose: ProseState,
    speedStrength: Double,
): Double {
    val key = givennessKey(token) ?: return 1.0

    val given = prose.isGiven(key)
    prose.record(key)
    if (!given) return 1.0

    return 1.0 - (GIVENNESS_GLIDE * speedStrength.coerceIn(0.0, 1.0))
}

internal fun recordGivennessWord(
    token: Token,
    prose: ProseState,
) {
    val key = givennessKey(token) ?: return
    prose.record(key)
}

private fun givennessKey(token: Token): String? {
    if (token.isSubwordChunk) return null
    val key = givennessKey(token.text)
    return key.takeIf { it.length >= GIVENNESS_MIN_CHARS && !isFunctionWord(it) }
}

private fun givennessKey(text: String): String =
    normalizeWord(text)
        .removeSuffix("'s")
        .removeSuffix("’s")
        .filter { it.isLetter() }

internal fun isPhraseBreakBefore(
    currentLower: String,
    nextLower: String,
    nextWord: Token,
): Boolean {
    // Before clause starters, add a micro-pause for comprehension
    if (nextLower in setOf(
            "which",
            "who",
            "whom",
            "whose",
            "that",
            "where",
            "when",
            "because",
            "although",
            "though",
            "unless",
            "until",
            "while",
            "after",
            "before",
            "if",
        )
    ) {
        return true
    }
    // Before coordinating conjunctions in longer sentences
    if (nextLower in setOf("and", "but", "or", "yet", "so") &&
        currentLower.length > MIN_COORDINATING_BOUNDARY_WORD_CHARS &&
        !nextWord.isClauseBoundary
    ) {
        return true
    }
    return false
}

internal fun frameDifficulty(words: List<Token>): Double {
    if (words.isEmpty()) return 0.0
    val total = words.sumOf { (1.0 - wordEase(it)) }
    return (total / words.size).coerceIn(0.0, 1.0)
}

internal fun shouldPreferHold(
    prev: Token,
    next: Token,
): Boolean {
    val prevLower = prev.text.lowercase()
    val nextLower = next.text.lowercase()
    val pairKey = "$prevLower $nextLower"
    val isHinted = pairKey in TIGHT_PAIR_HINTS
    val gluePair =
        prevLower in GLUE_WORDS &&
            nextLower in GLUE_WORDS &&
            prev.text.length <= MAX_GLUE_PAIR_WORD_CHARS &&
            next.text.length <= MAX_GLUE_PAIR_WORD_CHARS
    val easyPair =
        wordEase(prev) >= EASY_PAIR_THRESHOLD && wordEase(next) >= EASY_PAIR_THRESHOLD

    // Check coherence score for high-coherence pairs
    val coherence = ClauseDetector.getCoherenceScore(prevLower, nextLower)
    val highCoherence =
        coherence >= HIGH_COHERENCE_HOLD_THRESHOLD &&
            prev.text.length <= MAX_HIGH_COHERENCE_PREV_CHARS &&
            next.text.length <= MAX_HIGH_COHERENCE_NEXT_CHARS

    return (easyPair && (isHinted || gluePair)) || highCoherence
}

internal fun multiWordPenalty(wordCount: Int): Double =
    when (wordCount) {
        0, 1 -> 1.0
        2 -> TWO_WORD_FRAME_PENALTY
        else -> MULTI_WORD_FRAME_PENALTY
    }

internal enum class PhraseChunkReason {
    TIGHT_PAIR,
    PRONOUN_AUXILIARY,
    AUXILIARY_CONTENT,
    COHERENT_SHORT_PAIR,
    GENERAL_SHORT_PAIR,
    SUBWORD,
    TRAILING_HYPHEN,
    CLAUSE_BOUNDARY,
    COORDINATING_CONJUNCTION,
    SEMANTIC_ANCHOR,
    NO_AFFINITY,
}

internal data class PhraseChunkPairFeatures(
    val legacyCompatible: Boolean,
    val tightPair: Boolean,
    val pronounAuxiliaryBridge: Boolean,
    val auxiliaryContentBridge: Boolean,
    val coherentShortPair: Boolean,
    val generalShortPair: Boolean,
    val gluePair: Boolean,
    val bothCommon: Boolean,
    val coherenceScoreMilli: Int,
    val reasons: List<PhraseChunkReason>,
)

private data class PhraseChunkDecisionEvidence(
    val tightPair: Boolean,
    val pronounAuxiliaryBridge: Boolean,
    val auxiliaryContentBridge: Boolean,
    val coherentShortPair: Boolean,
    val generalShortPair: Boolean,
    val hasSubword: Boolean,
    val trailingHyphen: Boolean,
    val clauseBoundary: Boolean,
    val coordinatingConjunction: Boolean,
    val semanticAnchor: Boolean,
)

internal fun analyzePhraseChunkPair(
    prev: Token,
    next: Token,
): PhraseChunkPairFeatures {
    val prevLower = prev.text.lowercase()
    val nextLower = next.text.lowercase()
    val pairKey = "$prevLower $nextLower"

    val pronounAuxiliaryBridge =
        prevLower in PRONOUN_BRIDGE_WORDS &&
            nextLower in AUXILIARY_BRIDGE_WORDS &&
            prev.text.length <= PRONOUN_BRIDGE_MAX_CHARS &&
            next.text.length <= AUXILIARY_BRIDGE_MAX_CHARS
    val auxiliaryContentBridge =
        prevLower in AUXILIARY_BRIDGE_WORDS &&
            nextLower !in SEMANTIC_ANCHOR_WORDS &&
            next.text.length <= AUXILIARY_CONTENT_MAX_CHARS &&
            next.frequencyScore >= AUXILIARY_CONTENT_MIN_FREQUENCY
    val coherenceScore = ClauseDetector.getCoherenceScore(prevLower, nextLower)
    val coherentShortPair =
        coherenceScore >= PHRASE_CHUNK_COHERENCE_THRESHOLD &&
            prev.text.length <= MAX_COHERENT_CHUNK_PREV_CHARS &&
            next.text.length <= MAX_COHERENT_CHUNK_NEXT_CHARS

    val glue = prevLower in GLUE_WORDS || nextLower in GLUE_WORDS
    val bothShort =
        prev.text.length <= MAX_GENERAL_CHUNK_PREV_CHARS &&
            next.text.length <= MAX_GENERAL_CHUNK_NEXT_CHARS
    val bothCommon =
        prev.frequencyScore >= COMMON_WORD_FREQUENCY_THRESHOLD &&
            next.frequencyScore >= COMMON_WORD_FREQUENCY_THRESHOLD

    val generalShortPair = (glue && bothShort) || (bothShort && bothCommon)
    val tightPair = pairKey in TIGHT_PAIR_HINTS
    val hasSubword = prev.isSubwordChunk || next.isSubwordChunk
    val trailingHyphen = prev.text.endsWith("-")
    val clauseBoundary = prev.isClauseBoundary || next.isClauseBoundary
    val coordinatingConjunction = ClauseDetector.isCoordinatingConjunction(prevLower)
    val semanticAnchor = nextLower in SEMANTIC_ANCHOR_WORDS
    val decisionEvidence =
        PhraseChunkDecisionEvidence(
            tightPair = tightPair,
            pronounAuxiliaryBridge = pronounAuxiliaryBridge,
            auxiliaryContentBridge = auxiliaryContentBridge,
            coherentShortPair = coherentShortPair,
            generalShortPair = generalShortPair,
            hasSubword = hasSubword,
            trailingHyphen = trailingHyphen,
            clauseBoundary = clauseBoundary,
            coordinatingConjunction = coordinatingConjunction,
            semanticAnchor = semanticAnchor,
        )
    return PhraseChunkPairFeatures(
        legacyCompatible = decisionEvidence.isLegacyCompatible(),
        tightPair = tightPair,
        pronounAuxiliaryBridge = pronounAuxiliaryBridge,
        auxiliaryContentBridge = auxiliaryContentBridge,
        coherentShortPair = coherentShortPair,
        generalShortPair = generalShortPair,
        gluePair = glue,
        bothCommon = bothCommon,
        coherenceScoreMilli = (coherenceScore.coerceIn(0.0, 1.0) * FIXED_POINT_SCALE).toInt(),
        reasons = decisionEvidence.reasons(),
    )
}

private fun PhraseChunkDecisionEvidence.isLegacyCompatible(): Boolean {
    val disqualified = hasSubword || trailingHyphen || clauseBoundary || coordinatingConjunction
    return when {
        disqualified -> false
        tightPair -> true
        semanticAnchor -> false
        pronounAuxiliaryBridge || auxiliaryContentBridge -> true
        else -> coherentShortPair || generalShortPair
    }
}

private fun PhraseChunkDecisionEvidence.reasons(): List<PhraseChunkReason> =
    buildList {
        if (tightPair) add(PhraseChunkReason.TIGHT_PAIR)
        if (pronounAuxiliaryBridge) add(PhraseChunkReason.PRONOUN_AUXILIARY)
        if (auxiliaryContentBridge) add(PhraseChunkReason.AUXILIARY_CONTENT)
        if (coherentShortPair) add(PhraseChunkReason.COHERENT_SHORT_PAIR)
        if (generalShortPair) add(PhraseChunkReason.GENERAL_SHORT_PAIR)
        if (hasSubword) add(PhraseChunkReason.SUBWORD)
        if (trailingHyphen) add(PhraseChunkReason.TRAILING_HYPHEN)
        if (clauseBoundary) add(PhraseChunkReason.CLAUSE_BOUNDARY)
        if (coordinatingConjunction) add(PhraseChunkReason.COORDINATING_CONJUNCTION)
        if (semanticAnchor) add(PhraseChunkReason.SEMANTIC_ANCHOR)
        if (isEmpty()) add(PhraseChunkReason.NO_AFFINITY)
    }

internal fun isPhraseChunkCandidate(
    prev: Token,
    next: Token,
): Boolean = analyzePhraseChunkPair(prev, next).legacyCompatible

internal fun terminalWordMultiplier(
    wordIndex: Int,
    word: Token,
    frameTokens: List<Token>,
    nextToken: Token?,
    speedStrength: Double,
    emDashContourScale: Double = 1.0,
): Double {
    val punctIndex =
        (wordIndex + 1 until frameTokens.size).firstOrNull {
            frameTokens[it].type ==
                TokenType.PUNCTUATION
        }
            ?: return 1.0
    if (frameTokens.subList(wordIndex + 1, punctIndex).any {
            it.type == TokenType.WORD
        }
    ) {
        return 1.0
    }

    val punctToken = frameTokens[punctIndex]
    val ch = punctToken.text.firstOrNull() ?: return 1.0

    val tokenAfterPunct = frameTokens.getOrNull(punctIndex + 1) ?: nextToken

    if (ch == ',' && isThousandSeparator(word.text, tokenAfterPunct)) return 1.0
    if (ch == '.' && isDecimalPoint(word.text, tokenAfterPunct)) return 1.0

    val contourStrength =
        boundaryTailLiftWeight(
            token = punctToken,
            prevWord = word,
            nextToken = tokenAfterPunct,
        )
    if (contourStrength <= 0.0) return 1.0

    // A paired aside dash is a dip, not a stop: damp the pre-boundary stretch with it.
    val dashScale = if (isEmDashChar(ch)) emDashContourScale else 1.0
    val effectiveStrength = max(speedStrength, MIN_BOUNDARY_TAIL_LIFT_STRENGTH)
    val extra = MAX_BOUNDARY_TAIL_LIFT * contourStrength * dashScale

    return 1.0 + (extra * effectiveStrength)
}

internal fun boundaryTailLiftWeight(
    token: Token,
    prevWord: Token?,
    nextToken: Token?,
): Double {
    return RsvpPunctuationTimingPolicy.boundaryTailLiftWeight(
        token = token,
        prevWord = prevWord,
        nextToken = nextToken,
    )
}

internal fun wordEase(word: Token): Double {
    val letters = word.text.count { it.isLetterOrDigit() }.coerceAtLeast(1)
    val lengthScore =
        (
            (letters - WORD_EASE_BASE_CHARS).coerceAtLeast(0) /
                WORD_EASE_LENGTH_SCALE
            ).coerceIn(0.0, 1.0)
    val syllableScore =
        (
            (word.syllableCount - 1).coerceAtLeast(0) /
                WORD_EASE_SYLLABLE_SCALE
            ).coerceIn(0.0, 1.0)
    val rarityScore = (1.0 - word.frequencyScore).coerceIn(0.0, 1.0)
    val complexityScore = (word.complexityMultiplier - 1.0).coerceAtLeast(
        0.0
    ).coerceIn(0.0, 1.0)

    val difficulty =
        (lengthScore * WORD_EASE_LENGTH_WEIGHT) +
            (syllableScore * WORD_EASE_SYLLABLE_WEIGHT) +
            (rarityScore * WORD_EASE_RARITY_WEIGHT) +
            (complexityScore * WORD_EASE_COMPLEXITY_WEIGHT)

    return (1.0 - difficulty).coerceIn(0.0, 1.0)
}

private const val MAX_SPEAKER_TAG_FRAME_WORDS = 3
private const val MIN_DIALOGUE_SPEED_STRENGTH = 0.15
private const val MIN_COORDINATING_BOUNDARY_WORD_CHARS = 3
private const val MAX_GLUE_PAIR_WORD_CHARS = 4
private const val HIGH_COHERENCE_HOLD_THRESHOLD = 0.65
private const val MAX_HIGH_COHERENCE_PREV_CHARS = 5
private const val MAX_HIGH_COHERENCE_NEXT_CHARS = 6
private const val TWO_WORD_FRAME_PENALTY = 1.06
private const val MULTI_WORD_FRAME_PENALTY = 1.12
private const val PHRASE_CHUNK_COHERENCE_THRESHOLD = 0.7
private const val MAX_COHERENT_CHUNK_PREV_CHARS = 5
private const val MAX_COHERENT_CHUNK_NEXT_CHARS = 8
private const val MAX_GENERAL_CHUNK_PREV_CHARS = 4
private const val MAX_GENERAL_CHUNK_NEXT_CHARS = 7
private const val COMMON_WORD_FREQUENCY_THRESHOLD = 0.7
private const val FIXED_POINT_SCALE = 1000
private const val WORD_EASE_BASE_CHARS = 4
private const val WORD_EASE_LENGTH_SCALE = 8.0
private const val WORD_EASE_SYLLABLE_SCALE = 4.0
private const val WORD_EASE_LENGTH_WEIGHT = 0.35
private const val WORD_EASE_SYLLABLE_WEIGHT = 0.25
private const val WORD_EASE_RARITY_WEIGHT = 0.25
private const val WORD_EASE_COMPLEXITY_WEIGHT = 0.15
