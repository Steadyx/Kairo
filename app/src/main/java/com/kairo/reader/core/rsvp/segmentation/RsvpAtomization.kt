package com.kairo.reader.core.rsvp.segmentation

import com.kairo.reader.core.linguistics.DialogueAnalyzer
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.isMidSentencePunctuation
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy
import com.kairo.reader.core.rsvp.engine.BoundaryBefore
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import com.kairo.reader.core.rsvp.text.boundaryBeforeForPunctuation

internal enum class RsvpAtomKind {
    WORD,
    PUNCTUATION,
    STRUCTURAL,
}

internal enum class RsvpDialogueRole {
    NARRATION,
    DIALOGUE_CONTENT,
    SPEAKER_TAG,
    PARENTHETICAL_ASIDE,
}

/**
 * Immutable, invocation-local view of an expanded token.
 *
 * Atoms deliberately retain a one-to-one cursor and source-range mapping. This lets scored
 * segmentation reason about gaps and annotations without becoming a second punctuation owner.
 */
internal data class RsvpAtom(
    val token: Token,
    val expandedCursor: Int,
    val originalIndex: Int,
    val sourceCharacterStart: Int,
    val sourceCharacterEndExclusive: Int,
    val kind: RsvpAtomKind,
    val dialogueRole: RsvpDialogueRole = RsvpDialogueRole.NARRATION,
    val entitySpanId: Int? = null,
)

internal class RsvpAtomStream private constructor(val atoms: List<RsvpAtom>, val languagePolicy: RsvpLanguagePolicy,) {
    fun atomAtExpandedCursor(cursor: Int): RsvpAtom? = atoms.getOrNull(cursor)

    fun boundaryStrengthBefore(wordCursor: Int): Int {
        val current = atomAtExpandedCursor(wordCursor)?.takeIf { it.kind == RsvpAtomKind.WORD }
            ?: return RHYTHM_BOUNDARY_NONE
        val previousWordCursor =
            (wordCursor - 1 downTo 0).firstOrNull { cursor ->
                atomAtExpandedCursor(cursor)?.kind == RsvpAtomKind.WORD
            } ?: return RHYTHM_BOUNDARY_NONE
        val previous = atoms[previousWordCursor]

        var strength = RHYTHM_BOUNDARY_NONE
        for (cursor in previousWordCursor + 1 until wordCursor) {
            val atom = atoms[cursor]
            strength =
                maxOf(
                    strength,
                    atom.boundaryStrength(
                        prevWord = previous.token,
                        nextToken = current.token,
                        languagePolicy = languagePolicy,
                    ),
                )
        }
        if (languagePolicy == RsvpLanguagePolicy.ENGLISH && current.token.isClauseBoundary) {
            strength = maxOf(strength, RHYTHM_BOUNDARY_CLAUSE)
        }
        if (previous.dialogueRole != current.dialogueRole &&
            (previous.dialogueRole.isDialogueRole() || current.dialogueRole.isDialogueRole())
        ) {
            strength = maxOf(strength, RHYTHM_BOUNDARY_DIALOGUE_TRANSITION)
        }
        return strength
    }

