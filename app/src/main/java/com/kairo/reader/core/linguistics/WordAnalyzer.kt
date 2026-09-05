@file:Suppress("MagicNumber")

package com.kairo.reader.core.linguistics

import kotlin.math.ln

data class WordAnalysis(
    val syllableCount: Int,
    val frequencyScore: Double,
    val complexityMultiplier: Double,
    val orpIndex: Int,
    val isClauseBoundary: Boolean,
)

/**
 * Advanced word analysis utilities for cutting-edge RSVP timing.
 * Provides syllable counting, word frequency scoring, and complexity analysis.
 */
object WordAnalyzer {
    private val analysisCache =
        object : LinkedHashMap<String, WordAnalysis>(
            ANALYSIS_CACHE_INITIAL_CAPACITY,
            ANALYSIS_CACHE_LOAD_FACTOR,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, WordAnalysis>?
            ): Boolean = size > MAX_ANALYSIS_CACHE_ENTRIES
        }
    private val analysisCacheLock = Any()
    private val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y')
    private val singleSyllableSuffixes = listOf("tion", "sion", "cian", "tious", "cious")

    // Top 100 most common English words (score: 1.0)
    private val veryCommonWords =
        setOf(
            "the",
            "be",
            "to",
            "of",
            "and",
            "a",
            "in",
            "that",
            "have",
            "i",
            "it",
            "for",
            "not",
            "on",
            "with",
            "he",
            "as",
            "you",
            "do",
            "at",
            "this",
            "but",
            "his",
            "by",
            "from",
            "they",
            "we",
            "say",
            "her",
            "she",
            "or",
            "an",
            "will",
            "my",
            "one",
            "all",
            "would",
            "there",
            "their",
            "what",
            "so",
            "up",
            "out",
            "if",
            "about",
            "who",
            "get",
            "which",
            "go",
            "me",
            "when",
            "make",
            "can",
            "like",
            "time",
            "no",
            "just",
            "him",
            "know",
            "take",
            "people",
            "into",
            "year",
            "your",
            "good",
            "some",
            "could",
            "them",
            "see",
            "other",
            "than",
            "then",
            "now",
            "look",
            "only",
            "come",
            "its",
            "over",
            "think",
            "also",
            "back",
            "after",
            "use",
            "two",
            "how",
            "our",
            "work",
            "first",
            "well",
            "way",
            "even",
            "new",
            "want",
            "because",
            "any",
            "these",
            "give",
            "day",
            "most",
            "us",
        )

    // Common words (score: 0.85)
    private val commonWords =
        setOf(
            "been",
            "has",
            "more",
            "was",
            "were",
            "being",
            "had",
            "did",
            "does",
            "should",
            "much",
            "before",
            "where",
            "must",
            "through",
            "too",
            "very",
            "still",
            "those",
            "such",
            "here",
            "why",
            "came",
            "each",
            "may",
            "same",
            "both",
            "find",
            "long",
            "down",
            "made",
            "said",
            "while",
            "own",
            "part",
            "under",
            "might",
            "great",
            "never",
            "world",
            "hand",
            "high",
            "every",
            "last",
            "place",
            "went",
            "right",
            "old",
            "again",
            "found",
            "around",
            "three",
            "small",
            "between",
            "always",
            "next",
            "few",
            "house",
            "put",
            "thought",
            "eyes",
            "many",
            "head",
            "away",
            "once",
            "upon",
            "home",
        )

    // Moderately common words (score: 0.7)
    private val moderateWords =
        setOf(
            "something",
            "nothing",
            "another",
            "without",
            "though",
            "against",
            "enough",
            "almost",
            "perhaps",
            "during",
            "however",
            "morning",
            "together",
            "behind",
            "across",
            "anything",
            "everyone",
            "everything",
            "sometimes",
            "suddenly",
            "already",
            "himself",
            "herself",
            "themselves",
            "became",
            "woman",
            "children",
            "called",
            "really",
            "young",
            "asked",
            "father",
            "mother",
            "going",
            "looking",
            "night",
            "money",
            "water",
        )

    fun analyze(word: String): WordAnalysis {
        synchronized(analysisCacheLock) {
            analysisCache[word]?.let { return it }
        }

        val analysis = computeAnalysis(word)
        synchronized(analysisCacheLock) {
            analysisCache[word] = analysis
        }
        return analysis
    }

    /**
     * Estimates syllable count using linguistic rules.
     * Based on vowel groupings with adjustments for silent e, dipthongs, etc.
     */
    fun countSyllables(word: String): Int = analyze(word).syllableCount

    @Suppress("CyclomaticComplexMethod")
    private fun computeSyllables(word: String): Int {
        if (word.isEmpty()) return 0
        val lower = word.lowercase().trim()

        var count = 0
        var prevWasVowel = false

        for (i in lower.indices) {
            val char = lower[i]
            val isVowel = char in vowels

            if (isVowel && !prevWasVowel) {
                count++
            }
            prevWasVowel = isVowel
        }

        // Adjust for silent 'e' at end
        if (lower.endsWith("e") && count > 1 && !lower.endsWith("le")) {
            count--
        }

        // Adjust for common endings that add syllables
        if (lower.endsWith("le") && lower.length > 2 && lower[lower.length - 3] !in vowels) {
            count++
        }

        // Adjust for 'ed' endings (usually silent)
        if (lower.endsWith("ed") && count > 1) {
            val beforeEd = lower[lower.length - 3]
            if (beforeEd != 't' && beforeEd != 'd') {
                count--
            }
        }

        // Adjust for common suffixes that are single syllables
        for (suffix in singleSyllableSuffixes) {
            if (lower.endsWith(suffix)) {
                // These are typically 1 syllable, already counted correctly
                break
            }
        }

        val adjusted = if (lower.length <= 2) 1 else count
        return adjusted.coerceAtLeast(1)
    }

    /**
     * Returns a frequency score for common English words.
     * Score from 0.0 (very rare) to 1.0 (extremely common).
     * Common words need less display time; rare words need more.
     */
    fun getFrequencyScore(word: String): Double = analyze(word).frequencyScore

    /**
     * Estimates how *easy a word is to decode at a glance*, from [MIN_EASE] (slow, effortful) to
     * 1.0 (instant). This drives how much extra display time a word earns.
     *
     * It is computed from the word's own structure — no dictionary — so it generalises to any
     * word, including proper names and invented words:
     *  - **letter rarity**: words built from common English letters (e, t, a, o, …) decode faster
     *    than ones with rare letters (q, x, z, j, k, v), measured as mean per-letter surprisal;
     *  - **consonant clusters**: runs like "str", "ngth", "mps" are slower to resolve;
     *  - **short-word bonus**: very short tokens are recognised almost instantly.
     *
     * Word length and syllable count are deliberately *not* folded in here — the engine's length
     * curve and per-syllable time already handle those. Double-counting length is exactly what
     * made the old list-based heuristic over-slow common-but-long words.
     *
     * A small built-in set of ultra-common words acts only as a floor, so a word like "the" can
     * never be misjudged as effortful.
     */
    private fun computeFrequencyScore(word: String): Double {
        val letters = word.lowercase().filter { it.isLetter() }
        if (letters.isEmpty()) return NEUTRAL_EASE

        // Mean per-letter information content (bits). Common letters ~3-4 bits; rare ~7-10.
        val meanSurprisal = letters.sumOf { ch -> letterSurprisalBits(ch) } / letters.length
        val letterEase =
            ((HARD_SURPRISAL_BITS - meanSurprisal) / (HARD_SURPRISAL_BITS - EASY_SURPRISAL_BITS))
                .coerceIn(0.0, 1.0)

        // Hardest consonant cluster to decode (longest run of consecutive consonants).
        val clusterPenalty =
            when (longestConsonantRun(letters)) {
                0, 1, 2 -> 0.0
                3 -> CONSONANT_CLUSTER_PENALTY
                else -> CONSONANT_CLUSTER_PENALTY * 2
            }

        val shortBonus = if (letters.length <= 3) SHORT_WORD_EASE_BONUS else 0.0
        val computed = (letterEase + shortBonus - clusterPenalty).coerceIn(MIN_EASE, 1.0)

        return maxOf(computed, knownCommonEase(word.lowercase()))
    }

    /** A floor for words we are certain are common, so they are never over-slowed. */
    private fun knownCommonEase(word: String): Double =
        when (word) {
            in veryCommonWords -> 1.0
            in commonWords -> 0.85
            in moderateWords -> 0.7
            else -> 0.0
        }

    private fun letterSurprisalBits(ch: Char): Double {
        val pct = ENGLISH_LETTER_FREQ_PCT[ch] ?: UNKNOWN_LETTER_FREQ_PCT
        return -ln(pct / 100.0) / LN2
    }

    private fun longestConsonantRun(letters: String): Int {
        var best = 0
        var run = 0
        for (ch in letters) {
            if (ch in vowels) {
                run = 0
            } else {
                run++
                if (run > best) best = run
            }
        }
        return best
    }

    /**
     * Detects if a word is likely part of dialogue (quoted speech).
     */
    @Suppress("unused")
    fun isDialogueMarker(text: String): Boolean =
        text.contains('"') ||
            text.contains('\u201C') ||
            text.contains('\u201D') ||
            text.contains('\'') ||
            text.contains('\u2018') ||
            text.contains('\u2019')

    /**
     * Detects speaker attribution patterns (he said, she asked, etc.)
     */
    @Suppress("unused")
    fun isSpeakerAttribution(words: List<String>): Boolean {
        val lower = words.map { it.lowercase() }
        val attributionVerbs =
            setOf(
                "said",
                "asked",
                "replied",
                "answered",
                "whispered",
                "shouted",
                "yelled",
                "muttered",
                "murmured",
                "exclaimed",
                "declared",
                "demanded",
                "inquired",
                "responded",
                "added",
                "continued",
                "explained",
                "insisted",
                "suggested",
                "warned",
                "promised",
            )
        return lower.any { it in attributionVerbs }
    }

    /**
     * Calculates overall complexity score for a word.
     * Higher score = more complex = needs more display time.
     * Returns multiplier (1.0 = normal, >1.0 = slower, <1.0 = faster)
     */
    fun getComplexityMultiplier(word: String): Double = analyze(word).complexityMultiplier

    private fun computeAnalysis(word: String): WordAnalysis {
        val syllables = computeSyllables(word)
        val frequency = computeFrequencyScore(word)
        val complexity = computeComplexityMultiplier(word, syllables, frequency)
        return WordAnalysis(
            syllableCount = syllables,
            frequencyScore = frequency,
            complexityMultiplier = complexity,
            orpIndex = computeOrpIndex(word, frequency),
            isClauseBoundary = ClauseDetector.isClauseBoundary(word),
        )
    }

    private fun computeComplexityMultiplier(
        word: String,
        syllables: Int,
        frequency: Double,
    ): Double {
        val length = word.length

        // Base multiplier from syllables (each syllable adds ~10% time)
        val syllableMultiplier = 1.0 + (syllables - 1) * 0.1

        // Frequency adjustment (rare words get up to 30% more time)
        val frequencyMultiplier = 1.0 + (1.0 - frequency) * 0.3

        // Length adjustment for very long words
        val lengthMultiplier = if (length > 10) 1.1 else 1.0

        // Combine factors
        val combined = syllableMultiplier * frequencyMultiplier * lengthMultiplier

        // Clamp to reasonable range
        return combined.coerceIn(0.8, 1.6)
    }

    private fun computeOrpIndex(
        word: String,
        frequency: Double,
    ): Int {
        val length = word.length
        if (length <= 2) return 0

        val baseOrp =
            when {
                length <= 5 -> 1
                length <= 9 -> 2
                length <= 13 -> 3
                else -> 4
            }
        val adjustment =
            when {
                frequency > 0.8 && length > 5 -> 1
                frequency < 0.3 && length > 8 -> -1
                else -> 0
            }

        return (baseOrp + adjustment).coerceIn(0, length - 1)
    }

    // --- Decoding-ease model (dictionary-free) ---
    private const val LN2 = 0.6931471805599453
    private const val NEUTRAL_EASE = 0.6
    private const val MIN_EASE = 0.12

    // Mean per-letter surprisal (bits) mapped to ease: <= EASY reads instantly, >= HARD is slow.
    private const val EASY_SURPRISAL_BITS = 3.6
    private const val HARD_SURPRISAL_BITS = 6.2
    private const val CONSONANT_CLUSTER_PENALTY = 0.12
    private const val SHORT_WORD_EASE_BONUS = 0.15

    // Letters outside a-z (digits, accented chars) are treated as fairly uncommon.
    private const val UNKNOWN_LETTER_FREQ_PCT = 0.5

    // Standard English letter frequencies (% of letters in running text). This is alphabet
    // statistics — 26 numbers — not a word list, so the difficulty estimate generalises to any
    // word without us enumerating a dictionary.
    private val ENGLISH_LETTER_FREQ_PCT =
        mapOf(
            'e' to 12.70, 't' to 9.06, 'a' to 8.17, 'o' to 7.51, 'i' to 6.97, 'n' to 6.75,
            's' to 6.33, 'h' to 6.09, 'r' to 5.99, 'd' to 4.25, 'l' to 4.03, 'c' to 2.78,
            'u' to 2.76, 'm' to 2.41, 'w' to 2.36, 'f' to 2.23, 'g' to 2.02, 'y' to 1.97,
            'p' to 1.93, 'b' to 1.29, 'v' to 0.98, 'k' to 0.77, 'j' to 0.15, 'x' to 0.15,
            'q' to 0.10, 'z' to 0.07,
        )

    private const val ANALYSIS_CACHE_INITIAL_CAPACITY = 256
    private const val ANALYSIS_CACHE_LOAD_FACTOR = 0.75f
    private const val MAX_ANALYSIS_CACHE_ENTRIES = 4096
}

