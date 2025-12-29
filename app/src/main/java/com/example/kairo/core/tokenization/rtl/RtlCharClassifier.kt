package com.example.kairo.core.tokenization.rtl

internal object RtlCharClassifier {
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

    fun isWordChar(codePoint: Int): Boolean =
        Character.isLetterOrDigit(codePoint) || isRtlLetter(codePoint)

    private fun isRtlLetter(codePoint: Int): Boolean =
        inRange(codePoint, 0x0590, 0x05FF) || // Hebrew
            inRange(codePoint, 0x0600, 0x06FF) || // Arabic
            inRange(codePoint, 0x0750, 0x077F) || // Arabic Supplement
            inRange(codePoint, 0x08A0, 0x08FF) || // Arabic Extended-A
            inRange(codePoint, 0xFB50, 0xFDFF) || // Arabic Presentation Forms-A
            inRange(codePoint, 0xFE70, 0xFEFF) // Arabic Presentation Forms-B

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
            '\u02BC'.code, // Modifier letter apostrophe
            '\u2019'.code, // Right single quote
            '\''.code,
            '\u0640'.code, // Arabic tatweel
        )

    private val EXTRA_PUNCTUATION =
        setOf(
            '\u060C'.code, // Arabic comma
            '\u061B'.code, // Arabic semicolon
            '\u061F'.code, // Arabic question mark
            '\u06D4'.code, // Arabic full stop
            '\u066A'.code, // Arabic percent
            '\u066B'.code, // Arabic decimal separator
            '\u066C'.code, // Arabic thousands separator
            '\u05BE'.code, // Hebrew maqaf
            '\u05C0'.code, // Hebrew paseq
            '\u05C3'.code, // Hebrew sof pasuq
            '\u05F3'.code, // Hebrew geresh
            '\u05F4'.code, // Hebrew gershayim
            '\u066D'.code, // Arabic five pointed star
            '\u0701'.code, // Syriac supralinear full stop
            '\u0702'.code, // Syriac sublinear full stop
            '\u201C'.code, // “
            '\u201D'.code, // ”
            '\u2018'.code, // ‘
            '\u2019'.code, // ’
        )
}
