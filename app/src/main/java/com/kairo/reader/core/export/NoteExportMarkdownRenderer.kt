package com.kairo.reader.core.export

class NoteExportMarkdownRenderer(private val localization: NoteExportLocalization,) {
    fun render(document: NoteExportDocument): String =
        buildString {
            append("# ")
            append(escapeInline(localization.documentTitle(document.scope)))
            append("\n\n")
            append("**")
            append(escapeInline(localization.exportedOn(localization.formatDate(document.generatedAt))))
            append("**\n\n")
            append(escapeInline(localization.contentsSummary(document.noteCount, document.sources.size)))
            append("\n")
            document.sources.forEach { source ->
                append("\n## ")
                append(escapeInline(source.title))
                append("\n\n")
                append("**")
                append(escapeInline(localization.authorLabel))
                append(":** ")
                append(
                    if (source.authors.isEmpty()) {
                        escapeInline(localization.unknownAuthor)
                    } else {
                        source.authors.joinToString(", ") { escapeInline(it) }
                    },
                )
                append("\n")
                source.entries.forEachIndexed { index, entry ->
                    append("\n### ")
                    append(
                        escapeInline(
                            entry.chapterTitle
                                ?: localization.chapterFallback(entry.chapterNumber),
                        ),
                    )
                    append("\n\n**")
                    append(escapeInline(localization.noteLabel))
                    append("**\n\n")
                    append(escapeMultiline(entry.note))
                    append("\n\n**")
                    append(escapeInline(localization.passageLabel))
                    append("**\n\n")
                    appendQuoted(entry.passage)
                    append("\n\n**")
                    append(escapeInline(localization.createdLabel))
                    append(":** ")
                    append(escapeInline(localization.formatDate(entry.createdAt)))
                    if (entry.updatedAt != entry.createdAt) {
                        append("  \n**")
                        append(escapeInline(localization.updatedLabel))
                        append(":** ")
                        append(escapeInline(localization.formatDate(entry.updatedAt)))
                    }
                    append("\n")
                    if (index != source.entries.lastIndex) append("\n---\n")
                }
            }
        }.normalizeLineEndings()

    private fun StringBuilder.appendQuoted(text: String) {
        val lines = text.normalizeLineEndings().split('\n')
        lines.forEachIndexed { index, line ->
            append("> ")
            append(escapeInline(line))
            if (index != lines.lastIndex) append('\n')
        }
    }

    private fun escapeMultiline(text: String): String =
        text.normalizeLineEndings().split('\n').joinToString("\n") { escapeInline(it) }

    private fun escapeInline(text: String): String {
        val normalized = text.normalizeLineEndings().replace('\n', ' ')
        return buildString(normalized.length) {
            normalized.forEach { character ->
                when (character) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '|', '~', '=' -> {
                        append('\\')
                        append(character)
                    }
                    else -> append(character)
                }
            }
        }
    }

    private fun String.normalizeLineEndings(): String = replace("\r\n", "\n").replace('\r', '\n')
}
