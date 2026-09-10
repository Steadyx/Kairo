package com.kairo.reader.ui.rsvp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal data class RsvpContextWindow(val startIndex: Int, val endExclusive: Int, val focusStartIndex: Int, val focusEndExclusive: Int,)

internal data class RsvpReadingContext(
    val precedingCues: List<AnnotatedString>,
    val pausedPhrase: AnnotatedString,
    val followingCues: List<AnnotatedString> = emptyList(),
)

internal data class RsvpSentenceTickerContent(
    val text: AnnotatedString,
    val pivotPosition: Int,
    val focusStart: Int,
    val focusEndExclusive: Int,
    val displayedFocusStart: Int,
    val displayedFocusEndExclusive: Int,
)

internal data class ContextTickerFocusAlignment(val startOffset: Int, val endExclusiveOffset: Int, val pivotOffset: Int,)

/** Keep the focus fixed. Fuller context occupies available space only while paused. */
@Composable
internal fun RsvpContextStage(
    verticalBias: Float,
    modifier: Modifier = Modifier,
    pausedContext: @Composable () -> Unit = {},
    focus: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxSize().clipToBounds(),
        content = {
            Box(Modifier.fillMaxWidth().clipToBounds().testTag("rsvp-focus-row")) { focus() }
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).clipToBounds().testTag("rsvp-paused-context")) {
                pausedContext()
            }
        },
    ) { measurables, constraints ->
        val focusRow = measurables[0].measure(constraints.copy(minHeight = 0))
        val gap = CONTEXT_ROW_GAP.roundToPx()
        val focusY = (
            (constraints.maxHeight - focusRow.height) *
                ((verticalBias.coerceIn(VERTICAL_BIAS_MIN, VERTICAL_BIAS_MAX) + 1f) / 2f)
            ).roundToInt()
        val above = (focusY - gap).coerceAtLeast(0)
        val below = (constraints.maxHeight - focusY - focusRow.height - gap).coerceAtLeast(0)
        val panel = measurables[1].measure(constraints.copy(minHeight = 0, maxHeight = maxOf(above, below)))
        layout(constraints.maxWidth, constraints.maxHeight) {
            focusRow.placeRelative(0, focusY)
            if (panel.height <= below) {
                panel.placeRelative(0, focusY + focusRow.height + gap)
            } else if (panel.height <= above) {
                panel.placeRelative(0, focusY - gap - panel.height)
            }
        }
    }
}

private val CONTEXT_ROW_GAP = 16.dp
