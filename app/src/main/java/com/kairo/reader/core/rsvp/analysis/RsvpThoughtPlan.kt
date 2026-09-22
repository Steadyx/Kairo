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
    val processingHoldMs: Double = 0.0,
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
            val hold = integrationHold(words, config, languagePolicy)
            val sourceWords = words.distinctBy { it.originalIndex }
            val processingShare = if (sourceWords.size > 1) PROCESSING_SHARE else 0.0
            val lastChunks = words.associateBy { it.originalIndex }
            val start = words.first().originalIndex
            words.forEachIndexed { index, word ->
                cues[word.expandedIndex] = RsvpThoughtCue(
                    startTokenIndex = start,
                    endTokenIndexExclusive = endExclusive,
                    isLastWord = index == words.lastIndex,
                    protectedEmphasis = word.expandedIndex in protected,
                    integrationHoldMs = if (index == words.lastIndex) hold * (1.0 - processingShare) else 0.0,
                    processingHoldMs = if (lastChunks[word.originalIndex] == word) {
                        hold * processingShare / sourceWords.size
                    } else {
                        0.0
                    },
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

    private fun integrationHold(
        words: List<ExpandedToken>,
        config: RsvpConfig,
        languagePolicy: RsvpLanguagePolicy,
    ): Double {
        val informationWords = words.distinctBy { it.originalIndex }
            .distinctBy { normalizeWord(it.token.text) }
        // Without a language-specific familiarity model, word count is not evidence of density.
        // Keep length and numbers as the shared fallback for non-English and unknown text.
        val contentCount = if (languagePolicy == RsvpLanguagePolicy.ENGLISH) {
            informationWords.count {
                (EnglishReadingFrequency.zipf(ReadingDemandAnalyzer.normalize(it.token.text)) ?: 0.0) < COMMON_WORD_ZIPF
            }
        } else {
            0
        }
        val numbers = informationWords.count { it.token.text.any(Char::isDigit) }
        val lengthLoad = (informationWords.size - EASY_PHRASE_WORDS).coerceAtLeast(0).toDouble()
        val load = (contentCount - EASY_CONTENT_WORDS).coerceAtLeast(0) + numbers * NUMBER_LOAD + sqrt(lengthLoad)
        return (load * HOLD_PER_LOAD_MS).coerceAtMost(PHRASE_ALLOWANCE_MS) * config.difficultWordSupport
    }

    private val NEGATIONS = setOf("not", "never", "neither", "nor", "no", "cannot", "can't", "won't", "isn't", "wasn't")
    private val CONTRAST_MARKERS = setOf("but", "instead", "rather", "however", "only", "except")
    private val MODALS = setOf("can", "could", "may", "might", "must", "shall", "should", "will", "would")
    private const val COMMON_WORD_ZIPF = 5.0
    private const val EASY_CONTENT_WORDS = 3
    private const val PHRASE_ALLOWANCE_MS = 70.0
    private const val PROCESSING_SHARE = 0.4
    private const val NUMBER_LOAD = 0.75
    private const val EASY_PHRASE_WORDS = 7
    private const val HOLD_PER_LOAD_MS = 14.0
}
