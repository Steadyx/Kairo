package com.kairo.reader.core.rsvp.segmentation

import com.kairo.reader.core.linguistics.ClauseDetector
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy
import com.kairo.reader.core.rsvp.analysis.PhraseChunkPairFeatures
import com.kairo.reader.core.rsvp.analysis.analyzePhraseChunkPair
import com.kairo.reader.core.rsvp.analysis.isFunctionWord
import com.kairo.reader.core.rsvp.analysis.isSemanticAnchor
import com.kairo.reader.core.rsvp.analysis.normalizeWord
import com.kairo.reader.core.rsvp.analysis.wordEase
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import kotlin.math.roundToInt

internal object RsvpDpSegmenter {
    fun selectWordCount(
        atomStream: RsvpAtomStream,
        startCursor: Int,
        config: RsvpConfig,
        languagePolicy: RsvpLanguagePolicy,
    ): RsvpSegmentationDecision {
        val firstWordCursor = findFirstWordCursor(atomStream, startCursor)
        if (firstWordCursor == null) return emptyDecision()
        if (!config.enablePhraseChunking || config.maxWordsPerUnit <= 1) {
            return singleWordDecision(atomStream, firstWordCursor)
        }

        val window = buildWindow(atomStream, firstWordCursor, languagePolicy)
        if (window.words.size <= 1) return singleWordDecision(atomStream, firstWordCursor)

        val policyLimit =
            if (languagePolicy == RsvpLanguagePolicy.ENGLISH) {
                MAX_ENGLISH_SCORED_WORDS_PER_UNIT
            } else {
                MAX_NON_ENGLISH_SCORED_WORDS_PER_UNIT
            }
        val candidateLimit = config.maxWordsPerUnit.coerceIn(1, policyLimit)
        val bestScores = IntArray(window.words.size + 1)
        val bestWidths = IntArray(window.words.size) { 1 }
        val bestComponents = arrayOfNulls<List<RsvpScoreComponent>>(window.words.size)

        for (position in window.words.lastIndex downTo 0) {
            var bestPathScore = Int.MIN_VALUE
            var selectedWidth = 1
            var selectedComponents: List<RsvpScoreComponent> = emptyList()
            val maxWidth = minOf(candidateLimit, window.words.size - position)
            for (width in 1..maxWidth) {
                if (!candidateIsLegal(window, position, width, config)) break
                val components = scoreCandidate(window, position, width, languagePolicy)
                val candidateScore = components.sumOf(RsvpScoreComponent::value)
                val pathScore = candidateScore + bestScores[position + width]
                if (preferCandidate(pathScore, width, bestPathScore, selectedWidth)) {
                    bestPathScore = pathScore
                    selectedWidth = width
                    selectedComponents = components
                }
            }
            bestScores[position] = bestPathScore
            bestWidths[position] = selectedWidth
            bestComponents[position] = selectedComponents
        }

        val selectedWidth = bestWidths[0]
        val selectedAtoms = window.atoms.take(selectedWidth)
        val components = bestComponents[0].orEmpty()
        return RsvpSegmentationDecision(
            selectedWordCount = selectedWidth,
            selectedWordCursors = selectedAtoms.map(RsvpAtom::expandedCursor),
            boundaryStrengthBeforeMilli = atomStream.boundaryStrengthBefore(firstWordCursor),
            dialogueRole = selectedAtoms.commonDialogueRole(),
            pathScore = bestScores[0],
            selectedScore = components.sumOf(RsvpScoreComponent::value),
            weightVersion = RsvpSegmentationWeightsV2.VERSION,
            components = components,
        )
    }

    fun selectWordCount(
        expandedTokens: List<ExpandedToken>,
        startCursor: Int,
        config: RsvpConfig,
        languagePolicy: RsvpLanguagePolicy = RsvpLanguagePolicy.ENGLISH,
        useDialogueDetection: Boolean = config.useDialogueDetection,
        useParentheticalAside: Boolean = config.useParentheticalAside,
    ): RsvpSegmentationDecision =
        selectWordCount(
            atomStream =
            RsvpAtomStream.build(
                expandedTokens = expandedTokens,
                languagePolicy = languagePolicy,
                useDialogueDetection = useDialogueDetection,
                useParentheticalAside = useParentheticalAside,
            ),
            startCursor = startCursor,
            config = config,
            languagePolicy = languagePolicy,
        )

