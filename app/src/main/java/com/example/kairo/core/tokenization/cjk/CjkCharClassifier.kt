package com.example.kairo.core.tokenization.cjk

internal object CjkCharClassifier {
    fun isWhitespace(codePoint: Int): Boolean = Character.isWhitespace(codePoint)

    fun isPunctuation(codePoint: Int): Boolean {
        if (EXTRA_PUNCTUATION.contains(codePoint)) return true
        return when (Character.getType(codePoint)) {
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
            -> true
            else -> false
        }
    }

    fun isWordConnector(codePoint: Int): Boolean = WORD_CONNECTORS.contains(codePoint)

    fun isCombiningMark(codePoint: Int): Boolean =
        when (Character.getType(codePoint)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(),
            -> true
            else -> false
        }

    fun isLatinLike(codePoint: Int): Boolean {
        if (!Character.isLetterOrDigit(codePoint)) return false
        if (isCjk(codePoint)) return false
        if (isHangul(codePoint)) return false
        return true
    }

    fun isHangul(codePoint: Int): Boolean =
        inRange(codePoint, 0xAC00, 0xD7AF) || // Hangul syllables
            inRange(codePoint, 0x1100, 0x11FF) || // Hangul Jamo
            inRange(codePoint, 0x3130, 0x318F) || // Hangul Compatibility Jamo
            inRange(codePoint, 0xA960, 0xA97F) || // Hangul Jamo Extended-A
            inRange(codePoint, 0xD7B0, 0xD7FF) // Hangul Jamo Extended-B

    fun isCjk(codePoint: Int): Boolean =
        isHangul(codePoint) ||
            isKana(codePoint) ||
            isBopomofo(codePoint) ||
            isCjkIdeograph(codePoint)

    private fun isKana(codePoint: Int): Boolean =
        inRange(codePoint, 0x3040, 0x309F) || // Hiragana
            inRange(codePoint, 0x30A0, 0x30FF) || // Katakana
            inRange(codePoint, 0x31F0, 0x31FF) // Katakana Phonetic Extensions

    private fun isBopomofo(codePoint: Int): Boolean =
        inRange(codePoint, 0x3100, 0x312F) || // Bopomofo
            inRange(codePoint, 0x31A0, 0x31BF) // Bopomofo Extended

    private fun isCjkIdeograph(codePoint: Int): Boolean =
        inRange(codePoint, 0x3400, 0x4DBF) || // Extension A
            inRange(codePoint, 0x4E00, 0x9FFF) || // Unified Ideographs
            inRange(codePoint, 0xF900, 0xFAFF) || // Compatibility Ideographs
            inRange(codePoint, 0x20000, 0x2A6DF) || // Extension B
            inRange(codePoint, 0x2A700, 0x2B73F) || // Extension C
            inRange(codePoint, 0x2B740, 0x2B81F) || // Extension D
            inRange(codePoint, 0x2B820, 0x2CEAF) || // Extension E
            inRange(codePoint, 0x2CEB0, 0x2EBEF) || // Extension F
            inRange(codePoint, 0x30000, 0x3134F) || // Extension G
            inRange(codePoint, 0x2F800, 0x2FA1F) // Compatibility Ideographs Supplement

    private fun inRange(
        codePoint: Int,
        start: Int,
        end: Int,
    ): Boolean = codePoint in start..end

    private val WORD_CONNECTORS =
        setOf(
            '-'.code,
            '\u2010'.code, // Hyphen
            '\u2011'.code, // Non-breaking hyphen
            '\u2012'.code, // Figure dash
            '\u2013'.code, // En dash
            '\u2014'.code, // Em dash
            '\u2019'.code, // Right single quote
            '\''.code,
        )

    private val EXTRA_PUNCTUATION =
        setOf(
            '。'.code,
            '、'.code,
            '，'.code,
            '．'.code,
            '！'.code,
            '？'.code,
            '：'.code,
            '；'.code,
            '・'.code,
            '·'.code,
            '‧'.code,
            '｡'.code,
            '､'.code,
            '「'.code,
            '」'.code,
            '『'.code,
            '』'.code,
            '《'.code,
            '》'.code,
            '〈'.code,
            '〉'.code,
            '【'.code,
            '】'.code,
            '（'.code,
            '）'.code,
            '〔'.code,
            '〕'.code,
            '［'.code,
            '］'.code,
            '｛'.code,
            '｝'.code,
            '…'.code,
            '—'.code,
            '〜'.code,
            '～'.code,
            '※'.code,
            '•'.code,
        )
}
