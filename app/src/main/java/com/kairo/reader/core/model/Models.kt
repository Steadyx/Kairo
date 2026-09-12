package com.kairo.reader.core.model

const val DEFAULT_RSVP_FONT_SIZE_SP = 28f

data class BookId(val value: String,)

data class Book(
    val id: BookId,
    val title: String,
    val authors: List<String>,
    val languageTag: String? = null,
    val chapters: List<Chapter>,
    val tableOfContents: List<TableOfContentsEntry> = emptyList(),
    val coverImage: ByteArray? = null,
    val isCompleted: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Book) return false

        if (id != other.id) return false
        if (title != other.title) return false
        if (authors != other.authors) return false
        if (languageTag != other.languageTag) return false
        if (chapters != other.chapters) return false
        if (tableOfContents != other.tableOfContents) return false
        if (isCompleted != other.isCompleted) return false
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
        result = 31 * result + tableOfContents.hashCode()
        result = 31 * result + (coverImage?.contentHashCode() ?: 0)
        result = 31 * result + isCompleted.hashCode()
        return result
    }
}

data class TableOfContentsTarget(val chapterIndex: Int, val characterOffset: Int = 0,)

data class TableOfContentsEntry(val label: String, val depth: Int, val target: TableOfContentsTarget?,)

/**
 * Represents an internal link within chapter content.
 * Character positions are relative to plainText.
 */
data class ChapterLink(val startChar: Int, val endChar: Int, val targetChapterIndex: Int,)

data class Chapter(
    val index: Int,
    val title: String?,
    val htmlContent: String,
    val plainText: String,
    val imagePaths: List<String> = emptyList(),
    val wordCount: Int = 0,
    /** Links extracted from HTML with positions relative to plainText */
    val links: List<ChapterLink> = emptyList(),
    val wordCountVersion: Int = 0,
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
    val tempoMsPerWord: Long = 155L,
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
     * A low-emphasis sentence scaffold shown around the active RSVP unit.
     * This restores some spatial context without moving the ORP focus word.
     */
    val contextAssistMode: RsvpContextAssistMode = RsvpContextAssistMode.OFF,
    /** Briefly ease the live pace after the reader deliberately moves backwards. */
    val useRegressionAdaptivePacing: Boolean = true,
    /**
     * Extra pause after intermediate chunks when long words are split for RSVP.
     */
    val subwordChunkPauseMs: Long = 60L,
    /**
     * Punctuation pauses (milliseconds).
     * These are *breath* values; they are further shaped by pauseScaleExponent at very high WPM.
     */
    val commaPauseMs: Long = 150L,
    /** Full-stop pause for '.' */
    val periodPauseMs: Long = 330L,
    val semicolonPauseMs: Long = 260L,
    val colonPauseMs: Long = 242L,
    val dashPauseMs: Long = 258L,
    val parenthesesPauseMs: Long = 162L,
    val quotePauseMs: Long = 105L,
    /** Generic sentence-end pause for '!' and '?' */
    val sentenceEndPauseMs: Long = 355L,
    val paragraphPauseMs: Long = 440L,
    /**
     * Extra shaping for paragraph boundaries. The raw pause remains user-visible in settings,
     * while this controls how strongly a paragraph break is held during RSVP playback.
     */
    val paragraphPauseMultiplier: Double = 1.42,
    /**
     * Page breaks need a larger visual breath than paragraph boundaries because the RSVP frame
     * intentionally goes blank while the reader resets to the next page/scene.
     */
    val pageBreakPauseMultiplier: Double = 3.65,
    /**
     * How punctuation pauses scale as WPM increases.
     * Values < 1 compress pauses at high WPM, but minPauseScale preserves a baseline share so
     * punctuation does not collapse into a near-zero pause.
     */
    val pauseScaleExponent: Double = 0.44,
    val minPauseScale: Double = 0.84,
    /**
     * Adds a tiny post-punctuation settling hold after strong punctuation when reading continues.
     * This helps clause and sentence endings feel less clipped in high-speed RSVP.
     */
    val usePunctuationLandingHold: Boolean = true,
    /**
     * Context shaping.
     * Parentheticals and quoted speech are paced slightly differently for comprehension/flow.
     */
    val parentheticalMultiplier: Double = 1.14,
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
    val orpHighlightEnabled: Boolean = true,
    val orpGuideEnabled: Boolean = true,
    val orpGuideBrightness: Double = 1.0,
    val orpGuideThickness: Double = 1.0,
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
    val maxChunkLength: Int = 10,
    /** Global punctuation breathing multiplier. 1.0 is neutral. */
    val punctuationPauseFactor: Double = 1.08,
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
    /**
     * Breath-group focal stress: within each phrase (words between boundary punctuations),
     * one "focal" word keeps its natural length while the supporting words are compressed.
     * Creates a single peak per breath rather than treating every word as equally weighted.
     */
    val useFocalStress: Boolean = true,
    /** Duration multiplier applied to non-focal words in each breath group. 1.0 disables. */
    val focalSupportCompression: Double = 0.94,
    /**
     * Anticipatory landing: the word two positions before a clause/sentence boundary gets a small
     * stretch so the descent into punctuation feels like an approach rather than an abrupt stop.
     * The word immediately before the boundary already stretches via the tail-lift contour.
     */
    val useAnticipatoryLanding: Boolean = true,
    /** Duration multiplier applied to the N-2 word before a landing punctuation. 1.0 disables. */
    val anticipatoryLandingBoost: Double = 1.07,
    /**
     * Punctuation pause scaling applied when inside quoted speech. Dialogue typically flows
     * faster in the inner voice — commas and periods inside quotes get compressed so the
     * speech rhythm feels distinct from narration. 1.0 disables.
     */
    val dialoguePunctuationScale: Double = 0.96,
    /**
     * Parenthetical aside mode: words inside (), [], {}, or between em-dashes are compressed
     * so they feel like a faster, quieter side-remark. When enabled, overrides
     * [parentheticalMultiplier] for duration and suppresses the parenthetical hold.
     */
    val useParentheticalAside: Boolean = false,
    /** Duration multiplier applied to words inside an aside span. 1.0 disables. */
    val parentheticalAsideMultiplier: Double = 0.88,
)