    companion object {
        fun build(
            expandedTokens: List<ExpandedToken>,
            languagePolicy: RsvpLanguagePolicy,
            useDialogueDetection: Boolean,
            useParentheticalAside: Boolean,
        ): RsvpAtomStream {
            val baseAtoms = expandedTokens.map(::baseAtom)
            val withRoles =
                annotateRoles(
                    atoms = baseAtoms,
                    languagePolicy = languagePolicy,
                    useDialogueDetection = useDialogueDetection,
                    useParentheticalAside = useParentheticalAside,
                )
            return RsvpAtomStream(
                atoms = annotateEntities(withRoles, languagePolicy),
                languagePolicy = languagePolicy,
            )
        }

        private fun baseAtom(expanded: ExpandedToken): RsvpAtom =
            RsvpAtom(
                token = expanded.token,
                expandedCursor = expanded.expandedIndex,
                originalIndex = expanded.originalIndex,
                sourceCharacterStart = expanded.sourceCharacterStart,
                sourceCharacterEndExclusive = expanded.sourceCharacterEndExclusive,
                kind =
                when (expanded.token.type) {
                    TokenType.WORD -> RsvpAtomKind.WORD
                    TokenType.PUNCTUATION -> RsvpAtomKind.PUNCTUATION
                    TokenType.PARAGRAPH_BREAK,
                    TokenType.PAGE_BREAK -> RsvpAtomKind.STRUCTURAL
                },
            )

        private fun annotateRoles(
            atoms: List<RsvpAtom>,
            languagePolicy: RsvpLanguagePolicy,
            useDialogueDetection: Boolean,
            useParentheticalAside: Boolean,
        ): List<RsvpAtom> {
            if (!useDialogueDetection && !useParentheticalAside) return atoms

            var parentheticalDepth = 0
            var pairedQuoteDepth = 0
            val openSymmetricQuotes = mutableSetOf<Char>()
            val roles = MutableList(atoms.size) { RsvpDialogueRole.NARRATION }
            atoms.forEachIndexed { index, atom ->
                val character = atom.token.text.firstOrNull()
                if (atom.kind == RsvpAtomKind.PUNCTUATION && character != null) {
                    when (character) {
                        in PAIRED_OPENING_QUOTES -> pairedQuoteDepth++
                        in PAIRED_CLOSING_QUOTES ->
                            pairedQuoteDepth = (pairedQuoteDepth - 1).coerceAtLeast(0)
                        in SYMMETRIC_QUOTES -> {
                            if (!openSymmetricQuotes.add(character)) {
                                openSymmetricQuotes.remove(character)
                            }
                        }
                    }
                    if (character in OPENING_BRACKETS) parentheticalDepth++
                }
                if (atom.kind == RsvpAtomKind.WORD) {
                    val inScannedDialogue = pairedQuoteDepth > 0 || openSymmetricQuotes.isNotEmpty()
                    roles[index] =
                        when {
                            useDialogueDetection && (atom.token.isDialogue || inScannedDialogue) ->
                                RsvpDialogueRole.DIALOGUE_CONTENT
                            useParentheticalAside && parentheticalDepth > 0 ->
                                RsvpDialogueRole.PARENTHETICAL_ASIDE
                            else -> RsvpDialogueRole.NARRATION
                        }
                }
                if (atom.kind == RsvpAtomKind.PUNCTUATION &&
                    character != null &&
                    character in CLOSING_BRACKETS
                ) {
                    parentheticalDepth = (parentheticalDepth - 1).coerceAtLeast(0)
                }
            }

            if (useDialogueDetection && languagePolicy == RsvpLanguagePolicy.ENGLISH) {
                annotateEnglishSpeakerTags(
                    atoms = atoms,
                    baseRoles = roles.toList(),
                    roles = roles,
                )
            }
            return atoms.mapIndexed { index, atom -> atom.copy(dialogueRole = roles[index]) }
        }

        private fun annotateEnglishSpeakerTags(
            atoms: List<RsvpAtom>,
            baseRoles: List<RsvpDialogueRole>,
            roles: MutableList<RsvpDialogueRole>,
        ) {
            var cursor = 0
            while (cursor < atoms.size) {
                if (atoms[cursor].kind != RsvpAtomKind.WORD) {
                    cursor++
                    continue
                }
                val runEnd =
                    (cursor until atoms.size)
                        .firstOrNull { atoms[it].kind != RsvpAtomKind.WORD }
                        ?: atoms.size
                annotateSpeakerTagsInRun(atoms, baseRoles, roles, cursor, runEnd)
                cursor = runEnd
            }
        }

        private fun annotateSpeakerTagsInRun(
            atoms: List<RsvpAtom>,
            baseRoles: List<RsvpDialogueRole>,
            roles: MutableList<RsvpDialogueRole>,
            runStart: Int,
            runEnd: Int,
        ) {
            for (start in runStart until runEnd) {
                for (width in MAX_SPEAKER_TAG_WORDS downTo MIN_SPEAKER_TAG_WORDS) {
                    val endExclusive = start + width
                    if (endExclusive <= runEnd) {
                        annotateSpeakerTagCandidate(
                            atoms = atoms,
                            baseRoles = baseRoles,
                            roles = roles,
                            start = start,
                            endExclusive = endExclusive,
                        )
                    }
                }
            }
        }

        private fun annotateSpeakerTagCandidate(
            atoms: List<RsvpAtom>,
            baseRoles: List<RsvpDialogueRole>,
            roles: MutableList<RsvpDialogueRole>,
            start: Int,
            endExclusive: Int,
        ) {
            val candidateIsNarration =
                (start until endExclusive).all { index ->
                    baseRoles[index] == RsvpDialogueRole.NARRATION
                }
            if (!candidateIsNarration) return
            val words = atoms.subList(start, endExclusive).map { it.token.text }
            if (!DialogueAnalyzer.isSpeakerTag(words) ||
                !hasNearbyDialogue(baseRoles, start, endExclusive)
            ) {
                return
            }
            for (index in start until endExclusive) {
                roles[index] = RsvpDialogueRole.SPEAKER_TAG
            }
        }

        private fun hasNearbyDialogue(
            roles: List<RsvpDialogueRole>,
            start: Int,
            endExclusive: Int,
        ): Boolean {
            val from = (start - DIALOGUE_CONTEXT_ATOMS).coerceAtLeast(0)
            val to = (endExclusive + DIALOGUE_CONTEXT_ATOMS).coerceAtMost(roles.size)
            return (from until to).any { index ->
                index !in start until endExclusive &&
                    roles[index] == RsvpDialogueRole.DIALOGUE_CONTENT
            }
        }

        private fun annotateEntities(
            atoms: List<RsvpAtom>,
            languagePolicy: RsvpLanguagePolicy,
        ): List<RsvpAtom> {
            if (languagePolicy != RsvpLanguagePolicy.ENGLISH &&
                languagePolicy != RsvpLanguagePolicy.DEFAULT_NON_ENGLISH
            ) {
                return atoms
            }

            val entityIds = arrayOfNulls<Int>(atoms.size)
            var nextEntityId = 0
            var cursor = 0
            while (cursor < atoms.size) {
                if (atoms[cursor].kind != RsvpAtomKind.WORD || !atoms[cursor].isEntityHead(languagePolicy)) {
                    cursor++
                    continue
                }

                val span = mutableListOf(cursor)
                var scan = cursor + 1
                while (scan < atoms.size && span.size < MAX_ENTITY_WORDS) {
                    val candidate = atoms[scan]
                    if (candidate.kind != RsvpAtomKind.WORD) break
                    val canAppend =
                        candidate.hasProperCaseShape() ||
                            (
                                languagePolicy == RsvpLanguagePolicy.ENGLISH &&
                                    span.size <= MAX_ENTITY_WORDS - 2 &&
                                    candidate.token.text.lowercase() in ENGLISH_ENTITY_CONNECTORS &&
                                    atoms.getOrNull(scan + 1)?.hasProperCaseShape() == true
                                )
                    if (!canAppend) break
                    span += scan
                    scan++
                }

                val properWordCount = span.count { atoms[it].hasProperCaseShape() }
                val englishTitleHead =
                    languagePolicy == RsvpLanguagePolicy.ENGLISH &&
                        atoms[cursor].token.text.trimEnd('.').lowercase() in ENGLISH_ENTITY_TITLES
                if (properWordCount >= 2 || (englishTitleHead && properWordCount >= 1 && span.size >= 2)) {
                    span.forEach { entityIds[it] = nextEntityId }
                    nextEntityId++
                    cursor = scan
                } else {
                    cursor++
                }
            }
            return atoms.mapIndexed { index, atom -> atom.copy(entitySpanId = entityIds[index]) }
        }
    }
}