    private fun buildWindow(
        atomStream: RsvpAtomStream,
        firstWordCursor: Int,
        languagePolicy: RsvpLanguagePolicy,
    ): RsvpSegmentationWindow {
        val words = mutableListOf<RsvpSegmentationWordFeatures>()
        val atoms = mutableListOf<RsvpAtom>()
        val pairs = mutableListOf<RsvpSegmentationPairFeatures>()
        var cursor = firstWordCursor
        var previous: RsvpAtom? = null

        while (cursor < atomStream.atoms.size && words.size < RsvpSegmentationWeightsV2.HORIZON_WORDS) {
            val atom = atomStream.atoms[cursor]
            if (atom.kind != RsvpAtomKind.WORD) break
            if (previous != null) {
                val pair = pairFeatures(previous, atom, languagePolicy)
                if (!pair.joinAllowed) break
                pairs += pair
            }
            atoms += atom
            words += wordFeatures(atom, languagePolicy)
            previous = atom
            cursor++
        }

        val nextAtom = atomStream.atomAtExpandedCursor(cursor)
        val artificialHorizon =
            words.size == RsvpSegmentationWeightsV2.HORIZON_WORDS &&
                nextAtom?.kind == RsvpAtomKind.WORD &&
                previous != null &&
                pairFeatures(previous, nextAtom, languagePolicy).joinAllowed
        return RsvpSegmentationWindow(
            words = words,
            atoms = atoms,
            pairs = pairs,
            artificialHorizon = artificialHorizon,
        )
    }

    private fun wordFeatures(
        atom: RsvpAtom,
        languagePolicy: RsvpLanguagePolicy,
    ): RsvpSegmentationWordFeatures {
        val token = atom.token
        val normalized = normalizeWord(token.text)
        val visibleCharacters = visibleCodePointCount(token.text)
        val difficulty =
            if (languagePolicy == RsvpLanguagePolicy.ENGLISH) {
                ((1.0 - wordEase(token)) * RsvpSegmentationWeightsV2.FIXED_POINT_SCALE)
                    .roundToInt()
                    .coerceIn(0, RsvpSegmentationWeightsV2.FIXED_POINT_SCALE)
            } else {
                0
            }
        val characterLoad =
            when (languagePolicy) {
                RsvpLanguagePolicy.ENGLISH -> RsvpSegmentationWeightsV2.ENGLISH_CHARACTER_LOAD
                RsvpLanguagePolicy.CJK -> RsvpSegmentationWeightsV2.CJK_CHARACTER_LOAD
                RsvpLanguagePolicy.DEFAULT_NON_ENGLISH,
                RsvpLanguagePolicy.RTL,
                RsvpLanguagePolicy.UNKNOWN -> RsvpSegmentationWeightsV2.UNIVERSAL_CHARACTER_LOAD
            }
        return RsvpSegmentationWordFeatures(
            expandedIndex = atom.expandedCursor,
            characterCount = visibleCharacters,
            difficulty = difficulty,
            visualLoad = difficulty + (visibleCharacters * characterLoad),
            isFunctionWord = languagePolicy == RsvpLanguagePolicy.ENGLISH && isFunctionWord(normalized),
            isSemanticAnchor = languagePolicy == RsvpLanguagePolicy.ENGLISH && isSemanticAnchor(normalized),
            isSubword = token.isSubwordChunk,
        )
    }

    private fun pairFeatures(
        left: RsvpAtom,
        right: RsvpAtom,
        languagePolicy: RsvpLanguagePolicy,
    ): RsvpSegmentationPairFeatures {
        val joinAllowed = pairIsHardLegal(left, right, languagePolicy)
        if (!joinAllowed) {
            return RsvpSegmentationPairFeatures(
                leftExpandedIndex = left.expandedCursor,
                rightExpandedIndex = right.expandedCursor,
                joinAllowed = false,
                affinity = 0,
                components = emptyList(),
            )
        }

        val components =
            buildList {
                if (languagePolicy == RsvpLanguagePolicy.ENGLISH) {
                    addAll(englishAffinityComponents(analyzePhraseChunkPair(left.token, right.token)))
                } else {
                    addAll(universalAffinityComponents(left, right, languagePolicy))
                }
                addAll(annotationAffinityComponents(left, right, languagePolicy))
            }
        return RsvpSegmentationPairFeatures(
            leftExpandedIndex = left.expandedCursor,
            rightExpandedIndex = right.expandedCursor,
            joinAllowed = true,
            affinity = components.sumOf(RsvpScoreComponent::value),
            components = components,
        )
    }

