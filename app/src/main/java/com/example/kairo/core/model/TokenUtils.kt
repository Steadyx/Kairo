@file:Suppress("MagicNumber")

package com.example.kairo.core.model

import com.example.kairo.core.linguistics.WordAnalyzer
import kotlin.math.max

/**
 * Calculates the Optimal Recognition Point (ORP) index for a word.
 *
 * The ORP is the character position where the eye naturally focuses for
 * fastest word recognition. Research shows this is typically:
 * - 25-35% into the word for common words (eyes recognize them faster)
 * - Slightly earlier (20-25%) for rare/complex words (need more processing)
 *
 * This implementation uses word frequency to fine-tune the pivot position.
 */
fun calculateOrpIndex(word: String): Int {
    val length = word.length
    if (length <= 2) return 0

    // Get word frequency (common words can have later pivot)
    val frequency = WordAnalyzer.getFrequencyScore(word)

    // Base ORP position (traditional algorithm)
    val baseOrp =
        when {
            length <= 5 -> 1
            length <= 9 -> 2
            length <= 13 -> 3
            else -> 4
        }

    // Adjust based on frequency:
    // - Very common words (freq > 0.8): pivot can be 1 position later
    // - Rare words (freq < 0.3): keep base or go 1 earlier for longer words
    val adjustment =
        when {
            frequency > 0.8 && length > 5 -> 1 // Common words: slightly later pivot
            frequency < 0.3 && length > 8 -> -1 // Rare long words: earlier pivot
            else -> 0
        }

    return (baseOrp + adjustment).coerceIn(0, length - 1)
}

/**
 * Alternative ORP calculation that considers syllable structure.
 * Places pivot at the vowel of the most stressed syllable (approximated).
 */
@Suppress("unused")
fun calculateOrpIndexAdvanced(word: String): Int {
    val length = word.length
    if (length <= 2) return 0

    val lower = word.lowercase()
    val vowels = setOf('a', 'e', 'i', 'o', 'u')

    // Find vowel positions
    val vowelPositions = lower.indices.filter { lower[it] in vowels }

    // For most English words, stress is often on first or second syllable
    // Use the first vowel in the optimal recognition zone (20-35% into word)
    val optimalStart = (length * 0.2).toInt()
    val optimalEnd = (length * 0.35).toInt()

    // Find first vowel in or near optimal zone
    val optimalVowel =
        if (vowelPositions.isEmpty()) {
            length / 3 // No vowels, use 1/3 position
        } else {
            vowelPositions.firstOrNull { it in optimalStart..optimalEnd }
                ?: vowelPositions.firstOrNull { it <= optimalEnd }
                ?: vowelPositions.first()
        }

    return optimalVowel.coerceIn(0, length - 1)
}

fun isSentenceEndingPunctuation(char: Char): Boolean =
    char == '.' || char == '!' || char == '?' || char == '\u2026' // Include ellipsis …

fun isMidSentencePunctuation(char: Char): Boolean =
    char == ',' ||
        char == ';' ||
        char == ':' ||
        char == '\u2014' ||
        char == '\u2013' ||
        // Em-dash — and en-dash –
        char == ')' ||
        char == ']' ||
        char == '}' // Closing brackets get a slight pause

fun normalizeWhitespace(input: String): String =
    input
        .split("\n")
        .joinToString("\n") { line ->
            line.trim().replace(Regex("\\s+"), " ")
        }.trim()

@Suppress("unused")
fun calculatePause(
    type: TokenType,
    config: RsvpConfig,
): Long =
    when (type) {
        TokenType.PARAGRAPH_BREAK -> config.paragraphPauseMs
        TokenType.PAGE_BREAK -> max(
            config.paragraphPauseMs * 2,
            config.sentenceEndPauseMs + (config.paragraphPauseMs / 2)
        )
        TokenType.PUNCTUATION -> max(60L, (config.paragraphPauseMs * 0.4).toLong())
        TokenType.WORD -> 0L
    }

/**
 * Resolves a focus/position index to the nearest WORD token.
 *
 * If the token at [fromIndex] is already a word, returns it.
 * Otherwise searches outward, preferring the next word ahead of the index.
 * Falls back to 0 when no words are present.
 */
fun List<Token>.nearestWordIndex(fromIndex: Int): Int {
    if (isEmpty()) return 0
    val clamped = fromIndex.coerceIn(0, lastIndex)
    var resolved = if (this[clamped].type == TokenType.WORD) clamped else null

    if (resolved == null) {
        var offset = 1
        while (offset <= lastIndex && resolved == null) {
            val forward = clamped + offset
            if (forward <= lastIndex && this[forward].type == TokenType.WORD) {
                resolved = forward
            } else {
                val backward = clamped - offset
                if (backward >= 0 && this[backward].type == TokenType.WORD) {
                    resolved = backward
                }
            }
            offset += 1
        }
    }

    return resolved ?: 0
}

/**
 * Splits a hyphenated word token into multiple tokens for RSVP display.
 * For example, "over-telling" becomes ["over-", "telling"].
 * The hyphen stays attached to the preceding part for natural reading.
 *
 * Non-hyphenated tokens are returned as a single-element list.
 */
fun splitHyphenatedToken(token: Token): List<Token> {
    // Only split WORD tokens that contain hyphens
    val shouldSplit = token.type == TokenType.WORD && token.text.contains('-')

    // Don't split leading hyphens used as numeric signs (e.g., "-35c", "-10", "-3.14").
    // Those should be treated as a single unit for RSVP.
    val startsWithNumericDash =
        token.text.length > 1 &&
            token.text[0] == '-' &&
            token.text[1].isDigit()

    val parts = token.text.split('-')
    val canSplit = shouldSplit && !startsWithNumericDash && parts.size > 1

    if (!canSplit) return listOf(token)

    return parts.mapIndexed { index, part ->
        val isLast = index == parts.lastIndex
        // Attach hyphen to each part except the last
        val text = if (isLast) part else "$part-"

        Token(
            text = text,
            type = TokenType.WORD,
            orpIndex = calculateOrpIndex(part), // Calculate ORP for the word part (without hyphen)
            syllableCount = WordAnalyzer.countSyllables(part),
            frequencyScore = WordAnalyzer.getFrequencyScore(part),
            complexityMultiplier = WordAnalyzer.getComplexityMultiplier(part),
            isClauseBoundary = if (isLast) token.isClauseBoundary else false,
            isDialogue = token.isDialogue,
        )
    }
}
