package com.kairo.reader.core.rsvp.engine

import com.kairo.reader.core.model.PAIRED_CLOSING_PUNCTUATION_CHARS
import com.kairo.reader.core.model.PAIRED_OPENING_PUNCTUATION_CHARS

internal const val MIN_FRAME_MS = 40L

// Cadence dynamism: words easier than this ease pivot are allowed to glide *below* the baseline
// tempo (down to the readability floor), so the rhythm rises and falls with difficulty instead of
// only ever adding time on top of a flat baseline. Hard words keep the length/rarity time they
// earned. Kept gentle on purpose: an inner-monologue voice flows easy words lightly and evenly
// within a phrase rather than "zipping" them, so the glide is a soft lean, not a sprint.
internal const val DYNAMISM_EASE_PIVOT = 0.68
internal const val DYNAMISM_MAX_SPEEDUP = 0.13
internal const val MAX_MIN_PAUSE_SCALE = 0.97
internal const val BASE_MS_PER_WORD_AT_300 = 200.0
internal const val DEFAULT_CLAUSE_PAUSE_FACTOR = 1.25
internal const val MIN_BLINK_MS = 16L
internal const val ADAPTIVE_DIFFICULTY_FLOOR = 0.35
internal const val CLAUSE_BOUNDARY_HOLD_MS = 55.0
internal const val ADAPTIVE_HOLD_MAX_MS = 160.0
internal const val EASY_PAIR_THRESHOLD = 0.72
internal const val TRANSITION_HOLD_BASE_MS = 4.0
internal const val TRANSITION_HOLD_EXTRA_MS = 6.0
internal const val COHERENCE_HOLD_MS = 5.0

// Breath between intonation units. Applied before a grammatical phrase/clause boundary that has
// no punctuation (e.g. before "who", "which", "because", "when", a joining "and"/"but"). This is
// the pause an inner voice takes between thoughts — it groups words into phrases and gives the
// reader a beat to integrate each one, which is where comprehension lives. PHRASE_BREATH_BASE
// keeps a share of the breath present even at moderate reading speeds (the old hold scaled to
// ~nothing except at very high WPM, so phrasing was never actually felt).
internal const val PHRASE_BREAK_HOLD_MS = 30.0
internal const val PHRASE_BREATH_BASE = 0.5

// Phrase-arc shaping at the same grammatical (punctuation-free) boundaries the breath fires on.
// Speech prosody lengthens the final word of an intonation unit (pre-boundary lengthening) and
// articulates the word that opens the next one — without the onset lift, the function-word glide
// compresses clause-starters like "because"/"which" exactly where the new thought begins. Both
// lifts use the PHRASE_BREATH_BASE blend so the arc is felt at moderate speed too.
internal const val PHRASE_PRE_BOUNDARY_LIFT = 0.07
internal const val PHRASE_ONSET_LIFT = 0.05

// Given/new pacing: an inner voice glides over words already active in working memory. Content
// words repeat constantly in prose (names especially); the first mention earns its rarity/emphasis
// time, later mentions read lighter. Tracked in a small LRU of recently shown content words,
// cleared at page breaks (scene changes re-introduce). The glide also roughly cancels the
// proper-noun emphasis boost on re-mentions.
internal const val GIVENNESS_GLIDE = 0.08
internal const val GIVENNESS_MIN_CHARS = 5
internal const val GIVENNESS_MAX_ENTRIES = 160
internal const val GIVENNESS_INITIAL_CAPACITY = 64

// Em-dash role shaping. A dash is not one thing: paired dashes wrap an aside and should read as
// a light dip in register (parenthesis-like), not two full clause stops in a row; a dash that is
// cut off by a closing quote / paragraph end is an interruption and should clip, not linger; a
// lone mid-sentence dash is a dramatic beat and keeps the full dashPauseMs treatment.
internal const val EM_DASH_ASIDE_PAUSE_FACTOR = 0.55
internal const val EM_DASH_ASIDE_CONTOUR_FACTOR = 0.5
internal const val EM_DASH_INTERRUPTION_PAUSE_FACTOR = 0.45

