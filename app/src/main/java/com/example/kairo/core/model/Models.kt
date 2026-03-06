package com.example.kairo.core.model

data class BookId(val value: String,)

data class Book(
    val id: BookId,
    val title: String,
    val authors: List<String>,
    val languageTag: String? = null,
    val chapters: List<Chapter>,
    val coverImage: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Book) return false

        if (id != other.id) return false
        if (title != other.title) return false
        if (authors != other.authors) return false
        if (languageTag != other.languageTag) return false
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
        result = 31 * result + (languageTag?.hashCode() ?: 0)
        result = 31 * result + chapters.hashCode()
        result = 31 * result + (coverImage?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Represents an internal link within chapter content.
 * Character positions are relative to plainText.
 */
data class ChapterLink(
    val startChar: Int,
    val endChar: Int,
    val targetChapterIndex: Int,
)

data class Chapter(
    val index: Int,
    val title: String?,
    val htmlContent: String,
    val plainText: String,
    val imagePaths: List<String> = emptyList(),
    val wordCount: Int = 0,
    /** Links extracted from HTML with positions relative to plainText */
    val links: List<ChapterLink> = emptyList(),
)

data class ReadingPosition(
    val bookId: BookId,
    val chapterIndex: Int,
    val tokenIndex: Int,
    val wordIndex: Int = -1,
    val rsvpResumeCursor: Int = -1,
)

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
    /** If this token is inside a link, the target chapter index */
    val linkChapterIndex: Int? = null,
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
    /** Full-stop pause for '.' */
    val periodPauseMs: Long = 200L,
    val semicolonPauseMs: Long = 165L,
    val colonPauseMs: Long = 150L,
    val dashPauseMs: Long = 155L,
    val parenthesesPauseMs: Long = 120L,
    val quotePauseMs: Long = 60L,
    /** Generic sentence-end pause for '!' and '?' */
    val sentenceEndPauseMs: Long = 200L,
    val paragraphPauseMs: Long = 240L,
    /**
     * How punctuation pauses scale as WPM increases.
     * Values < 1 compress pauses at high WPM, but minPauseScale preserves a baseline share so
     * punctuation does not collapse into a near-zero pause.
     */
    val pauseScaleExponent: Double = 0.6,
    val minPauseScale: Double = 0.6,
    /**
     * Adds a tiny post-punctuation settling hold after strong punctuation when reading continues.
     * This helps clause and sentence endings feel less clipped in high-speed RSVP.
     */
    val usePunctuationLandingHold: Boolean = true,
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
    val adaptiveDifficultyMaxHoldMs: Long = 70L,
    val complexWordHoldMs: Long = 45L,
    val useClausePausing: Boolean = true,
    val useDialogueDetection: Boolean = true,
    val useProsodyPacing: Boolean = true,
    val prosodyStrength: Double = 1.0,
    val complexWordThreshold: Double = 1.3,
    val clausePauseFactor: Double = 1.25,
    val blinkMode: BlinkMode = BlinkMode.OFF,
)

enum class BlinkMode { OFF, SUBTLE, ADAPTIVE }

