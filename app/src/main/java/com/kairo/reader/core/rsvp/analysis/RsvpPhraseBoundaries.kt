package com.kairo.reader.core.rsvp.analysis

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy

/** One conservative decision shared by grouping, thought metadata, and timing. */
internal object RsvpPhraseBoundaries {
    fun annotate(tokens: List<Token>, language: RsvpLanguagePolicy): List<Token> =
        tokens.mapIndexed { index, token ->
            val boundary = isBoundary(tokens, index, language)
            if (token.isClauseBoundary == boundary) token else token.copy(isClauseBoundary = boundary)
        }

    fun isBoundary(tokens: List<Token>, index: Int, language: RsvpLanguagePolicy): Boolean =
        language == RsvpLanguagePolicy.ENGLISH && tokens[index].type == TokenType.WORD && startsClause(tokens, index)

    private fun startsClause(tokens: List<Token>, index: Int): Boolean {
        val word = normalizeWord(tokens[index].text)
        val previous = tokens.getOrNull(index - 1)?.takeIf { it.type == TokenType.WORD }?.text?.let(::normalizeWord)
        val next = tokens.getOrNull(index + 1)?.takeIf { it.type == TokenType.WORD }?.text?.let(::normalizeWord)
        val afterNext = tokens.getOrNull(index + 2)?.takeIf { it.type == TokenType.WORD }?.text?.let(::normalizeWord)
        if ("$previous $word" in CONNECTED_LEADS) return false
        if (word in CLEAR_CLAUSE_STARTERS) return true
        if (previous == null || next == null) return false
        if ("$word $next" in PAIRED_CLAUSE_STARTERS) return true
        return when (word) {
            "that" -> next in SUBJECTS || (next in FINITE_AUXILIARIES && previous !in COMPLEMENT_VERBS)
            "who", "whom", "whose", "which" -> previous !in COMPLEMENT_VERBS && previous !in PREPOSITIONS
            in TEMPORAL_STARTERS ->
                next in SUBJECTS ||
                    next in VERBAL_PARTICIPLES ||
                    (next.endsWith("ing") && next.length > MIN_PARTICIPLE_LENGTH && afterNext in OBJECT_LEADS)
            in COORDINATORS -> next in SUBJECTS && tokens.getOrNull(index - 2)?.type == TokenType.WORD
            else -> false
        }
    }

    private const val MIN_PARTICIPLE_LENGTH = 4
    private val SUBJECTS = setOf("i", "you", "he", "she", "it", "we", "they", "there")
    private val FINITE_AUXILIARIES = setOf("is", "was", "were", "has", "had", "can", "could", "will", "would")
    private val COMPLEMENT_VERBS = setOf("know", "knew", "ask", "asked", "wonder", "wondered", "say", "said", "think", "thought")
    private val PREPOSITIONS = setOf("of", "to", "for", "from", "with", "about", "at", "by", "in", "on")
    private val CONNECTED_LEADS = setOf("even though", "as though", "as if", "so that", "such that", "now that", "in that")
    private val PAIRED_CLAUSE_STARTERS = setOf("even though", "as though", "as if", "so that", "now that")
    private val VERBAL_PARTICIPLES = setOf("leaving", "arriving", "speaking", "eating", "sleeping", "returning", "starting", "finishing")
    private val OBJECT_LEADS = setOf("a", "an", "the", "my", "your", "his", "her", "our", "their", "it", "them")
    private val TEMPORAL_STARTERS = setOf("when", "where", "whether", "while", "since", "before", "after", "until")
    private val COORDINATORS = setOf("and", "but", "or", "yet", "so")
    private val CLEAR_CLAUSE_STARTERS = setOf(
        "because", "although", "though", "unless", "if", "whereas", "however", "therefore", "moreover",
        "furthermore", "nevertheless", "meanwhile", "otherwise", "hence", "thus", "consequently", "accordingly",
    )
}