/**
 * Clause and phrase boundary detection for intelligent chunking.
 */
object ClauseDetector {
    // Words that typically start new clauses
    private val clauseStarters =
        setOf(
            "which",
            "that",
            "who",
            "whom",
            "whose",
            "where",
            "when",
            "while",
            "because",
            "although",
            "though",
            "unless",
            "until",
            "since",
            "if",
            "after",
            "before",
            "whenever",
            "wherever",
            "whether",
            "however",
            "therefore",
            "moreover",
            "furthermore",
            "nevertheless",
            "meanwhile",
            "otherwise",
            "besides",
            "hence",
            "thus",
            "consequently",
            "accordingly",
        )

    // Coordinating conjunctions (FANBOYS)
    private val coordinatingConjunctions =
        setOf(
            "for",
            "and",
            "nor",
            "but",
            "or",
            "yet",
            "so",
        )

    // Words that typically end phrases (natural pause points)
    private val phraseEnders =
        setOf(
            "now",
            "then",
            "here",
            "there",
            "today",
            "tonight",
            "tomorrow",
            "yesterday",
            "again",
            "still",
            "already",
            "finally",
            "suddenly",
            "slowly",
            "quickly",
            "away",
            "back",
            "down",
            "up",
            "out",
            "off",
            "together",
            "alone",
            "indeed",
            "perhaps",
            "maybe",
            "certainly",
            "probably",
            "definitely",
            "always",
            "never",
            "sometimes",
            "often",
            "usually",
            "rarely",
        )