// Sentence wrap-up: integration time at a sentence end scales with how much sentence the reader
// is holding. Short sentences turn over briskly (factor < 1); long sentences earn a fuller stop
// (factor > 1). Applied to the sentence-end punctuation pause only.
internal const val SENTENCE_WRAP_UP_SHORT_WORDS = 6.0
internal const val SENTENCE_WRAP_UP_LONG_WORDS = 22.0
internal const val SENTENCE_WRAP_UP_MIN_FACTOR = 0.90
internal const val SENTENCE_WRAP_UP_MAX_FACTOR = 1.18
internal const val FUNCTION_BRIDGE_HOLD_MS = 2.0
internal const val CLAUSE_START_HOLD_FRACTION = 0.18
internal const val SENTENCE_START_HOLD_FRACTION = 0.24
internal const val CLAUSE_START_MIN_HOLD_MS = 4.0
internal const val SENTENCE_START_MIN_HOLD_MS = 6.0
internal const val PARENTHETICAL_HOLD_FRACTION = 0.50
internal const val CLAUSE_LEAD_HOLD_FRACTION = 0.28
internal const val QUOTE_TRANSITION_HOLD_FRACTION = 0.55
internal const val DIALOGUE_ENTRY_BOOST = 0.08
internal const val NUMBER_EMPHASIS_BOOST = 0.12
internal const val PROPER_NOUN_BOOST = 0.08
internal const val ACRONYM_EMPHASIS_BOOST = 0.10
internal const val MAX_EMPHASIS_MULTIPLIER = 1.25
internal const val FUNCTION_WORD_GLIDE_STRONG = 0.09
internal const val FUNCTION_WORD_GLIDE_LIGHT = 0.05
internal const val CONTENT_WORD_STRESS_BOOST = 0.05
internal const val SEMANTIC_ANCHOR_BOOST = 0.10
internal const val FUNCTION_BRIDGE_COHERENCE_THRESHOLD = 0.65
internal const val PRONOUN_BRIDGE_MAX_CHARS = 6
internal const val AUXILIARY_BRIDGE_MAX_CHARS = 6
internal const val AUXILIARY_CONTENT_MAX_CHARS = 8
internal const val AUXILIARY_CONTENT_MIN_FREQUENCY = 0.45
internal const val MIN_PROSODY_MULTIPLIER = 0.88
internal const val MAX_PROSODY_MULTIPLIER = 1.18
internal const val CLAUSE_LEAD_BOOST_MS = 20.0
internal const val SENTENCE_END_BREAK_BOOST_MS = 40.0
internal const val EMBEDDED_QUOTE_FACTOR = 0.45
internal const val FLOW_EMA_ALPHA = 0.25
internal const val FLOW_MAX_BOOST = 0.04
internal const val FLOW_MAX_SLOWDOWN = 0.05
internal const val FLOW_STRENGTH = 0.12
internal const val MAX_BOUNDARY_TAIL_LIFT = 0.30
internal const val MIN_BOUNDARY_TAIL_LIFT_STRENGTH = 0.85
internal const val MIN_LANDING_HOLD_MS = 8.0
internal const val MAX_LANDING_HOLD_MS = 30.0
internal const val LANDING_HOLD_SPEED_BOOST = 0.35
internal const val MIN_FOCAL_SUPPORT_COMPRESSION = 0.75
internal const val MAX_ANTICIPATORY_LANDING_BOOST = 1.2
internal const val PHRASE_CONTOUR_WORD_WINDOW = 2
internal const val SENTENCE_PRE_BOUNDARY_CONTOUR = 0.07
internal const val SENTENCE_ANTICIPATORY_CONTOUR = 0.04
internal const val SENTENCE_RESTART_CONTOUR = 0.08
internal const val SENTENCE_CONTOUR_PAUSE_RETAINED = 0.94
internal const val CLAUSE_PRE_BOUNDARY_CONTOUR = 0.035
internal const val CLAUSE_ANTICIPATORY_CONTOUR = 0.018
internal const val CLAUSE_RESTART_CONTOUR = 0.04
internal const val CLAUSE_CONTOUR_PAUSE_RETAINED = 0.96
internal const val PARAGRAPH_BREAK_RETENTION_BOOST = 0.22
internal const val PARAGRAPH_SENTENCE_MULTIPLIER = 0.8
internal const val PAGE_BREAK_RETENTION_BOOST = 0.32
internal const val PAGE_BREAK_SENTENCE_MULTIPLIER_RATIO = 0.85