    private fun pairIsHardLegal(
        left: RsvpAtom,
        right: RsvpAtom,
        languagePolicy: RsvpLanguagePolicy,
    ): Boolean {
        if (left.kind != RsvpAtomKind.WORD || right.kind != RsvpAtomKind.WORD) return false
        if (right.expandedCursor != left.expandedCursor + 1) return false
        if (left.token.isSubwordChunk || right.token.isSubwordChunk) return false
        if (left.token.text.endsWith("-")) return false
        if (languagePolicy != RsvpLanguagePolicy.ENGLISH) return true

        val leftLower = normalizeWord(left.token.text)
        return !left.token.isClauseBoundary &&
            !right.token.isClauseBoundary &&
            !ClauseDetector.isCoordinatingConjunction(leftLower)
    }

    private fun englishAffinityComponents(evidence: PhraseChunkPairFeatures): List<RsvpScoreComponent> =
        buildList {
            if (evidence.tightPair) {
                add(
                    RsvpScoreComponent(
                        RsvpSegmentationReason.TIGHT_PAIR,
                        RsvpSegmentationWeightsV2.TIGHT_PAIR_AFFINITY,
                    )
                )
            }
            if (evidence.pronounAuxiliaryBridge) {
                add(
                    RsvpScoreComponent(
                        RsvpSegmentationReason.PRONOUN_AUXILIARY,
                        RsvpSegmentationWeightsV2.PRONOUN_AUXILIARY_AFFINITY,
                    )
                )
            }
            if (evidence.auxiliaryContentBridge) {
                add(
                    RsvpScoreComponent(
                        RsvpSegmentationReason.AUXILIARY_CONTENT,
                        RsvpSegmentationWeightsV2.AUXILIARY_CONTENT_AFFINITY,
                    )
                )
            }
            if (evidence.coherentShortPair) {
                add(
                    RsvpScoreComponent(
                        RsvpSegmentationReason.COHERENT_PAIR,
                        evidence.coherenceScoreMilli * RsvpSegmentationWeightsV2.COHERENCE_AFFINITY_MAX /
                            RsvpSegmentationWeightsV2.FIXED_POINT_SCALE,
                    )
                )
            }
            if (evidence.generalShortPair) {
                add(
                    RsvpScoreComponent(
                        RsvpSegmentationReason.GENERAL_SHORT_PAIR,
                        RsvpSegmentationWeightsV2.GENERAL_SHORT_PAIR_AFFINITY,
                    )
                )
            }
            if (evidence.gluePair) {
                add(
                    RsvpScoreComponent(
                        RsvpSegmentationReason.GLUE_PAIR,
                        RsvpSegmentationWeightsV2.GLUE_PAIR_AFFINITY,
                    )
                )
            }
            if (evidence.bothCommon) {
                add(
                    RsvpScoreComponent(
                        RsvpSegmentationReason.COMMON_PAIR,
                        RsvpSegmentationWeightsV2.COMMON_PAIR_AFFINITY,
                    )
                )
            }
        }

    private fun universalAffinityComponents(
        left: RsvpAtom,
        right: RsvpAtom,
        languagePolicy: RsvpLanguagePolicy,
    ): List<RsvpScoreComponent> {
        val combinedCharacters = visibleCodePointCount(left.token.text) + visibleCodePointCount(right.token.text)
        return buildList {
            when {
                languagePolicy == RsvpLanguagePolicy.CJK &&
                    combinedCharacters <= RsvpSegmentationWeightsV2.CJK_SHORT_PAIR_MAX_VISIBLE_CODE_POINTS ->
                    add(
                        RsvpScoreComponent(
                            RsvpSegmentationReason.CJK_SHORT_UNITS,
                            RsvpSegmentationWeightsV2.CJK_SHORT_UNITS_AFFINITY,
                        )
                    )
                combinedCharacters <= RsvpSegmentationWeightsV2.UNIVERSAL_SHORT_PAIR_MAX_VISIBLE_CODE_POINTS ->
                    add(
                        RsvpScoreComponent(
                            RsvpSegmentationReason.UNIVERSAL_SHORT_PAIR,
                            RsvpSegmentationWeightsV2.UNIVERSAL_SHORT_PAIR_AFFINITY,
                        )
                    )
            }
        }
    }