    // Words that lead into dependent phrases (reader should anticipate)
    private val phraseLeaders =
        setOf(
            "every",
            "each",
            "any",
            "some",
            "no",
            "all",
            "both",
            "such",
            "what",
            "whatever",
            "whichever",
            "another",
            "other",
            "many",
            "few",
            "several",
            "most",
            "more",
            "less",
            "much",
            "little",
            "enough",
            "only",
            "even",
            "just",
            "also",
        )

    /**
     * Detects if a word is a clause boundary marker.
     */
    fun isClauseBoundary(word: String): Boolean = word.lowercase() in clauseStarters

    /**
     * Detects coordinating conjunctions that might warrant a pause.
     */
    fun isCoordinatingConjunction(word: String): Boolean =
        word.lowercase() in coordinatingConjunctions

    /**
     * Detects if a word typically ends a phrase (natural pause point).
     */
    fun isPhraseEnder(word: String): Boolean = word.lowercase() in phraseEnders

    /**
     * Detects if a word leads into a dependent phrase.
     */
    fun isPhraseLeader(word: String): Boolean = word.lowercase() in phraseLeaders

    /**
     * Calculates pause factor based on grammatical structure.
     * Returns multiplier for pause duration.
     */
    fun getClausePauseFactor(
        word: String,
        nextWord: String?,
    ): Double {
        val lower = word.lowercase()
        val nextLower = nextWord?.lowercase()

        // Subordinate clause starters get a slight pause
        val pauseForClauseStarter = lower in clauseStarters

        // Coordinating conjunctions between clauses
        val pauseForConjunction =
            lower in coordinatingConjunctions &&
                nextLower != null &&
                nextLower in setOf("i", "he", "she", "they", "we", "it", "the", "a", "an")

        // Phrase enders get a micro-pause for comprehension
        val pauseForPhraseEnd = lower in phraseEnders && nextLower != null

        // Before clause-starting conjunctions in the next position
        val pauseBeforeClause = nextLower in clauseStarters

        return when {
            pauseForClauseStarter -> 1.18
            pauseForConjunction -> 1.22
            pauseBeforeClause -> 1.12
            pauseForPhraseEnd -> 1.08
            else -> 1.0
        }
    }

