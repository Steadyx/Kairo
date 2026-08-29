package com.kairo.reader.core.rsvp.segmentation

internal data class RsvpSegmentationWordFeatures(
    val expandedIndex: Int,
    val characterCount: Int,
    val difficulty: Int,
    val visualLoad: Int,
    val isFunctionWord: Boolean,
    val isSemanticAnchor: Boolean,
    val isSubword: Boolean,
)

internal data class RsvpSegmentationPairFeatures(
    val leftExpandedIndex: Int,
    val rightExpandedIndex: Int,
    val joinAllowed: Boolean,
    val affinity: Int,
    val components: List<RsvpScoreComponent>,
)

internal enum class RsvpSegmentationReason {
    TIGHT_PAIR,
    PRONOUN_AUXILIARY,
    AUXILIARY_CONTENT,
    COHERENT_PAIR,
    GENERAL_SHORT_PAIR,
    GLUE_PAIR,
    COMMON_PAIR,
    UNIVERSAL_SHORT_PAIR,
    CJK_SHORT_UNITS,
    ENTITY_INTERNAL,
    CASE_SHAPE_COHESION,
    DIALOGUE_COHESION,
    SPEAKER_TAG,
    PARENTHETICAL_COHESION,
    DIALOGUE_ROLE_TRANSITION,
    INTERNAL_AFFINITY,
    CUT_AFFINITY,
    LOAD_EXCESS,
    DIFFICULT_WORD_COLLISION,
    SINGLE_WORD_FALLBACK,
    ARTIFICIAL_HORIZON_NEUTRAL,
}

internal data class RsvpScoreComponent(val reason: RsvpSegmentationReason, val value: Int,)

internal data class RsvpSegmentationDecision(
    val selectedWordCount: Int,
    val selectedWordCursors: List<Int>,
    val boundaryStrengthBeforeMilli: Int,
    val dialogueRole: RsvpDialogueRole,
    val pathScore: Int,
    val selectedScore: Int,
    val weightVersion: Int,
    val components: List<RsvpScoreComponent>,
)
