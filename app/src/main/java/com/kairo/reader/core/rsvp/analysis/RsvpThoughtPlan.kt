package com.kairo.reader.core.rsvp.analysis

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTier
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTimingPolicy
import kotlin.math.sqrt

/** Source-based phrase boundaries are shared by timing, replay and peripheral context. */
internal data class RsvpThoughtCue(
    val startTokenIndex: Int,
    val endTokenIndexExclusive: Int,
    val isLastWord: Boolean,
    val protectedEmphasis: Boolean,
    val integrationHoldMs: Double,
)

internal object RsvpThoughtPlan {
    fun analyze(
        expanded: List<ExpandedToken>,
        config: RsvpConfig,
        languagePolicy: RsvpLanguagePolicy,
    ): Map<Int, RsvpThoughtCue> {
        val cues = HashMap<Int, RsvpThoughtCue>()
        val words = ArrayList<ExpandedToken>()
        val english = languagePolicy == RsvpLanguagePolicy.ENGLISH
        var previousModals = emptySet<String>()

        fun finish(endExclusive: Int) {
            if (words.isEmpty()) return
            val protected = protectedWords(words, english, previousModals)
            val hold = integrationHold(words, config)
            val start = words.first().originalIndex
            words.forEachIndexed { index, word ->
                cues[word.expandedIndex] = RsvpThoughtCue(
                    startTokenIndex = start,
                    endTokenIndexExclusive = endExclusive,
                    isLastWord = index == words.lastIndex,
                    protectedEmphasis = word.expandedIndex in protected,
                    integrationHoldMs = if (index == words.lastIndex) hold else 0.0,
                )
            }
            previousModals = words.map { normalizeWord(it.token.text) }.filter { it in MODALS }.toSet()
            words.clear()
        }

        expanded.forEachIndexed { index, entry ->
            val token = entry.token
            when (token.type) {
                TokenType.WORD -> {
                    if (english && token.isClauseBoundary && !token.isSubwordChunk) finish(entry.originalIndex)
                    words += entry
                }
                TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> {
                    finish(entry.originalIndex)
                    previousModals = emptySet()
                }
                TokenType.PUNCTUATION -> {
                    val tier = RsvpPunctuationTimingPolicy.resolveTier(
                        token,
                        words.lastOrNull()?.token,
                        expanded.getOrNull(index + 1)?.token,
                    )
                    if (tier == RsvpPunctuationTier.CLAUSE_BREAK || tier == RsvpPunctuationTier.SENTENCE_END) {
                        finish(entry.originalIndex + 1)
                    }
                }
            }
        }
        finish((expanded.lastOrNull()?.originalIndex ?: -1) + 1)
        return cues
    }

    private fun protectedWords(
        words: List<ExpandedToken>,
        english: Boolean,
        previousModals: Set<String>,
    ): Set<Int> = buildSet {
        words.forEachIndexed { index, entry ->
            val token = entry.token
            if (token.isSubwordChunk) return@forEachIndexed
            val word = normalizeWord(token.text)
            val previous = words.getOrNull(index - 1)?.token?.text?.let(::normalizeWord)
            val contrast = english &&
                (
                    word in NEGATIONS ||
                        word in CONTRAST_MARKERS ||
                        previous in CONTRAST_MARKERS ||
                        (word in MODALS && previousModals.any { it != word })
                    )
            if (contrast || token.text.any(Char::isDigit)) add(entry.expandedIndex)
        }
    }

    private fun integrationHold(words: List<ExpandedToken>, config: RsvpConfig): Double {
        if (!config.useAdaptiveTiming) return 0.0
        // Count source words once: spelling chunks must not manufacture cognitive load.
        val sourceWords = words.distinctBy { it.originalIndex }
        val informationWords = sourceWords.distinctBy { normalizeWord(it.token.text) }
        val density = informationWords.sumOf {
            (1.0 - it.token.frequencyScore).coerceIn(0.0, 1.0) +
                (it.token.complexityMultiplier - 1.0).coerceIn(0.0, 1.0) +
                if (it.token.text.any(Char::isDigit)) NUMBER_LOAD else 0.0
        }
        val lengthLoad = (informationWords.size - EASY_PHRASE_WORDS).coerceAtLeast(0).toDouble()
        val load = (density - EASY_DENSITY).coerceAtLeast(0.0) + sqrt(lengthLoad)
        return (load * HOLD_PER_LOAD_MS).coerceAtMost(config.adaptiveDifficultyMaxHoldMs.toDouble())
    }

    private val NEGATIONS = setOf("not", "never", "neither", "nor", "no", "cannot", "can't", "won't", "isn't", "wasn't")
    private val CONTRAST_MARKERS = setOf("but", "instead", "rather", "however", "only", "except")
    private val MODALS = setOf("can", "could", "may", "might", "must", "shall", "should", "will", "would")
    private const val NUMBER_LOAD = 0.75
    private const val EASY_PHRASE_WORDS = 7
    private const val EASY_DENSITY = 2.0
    private const val HOLD_PER_LOAD_MS = 14.0
}
