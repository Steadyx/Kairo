package com.kairo.reader.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.shouldInsertSpaceBeforeToken
import com.kairo.reader.ui.saved.displayColor

/** Build spans and painted highlights together, preserving their shared token and character positions. */
@Suppress("LongMethod")
internal fun buildReaderParagraphVisualContent(
    state: ParagraphTextState,
    localFocusIndex: Int,
    spanStyles: ReaderSpanStyles,
    paragraphIndent: ParagraphStyle,
    spanContrast: ReaderSpanContrast?,
): ReaderParagraphVisualContent {
    val paragraph = state.paragraph
    val focusStyle = spanStyles.focus
    val linkStyle = spanStyles.link
    val primary = focusStyle.color
    val tertiary = linkStyle.color
    val inlineHighlights = mutableListOf<ReaderInlineHighlightRange>()
    val text = buildAnnotatedString {
        paragraph.tokens.forEachIndexed { localIndex, token ->
            if (token.type == TokenType.PARAGRAPH_BREAK ||
                token.type == TokenType.PAGE_BREAK
            ) {
                return@forEachIndexed
            }
            val globalIndex = paragraph.startIndex + localIndex

            val prevToken = if (localIndex > 0) paragraph.tokens[localIndex - 1] else null
            val needsSpaceBefore =
                shouldInsertSpaceBeforeToken(token, prevToken, localIndex)

            if (needsSpaceBefore) append(" ")

            val start = length
            val highlightStart = if (needsSpaceBefore) start - 1 else start
            append(token.text)
            val end = length

            addStringAnnotation(
                tag = "tokenIndex",
                annotation = globalIndex.toString(),
                start = start,
                end = end
            )

            // Add link annotation if token has a link
            val interactiveChapterLinkTarget =
                resolveInteractiveChapterLinkTarget(
                    token = token,
                    nonInteractiveTargets = state.nonInteractiveChapterLinkTargets,
                )
            if (interactiveChapterLinkTarget != null) {
                addStringAnnotation(
                    tag = "chapterLink",
                    annotation = interactiveChapterLinkTarget.toString(),
                    start = start,
                    end = end
                )
                addStyle(linkStyle, start, end)
            }

            val overlays = if (spanContrast != null) mutableListOf<Color>() else null
            state.savedAnnotations
                .firstOrNull { globalIndex in it.tokenRange }
                ?.let { annotation ->
                    overlays?.add(annotation.color.displayColor().copy(alpha = SAVED_HIGHLIGHT_ALPHA))
                    inlineHighlights.addOrExtendInlineHighlight(
                        key = "saved:${annotation.id}",
                        start = highlightStart,
                        endExclusive = end,
                        color = annotation.color.displayColor().copy(alpha = SAVED_HIGHLIGHT_ALPHA),
                    )
                }

            if (localIndex == localFocusIndex) addStyle(focusStyle, start, end)
            if (state.searchMatchRange?.contains(globalIndex) == true) {
                overlays?.add(tertiary.copy(alpha = SEARCH_HIGHLIGHT_ALPHA))
                inlineHighlights.addOrExtendInlineHighlight(
                    key = SEARCH_HIGHLIGHT_KEY,
                    start = highlightStart,
                    endExclusive = end,
                    color = tertiary.copy(alpha = SEARCH_HIGHLIGHT_ALPHA),
                )
            }
            if (state.selectionRange?.contains(globalIndex) == true) {
                overlays?.add(primary.copy(alpha = SELECTION_HIGHLIGHT_ALPHA))
                inlineHighlights.addOrExtendInlineHighlight(
                    key = SELECTION_HIGHLIGHT_KEY,
                    start = highlightStart,
                    endExclusive = end,
                    color = primary.copy(alpha = SELECTION_HIGHLIGHT_ALPHA),
                )
            }
            spanContrast?.style(overlays, spanStyles, localIndex == localFocusIndex, interactiveChapterLinkTarget != null)
                ?.let { addStyle(it, start, end) }
        }
        addStyle(paragraphIndent, start = 0, end = length)
    }
    return ReaderParagraphVisualContent(text, inlineHighlights)
}

private const val SAVED_HIGHLIGHT_ALPHA = 0.18f
private const val SEARCH_HIGHLIGHT_ALPHA = 0.18f
private const val SELECTION_HIGHLIGHT_ALPHA = 0.22f
private const val SEARCH_HIGHLIGHT_KEY = "search"
private const val SELECTION_HIGHLIGHT_KEY = "selection"
