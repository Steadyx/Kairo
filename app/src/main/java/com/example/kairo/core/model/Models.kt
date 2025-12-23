package com.example.kairo.core.model

data class BookId(val value: String,)

data class Book(
    val id: BookId,
    val title: String,
    val authors: List<String>,
    val chapters: List<Chapter>,
    val coverImage: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Book) return false

        if (id != other.id) return false
        if (title != other.title) return false
        if (authors != other.authors) return false
        if (chapters != other.chapters) return false
        if (coverImage != null) {
            if (other.coverImage == null) return false
            if (!coverImage.contentEquals(other.coverImage)) return false
        } else if (other.coverImage != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + authors.hashCode()
        result = 31 * result + chapters.hashCode()
        result = 31 * result + (coverImage?.contentHashCode() ?: 0)
        return result
    }
}

data class Chapter(
    val index: Int,
    val title: String?,
    val htmlContent: String,
    val plainText: String,
    val imagePaths: List<String> = emptyList(),
    val wordCount: Int = 0,
)

data class ReadingPosition(val bookId: BookId, val chapterIndex: Int, val tokenIndex: Int,)

data class Bookmark(
    val id: String,
    val bookId: BookId,
    val chapterIndex: Int,
    val tokenIndex: Int,
    val previewText: String,
    val createdAt: Long,
)

data class BookmarkItem(val bookmark: Bookmark, val book: Book, val chapterCount: Int,)

enum class TokenType { WORD, PUNCTUATION, PARAGRAPH_BREAK, PAGE_BREAK }

data class Token(
    val text: String,
    val type: TokenType,
    val orpIndex: Int? = null,
    val pauseAfterMs: Long = 0L,
    val highlightStart: Int? = null,
    val highlightEndExclusive: Int? = null,
    // Advanced linguistic metadata
    val syllableCount: Int = 1,
    val frequencyScore: Double = 0.5, // 0.0 = rare, 1.0 = very common
    val complexityMultiplier: Double = 1.0, // Timing multiplier
    val isClauseBoundary: Boolean = false,
    val isDialogue: Boolean = false,
    val isSubwordChunk: Boolean = false,
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
    val enablePhraseChunking: Boolean = false,
    val maxWordsPerUnit: Int = 2,
    val maxCharsPerUnit: Int = 14,
    /**
     * Extra pause after intermediate chunks when long words are split for RSVP.
     */
    val subwordChunkPauseMs: Long = 60L,
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
     *
     * Note: the redesigned engine may still use a subset of these (e.g. clause pacing),
     * but they remain optional/compat-focused.
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
    val clausePauseFactor: Double = 1.25,
    val blinkMode: BlinkMode = BlinkMode.OFF,
)

enum class BlinkMode { OFF, SUBTLE, ADAPTIVE }

enum class RsvpProfile {
    BALANCED,
    CHILL,
    SPRINT,
    STUDY,
}

object RsvpProfileIds {
    const val CUSTOM_UNSAVED: String = "custom:unsaved"

    fun builtIn(profile: RsvpProfile): String = "builtin:${profile.name}"

    fun isBuiltIn(id: String): Boolean = id.startsWith("builtin:")

    fun isCustom(id: String): Boolean = id.startsWith("user:")

    fun parseBuiltIn(id: String): RsvpProfile? {
        val name = id.removePrefix("builtin:")
        return runCatching { RsvpProfile.valueOf(name) }.getOrNull()
    }
}

data class RsvpCustomProfile(val id: String, val name: String, val config: RsvpConfig, val updatedAtMs: Long,)

fun RsvpProfile.displayName(): String =
    when (this) {
        RsvpProfile.BALANCED -> "Balanced"
        RsvpProfile.CHILL -> "Chill"
        RsvpProfile.SPRINT -> "Sprint"
        RsvpProfile.STUDY -> "Study"
    }