internal fun visibleCodePointCount(text: String): Int {
    var count = 0
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val type = Character.getType(codePoint)
        if (type != Character.NON_SPACING_MARK.toInt() &&
            type != Character.COMBINING_SPACING_MARK.toInt() &&
            type != Character.ENCLOSING_MARK.toInt()
        ) {
            count++
        }
        index += Character.charCount(codePoint)
    }
    return count
}

private fun RsvpAtom.boundaryStrength(
    prevWord: Token,
    nextToken: Token,
    languagePolicy: RsvpLanguagePolicy,
): Int =
    when (token.type) {
        TokenType.PAGE_BREAK,
        TokenType.PARAGRAPH_BREAK -> RHYTHM_BOUNDARY_HARD
        TokenType.PUNCTUATION -> {
            val contextualBoundary =
                boundaryBeforeForPunctuation(
                    token = token,
                    prevWord = prevWord,
                    nextToken = nextToken,
                ).withoutEnglishCommaPromotion(token, languagePolicy)
            when (contextualBoundary) {
                BoundaryBefore.SENTENCE,
                BoundaryBefore.PARAGRAPH,
                BoundaryBefore.PAGE -> RHYTHM_BOUNDARY_HARD
                BoundaryBefore.CLAUSE -> RHYTHM_BOUNDARY_CLAUSE
                BoundaryBefore.NONE ->
                    when (val character = token.text.firstOrNull()) {
                        in COMMA_LIKE_PUNCTUATION -> RHYTHM_BOUNDARY_COMMA
                        in ALL_DIALOGUE_QUOTES -> RHYTHM_BOUNDARY_QUOTE
                        in OPENING_BRACKETS,
                        in CLOSING_BRACKETS -> RHYTHM_BOUNDARY_ASIDE
                        else ->
                            if (character != null && isMidSentencePunctuation(character)) {
                                RHYTHM_BOUNDARY_CLAUSE
                            } else {
                                RHYTHM_BOUNDARY_NONE
                            }
                    }
            }
        }
        TokenType.WORD -> RHYTHM_BOUNDARY_NONE
    }

