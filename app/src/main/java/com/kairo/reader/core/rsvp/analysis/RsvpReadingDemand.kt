@file:Suppress("MagicNumber")

package com.kairo.reader.core.rsvp.analysis

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.exp
import kotlin.math.ln

/** One recognition estimate, with a separate small novelty allowance. Neither learns from the reader. */
internal data class RsvpReadingDemand(val recognition: Double, val novelty: Double = 0.0, val share: Double = 1.0) {
    val difficulty: Double get() = 1.0 - exp(-recognition)

    fun allowanceMs(tempoMs: Double): Double {
        val cap = (tempoMs * 0.8).coerceIn(80.0, 180.0)
        // Novelty consumes only the remaining headroom; it cannot inflate the recognition cap.
        return share * cap * (1.0 - exp(-(recognition + novelty)))
    }
}

internal object ReadingDemandAnalyzer {
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT).replace('’', '\'')
        .trim { !it.isLetterOrDigit() }

    fun analyze(token: Token, policy: RsvpLanguagePolicy, previousOccurrences: Int = 0): RsvpReadingDemand {
        val word = normalize(token.text)
        val length = word.codePoints().filter { Character.isLetterOrDigit(it) }.count().toInt()
        val digits = word.count(Char::isDigit)
        val lengthBase = if (policy == RsvpLanguagePolicy.CJK) 1 else 3
        val lengthScale = if (policy == RsvpLanguagePolicy.CJK) 2.0 else 4.0
        val visual = 0.55 * ln(1.0 + (length - lengthBase).coerceAtLeast(0) / lengthScale)
        val english = policy == RsvpLanguagePolicy.ENGLISH && word.isNotEmpty() && word.all { it in 'a'..'z' || it == '\'' }
        val rarity = when {
            digits > 0 -> 0.35 * ln(1.0 + digits)
            english -> EnglishReadingFrequency.recognitionZipf(word)?.let { ((5.5 - it) / 3.0).coerceIn(0.0, 1.0) }
                ?: spellingFallback(word)
            else -> 0.0 // No English frequency or pronunciation assumptions for other scripts/languages.
        }
        val novelty = if (english) {
            0.25 * rarity / (1.0 + previousOccurrences.coerceIn(0, 8))
        } else {
            0.0
        }
        // Familiarity can help recognition, but cannot erase the work of decoding a long word.
        // Use a floor, not another additive syllable allowance on top of length and rarity.
        val decoding = if (english) decodingDemand(word, length) else 0.0
        return RsvpReadingDemand(maxOf(visual + 0.85 * rarity, decoding), novelty)
    }

    /** Build from source positions before splitting. Seeking cannot turn a repeated term into a first occurrence. */
    fun plan(tokens: List<Token>, startIndex: Int, policy: RsvpLanguagePolicy): Map<Int, RsvpReadingDemand> {
        var paragraphStart = startIndex.coerceIn(0, tokens.size)
        while (paragraphStart > 0 &&
            tokens[paragraphStart - 1].type != TokenType.PARAGRAPH_BREAK &&
            tokens[paragraphStart - 1].type != TokenType.PAGE_BREAK
        ) {
            paragraphStart--
        }
        val seen = object : LinkedHashMap<String, Int>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean = size > 2048
        }
        val result = HashMap<Int, RsvpReadingDemand>()
        for (index in paragraphStart until tokens.size) {
            val token = tokens[index]
            when (token.type) {
                TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> seen.clear()
                TokenType.PUNCTUATION -> Unit
                TokenType.WORD -> {
                    val key = normalize(token.text)
                    val count = seen[key] ?: 0
                    if (index >= startIndex) result[index] = analyze(token, policy, count)
                    seen[key] = (count + 1).coerceAtMost(8)
                }
            }
        }
        return result
    }

    fun attach(
        expanded: List<ExpandedToken>,
        tokens: List<Token>,
        startIndex: Int,
        policy: RsvpLanguagePolicy,
    ): List<ExpandedToken> {
        val demands = plan(tokens, startIndex, policy)
        val sourceLengths = HashMap<Int, Int>()
        for (entry in expanded) {
            if (entry.token.type == TokenType.WORD) {
                sourceLengths.merge(entry.originalIndex, displayedLength(entry.token), Int::plus)
            }
        }
        return expanded.map { entry ->
            val demand = demands[entry.originalIndex]
            entry.copy(
                readingDemand = demand?.copy(
                    share = displayedLength(entry.token).toDouble() / sourceLengths.getValue(entry.originalIndex),
                )
            )
        }
    }

    fun displayedLength(token: Token): Int {
        val start = token.highlightStart
        val end = token.highlightEndExclusive
        val validHighlight = start != null && end != null && start >= 0 && end > start && end <= token.text.length
        val visible = if (token.isSubwordChunk && validHighlight) {
            token.text.substring(requireNotNull(start), requireNotNull(end))
        } else {
            token.text
        }
        return visible.codePoints().filter { Character.isLetterOrDigit(it) }.count().toInt().coerceAtLeast(1)
    }

    private fun decodingDemand(word: String, length: Int): Double {
        val vowelGroups = word.indices.count { index ->
            word[index] in "aeiouy" && (index == 0 || word[index - 1] !in "aeiouy")
        }
        // Vowel groups are an orthographic cue, not a claim about exact spoken syllables.
        val ending = word.removeSuffix("'s").removeSuffix("s")
        val silentEnding = ending.endsWith("e") && !ending.endsWith("le")
        val groups = (vowelGroups - if (silentEnding) 1 else 0).coerceAtLeast(1)
        return 0.14 * (length - 7).coerceAtLeast(0) + 0.08 * (groups - 3).coerceAtLeast(0)
    }

    private fun spellingFallback(word: String): Double {
        var run = 0
        var longest = 0
        for (letter in word) {
            run = if (letter in "aeiouy'") 0 else run + 1
            longest = maxOf(longest, run)
        }
        // Missing from a finite table is uncertainty, not proof that the word is maximally difficult.
        return (0.65 + (longest - 2).coerceAtLeast(0) * 0.06).coerceAtMost(0.9)
    }
}

internal object EnglishReadingFrequency {
    private val frequencies: Map<String, Int> by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/reading/english-frequency.tsv.gz"))
        GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.associate { line ->
                val separator = line.indexOf('\t')
                line.substring(0, separator) to line.substring(separator + 1).toInt()
            }
        }
    }

    fun zipf(word: String): Double? = frequencies[word]?.div(100.0)

    /** A familiar base form provides evidence for simple inflections, with a small recognition cost. */
    fun recognitionZipf(word: String): Double? {
        val candidates = buildList {
            zipf(word)?.let(::add)
            val bases = buildList {
                if (word.endsWith("'s")) add(word.dropLast(2))
                if (word.endsWith("s") && !word.endsWith("ss")) add(word.dropLast(1))
                if (word.endsWith("es")) add(word.dropLast(2))
                if (word.endsWith("ies")) add(word.dropLast(3) + "y")
            }
            bases.mapNotNull(::zipf).forEach { add(it - 0.15) }
        }
        return candidates.maxOrNull()
    }
}