internal val OPENING_PUNCTUATION = PAIRED_OPENING_PUNCTUATION_CHARS
internal val CURRENCY_PREFIX_PUNCTUATION = setOf('$', '€', '£', '¥')
internal val CURRENCY_NUMERIC_WORD_REGEX = Regex("""\d+(?:[.,]\d+)*""")

internal val QUOTE_OR_BRACKET_PUNCTUATION =
    PAIRED_OPENING_PUNCTUATION_CHARS + PAIRED_CLOSING_PUNCTUATION_CHARS + '"'

internal val SKIPPABLE_BOUNDARY_PUNCTUATION =
    PAIRED_OPENING_PUNCTUATION_CHARS + PAIRED_CLOSING_PUNCTUATION_CHARS + '"'

internal val GLUE_WORDS =
    setOf(
        // Articles
        "a",
        "an",
        "the",
        // Prepositions
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
        "over",
        "under",
        "through",
        "between",
        "among",
        "against",
        "toward",
        "towards",
        // Conjunctions
        "and",
        "or",
        "but",
        "nor",
        "yet",
        "so",
        "as",
        "if",
        "than",
        "then",
        // Relative pronouns
        "that",
        "which",
        "who",
        "whom",
        "whose",
        // Auxiliary verbs
        "is",
        "are",
        "was",
        "were",
        "be",
        "been",
        "being",
        "has",
        "have",
        "had",
        "do",
        "does",
        "did",
        "will",
        "would",
        "can",
        "could",
        "shall",
        "should",
        "may",
        "might",
        "must",
        // Common pronouns
        "i",
        "me",
        "my",
        "we",
        "us",
        "our",
        "you",
        "your",
        "he",
        "him",
        "his",
        "she",
        "her",
        "it",
        "its",
        "they",
        "them",
        "their",
        // Negation
        "not",
        "no",
        // Common short words that flow naturally
        "all",
        "any",
        "some",
        "each",
        "every",
        "both",
        "few",
        "more",
        "most",
        "other",
        "such",
        "own",
        "same",
        "just",
        "only",
        "very",
        "too",
        "also",
        "still",
        "even",
        "now",
        "here",
        "there",
        "when",
        "where",
        "how",
        "why",
        "what",
        "this",
        "these",
        "those",
    )

internal val FUNCTION_WORDS =
    setOf(
        "a",
        "an",
        "the",
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
        "over",
        "under",
        "through",
        "between",
        "among",
        "against",
        "toward",
        "towards",
        "and",
        "or",
        "nor",
        "yet",
        "so",
        "as",
        "if",
        "than",
        "then",
        "that",
        "which",
        "who",
        "whom",
        "whose",
        "is",
        "are",
        "was",
        "were",
        "be",
        "been",
        "being",
        "has",
        "have",
        "had",
        "do",
        "does",
        "did",
        "will",
        "would",
        "can",
        "could",
        "shall",
        "should",
        "may",
        "might",
        "must",
        "i",
        "me",
        "my",
        "we",
        "us",
        "our",
        "you",
        "your",
        "he",
        "him",
        "his",
        "she",
        "her",
        "it",
        "its",
        "they",
        "them",
        "their",
    )

internal val FUNCTION_BRIDGE_WORDS =
    setOf(
        "a",
        "an",
        "the",
        "of",
        "to",
        "in",
        "on",
        "at",
        "for",
        "with",
        "from",
        "by",
        "my",
        "your",
        "his",
        "her",
        "our",
        "their",
    )

