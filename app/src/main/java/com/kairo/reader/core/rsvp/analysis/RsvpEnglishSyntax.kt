package com.kairo.reader.core.rsvp.analysis

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType

/** Bounded evidence for English clauses, with no guessed boundary when the pattern is ambiguous. */
internal object RsvpEnglishSyntax {
    fun predicateAfterSubject(tokens: List<Token>, start: Int): Int? {
        val first = tokens.getOrNull(start)?.takeIf { it.type == TokenType.WORD } ?: return null
        val word = normalizeWord(first.text)
        if (word in SUBJECT_PRONOUNS) return (start + 1).takeIf { isFiniteVerb(tokens.wordAt(it)) }
        val named = word !in DETERMINERS && first.text.firstOrNull()?.isUpperCase() == true
        if (!named && word !in DETERMINERS) return null
        for (index in start + 1..start + MAX_SUBJECT_WORDS) {
            val candidate = tokens.wordAt(index) ?: return null
            if (index > start + 1 || named) {
                if (isFiniteVerb(candidate) && predicateContinuation(tokens, index, named)) return index
            }
            if (candidate in SUBJECT_STOPS || (named && tokens[index].text.firstOrNull()?.isUpperCase() != true)) return null
        }
        return null
    }

    fun relativeClauseResolutions(tokens: List<Token>): Set<Int> = buildSet {
        tokens.forEachIndexed { index, token ->
            if (token.type != TokenType.WORD || normalizeWord(token.text) !in RELATIVE_LEADS) return@forEachIndexed
            val previous = tokens.wordAt(index - 1) ?: return@forEachIndexed
            if (previous in COMPLEMENT_LEADS &&
                (previous !in COMPLEMENT_NOUNS || tokens.wordAt(index - 2) in SUBJECT_PRONOUNS)
            ) {
                return@forEachIndexed
            }
            val predicate = predicateAfterSubject(tokens, index + 1) ?: return@forEachIndexed
            findResolution(tokens, predicate)?.let(::add)
        }
    }

    private fun findResolution(tokens: List<Token>, predicate: Int): Int? {
        if (tokens.wordAt(predicate) in REPORTING_VERBS) return null
        // shortcut: only unpunctuated, non-nested relative clauses; broaden with an ambiguity corpus.
        for (index in predicate + 1..predicate + MAX_RELATIVE_WORDS) {
            val word = tokens.wordAt(index) ?: return null
            if (word in RELATIVE_STOPS) return null
            val previous = tokens.wordAt(index - 1)
            if (word in FINITE_AUXILIARIES && previous != null && isLexicalPast(previous)) return index
            if (word in REPORTING_VERBS) return null
        }
        return null
    }

    private fun predicateContinuation(tokens: List<Token>, index: Int, namedSubject: Boolean): Boolean {
        val word = tokens.wordAt(index) ?: return false
        if (word in FINITE_AUXILIARIES) return true
        val next = tokens.getOrNull(index + 1) ?: return true
        val nextWord = normalizeWord(next.text)
        // "The note left on the table" can describe a noun rather than introduce a predicate.
        if (!namedSubject && (word.endsWith("ed") || word in PAST_PARTICIPLES) && nextWord in PARTICIPLE_MODIFIERS) return false
        if (word in INTRANSITIVE_PAST) return true
        return next.type != TokenType.WORD || nextWord in VERB_FOLLOWERS || nextWord.endsWith("ly")
    }

    private fun isFiniteVerb(word: String?): Boolean =
        word != null && (word in FINITE_AUXILIARIES || isLexicalPast(word))

    private fun isLexicalPast(word: String): Boolean =
        word in INTRANSITIVE_PAST ||
            word in TRANSITIVE_PAST ||
            (word.length > MIN_PAST_LENGTH && word.endsWith("ed"))

    private fun List<Token>.wordAt(index: Int): String? =
        getOrNull(index)?.takeIf { it.type == TokenType.WORD && !it.isSubwordChunk }?.text?.let(::normalizeWord)

    private const val MAX_SUBJECT_WORDS = 4
    private const val MAX_RELATIVE_WORDS = 12
    private const val MIN_PAST_LENGTH = 4
    private val SUBJECT_PRONOUNS = setOf("i", "you", "he", "she", "it", "we", "they", "there")
    private val DETERMINERS = setOf("a", "an", "the", "my", "your", "his", "her", "our", "their", "this", "that")
    private val FINITE_AUXILIARIES =
        setOf("is", "are", "was", "were", "has", "have", "had", "can", "could", "will", "would", "must", "should")
    private val INTRANSITIVE_PAST = setOf("arrived", "left", "came", "went", "returned", "stopped", "slept", "fell", "began")
    private val TRANSITIVE_PAST = setOf("read", "wrote", "made", "took", "found", "bought", "sent", "built", "chose")
    private val PAST_PARTICIPLES = setOf("left", "read", "made", "found", "bought", "sent", "built")
    private val PARTICIPLE_MODIFIERS =
        setOf("in", "on", "at", "from", "with", "by", "to", "for", "under", "over", "near", "beside", "between", "without")
    private val RELATIVE_LEADS = setOf("that", "which", "whom")
    private val REPORTING_VERBS = setOf("said", "thought", "knew", "believed", "heard", "claimed", "reported")
    private val COMPLEMENT_NOUNS = setOf("report", "claim")
    private val COMPLEMENT_LEADS = REPORTING_VERBS + setOf("know", "think", "say", "believe", "hear", "claim", "report", "ask", "asked")
    private val SUBJECT_STOPS = SUBJECT_PRONOUNS + setOf("of", "in", "on", "at", "for", "with", "and", "or", "but", "before", "after")
    private val RELATIVE_STOPS = RELATIVE_LEADS + setOf("who", "and", "or", "but", "because", "if", "when")
    private val VERB_FOLLOWERS =
        DETERMINERS + FINITE_AUXILIARIES + setOf("to", "in", "on", "at", "from", "with", "before", "after", "it", "them")
}
