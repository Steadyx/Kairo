package com.kairo.reader.core.export

object NoteExportFileNames {
    const val MAX_BASENAME_CODE_POINTS = 80

    fun suggestedFileName(
        scope: NoteExportScope,
        format: NoteExportFormat,
        date: String,
        sourceTitle: String?,
        allNotesTitle: String,
        singleNoteLabel: String,
    ): String {
        val rawName =
            when (scope) {
                NoteExportScope.All -> "$allNotesTitle $date"
                is NoteExportScope.Book ->
                    listOfNotNull(
                        sourceTitle?.takeIf(String::isNotBlank),
                        date,
                    ).joinToString(" - ")
                is NoteExportScope.Single ->
                    listOfNotNull(
                        sourceTitle?.takeIf(String::isNotBlank),
                        singleNoteLabel,
                        date,
                    ).joinToString(" - ")
            }
        val fallback = "$allNotesTitle $date"
        return "${sanitizeBasename(rawName, fallback)}.${format.extension}"
    }

    fun sanitizeBasename(
        rawName: String,
        fallback: String,
        maxCodePoints: Int = MAX_BASENAME_CODE_POINTS,
    ): String {
        require(maxCodePoints > 0) { "The filename limit must be positive" }
        val cleaned = clean(rawName, maxCodePoints)
        if (cleaned.isNotEmpty()) return cleaned
        return clean(fallback, maxCodePoints).ifEmpty { DEFAULT_FALLBACK }
    }

    private fun clean(
        value: String,
        maxCodePoints: Int,
    ): String {
        val output = StringBuilder(value.length)
        var previousWasWhitespace = false
        value.codePoints().forEach { codePoint ->
            when {
                isBidiControl(codePoint) || Character.isISOControl(codePoint) -> Unit
                isUnsafeSeparator(codePoint) -> appendSpace(output, previousWasWhitespace).also {
                    previousWasWhitespace = it
                }
                Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) ->
                    appendSpace(output, previousWasWhitespace).also { previousWasWhitespace = it }
                else -> {
                    output.appendCodePoint(codePoint)
                    previousWasWhitespace = false
                }
            }
        }
        val trimmed = output.toString().trim().trimEnd('.', ' ')
        if (trimmed.isEmpty()) return ""
        val limited =
            if (trimmed.codePointCount(0, trimmed.length) <= maxCodePoints) {
                trimmed
            } else {
                trimmed.substring(0, trimmed.offsetByCodePoints(0, maxCodePoints))
            }
        return limited.trimEnd('.', ' ')
    }

    private fun appendSpace(
        output: StringBuilder,
        previousWasWhitespace: Boolean,
    ): Boolean {
        if (!previousWasWhitespace && output.isNotEmpty()) output.append(' ')
        return true
    }

    private fun isUnsafeSeparator(codePoint: Int): Boolean =
        codePoint == '/'.code ||
            codePoint == '\\'.code ||
            codePoint == ':'.code ||
            codePoint == '<'.code ||
            codePoint == '>'.code ||
            codePoint == '"'.code ||
            codePoint == '|'.code ||
            codePoint == '?'.code ||
            codePoint == '*'.code

    private fun isBidiControl(codePoint: Int): Boolean =
        codePoint in BIDI_EMBEDDING_START..BIDI_EMBEDDING_END ||
            codePoint in BIDI_ISOLATE_START..BIDI_ISOLATE_END ||
            codePoint == LEFT_TO_RIGHT_MARK ||
            codePoint == RIGHT_TO_LEFT_MARK

    private const val LEFT_TO_RIGHT_MARK = 0x200E
    private const val RIGHT_TO_LEFT_MARK = 0x200F
    private const val BIDI_EMBEDDING_START = 0x202A
    private const val BIDI_EMBEDDING_END = 0x202E
    private const val BIDI_ISOLATE_START = 0x2066
    private const val BIDI_ISOLATE_END = 0x2069
    private const val DEFAULT_FALLBACK = "Kairo"
}