    /**
     * Detects parenthetical asides (text in parentheses, em-dashes, etc.)
     */
    @Suppress("unused")
    fun isParentheticalMarker(text: String): Boolean =
        text.contains('(') ||
            text.contains(')') ||
            text.contains('—') ||
            text.contains("--") ||
            text.contains('–')

    /**
     * Returns a coherence score for how strongly two words belong together.
     * Higher score = should be read as a unit or with minimal pause between.
     * Score from 0.0 (no special relationship) to 1.0 (tight phrase).
     */
    fun getCoherenceScore(
        word: String,
        nextWord: String?,
    ): Double {
        if (nextWord == null) return 0.0
        val lower = word.lowercase()
        return when {
            lower in setOf("a", "an", "the") -> 0.9
            lower in setOf("my", "your", "his", "her", "its", "our", "their") -> 0.85
            lower in phraseLeaders -> 0.75
            lower in setOf(
                "of",
                "to",
                "in",
                "on",
                "at",
                "by",
                "for",
                "with",
                "from",
                "into",
                "onto",
                "upon",
                "about",
                "through",
                "between",
                "among",
                "against",
                "toward",
                "towards",
            ) -> 0.7
            lower in setOf("very", "quite", "rather", "too", "so", "really", "almost", "nearly") -> 0.8
            lower in setOf("will", "would", "can", "could", "should", "must", "may", "might") -> 0.65
            else -> 0.0
        }
    }
}