private fun BoundaryBefore.withoutEnglishCommaPromotion(
    token: Token,
    languagePolicy: RsvpLanguagePolicy,
): BoundaryBefore =
    if (this == BoundaryBefore.CLAUSE &&
        token.text.firstOrNull() == ',' &&
        languagePolicy != RsvpLanguagePolicy.ENGLISH
    ) {
        BoundaryBefore.NONE
    } else {
        this
    }

private fun RsvpDialogueRole.isDialogueRole(): Boolean =
    this == RsvpDialogueRole.DIALOGUE_CONTENT || this == RsvpDialogueRole.SPEAKER_TAG

private fun RsvpAtom.isEntityHead(languagePolicy: RsvpLanguagePolicy): Boolean =
    hasProperCaseShape() ||
        (
            languagePolicy == RsvpLanguagePolicy.ENGLISH &&
                token.text.trimEnd('.').lowercase() in ENGLISH_ENTITY_TITLES
            )

private fun RsvpAtom.hasProperCaseShape(): Boolean =
    token.type == TokenType.WORD && (token.text.isTitleCaseWord() || token.text.isShortAcronym())

private fun String.isTitleCaseWord(): Boolean {
    val letters = codePoints().toArray().filter(Character::isLetter)
    if (letters.isEmpty() || !Character.isUpperCase(letters.first())) return false
    return letters.drop(1).any(Character::isLowerCase) &&
        letters.drop(1).none(Character::isUpperCase)
}

private fun String.isShortAcronym(): Boolean {
    val letters = codePoints().toArray().filter(Character::isLetter)
    return letters.size in MIN_ACRONYM_LETTERS..MAX_ACRONYM_LETTERS &&
        letters.all(Character::isUpperCase)
}

internal const val RHYTHM_BOUNDARY_NONE = 0
internal const val RHYTHM_BOUNDARY_HARD = 1000
internal const val RHYTHM_BOUNDARY_CLAUSE = 550
internal const val RHYTHM_BOUNDARY_DIALOGUE_TRANSITION = 400
internal const val RHYTHM_BOUNDARY_QUOTE = 350
internal const val RHYTHM_BOUNDARY_COMMA = 300
internal const val RHYTHM_BOUNDARY_ASIDE = 300

private val OPENING_BRACKETS = setOf('(', '[', '{')
private val CLOSING_BRACKETS = setOf(')', ']', '}')
private val PAIRED_OPENING_QUOTES =
    setOf('\u201C', '\u2018', '\u00AB', '\u2039', '\u300C', '\u300E')
private val PAIRED_CLOSING_QUOTES =
    setOf('\u201D', '\u2019', '\u00BB', '\u203A', '\u300D', '\u300F')
private val SYMMETRIC_QUOTES = setOf('"')
private val ALL_DIALOGUE_QUOTES = PAIRED_OPENING_QUOTES + PAIRED_CLOSING_QUOTES + SYMMETRIC_QUOTES
private val COMMA_LIKE_PUNCTUATION = setOf(',', '\u3001', '\uFF0C', '\u060C')
private val ENGLISH_ENTITY_TITLES = setOf("mr", "mrs", "ms", "miss", "dr", "professor", "sir", "lady", "lord")
private val ENGLISH_ENTITY_CONNECTORS = setOf("of", "the", "and", "de", "van", "von")
private const val MAX_ENTITY_WORDS = 5
private const val MIN_ACRONYM_LETTERS = 2
private const val MAX_ACRONYM_LETTERS = 5
private const val MIN_SPEAKER_TAG_WORDS = 2
private const val MAX_SPEAKER_TAG_WORDS = 3
private const val DIALOGUE_CONTEXT_ATOMS = 4
