package com.kairo.reader.ui.reader

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp
import com.kairo.reader.R
import com.kairo.reader.core.model.RsvpConfigConstraints
import com.kairo.reader.core.model.SavedAnnotation
import com.kairo.reader.core.model.TimedReadingMode
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.shouldInsertSpaceBeforeToken
import com.kairo.reader.ui.saved.displayColor
import com.kairo.reader.ui.theme.LocalReaderFont
import com.kairo.reader.ui.theme.composeFontFamily
import com.kairo.reader.ui.theme.readingColor

// Rich text spans, focus semantics, and tap geometry must be built in one AnnotatedString layout pass.
@Suppress("LongMethod")
@Composable
internal fun ParagraphText(
    state: ParagraphTextState,
    actions: ParagraphTextActions,
) {
    val paragraph = state.paragraph
    val focusIndex = state.focusIndex
    val fontSizeSp = state.fontSizeSp
    val textBrightness = state.textBrightness
    val baseStyle =
        TextStyle(
            fontFamily = LocalReaderFont.current.composeFontFamily(),
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.5f).sp,
            color = readingColor(
                MaterialTheme.colorScheme.onBackground.copy(
                    alpha =
                    textBrightness.coerceIn(
                        RsvpConfigConstraints.MIN_TEXT_BRIGHTNESS.toFloat(),
                        RsvpConfigConstraints.MAX_TEXT_BRIGHTNESS.toFloat(),
                    ),
                )
            ),

        )
    val paragraphIndent =
        remember(fontSizeSp) {
            ParagraphStyle(
                textIndent = TextIndent(firstLine = (fontSizeSp * PARAGRAPH_INDENT_FACTOR).sp),
            )
        }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val focusStyle =
        remember(primary) {
            SpanStyle(
                fontWeight = FontWeight.Bold,
                color = primary.copy(alpha = 0.95f),
                background = primary.copy(alpha = 0.16f),
            )
        }
    val linkStyle =
        remember(tertiary) {
            SpanStyle(
                color = tertiary,
                textDecoration = TextDecoration.Underline,
            )
        }
    val localFocusIndex =
        remember(paragraph.startIndex, paragraph.tokens.size, focusIndex) {
            (focusIndex - paragraph.startIndex)
                .takeIf { localIndex -> localIndex in paragraph.tokens.indices }
                ?: NO_PARAGRAPH_FOCUS
        }

    val visualContent =
        remember(
            paragraph.tokens,
            paragraph.startIndex,
            localFocusIndex,
            focusStyle,
            linkStyle,
            paragraphIndent,
            state.nonInteractiveChapterLinkTargets,
            state.savedAnnotations,
            state.selectionRange,
            state.searchMatchRange,
        ) {
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

                    state.savedAnnotations
                        .firstOrNull { globalIndex in it.tokenRange }
                        ?.let { annotation ->
                            inlineHighlights.addOrExtendInlineHighlight(
                                key = "saved:${annotation.id}",
                                start = highlightStart,
                                endExclusive = end,
                                color = annotation.color.displayColor().copy(alpha = SAVED_HIGHLIGHT_ALPHA),
                            )
                        }

                    if (localIndex == localFocusIndex) addStyle(focusStyle, start, end)
                    if (state.searchMatchRange?.contains(globalIndex) == true) {
                        inlineHighlights.addOrExtendInlineHighlight(
                            key = SEARCH_HIGHLIGHT_KEY,
                            start = highlightStart,
                            endExclusive = end,
                            color = tertiary.copy(alpha = SEARCH_HIGHLIGHT_ALPHA),
                        )
                    }
                    if (state.selectionRange?.contains(globalIndex) == true) {
                        inlineHighlights.addOrExtendInlineHighlight(
                            key = SELECTION_HIGHLIGHT_KEY,
                            start = highlightStart,
                            endExclusive = end,
                            color = primary.copy(alpha = SELECTION_HIGHLIGHT_ALPHA),
                        )
                    }
                }
                addStyle(paragraphIndent, start = 0, end = length)
            }
            ReaderParagraphVisualContent(text, inlineHighlights)
        }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val currentAnnotated by rememberUpdatedState(visualContent.text)
    val currentFocusIndex by rememberUpdatedState(focusIndex)
    val currentActions by rememberUpdatedState(actions)
    val currentSelectionRange by rememberUpdatedState(state.selectionRange)
    val timedReadingModeLabel =
        stringResource(
            when (state.timedReadingMode) {
                TimedReadingMode.RSVP -> R.string.timed_reading_mode_rsvp
                TimedReadingMode.BIONIC -> R.string.timed_reading_mode_bionic
            },
        )
    val startTimedReadingActionLabel =
        stringResource(R.string.reader_start_timed_reading_action, timedReadingModeLabel)
    val startSelectionActionLabel = stringResource(R.string.reader_start_selection_action)
    val extendSelectionBackwardLabel = stringResource(R.string.reader_extend_selection_backward_action)
    val extendSelectionForwardLabel = stringResource(R.string.reader_extend_selection_forward_action)
    val cancelSelectionLabel = stringResource(R.string.reader_cancel_selection_action)

    Text(
        text = visualContent.text,
        style = baseStyle,
        modifier =
        Modifier
            .fillMaxWidth()
            .drawReaderInlineHighlights(
                layoutResult = { layoutResult },
                highlights = { visualContent.highlights },
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { position ->
                        val layout = layoutResult ?: return@detectTapGestures
                        val annotations = currentAnnotated
                        val offset = layout.getOffsetForPosition(position).coerceIn(
                            0,
                            (
                                annotations.length -
                                    1
                                ).coerceAtLeast(0)
                        )

                        // First check for chapter link tap
                        val linkHit = annotations.getStringAnnotations(
                            "chapterLink",
                            offset,
                            offset
                        ).firstOrNull()
                        val chapterSelected = currentActions.onChapterSelected
                        if (linkHit != null && chapterSelected != null) {
                            val chapterIndex = linkHit.item.toIntOrNull()
                            if (chapterIndex != null) {
                                chapterSelected(chapterIndex)
                                return@detectTapGestures
                            }
                        }

                        // Otherwise handle normal token tap
                        val hit =
                            annotations.getStringAnnotations(
                                "tokenIndex",
                                offset,
                                offset
                            ).firstOrNull()
                                ?: return@detectTapGestures
                        val tokenIndex = hit.item.toIntOrNull() ?: return@detectTapGestures
                        if (currentSelectionRange != null) {
                            currentActions.onSelectionExtend(tokenIndex)
                            return@detectTapGestures
                        }
                        if (tokenIndex ==
                            currentFocusIndex
                        ) {
                            currentActions.onStartTimedReading(tokenIndex)
                        } else {
                            currentActions.onFocusChange(tokenIndex)
                        }
                    },
                    onLongPress = { position ->
                        val layout = layoutResult ?: return@detectTapGestures
                        val annotations = currentAnnotated
                        val offset = layout.getOffsetForPosition(position).coerceIn(
                            0,
                            (
                                annotations.length -
                                    1
                                ).coerceAtLeast(0)
                        )
                        val hit =
                            annotations.getStringAnnotations(
                                "tokenIndex",
                                offset,
                                offset
                            ).firstOrNull()
                                ?: return@detectTapGestures
                        val tokenIndex = hit.item.toIntOrNull() ?: return@detectTapGestures
                        if (tokenIndex != currentFocusIndex) currentActions.onFocusChange(tokenIndex)
                        currentActions.onSelectionStart(tokenIndex)
                    },
                )
            }
            .onKeyEvent { event ->
                handleParagraphActivationKey(
                    event = event,
                    focusIndex = currentFocusIndex,
                    onStartTimedReading = currentActions.onStartTimedReading,
                )
            }
            .focusable()
            .semantics {
                role = Role.Button
                onClick(label = startTimedReadingActionLabel) {
                    if (currentFocusIndex >= 0) {
                        currentActions.onStartTimedReading(currentFocusIndex)
                        true
                    } else {
                        false
                    }
                }
                onLongClick(label = startSelectionActionLabel) {
                    if (currentFocusIndex >= 0) {
                        currentActions.onSelectionStart(currentFocusIndex)
                        true
                    } else {
                        false
                    }
                }
                val selectionRange = currentSelectionRange
                if (selectionRange != null) {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(extendSelectionBackwardLabel) {
                                val target =
                                    (selectionRange.first - 1).coerceAtLeast(paragraph.startIndex)
                                currentActions.onSelectionExtend(target)
                                true
                            },
                            CustomAccessibilityAction(extendSelectionForwardLabel) {
                                val target =
                                    (selectionRange.last + 1)
                                        .coerceAtMost(paragraph.startIndex + paragraph.tokens.lastIndex)
                                currentActions.onSelectionExtend(target)
                                true
                            },
                            CustomAccessibilityAction(cancelSelectionLabel) {
                                currentActions.onSelectionCancel()
                                true
                            },
                        )
                }
            },
        onTextLayout = { layoutResult = it },
    )
}

