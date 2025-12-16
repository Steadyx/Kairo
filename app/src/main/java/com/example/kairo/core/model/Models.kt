package com.example.kairo.core.model

data class BookId(val value: String)

data class Book(
    val id: BookId,
    val title: String,
    val authors: List<String>,
    val chapters: List<Chapter>,
    val coverImage: ByteArray? = null
)

data class Chapter(
    val index: Int,
    val title: String?,
    val htmlContent: String,
    val plainText: String,
    val imagePaths: List<String> = emptyList()
)

data class ReadingPosition(
    val bookId: BookId,
    val chapterIndex: Int,
    val tokenIndex: Int
)

data class Bookmark(
    val id: String,
    val bookId: BookId,
    val chapterIndex: Int,
    val tokenIndex: Int,
    val previewText: String,
    val createdAt: Long
)

data class BookmarkItem(
    val bookmark: Bookmark,
    val book: Book,
    val chapterCount: Int
)

enum class TokenType { WORD, PUNCTUATION, PARAGRAPH_BREAK }

data class Token(
    val text: String,
    val type: TokenType,
    val orpIndex: Int? = null,
    val pauseAfterMs: Long = 0L,
    // Advanced linguistic metadata
    val syllableCount: Int = 1,
    val frequencyScore: Double = 0.5,  // 0.0 = rare, 1.0 = very common
    val complexityMultiplier: Double = 1.0,  // Timing multiplier
    val isClauseBoundary: Boolean = false,
    val isDialogue: Boolean = false
)

data class RsvpConfig(
    /**
     * Tempo in milliseconds for a baseline, easy word.
     *
     * This is the primary speed control for the engine. The actual time per unit is then shaped
     * by readability floors, difficulty (length/syllables/rarity/complexity), punctuation, context,
     * and rhythm smoothing.
     *
     * An estimated WPM can be derived from this, but WPM is not the direct control.
     */
    val tempoMsPerWord: Long = 115L,

    /**
     * Word timing floors.
     * These prevent "flashing" at very high WPM and keep long/complex words readable.
     */
    val minWordMs: Long = 45L,
    val longWordMinMs: Long = 120L,
    val longWordChars: Int = 10,

    /**
     * Difficulty model.
     * - syllableExtraMs: additional time per syllable beyond the first.
     * - rarityExtraMaxMs: additional time for rare words (0..max) based on frequencyScore.
     * - complexityStrength: how strongly to apply Token.complexityMultiplier (0..1).
     */
    val syllableExtraMs: Long = 16L,
    val rarityExtraMaxMs: Long = 65L,
    val complexityStrength: Double = 0.65,

    /**
     * Length curve. Adds time for longer words smoothly instead of abrupt thresholds.
     * lengthStrength controls overall impact; lengthExponent controls how quickly it grows.
     */
    val lengthStrength: Double = 0.9,
    val lengthExponent: Double = 1.35,

    /**
     * Chunking / units.
     * The engine can show short phrase units (e.g., "in the") to reduce flicker and improve flow.
     */
    val enablePhraseChunking: Boolean = true,
    val maxWordsPerUnit: Int = 2,
    val maxCharsPerUnit: Int = 14,

    /**
     * Punctuation pauses (milliseconds).
     * These are *breath* values; they are further shaped by pauseScaleExponent at very high WPM.
     */
    val commaPauseMs: Long = 95L,
    val semicolonPauseMs: Long = 165L,
    val colonPauseMs: Long = 150L,
    val dashPauseMs: Long = 155L,
    val parenthesesPauseMs: Long = 120L,
    val quotePauseMs: Long = 60L,
    val sentenceEndPauseMs: Long = 200L,
    val paragraphPauseMs: Long = 240L,

    /**
     * How punctuation pauses scale as WPM increases.
     * Values < 1 compress pauses at high WPM, but floors still apply so punctuation doesn't vanish.
     */
    val pauseScaleExponent: Double = 0.6,
    val minPauseScale: Double = 0.6,

    /**
     * Context shaping.
     * Parentheticals and quoted speech are paced slightly differently for comprehension/flow.
     */
    val parentheticalMultiplier: Double = 1.12,
    val dialogueMultiplier: Double = 0.97,

    /**
     * Rhythm shaping.
     * smoothingAlpha is EMA smoothing (0..1). Lower = steadier but less responsive.
     * maxSpeedupFactor/maxSlowdownFactor clamp jitter between adjacent units.
     */
    val smoothingAlpha: Double = 0.35,
    val maxSpeedupFactor: Double = 1.25,
    val maxSlowdownFactor: Double = 1.45,

    /**
     * ORP + session ramping.
     */
    val orpEnabled: Boolean = true,
    val startDelayMs: Long = 250L,
    val endDelayMs: Long = 350L,
    val rampUpFrames: Int = 5,
    val rampDownFrames: Int = 3,

    /**
     * Legacy/compat fields (kept for older persistence & UI wiring).
     * Not used by the redesigned engine.
     */
    val baseWpm: Int = 500,
    val wordsPerFrame: Int = 1,
    val maxChunkLength: Int = 10,
    val punctuationPauseFactor: Double = 1.6,
    val longWordMultiplier: Double = 1.2,
    val useAdaptiveTiming: Boolean = true,
    val useClausePausing: Boolean = true,
    val useDialogueDetection: Boolean = true,
    val complexWordThreshold: Double = 1.3,
    val clausePauseFactor: Double = 1.25
)

data class UserPreferences(
    val rsvpConfig: RsvpConfig = RsvpConfig(),
    val readerFontSizeSp: Float = 20f,
    val readerTheme: ReaderTheme = ReaderTheme.SEPIA,
    val invertedScroll: Boolean = false,
    // RSVP-specific font settings (decoupled from reader)
    val rsvpFontSizeSp: Float = 44f,
    val rsvpFontWeight: RsvpFontWeight = RsvpFontWeight.LIGHT,
    val rsvpFontFamily: RsvpFontFamily = RsvpFontFamily.INTER,
    /**
     * Vertical bias for RSVP ORP display.
     * -1f = top, 0f = center, 1f = bottom.
     * Default is slightly above center to match prior layout.
     */
    val rsvpVerticalBias: Float = -0.15f,
    /**
     * Horizontal bias for RSVP ORP display.
     * -1f = left, 0f = center, 1f = right.
     * Default is slightly left to give long words more right-side space.
     */
    val rsvpHorizontalBias: Float = -0.12f,
    // Focus mode
    val focusModeEnabled: Boolean = false,
    val focusHideStatusBar: Boolean = true,
    val focusPauseNotifications: Boolean = false,
    val focusApplyInReader: Boolean = true,
    val focusApplyInRsvp: Boolean = true
)

enum class ReaderTheme { LIGHT, DARK, SEPIA }

enum class RsvpFontFamily { INTER, ROBOTO }

enum class RsvpFontWeight { LIGHT, NORMAL, MEDIUM }