enum class RsvpProfile {
    BALANCED,
    CHILL,
    NARRATIVE,
    FOCUS,
    FLOW,
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

fun RsvpProfile.defaultConfig(): RsvpConfig =
    when (this) {
        RsvpProfile.BALANCED ->
            RsvpConfig().copy(
                tempoMsPerWord = 112L,
                minWordMs = 48L,
                longWordMinMs = 136L,
                longWordChars = 10,
                syllableExtraMs = 16L,
                rarityExtraMaxMs = 65L,
                complexityStrength = 0.66,
                lengthStrength = 0.90,
                lengthExponent = 1.35,
                enablePhraseChunking = false,
                maxWordsPerUnit = 2,
                maxCharsPerUnit = 14,
                subwordChunkPauseMs = 58L,
                commaPauseMs = 118L,
                periodPauseMs = 262L,
                semicolonPauseMs = 196L,
                colonPauseMs = 180L,
                dashPauseMs = 186L,
                parenthesesPauseMs = 120L,
                quotePauseMs = 78L,
                sentenceEndPauseMs = 236L,
                paragraphPauseMs = 292L,
                pauseScaleExponent = 0.52,
                minPauseScale = 0.74,
                parentheticalMultiplier = 1.13,
                dialogueMultiplier = 0.96,
                smoothingAlpha = 0.30,
                maxSpeedupFactor = 1.22,
                maxSlowdownFactor = 1.42,
                startDelayMs = 220L,
                endDelayMs = 300L,
                rampUpFrames = 4,
                rampDownFrames = 3,
                useAdaptiveTiming = true,
                adaptiveDifficultyMaxHoldMs = 70L,
                complexWordHoldMs = 45L,
                useClausePausing = true,
                useDialogueDetection = true,
                complexWordThreshold = 1.30,
                clausePauseFactor = 1.24,
                useProsodyPacing = true,
                prosodyStrength = 1.06,
                blinkMode = BlinkMode.OFF,
            )
        RsvpProfile.CHILL ->
            RsvpConfig().copy(
                tempoMsPerWord = 150L,
                minWordMs = 62L,
                longWordMinMs = 168L,
                longWordChars = 9,
                syllableExtraMs = 18L,
                rarityExtraMaxMs = 78L,
                complexityStrength = 0.74,
                lengthStrength = 1.00,
                lengthExponent = 1.32,
                enablePhraseChunking = false,
                maxWordsPerUnit = 2,
                maxCharsPerUnit = 14,
                subwordChunkPauseMs = 70L,
                commaPauseMs = 155L,
                periodPauseMs = 375L,
                semicolonPauseMs = 245L,
                colonPauseMs = 226L,
                dashPauseMs = 232L,
                parenthesesPauseMs = 150L,
                quotePauseMs = 104L,
                sentenceEndPauseMs = 334L,
                paragraphPauseMs = 448L,
                pauseScaleExponent = 0.58,
                minPauseScale = 0.80,
                parentheticalMultiplier = 1.20,
                dialogueMultiplier = 0.95,
                smoothingAlpha = 0.20,
                maxSpeedupFactor = 1.16,
                maxSlowdownFactor = 1.72,
                startDelayMs = 300L,
                endDelayMs = 420L,
                rampUpFrames = 6,
                rampDownFrames = 4,
                useAdaptiveTiming = true,
                adaptiveDifficultyMaxHoldMs = 95L,
                complexWordHoldMs = 60L,
                useClausePausing = true,
                useDialogueDetection = true,
                complexWordThreshold = 1.22,
                clausePauseFactor = 1.38,
                useProsodyPacing = true,
                prosodyStrength = 1.25,
                blinkMode = BlinkMode.OFF,
            )
        RsvpProfile.NARRATIVE ->
            RsvpConfig().copy(
                tempoMsPerWord = 110L,
                minWordMs = 50L,
                longWordMinMs = 146L,
                longWordChars = 10,
                syllableExtraMs = 16L,
                rarityExtraMaxMs = 72L,
                complexityStrength = 0.70,
                lengthStrength = 0.95,
                lengthExponent = 1.32,
                enablePhraseChunking = false,
                maxWordsPerUnit = 2,
                maxCharsPerUnit = 14,
                subwordChunkPauseMs = 64L,
                commaPauseMs = 130L,
                periodPauseMs = 300L,
                semicolonPauseMs = 210L,
                colonPauseMs = 195L,
                dashPauseMs = 206L,
                parenthesesPauseMs = 136L,
                quotePauseMs = 118L,
                sentenceEndPauseMs = 256L,
                paragraphPauseMs = 350L,
                pauseScaleExponent = 0.53,
                minPauseScale = 0.74,
                parentheticalMultiplier = 1.18,
                dialogueMultiplier = 0.92,
                clausePauseFactor = 1.34,
                smoothingAlpha = 0.27,
                maxSpeedupFactor = 1.20,
                maxSlowdownFactor = 1.50,
                startDelayMs = 260L,
                endDelayMs = 340L,
                rampUpFrames = 5,
                rampDownFrames = 3,
                useAdaptiveTiming = true,
                adaptiveDifficultyMaxHoldMs = 80L,
                complexWordHoldMs = 50L,
                useClausePausing = true,
                useDialogueDetection = true,
                complexWordThreshold = 1.26,
                useProsodyPacing = true,
                prosodyStrength = 1.38,
                blinkMode = BlinkMode.OFF,
            )
        RsvpProfile.FOCUS ->
            RsvpConfig().copy(
                tempoMsPerWord = 94L,
                minWordMs = 46L,
                longWordMinMs = 130L,
                longWordChars = 10,
                syllableExtraMs = 14L,
                rarityExtraMaxMs = 55L,
                complexityStrength = 0.58,
                lengthStrength = 0.82,
                lengthExponent = 1.25,
                enablePhraseChunking = false,
                maxWordsPerUnit = 2,
                maxCharsPerUnit = 13,
                subwordChunkPauseMs = 50L,
                commaPauseMs = 96L,
                periodPauseMs = 232L,
                semicolonPauseMs = 162L,
                colonPauseMs = 150L,
                dashPauseMs = 154L,
                parenthesesPauseMs = 100L,
                quotePauseMs = 52L,
                sentenceEndPauseMs = 200L,
                paragraphPauseMs = 250L,
                pauseScaleExponent = 0.50,
                minPauseScale = 0.74,
                parentheticalMultiplier = 1.10,
                dialogueMultiplier = 0.98,
                smoothingAlpha = 0.32,
                maxSpeedupFactor = 1.18,
                maxSlowdownFactor = 1.30,
                startDelayMs = 180L,
                endDelayMs = 230L,
                rampUpFrames = 3,
                rampDownFrames = 2,
                useAdaptiveTiming = true,
                adaptiveDifficultyMaxHoldMs = 52L,
                complexWordHoldMs = 32L,
                useClausePausing = true,
                useDialogueDetection = true,
                complexWordThreshold = 1.36,
                clausePauseFactor = 1.14,
                useProsodyPacing = true,
                prosodyStrength = 0.88,
                blinkMode = BlinkMode.ADAPTIVE,
            )
        RsvpProfile.FLOW ->
            RsvpConfig().copy(
                tempoMsPerWord = 102L,
                minWordMs = 48L,
                longWordMinMs = 132L,
                longWordChars = 10,
                syllableExtraMs = 15L,
                rarityExtraMaxMs = 62L,
                complexityStrength = 0.62,
                lengthStrength = 0.88,
                lengthExponent = 1.30,
                enablePhraseChunking = false,
                maxWordsPerUnit = 2,
                maxCharsPerUnit = 15,
                subwordChunkPauseMs = 56L,
                commaPauseMs = 104L,
                periodPauseMs = 246L,
                semicolonPauseMs = 174L,
                colonPauseMs = 162L,
                dashPauseMs = 168L,
                parenthesesPauseMs = 110L,
                quotePauseMs = 64L,
                sentenceEndPauseMs = 214L,
                paragraphPauseMs = 272L,
                pauseScaleExponent = 0.52,
                minPauseScale = 0.74,
                parentheticalMultiplier = 1.12,
                dialogueMultiplier = 0.95,
                smoothingAlpha = 0.28,
                maxSpeedupFactor = 1.20,
                maxSlowdownFactor = 1.34,
                startDelayMs = 190L,
                endDelayMs = 240L,
                rampUpFrames = 4,
                rampDownFrames = 2,
                useAdaptiveTiming = true,
                adaptiveDifficultyMaxHoldMs = 58L,
                complexWordHoldMs = 36L,
                useClausePausing = true,
                useDialogueDetection = true,
                complexWordThreshold = 1.34,
                clausePauseFactor = 1.20,
                useProsodyPacing = true,
                prosodyStrength = 1.12,
                blinkMode = BlinkMode.SUBTLE,
            )
        RsvpProfile.SPRINT ->
            RsvpConfig().copy(
                tempoMsPerWord = 80L,
                minWordMs = 44L,
                longWordMinMs = 124L,
                longWordChars = 9,
                syllableExtraMs = 18L,
                rarityExtraMaxMs = 82L,
                complexityStrength = 0.78,
                lengthStrength = 0.98,
                lengthExponent = 1.35,
                enablePhraseChunking = false,
                maxWordsPerUnit = 2,
                maxCharsPerUnit = 12,
                subwordChunkPauseMs = 48L,
                commaPauseMs = 88L,
                periodPauseMs = 208L,
                semicolonPauseMs = 152L,
                colonPauseMs = 142L,
                dashPauseMs = 148L,
                parenthesesPauseMs = 94L,
                quotePauseMs = 44L,
                sentenceEndPauseMs = 182L,
                paragraphPauseMs = 228L,
                pauseScaleExponent = 0.46,
                minPauseScale = 0.75,
                parentheticalMultiplier = 1.08,
                dialogueMultiplier = 0.99,
                smoothingAlpha = 0.24,
                maxSpeedupFactor = 1.14,
                maxSlowdownFactor = 1.24,
                startDelayMs = 140L,
                endDelayMs = 180L,
                rampUpFrames = 2,
                rampDownFrames = 2,
                useAdaptiveTiming = true,
                adaptiveDifficultyMaxHoldMs = 50L,
                complexWordHoldMs = 30L,
                useClausePausing = true,
                useDialogueDetection = true,
                complexWordThreshold = 1.38,
                clausePauseFactor = 1.14,
                useProsodyPacing = true,
                prosodyStrength = 0.80,
                blinkMode = BlinkMode.ADAPTIVE,
            )
        RsvpProfile.STUDY ->
            RsvpConfig().copy(
                tempoMsPerWord = 134L,
                minWordMs = 60L,
                longWordMinMs = 184L,
                longWordChars = 9,
                syllableExtraMs = 20L,
                rarityExtraMaxMs = 95L,
                complexityStrength = 0.90,
                lengthStrength = 1.08,
                lengthExponent = 1.38,
                enablePhraseChunking = false,
                maxWordsPerUnit = 2,
                maxCharsPerUnit = 14,
                subwordChunkPauseMs = 78L,
                commaPauseMs = 148L,
                periodPauseMs = 388L,
                semicolonPauseMs = 248L,
                colonPauseMs = 232L,
                dashPauseMs = 236L,
                parenthesesPauseMs = 168L,
                quotePauseMs = 108L,
                sentenceEndPauseMs = 340L,
                paragraphPauseMs = 470L,
                pauseScaleExponent = 0.60,
                minPauseScale = 0.82,
                parentheticalMultiplier = 1.22,
                dialogueMultiplier = 0.95,
                smoothingAlpha = 0.18,
                maxSpeedupFactor = 1.12,
                maxSlowdownFactor = 1.78,
                startDelayMs = 320L,
                endDelayMs = 440L,
                rampUpFrames = 7,
                rampDownFrames = 4,
                useAdaptiveTiming = true,
                adaptiveDifficultyMaxHoldMs = 110L,
                complexWordHoldMs = 72L,
                useClausePausing = true,
                useDialogueDetection = true,
                complexWordThreshold = 1.20,
                clausePauseFactor = 1.44,
                useProsodyPacing = true,
                prosodyStrength = 1.24,
                blinkMode = BlinkMode.OFF,
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