fun RsvpProfile.description(): String =
    when (this) {
        RsvpProfile.BALANCED -> "Smooth, readable default"
        RsvpProfile.CHILL -> "More breathing room and stronger pauses"
        RsvpProfile.SPRINT -> "Fast, lighter pauses, higher flow"
        RsvpProfile.STUDY -> "Deliberate pacing for comprehension"
    }

fun RsvpProfile.defaultConfig(): RsvpConfig =
    when (this) {
        RsvpProfile.BALANCED -> RsvpConfig()
        RsvpProfile.CHILL ->
            RsvpConfig().copy(
                tempoMsPerWord = 140L,
                minWordMs = 55L,
                longWordMinMs = 145L,
                commaPauseMs = 120L,
                sentenceEndPauseMs = 250L,
                paragraphPauseMs = 330L,
                smoothingAlpha = 0.28,
                maxSlowdownFactor = 1.55,
            )
        RsvpProfile.SPRINT ->
            RsvpConfig().copy(
                tempoMsPerWord = 85L,
                minWordMs = 40L,
                longWordMinMs = 105L,
                commaPauseMs = 70L,
                sentenceEndPauseMs = 150L,
                paragraphPauseMs = 190L,
                pauseScaleExponent = 0.5,
                smoothingAlpha = 0.45,
                maxSpeedupFactor = 1.35,
                maxSlowdownFactor = 1.35,
            )
        RsvpProfile.STUDY ->
            RsvpConfig().copy(
                tempoMsPerWord = 130L,
                minWordMs = 55L,
                longWordMinMs = 165L,
                rarityExtraMaxMs = 85L,
                complexityStrength = 0.8,
                enablePhraseChunking = false,
                maxWordsPerUnit = 1,
                commaPauseMs = 115L,
                sentenceEndPauseMs = 260L,
                paragraphPauseMs = 380L,
                smoothingAlpha = 0.25,
                maxSlowdownFactor = 1.6,
            )
    }

data class UserPreferences(
    val rsvpConfig: RsvpConfig = RsvpConfig(),
    val rsvpSelectedProfileId: String = RsvpProfileIds.builtIn(RsvpProfile.BALANCED),
    val rsvpCustomProfiles: List<RsvpCustomProfile> = emptyList(),
    val readerFontSizeSp: Float = 20f,
    val readerTheme: ReaderTheme = ReaderTheme.SEPIA,
    val readerTextBrightness: Float = 0.88f,
    val invertedScroll: Boolean = false,
    // RSVP-specific font settings (decoupled from reader)
    val rsvpFontSizeSp: Float = 44f,
    val rsvpTextBrightness: Float = 0.88f,
    val rsvpFontWeight: RsvpFontWeight = RsvpFontWeight.LIGHT,
    val rsvpFontFamily: RsvpFontFamily = RsvpFontFamily.INTER,
    /**
     * Vertical bias for RSVP ORP display.
     * -1f = top, 0f = center, 1f = bottom.
     * Default is slightly above center to match prior layout.
     */
    val rsvpVerticalBias: Float = DEFAULT_RSVP_VERTICAL_BIAS,
    /**
     * Horizontal bias for RSVP ORP display.
     * -1f = left, 0f = center, 1f = right.
     * Default is centered so words stay balanced unless adjusted.
     */
    val rsvpHorizontalBias: Float = DEFAULT_RSVP_HORIZONTAL_BIAS,
    val unlockExtremeSpeed: Boolean = false,
    // Focus mode
    val focusModeEnabled: Boolean = false,
    val focusHideStatusBar: Boolean = true,
    val focusPauseNotifications: Boolean = false,
    val focusApplyInReader: Boolean = true,
    val focusApplyInRsvp: Boolean = true,
)

enum class ReaderTheme { LIGHT, DARK, SEPIA, NORD, CYBERPUNK, FOREST }

enum class RsvpFontFamily { INTER, ROBOTO }

enum class RsvpFontWeight { LIGHT, NORMAL, MEDIUM }

private const val DEFAULT_RSVP_VERTICAL_BIAS = -0.15f
private const val DEFAULT_RSVP_HORIZONTAL_BIAS = 0f
