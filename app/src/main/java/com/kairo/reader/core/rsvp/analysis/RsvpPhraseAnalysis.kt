@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.core.rsvp.analysis

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.isSentenceEndingPunctuation
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy
import com.kairo.reader.core.rsvp.engine.CLAUSE_ANTICIPATORY_CONTOUR
import com.kairo.reader.core.rsvp.engine.CLAUSE_PRE_BOUNDARY_CONTOUR
import com.kairo.reader.core.rsvp.engine.CLAUSE_RESTART_CONTOUR
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import com.kairo.reader.core.rsvp.engine.PHRASE_CONTOUR_WORD_WINDOW
import com.kairo.reader.core.rsvp.engine.PhraseContour
import com.kairo.reader.core.rsvp.engine.SENTENCE_ANTICIPATORY_CONTOUR
import com.kairo.reader.core.rsvp.engine.SENTENCE_PRE_BOUNDARY_CONTOUR
import com.kairo.reader.core.rsvp.engine.SENTENCE_RESTART_CONTOUR
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTier
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTimingPolicy
import kotlin.math.max

internal data class RsvpTokenAnalysis(
    val focalWordIndices: Set<Int>,
    val landingWordIndices: Set<Int>,
    val emDashAsideIndices: Set<Int>,
    /** Expanded indices of em/en-dash tokens that open or close a paired aside within a sentence. */
    val pairedEmDashIndices: Set<Int>,
    val phraseContours: Map<Int, PhraseContour>,
    val thoughtCues: Map<Int, RsvpThoughtCue> = emptyMap(),
) {
    companion object {
        val EMPTY =
            RsvpTokenAnalysis(
                focalWordIndices = emptySet(),
                landingWordIndices = emptySet(),
                emDashAsideIndices = emptySet(),
                pairedEmDashIndices = emptySet(),
                phraseContours = emptyMap(),
            )
    }
}

