package com.kairo.reader.core.model

/** User-editable RSVP bounds shared by UI, persistence, profile codecs, and engine validation. */
object RsvpConfigConstraints {
    const val MAX_DIFFICULT_WORD_SUPPORT = 2.0
    const val PERCENT_SCALE = 100.0

    const val MIN_WORD_MS = 20L
    const val MAX_WORD_MS = 140L
    const val MIN_LONG_WORD_MS = 60L
    const val MAX_LONG_WORD_MS = 300L
    const val MIN_LONG_WORD_CHARS = 8
    const val MAX_LONG_WORD_CHARS = 14
    const val MAX_SUBWORD_PAUSE_MS = 200L
    const val MAX_SYLLABLE_EXTRA_MS = 45L
    const val MAX_RARITY_EXTRA_MS = 200L
    const val MIN_LENGTH_EXPONENT = 0.1

    const val MIN_PUNCTUATION_PAUSE_FACTOR = 0.5
    const val MAX_PUNCTUATION_PAUSE_FACTOR = 1.75
    const val MAX_COMMA_PAUSE_MS = 260L
    const val MAX_PERIOD_PAUSE_MS = 500L
    const val MAX_DASH_PAUSE_MS = 320L
    const val MAX_SEMICOLON_PAUSE_MS = 360L
    const val MAX_COLON_PAUSE_MS = 360L
    const val MAX_PARENTHESES_PAUSE_MS = 320L
    const val MAX_QUOTE_PAUSE_MS = 200L
    const val MAX_SENTENCE_END_PAUSE_MS = 500L
    const val MAX_PARAGRAPH_PAUSE_MS = 800L
    const val MIN_PARAGRAPH_PAUSE_MULTIPLIER = 0.75
    const val MAX_PARAGRAPH_PAUSE_MULTIPLIER = 2.5
    const val MIN_PAGE_BREAK_PAUSE_MULTIPLIER = 1.0
    const val MAX_PAGE_BREAK_PAUSE_MULTIPLIER = 5.0

    const val MIN_PAUSE_SCALE_EXPONENT = 0.2
    const val MAX_PAUSE_SCALE_EXPONENT = 0.9
    const val MIN_PAUSE_SCALE = 0.3
    const val MAX_PAUSE_SCALE = 1.0
    const val MIN_PARENTHETICAL_MULTIPLIER = 1.0
    const val MAX_PARENTHETICAL_MULTIPLIER = 1.35
    const val MIN_DIALOGUE_MULTIPLIER = 0.85
    const val MAX_DIALOGUE_MULTIPLIER = 1.05
    const val MIN_DIALOGUE_PUNCTUATION_SCALE = 0.5
    const val MAX_DIALOGUE_PUNCTUATION_SCALE = 1.0
    const val MIN_PARENTHETICAL_ASIDE_MULTIPLIER = 0.5
    const val MAX_PARENTHETICAL_ASIDE_MULTIPLIER = 1.0

    const val MAX_ADAPTIVE_HOLD_MS = 200L
    const val MIN_COMPLEX_WORD_THRESHOLD = 1.0
    const val MAX_COMPLEX_WORD_THRESHOLD = 1.6
    const val MIN_FOCAL_SUPPORT_COMPRESSION = 0.75
    const val MAX_FOCAL_SUPPORT_COMPRESSION = 1.0
    const val MIN_ANTICIPATORY_LANDING_BOOST = 1.0
    const val MAX_ANTICIPATORY_LANDING_BOOST = 1.2
    const val MIN_CLAUSE_PAUSE_FACTOR = 1.0
    const val MAX_CLAUSE_PAUSE_FACTOR = 1.6
    const val MIN_PROSODY_STRENGTH = 0.0
    const val MAX_PROSODY_STRENGTH = 1.6

    const val MIN_TEXT_BRIGHTNESS = 0.55
    const val MAX_TEXT_BRIGHTNESS = 1.0
    const val MIN_ORP_GUIDE_BRIGHTNESS = 0.25
    const val MAX_ORP_GUIDE_BRIGHTNESS = 2.0
    const val MIN_ORP_GUIDE_THICKNESS = 0.5
    const val MAX_ORP_GUIDE_THICKNESS = 3.0
}
