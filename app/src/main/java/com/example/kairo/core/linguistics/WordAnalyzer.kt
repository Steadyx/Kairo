@file:Suppress("MagicNumber")

package com.example.kairo.core.linguistics

/**
 * Advanced word analysis utilities for cutting-edge RSVP timing.
 * Provides syllable counting, word frequency scoring, and complexity analysis.
 */
object WordAnalyzer {
    /**
     * Estimates syllable count using linguistic rules.
     * Based on vowel groupings with adjustments for silent e, dipthongs, etc.
     */
    @Suppress("CyclomaticComplexMethod")
    fun countSyllables(word: String): Int {
        if (word.isEmpty()) return 0
        val lower = word.lowercase().trim()

        var count = 0
        var prevWasVowel = false
        val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y')

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
        val singleSyllableSuffixes = listOf("tion", "sion", "cian", "tious", "cious")
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
    @Suppress("CyclomaticComplexMethod")
    fun getFrequencyScore(word: String): Double {
        val lower = word.lowercase()

        // Top 100 most common English words (score: 1.0)
        val veryCommon =
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
        val common =
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
        val moderate =
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

        // Heuristic for unknown words based on word length and structure
        val lengthPenalty =
            when {
                lower.length <= 4 -> 0.6 // Short words are often common
                lower.length <= 6 -> 0.5
                lower.length <= 8 -> 0.4
                lower.length <= 10 -> 0.3
                else -> 0.2 // Very long words are usually rare/complex
            }

        // Bonus for common word patterns
        val patternBonus =
            when {
                lower.endsWith("ing") -> 0.1
                lower.endsWith("ed") -> 0.1
                lower.endsWith("ly") -> 0.05
                lower.endsWith("ness") -> 0.0
                lower.endsWith("ment") -> 0.0
                lower.endsWith("tion") -> -0.05 // Technical/formal
                lower.endsWith("ology") -> -0.1 // Scientific
                else -> 0.0
            }

        val score =
            when (lower) {
                in veryCommon -> 1.0
                in common -> 0.85
                in moderate -> 0.7
                else -> (lengthPenalty + patternBonus).coerceIn(0.1, 0.6)
            }

        return score
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
    fun getComplexityMultiplier(word: String): Double {
        val syllables = countSyllables(word)
        val frequency = getFrequencyScore(word)
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
        val nextLower = nextWord.lowercase()

        // Articles leading into nouns/adjectives
        if (lower in setOf("a", "an", "the")) return 0.9

        // Possessives
        if (lower in setOf("my", "your", "his", "her", "its", "our", "their")) return 0.85

        // Phrase leaders
        if (lower in phraseLeaders) return 0.75

        // Prepositions leading into their objects
        if (lower in setOf(
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
            )
        ) {
            return 0.7
        }

        // Adverbs modifying what follows
        if (lower in setOf("very", "quite", "rather", "too", "so", "really", "almost", "nearly")) {
            return 0.8
        }

        // Modal verbs
        if (lower in setOf("will", "would", "can", "could", "should", "must", "may", "might")) {
            return 0.65
        }

        return 0.0
    }
}

/**
 * Dialogue detection and pacing for natural speech patterns.
 */
object DialogueAnalyzer {
    private var inDialogue = false

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
     * Tracks dialogue state and returns appropriate timing multiplier.
     */
    @Suppress("unused")
    fun processToken(text: String): Double {
        // Simple quote tracking using unicode escapes for smart quotes
        // \u201C = " (left double quote), \u201D = " (right double quote)
        // \u2018 = ' (left single quote), \u2019 = ' (right single quote)
        if ('"' in text || '\u201C' in text || '\u201D' in text) {
            inDialogue = !inDialogue
        }

        // Dialogue typically reads slightly faster (mimics speech cadence)
        return if (inDialogue) 0.95 else 1.0
    }

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

    fun reset() {
        inDialogue = false
    }
}