internal fun analyzeExpandedTokens(
    expanded: List<ExpandedToken>,
    config: RsvpConfig,
    languagePolicy: RsvpLanguagePolicy = RsvpLanguagePolicy.UNKNOWN,
): RsvpTokenAnalysis {
    if (expanded.isEmpty()) return RsvpTokenAnalysis.EMPTY

    val focal = HashSet<Int>()
    val landings = HashSet<Int>()
    val asides = HashSet<Int>()
    val pairedDashes = HashSet<Int>()
    val contours = HashMap<Int, PhraseContour>()
    val thoughtCues = RsvpThoughtPlan.analyze(expanded, config, languagePolicy)
    val breathGroup = ArrayList<ExpandedToken>()
    var previousWord: Token? = null
    var emDashAsideCloseIndex = -1

    fun applyRestartContour(
        tier: RsvpPunctuationTier,
        afterIndex: Int,
    ) {
        var cursor = afterIndex + 1
        while (cursor < expanded.size) {
            val entry = expanded[cursor]
            when (entry.token.type) {
                TokenType.WORD -> {
                    val weight = restartContourWeight(tier = tier, distance = 1)
                    if (weight > 0.0) {
                        contours.mergeContour(entry.expandedIndex, restartWeight = weight)
                    }
                    return
                }
                TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> return
                TokenType.PUNCTUATION -> cursor++
            }
        }
    }

    fun applyBoundaryEffects(
        tier: RsvpPunctuationTier,
        boundaryIndex: Int,
    ) {
        if (config.useAnticipatoryLanding && breathGroup.size >= 2) {
            landings += breathGroup[breathGroup.lastIndex - 1].expandedIndex
        }
        breathGroup
            .asReversed()
            .take(PHRASE_CONTOUR_WORD_WINDOW)
            .forEachIndexed { distanceIndex, word ->
                val weight = preBoundaryContourWeight(tier = tier, distance = distanceIndex + 1)
                if (weight > 0.0) {
                    contours.mergeContour(word.expandedIndex, preBoundaryWeight = weight)
                }
            }
        if (config.useFocalStress) {
            addFocalWord(breathGroup, focal, thoughtCues, languagePolicy)
        }
        breathGroup.clear()
        applyRestartContour(tier = tier, afterIndex = boundaryIndex)
    }

    expanded.forEachIndexed { index, entry ->
        val token = entry.token
        when (token.type) {
            TokenType.WORD -> {
                if (startsNewThought(entry, breathGroup, thoughtCues)) {
                    applyBoundaryEffects(RsvpPunctuationTier.CLAUSE_BREAK, index - 1)
                }
                if (config.useParentheticalAside && emDashAsideCloseIndex > index) {
                    asides += entry.expandedIndex
                }
                breathGroup += entry
                previousWord = token
            }
            TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> {
                if (config.useFocalStress) {
                    addFocalWord(breathGroup, focal, thoughtCues, languagePolicy)
                }
                breathGroup.clear()
                previousWord = null
                emDashAsideCloseIndex = -1
            }
            TokenType.PUNCTUATION -> {
                // Pair detection runs unconditionally: even with the aside word-compression off,
                // the timing model needs to know paired dashes so they read as one light dip
                // instead of two full clause stops.
                if (emDashAsideCloseIndex < index && isEmDashChar(token.text.firstOrNull())) {
                    val closeIndex = findEmDashAsideClose(expanded, index + 1)
                    if (closeIndex > index) {
                        emDashAsideCloseIndex = closeIndex
                        pairedDashes += index
                        pairedDashes += closeIndex
                    }
                }
                val tier =
                    RsvpPunctuationTimingPolicy.resolveTier(
                        token = token,
                        prevWord = previousWord,
                        nextToken = nextTokenAfter(expanded, index),
                    )
                if (tier.isThoughtBoundary()) {
                    applyBoundaryEffects(tier = tier, boundaryIndex = index)
                }
                if (index >= emDashAsideCloseIndex) {
                    emDashAsideCloseIndex = -1
                }
            }
        }
    }
    if (config.useFocalStress) {
        addFocalWord(breathGroup, focal, thoughtCues, languagePolicy)
    }

    return RsvpTokenAnalysis(
        focalWordIndices = if (config.useFocalStress) focal else emptySet(),
        landingWordIndices = if (config.useAnticipatoryLanding) landings else emptySet(),
        emDashAsideIndices = if (config.useParentheticalAside) asides else emptySet(),
        pairedEmDashIndices = pairedDashes,
        phraseContours = contours,
        thoughtCues = thoughtCues,
    )
}

private fun RsvpPunctuationTier.isThoughtBoundary(): Boolean =
    this == RsvpPunctuationTier.SENTENCE_END || this == RsvpPunctuationTier.CLAUSE_BREAK

private fun startsNewThought(
    entry: ExpandedToken,
    group: List<ExpandedToken>,
    cues: Map<Int, RsvpThoughtCue>,
): Boolean =
    group.isNotEmpty() &&
        cues[entry.expandedIndex]?.startTokenIndex == entry.originalIndex &&
        group.last().originalIndex != entry.originalIndex

private fun addFocalWord(
    group: List<ExpandedToken>,
    focal: MutableSet<Int>,
    thoughtCues: Map<Int, RsvpThoughtCue>,
    languagePolicy: RsvpLanguagePolicy,
) {
    if (group.isEmpty()) return
    val protected = group.filter { thoughtCues[it.expandedIndex]?.protectedEmphasis == true }
    if (protected.isNotEmpty()) {
        protected.forEach { focal += it.expandedIndex }
        return
    }
    if (group.size == 1) {
        focal += group.first().expandedIndex
        return
    }

    val focus = if (languagePolicy == RsvpLanguagePolicy.ENGLISH) confidentEnglishFocus(group) else null
    if (focus != null) {
        focal += focus.expandedIndex
    } else {
        // Ambiguous prose stays neutral. Length and rarity already have recognition allowances;
        // neither is evidence that the author intended rhetorical stress.
        group.forEach { focal += it.expandedIndex }
    }
}