    private fun annotationAffinityComponents(
        left: RsvpAtom,
        right: RsvpAtom,
        languagePolicy: RsvpLanguagePolicy,
    ): List<RsvpScoreComponent> =
        buildList {
            if (left.entitySpanId != null && left.entitySpanId == right.entitySpanId) {
                add(
                    RsvpScoreComponent(
                        reason =
                        if (languagePolicy == RsvpLanguagePolicy.ENGLISH) {
                            RsvpSegmentationReason.ENTITY_INTERNAL
                        } else {
                            RsvpSegmentationReason.CASE_SHAPE_COHESION
                        },
                        value =
                        if (languagePolicy == RsvpLanguagePolicy.ENGLISH) {
                            RsvpSegmentationWeightsV2.ENTITY_INTERNAL_AFFINITY
                        } else {
                            RsvpSegmentationWeightsV2.CASE_SHAPE_COHESION_AFFINITY
                        },
                    )
                )
            }
            when {
                left.dialogueRole == RsvpDialogueRole.SPEAKER_TAG &&
                    right.dialogueRole == RsvpDialogueRole.SPEAKER_TAG ->
                    add(
                        RsvpScoreComponent(
                            RsvpSegmentationReason.SPEAKER_TAG,
                            RsvpSegmentationWeightsV2.SPEAKER_TAG_AFFINITY,
                        )
                    )
                left.dialogueRole == RsvpDialogueRole.PARENTHETICAL_ASIDE &&
                    right.dialogueRole == RsvpDialogueRole.PARENTHETICAL_ASIDE ->
                    add(
                        RsvpScoreComponent(
                            RsvpSegmentationReason.PARENTHETICAL_COHESION,
                            RsvpSegmentationWeightsV2.PARENTHETICAL_COHESION_AFFINITY,
                        )
                    )
                left.dialogueRole == RsvpDialogueRole.DIALOGUE_CONTENT &&
                    right.dialogueRole == RsvpDialogueRole.DIALOGUE_CONTENT ->
                    add(
                        RsvpScoreComponent(
                            RsvpSegmentationReason.DIALOGUE_COHESION,
                            RsvpSegmentationWeightsV2.DIALOGUE_COHESION_AFFINITY,
                        )
                    )
                left.dialogueRole != right.dialogueRole &&
                    (left.dialogueRole.isDialogueOrSpeaker() || right.dialogueRole.isDialogueOrSpeaker()) ->
                    add(
                        RsvpScoreComponent(
                            RsvpSegmentationReason.DIALOGUE_ROLE_TRANSITION,
                            -RsvpSegmentationWeightsV2.DIALOGUE_ROLE_TRANSITION_PENALTY,
                        )
                    )
            }
        }

    private fun candidateIsLegal(
        window: RsvpSegmentationWindow,
        position: Int,
        width: Int,
        config: RsvpConfig,
    ): Boolean {
        if (width == 1) return true
        val characters = window.words.subList(position, position + width).sumOf { it.characterCount }
        if (characters > config.maxCharsPerUnit.coerceAtLeast(1)) return false
        return (position until position + width - 1).all { pairIndex ->
            window.pairs.getOrNull(pairIndex)?.joinAllowed == true
        }
    }

    private fun scoreCandidate(
        window: RsvpSegmentationWindow,
        position: Int,
        width: Int,
        languagePolicy: RsvpLanguagePolicy,
    ): List<RsvpScoreComponent> {
        if (width == 1) {
            return buildList {
                add(RsvpScoreComponent(RsvpSegmentationReason.SINGLE_WORD_FALLBACK, 0))
                addCutOrHorizonComponent(window, position + width)
            }
        }

        val words = window.words.subList(position, position + width)
        val internalComponents =
            (position until position + width - 1).flatMap { pairIndex ->
                window.pairs[pairIndex].components
            }
        val load = words.sumOf(RsvpSegmentationWordFeatures::visualLoad)
        val loadExcess = (load - RsvpSegmentationWeightsV2.FRAME_LOAD_BUDGET).coerceAtLeast(0)
        val maxDifficulty = words.maxOf(RsvpSegmentationWordFeatures::difficulty)
        val difficultCollisionPenalty =
            if (languagePolicy == RsvpLanguagePolicy.ENGLISH &&
                maxDifficulty >= RsvpSegmentationWeightsV2.DIFFICULT_WORD_THRESHOLD
            ) {
                RsvpSegmentationWeightsV2.DIFFICULT_COLLISION_BASE_PENALTY +
                    (
                        (maxDifficulty - RsvpSegmentationWeightsV2.DIFFICULT_WORD_THRESHOLD) /
                            RsvpSegmentationWeightsV2.DIFFICULT_COLLISION_EXCESS_DIVISOR
                        )
            } else {
                0
            }
        return buildList {
            addAll(internalComponents)
            if (loadExcess > 0) {
                add(
                    RsvpScoreComponent(
                        RsvpSegmentationReason.LOAD_EXCESS,
                        -(loadExcess * RsvpSegmentationWeightsV2.LOAD_EXCESS_PENALTY),
                    )
                )
            }
            if (difficultCollisionPenalty > 0) {
                add(
                    RsvpScoreComponent(
                        RsvpSegmentationReason.DIFFICULT_WORD_COLLISION,
                        -difficultCollisionPenalty,
                    )
                )
            }
            addCutOrHorizonComponent(window, position + width)
        }
    }

