package com.kairo.reader.core.rsvp.analysis

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.splitTokenForRsvp
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy
import java.util.Locale

/** Presentation policy using family familiarity and spelling shape, independent of timing scores. */
internal object RsvpWordPartSupport {
    fun split(token: Token, config: RsvpConfig, policy: RsvpLanguagePolicy): List<Token> {
        val base = automaticBase(token, config, policy)
        val supported = base?.takeIf(::benefitsFromParts)?.let {
            splitTokenForRsvp(token.copy(text = it), PART_LENGTH, config.subwordChunkPauseMs)
        }
        if (supported == null) return configuredSplit(token, config)

        // Keep the same boundaries across the family. A plural/possessive ending stays on the
        // final part instead of moving every boundary or manufacturing an extra reading beat.
        val parts = supported.mapIndexed { index, part ->
            part.copy(
                text = token.text,
                highlightEndExclusive = if (index == supported.lastIndex) token.text.length else part.highlightEndExclusive,
            )
        }
        val exceedsExplicitLimit = parts.any {
            requireNotNull(it.highlightEndExclusive) - requireNotNull(it.highlightStart) > config.maxChunkLength
        }
        return if (exceedsExplicitLimit) configuredSplit(token, config) else parts
    }

    private fun automaticBase(token: Token, config: RsvpConfig, policy: RsvpLanguagePolicy): String? {
        if (policy != RsvpLanguagePolicy.ENGLISH ||
            token.type != TokenType.WORD ||
            config.difficultWordSupport <= 0.0 ||
            config.maxChunkLength <= PART_LENGTH ||
            token.isSubwordChunk
        ) {
            return null
        }
        val word = token.text.lowercase(Locale.ROOT).replace('’', '\'')
        val stem = word.removeSuffix("'s").removeSuffix("'")
        if (stem.isEmpty() || stem.any { it !in 'a'..'z' }) return null
        return regularBase(stem)
    }

    /** Regular inflections work without a dictionary; ambiguous -es/-is forms need an attested base. */
    private fun regularBase(word: String): String = when {
        word.endsWith("es") && EnglishReadingFrequency.zipf(word.dropLast(ES_LENGTH) + "is") != null ->
            word.dropLast(ES_LENGTH) + "is"
        word.endsWith("ies") -> word.dropLast(IES_LENGTH) + "y"
        SIBILANT_PLURALS.any(word::endsWith) -> word.dropLast(ES_LENGTH)
        word.endsWith("s") && SINGULAR_S_ENDINGS.none(word::endsWith) -> word.dropLast(1)
        else -> word
    }

    private fun benefitsFromParts(base: String): Boolean {
        val vowelGroups = base.indices.count { index ->
            base[index] in VOWELS && (index == 0 || base[index - 1] !in VOWELS)
        }
        val silentEnding = base.endsWith("e") && !base.endsWith("le")
        val groups = vowelGroups - if (silentEnding) 1 else 0
        if (base.length < UNCOMMON_BASE_LENGTH || groups < UNCOMMON_VOWEL_GROUPS) return false
        // Very long spellings still benefit from parts even when the vocabulary is familiar.
        if (base.length >= LONG_BASE_LENGTH && groups >= MIN_VOWEL_GROUPS) return true
        val familiarity = familiarityZipf(base)
        return when {
            familiarity == null ->
                // An absent entry is uncertain, so require stronger spelling evidence than
                // for a known uncommon word. Short names and ordinary unknowns stay whole.
                (base.length >= MIN_BASE_LENGTH && groups >= UNCOMMON_VOWEL_GROUPS) ||
                    (base.length >= UNCOMMON_BASE_LENGTH && groups >= MIN_VOWEL_GROUPS)
            familiarity <= UNCOMMON_ZIPF ->
                base.length >= UNCOMMON_BASE_LENGTH && groups >= UNCOMMON_VOWEL_GROUPS
            familiarity <= FAMILIAR_ZIPF ->
                base.length >= MIN_BASE_LENGTH && groups >= MIN_VOWEL_GROUPS
            else -> false
        }
    }

    private fun familiarityZipf(base: String): Double? {
        var candidates = listOf(base)
        var familiarity = EnglishReadingFrequency.zipf(base)
        // Two bounded steps cover forms such as deepen-ing and cleanli-ness.
        // Only an attested word supplies evidence; speculative stems never count as rare.
        repeat(MAX_STEM_DEPTH) { depth ->
            candidates = candidates.flatMap(::regularStems).distinct()
            val attested = candidates.mapNotNull(EnglishReadingFrequency::zipf).maxOrNull()
                ?.minus(STEM_FAMILIARITY_COST * (depth + 1))
            familiarity = listOfNotNull(familiarity, attested).maxOrNull()
        }
        return familiarity
    }

    private fun regularStems(word: String): List<String> {
        val suffix = FAMILIAR_SUFFIXES.firstOrNull(word::endsWith) ?: return emptyList()
        val stem = word.dropLast(suffix.length)
        return buildList {
            add(stem)
            if (suffix in SILENT_E_SUFFIXES) add(stem + "e")
            if (stem.endsWith("i")) add(stem.dropLast(1) + "y")
            if (suffix in VERB_SUFFIXES && stem.length >= MIN_STEM_LENGTH && stem.last() == stem[stem.lastIndex - 1]) {
                add(stem.dropLast(1))
            }
        }.filter { it.length >= MIN_STEM_LENGTH }
    }

    private fun configuredSplit(token: Token, config: RsvpConfig): List<Token> =
        splitTokenForRsvp(token, config.maxChunkLength, config.subwordChunkPauseMs)

    private const val PART_LENGTH = 6

    // Four vowel groups also occur in everyday prose at 10–13 letters. Requiring
    // 14 made support effectively disappear outside unusually long terminology.
    private const val MIN_BASE_LENGTH = 10
    private const val MIN_VOWEL_GROUPS = 4
    private const val LONG_BASE_LENGTH = 14
    private const val UNCOMMON_BASE_LENGTH = 8
    private const val UNCOMMON_VOWEL_GROUPS = 3
    private const val UNCOMMON_ZIPF = 3.5
    private const val FAMILIAR_ZIPF = 4.3
    private const val STEM_FAMILIARITY_COST = 0.15
    private const val MIN_STEM_LENGTH = 3
    private const val MAX_STEM_DEPTH = 2
    private const val IES_LENGTH = 3
    private const val ES_LENGTH = 2
    private const val VOWELS = "aeiouy"
    private val SIBILANT_PLURALS = listOf("sses", "shes", "ches", "xes", "zzes")
    private val SINGULAR_S_ENDINGS = listOf("ss", "us", "is", "ous")
    private val FAMILIAR_SUFFIXES = listOf("ing", "ness", "ment", "less", "ed", "ly", "en")
    private val SILENT_E_SUFFIXES = setOf("ing", "ed", "ment")
    private val VERB_SUFFIXES = setOf("ing", "ed")
}
