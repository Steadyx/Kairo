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
    val baseWpm: Int = 300,
    val wordsPerFrame: Int = 1,
    val maxChunkLength: Int = 10,
    val punctuationPauseFactor: Double = 1.6,
    val paragraphPauseMs: Long = 250L,
    val longWordMultiplier: Double = 1.2,
    val orpEnabled: Boolean = true,
    val startDelayMs: Long = 250L,
    val endDelayMs: Long = 350L,
    // Advanced timing features
    val useAdaptiveTiming: Boolean = true,  // Enable syllable/frequency adjustments
    val useClausePausing: Boolean = true,   // Pause at clause boundaries
    val useDialogueDetection: Boolean = true,  // Adjust for dialogue
    val rampUpFrames: Int = 5,              // Gradual speed increase at start
    val rampDownFrames: Int = 3,            // Gradual speed decrease at end
    val complexWordThreshold: Double = 1.3, // Threshold for extra time on complex words
    val clausePauseFactor: Double = 1.25    // Pause multiplier at clause boundaries
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