private fun handleParagraphActivationKey(
    event: KeyEvent,
    focusIndex: Int,
    onStartTimedReading: (Int) -> Unit,
): Boolean {
    val isActivationKey =
        event.key == Key.Enter ||
            event.key == Key.NumPadEnter ||
            event.key == Key.Spacebar ||
            event.key == Key.DirectionCenter
    if (!isActivationKey || focusIndex < 0) return false
    if (event.type == KeyEventType.KeyUp) onStartTimedReading(focusIndex)
    return true
}

internal data class ParagraphTextState(
    val paragraph: Paragraph,
    val focusIndex: Int,
    val fontSizeSp: Float,
    val textBrightness: Float,
    val timedReadingMode: TimedReadingMode,
    val nonInteractiveChapterLinkTargets: Set<Int> = emptySet(),
    val savedAnnotations: List<SavedAnnotation> = emptyList(),
    val selectionRange: IntRange? = null,
    val searchMatchRange: IntRange? = null,
)

internal data class ParagraphTextActions(
    val onFocusChange: (Int) -> Unit,
    val onStartTimedReading: (Int) -> Unit,
    val onChapterSelected: ((Int) -> Unit)? = null,
    val onSelectionStart: (Int) -> Unit = {},
    val onSelectionExtend: (Int) -> Unit = {},
    val onSelectionCancel: () -> Unit = {},
)

private const val PARAGRAPH_INDENT_FACTOR = 0.55f

private const val NO_PARAGRAPH_FOCUS = -1
private const val SAVED_HIGHLIGHT_ALPHA = 0.18f
private const val SEARCH_HIGHLIGHT_ALPHA = 0.18f
private const val SELECTION_HIGHLIGHT_ALPHA = 0.22f
private const val SEARCH_HIGHLIGHT_KEY = "search"
private const val SELECTION_HIGHLIGHT_KEY = "selection"