private fun confidentEnglishFocus(group: List<ExpandedToken>): ExpandedToken? {
    val candidates = group.filter {
        !it.token.isSubwordChunk &&
            !isFunctionWord(normalizeWord(it.token.text)) &&
            normalizeWord(it.token.text) !in LOW_STRESS_TAILS
    }
    if (candidates.size == 1) return candidates.single()
    val last = group.last()
    val previous = group.getOrNull(group.lastIndex - 1)?.token?.text?.let(::normalizeWord)
    return last.takeIf { it in candidates && previous in FOCUS_LEADS }
}

private val FOCUS_LEADS = setOf("a", "an", "the", "my", "your", "his", "her", "our", "their", "is", "was", "were", "are")
private val LOW_STRESS_TAILS = setOf("again", "just", "really", "very", "then", "here", "there")

internal fun MutableMap<Int, PhraseContour>.mergeContour(
    expandedIndex: Int,
    preBoundaryWeight: Double = 0.0,
    restartWeight: Double = 0.0,
) {
    val current = this[expandedIndex] ?: PhraseContour.NONE
    this[expandedIndex] =
        PhraseContour(
            preBoundaryWeight = max(current.preBoundaryWeight, preBoundaryWeight),
            restartWeight = max(current.restartWeight, restartWeight),
        )
}

internal fun preBoundaryContourWeight(
    tier: RsvpPunctuationTier,
    distance: Int,
): Double =
    when (tier) {
        RsvpPunctuationTier.SENTENCE_END ->
            when (distance) {
                1 -> SENTENCE_PRE_BOUNDARY_CONTOUR
                2 -> SENTENCE_ANTICIPATORY_CONTOUR
                else -> 0.0
            }
        RsvpPunctuationTier.CLAUSE_BREAK ->
            when (distance) {
                1 -> CLAUSE_PRE_BOUNDARY_CONTOUR
                2 -> CLAUSE_ANTICIPATORY_CONTOUR
                else -> 0.0
            }
        RsvpPunctuationTier.SOFT_SEPARATOR, RsvpPunctuationTier.NONE -> 0.0
    }

internal fun restartContourWeight(
    tier: RsvpPunctuationTier,
    distance: Int,
): Double =
    when (tier) {
        RsvpPunctuationTier.SENTENCE_END ->
            when (distance) {
                1 -> SENTENCE_RESTART_CONTOUR
                else -> 0.0
            }
        RsvpPunctuationTier.CLAUSE_BREAK ->
            when (distance) {
                1 -> CLAUSE_RESTART_CONTOUR
                else -> 0.0
            }
        RsvpPunctuationTier.SOFT_SEPARATOR, RsvpPunctuationTier.NONE -> 0.0
    }

internal fun phraseContourMultiplier(
    contour: PhraseContour,
    speedStrength: Double,
): Double {
    if (contour == PhraseContour.NONE || speedStrength <= 0.0) return 1.0
    val weight = contour.preBoundaryWeight + contour.restartWeight
    return 1.0 + (weight * speedStrength)
}

internal fun nextTokenAfter(
    expanded: List<ExpandedToken>,
    index: Int,
): Token? = expanded.getOrNull(index + 1)?.token

internal fun findEmDashAsideClose(expanded: List<ExpandedToken>, startIndex: Int): Int {
    for (j in startIndex until expanded.size) {
        val t = expanded[j].token
        when (t.type) {
            TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> return -1
            TokenType.PUNCTUATION -> {
                val ch = t.text.firstOrNull() ?: continue
                if (isEmDashChar(ch)) return j
                if (isSentenceEndingPunctuation(ch)) return -1
            }
            TokenType.WORD -> Unit
        }
    }
    return -1
}

internal fun isEmDashChar(ch: Char?): Boolean =
    ch == '\u2014' || ch == '\u2013'