internal val PRONOUN_BRIDGE_WORDS =
    setOf(
        "i",
        "you",
        "he",
        "she",
        "it",
        "we",
        "they",
        "there",
        "this",
        "that",
        "who",
        "what",
    )

internal val AUXILIARY_BRIDGE_WORDS =
    setOf(
        "am",
        "is",
        "are",
        "was",
        "were",
        "be",
        "been",
        "being",
        "have",
        "has",
        "had",
        "do",
        "does",
        "did",
        "will",
        "would",
        "can",
        "could",
        "should",
        "may",
        "might",
        "must",
    )

internal val SEMANTIC_ANCHOR_WORDS =
    setOf(
        "not",
        "no",
        "never",
        "none",
        "nothing",
        "nobody",
        "neither",
        "nor",
        "only",
        "even",
        "except",
        "unless",
        "until",
        "however",
        "but",
        "yet",
        "despite",
        "although",
        "though",
    )

internal val TIGHT_PAIR_HINTS =
    setOf(
        // Article + preposition patterns
        "to the",
        "in the",
        "of the",
        "on the",
        "at the",
        "for the",
        "with the",
        "from the",
        "by the",
        "into the",
        "through the",
        "over the",
        "under the",
        "about the",
        "to a",
        "in a",
        "of a",
        "on a",
        "at a",
        "for a",
        "with a",
        "from a",
        "by a",
        "into a",
        "to an",
        "in an",
        "of an",
        "on an",
        // Possessive patterns
        "to my",
        "in my",
        "of my",
        "on my",
        "at my",
        "for my",
        "with my",
        "to his",
        "in his",
        "of his",
        "on his",
        "at his",
        "for his",
        "with his",
        "to her",
        "in her",
        "of her",
        "on her",
        "at her",
        "for her",
        "with her",
        "to their",
        "in their",
        "of their",
        "on their",
        "at their",
        "for their",
        "with their",
        "to our",
        "in our",
        "of our",
        "on our",
        "to your",
        "in your",
        "of your",
        // Comparative/conjunction patterns
        "as a",
        "as an",
        "as the",
        "as if",
        "so that",
        "such a",
        "such an",
        "but the",
        "and the",
        "or the",
        "and a",
        "or a",
        // Common verb patterns
        "is the",
        "is a",
        "is an",
        "was the",
        "was a",
        "was an",
        "are the",
        "were the",
        "has the",
        "has a",
        "had the",
        "had a",
        "have the",
        "have a",
        "will be",
        "would be",
        "can be",
        "could be",
        "should be",
        "must be",
        "may be",
        "might be",
        "am not",
        "is not",
        "are not",
        "was not",
        "were not",
        "do not",
        "does not",
        "did not",
        "have not",
        "has not",
        "had not",
        "will not",
        "would not",
        "can not",
        "could not",
        "should not",
        "may not",
        "might not",
        "must not",
        // Common idiom starters
        "it was",
        "it is",
        "there is",
        "there was",
        "there are",
        "there were",
        "this is",
        "that is",
        "what is",
        "who is",
        "i am",
        "i was",
        "i have",
        "i had",
        "i would",
        "i could",
        "he was",
        "he had",
        "he is",
        "she was",
        "she had",
        "she is",
        "they are",
        "they were",
        "they have",
        "they had",
        "we are",
        "we were",
        "we have",
        "we had",
        "you are",
        "you were",
        "you have",
        // Time expressions
        "at last",
        "at once",
        "at first",
        "of course",
        "in fact",
        "for now",
        "by now",
        "so far",
        "as yet",
        "no more",
        "no less",
        "not yet",
        // Common two-word phrases
        "all the",
        "all of",
        "one of",
        "some of",
        "most of",
        "each of",
        "many of",
        "much of",
        "none of",
        "part of",
        "out of",
        "up to",
        "due to",
        "next to",
        "close to",
        "back to",
        "down to",
    )