/**
 * Dialogue detection and pacing for natural speech patterns.
 */
object DialogueAnalyzer {
    private val speakerVerbs =
        setOf(
            "said",
            "asked",
            "replied",
            "answered",
            "whispered",
            "shouted",
            "yelled",
            "muttered",
            "murmured",
            "exclaimed",
            "declared",
            "demanded",
            "inquired",
            "responded",
            "added",
            "continued",
            "explained",
            "insisted",
            "suggested",
            "warned",
            "promised",
        )

    private val speakerPatterns =
        listOf(
            Regex("^(he|she|they|i|we|it|\\w+) (said|asked|replied|answered|whispered)"),
            Regex("(said|asked|replied|answered|whispered) (he|she|they|\\w+)$"),
            Regex("^\".*\" (said|asked|replied|answered|whispered)"),
        )

    /**
     * Detects if current context appears to be a speaker tag.
     * Speaker tags should be read quickly as they're not the main content.
     */
    fun isSpeakerTag(words: List<String>): Boolean {
        if (words.isEmpty()) return false

        val lower = words.map { it.lowercase() }
        val hasVerb = lower.any { it in speakerVerbs }
        val text = lower.joinToString(" ")
        val matchesPattern = speakerPatterns.any { it.containsMatchIn(text) }

        return hasVerb && matchesPattern
    }

    fun isSpeakerVerb(word: String): Boolean {
        if (word.isEmpty()) return false
        return word.lowercase() in speakerVerbs
    }

    /**
     * Returns timing multiplier for speaker tags (read faster).
     */
    const val SPEAKER_TAG_MULTIPLIER: Double = 0.85
}