    private fun MutableList<RsvpScoreComponent>.addCutOrHorizonComponent(
        window: RsvpSegmentationWindow,
        endPosition: Int,
    ) {
        if (endPosition < window.words.size) {
            val affinity = window.pairs[endPosition - 1].affinity
            add(
                RsvpScoreComponent(
                    RsvpSegmentationReason.CUT_AFFINITY,
                    -(affinity / RsvpSegmentationWeightsV2.CUT_AFFINITY_DIVISOR),
                )
            )
        } else if (window.artificialHorizon) {
            add(RsvpScoreComponent(RsvpSegmentationReason.ARTIFICIAL_HORIZON_NEUTRAL, 0))
        }
    }

    private fun singleWordDecision(
        atomStream: RsvpAtomStream,
        wordCursor: Int,
    ): RsvpSegmentationDecision {
        val atom = requireNotNull(atomStream.atomAtExpandedCursor(wordCursor))
        return RsvpSegmentationDecision(
            selectedWordCount = 1,
            selectedWordCursors = listOf(wordCursor),
            boundaryStrengthBeforeMilli = atomStream.boundaryStrengthBefore(wordCursor),
            dialogueRole = atom.dialogueRole,
            pathScore = 0,
            selectedScore = 0,
            weightVersion = RsvpSegmentationWeightsV2.VERSION,
            components = listOf(RsvpScoreComponent(RsvpSegmentationReason.SINGLE_WORD_FALLBACK, 0)),
        )
    }

    private fun emptyDecision(): RsvpSegmentationDecision =
        RsvpSegmentationDecision(
            selectedWordCount = 0,
            selectedWordCursors = emptyList(),
            boundaryStrengthBeforeMilli = RHYTHM_BOUNDARY_NONE,
            dialogueRole = RsvpDialogueRole.NARRATION,
            pathScore = 0,
            selectedScore = 0,
            weightVersion = RsvpSegmentationWeightsV2.VERSION,
            components = emptyList(),
        )

    internal fun preferCandidate(
        candidateScore: Int,
        candidateWidth: Int,
        currentBestScore: Int,
        currentBestWidth: Int,
    ): Boolean =
        candidateScore > currentBestScore ||
            (candidateScore == currentBestScore && candidateWidth < currentBestWidth)

    private fun findFirstWordCursor(
        atomStream: RsvpAtomStream,
        startCursor: Int,
    ): Int? =
        (startCursor.coerceAtLeast(0) until atomStream.atoms.size)
            .firstOrNull { cursor -> atomStream.atoms[cursor].kind == RsvpAtomKind.WORD }

    private fun List<RsvpAtom>.commonDialogueRole(): RsvpDialogueRole {
        val first = firstOrNull()?.dialogueRole ?: return RsvpDialogueRole.NARRATION
        return first.takeIf { role -> all { atom -> atom.dialogueRole == role } }
            ?: RsvpDialogueRole.NARRATION
    }

    private fun RsvpDialogueRole.isDialogueOrSpeaker(): Boolean =
        this == RsvpDialogueRole.DIALOGUE_CONTENT || this == RsvpDialogueRole.SPEAKER_TAG

    private data class RsvpSegmentationWindow(
        val words: List<RsvpSegmentationWordFeatures>,
        val atoms: List<RsvpAtom>,
        val pairs: List<RsvpSegmentationPairFeatures>,
        val artificialHorizon: Boolean,
    )

    private const val MAX_ENGLISH_SCORED_WORDS_PER_UNIT = 3
    private const val MAX_NON_ENGLISH_SCORED_WORDS_PER_UNIT = 2
}