enum class BlinkMode { OFF, SUBTLE, ADAPTIVE }

// SENTENCE_TICKER remains the persisted key for the Continuous inline mode.
enum class RsvpContextAssistMode { OFF, PREVIOUS_WORDS, FULL_CLAUSE, SENTENCE_TICKER }

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

enum class TimedReadingMode { RSVP, BIONIC }

data class BionicReadingPreferences(
    val fixationStrength: Float = 0.45f,
    val highlightStrength: Float = 0.16f,
    val fontSizeSp: Float = 24f,
    val textBrightness: Float = 0.90f,
)

data class UserPreferences(
    val rsvpConfig: RsvpConfig = RsvpProfile.BALANCED.defaultConfig(),
    val rsvpTempoMsPerWord: Long = RsvpProfile.BALANCED.defaultConfig().tempoMsPerWord,
    val rsvpSelectedProfileId: String = RsvpProfileIds.builtIn(RsvpProfile.BALANCED),
    val rsvpCustomProfiles: List<RsvpCustomProfile> = emptyList(),
    val hasSeenStartingTutorial: Boolean = false,
    val readerFontSizeSp: Float = 18f,
    val readerTheme: ReaderTheme = ReaderTheme.LIGHT,
    val customTheme: CustomTheme = CustomTheme(),
    val readerFontFamily: RsvpFontFamily = RsvpFontFamily.MERRIWEATHER,
    val interfaceFontFamily: RsvpFontFamily = RsvpFontFamily.SYSTEM_SANS,
    val readerTextBrightness: Float = 0.88f,
    val invertedScroll: Boolean = false,
    // RSVP-specific font settings (decoupled from reader)
    val rsvpFontSizeSp: Float = DEFAULT_RSVP_FONT_SIZE_SP,
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
    /**
     * Positioning-mode adjustment grid: when enabled, dragging the word in positioning mode
     * snaps toward evenly spaced alignment lines; disabled = free-form adjustment.
     * Snap strength 0f..1f controls how close to a line the drag must be before it snaps
     * (1f quantizes fully to the grid).
     */
    val rsvpPositioningGridEnabled: Boolean = true,
    val rsvpPositioningGridSnap: Float = DEFAULT_RSVP_POSITIONING_GRID_SNAP,
    val timedReadingMode: TimedReadingMode = TimedReadingMode.RSVP,
    val bionicReading: BionicReadingPreferences = BionicReadingPreferences(),
    val unlockExtremeSpeed: Boolean = false,
    // Focus mode
    val focusModeEnabled: Boolean = false,
    val focusHideStatusBar: Boolean = true,
    val focusPauseNotifications: Boolean = false,
    val focusApplyInReader: Boolean = true,
    val focusApplyInRsvp: Boolean = true,
    val weeklyReadingGoalMinutes: Int = 120,
    val momentumResetCutoffAt: Long = 0L,
)

enum class ReaderTheme {
    CUSTOM,
    LIGHT,
    LINEN,
    MIST,
    SAGE,
    SEPIA,
    DARK,
    INK,
    PLUM,
    EMBER,
    NORD,
    CYBERPUNK,
    FOREST,
}

enum class RsvpFontFamily { INTER, ROBOTO, MERRIWEATHER, LORA, LEXEND, SYSTEM_SANS, SYSTEM_SERIF, MONOSPACE }

enum class RsvpFontWeight { LIGHT, NORMAL, MEDIUM }

private const val DEFAULT_RSVP_VERTICAL_BIAS = -0.15f
private const val DEFAULT_RSVP_HORIZONTAL_BIAS = 0f
private const val DEFAULT_RSVP_POSITIONING_GRID_SNAP = 0.5f
