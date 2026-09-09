package com.kairo.reader.ui.rsvp

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.RsvpFontWeight
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun RsvpInlineContext(
    preceding: List<AnnotatedString>,
    following: List<AnnotatedString>,
    focusOffsetPx: Float,
    focusMeasurement: TextLayoutResult,
    focusStyle: TextStyle,
    focusHeight: Dp,
    lineWidthPx: Float,
) {
    RsvpInlineContextCue(
        candidates = preceding,
        focusEdgePx = (focusOffsetPx + focusMeasurement.getLineLeft(0)).coerceAtLeast(0f),
        focusBaselinePx = focusMeasurement.firstBaseline,
        focusStyle = focusStyle,
        focusHeight = focusHeight,
    )
    RsvpInlineContextCue(
        candidates = following,
        focusEdgePx = focusOffsetPx + focusMeasurement.getLineRight(0),
        focusBaselinePx = focusMeasurement.firstBaseline,
        focusStyle = focusStyle,
        focusHeight = focusHeight,
        following = true,
        lineWidthPx = lineWidthPx,
    )
}

/** The measured focus owns its space; the cue can only use the room left over. */
@Composable
private fun RsvpInlineContextCue(
    candidates: List<AnnotatedString>,
    focusEdgePx: Float,
    focusBaselinePx: Float,
    focusStyle: TextStyle,
    focusHeight: Dp,
    following: Boolean = false,
    lineWidthPx: Float = 0f,
) {
    val density = LocalDensity.current
    val gap = with(density) { INLINE_CONTEXT_GAP.toPx() }
    val edge = if (following) kotlin.math.ceil(focusEdgePx + gap).toInt() else floor(focusEdgePx - gap).toInt()
    val available = (if (following) floor(lineWidthPx).toInt() - edge else edge).coerceAtLeast(0)
    if (candidates.isEmpty() || available == 0) return
    val textMeasurer = rememberTextMeasurer()
    val fontSize = inlineContextFontSizeSp(focusStyle.fontSize.value)
    val style = focusStyle.copy(fontSize = fontSize.sp, lineHeight = (fontSize * ORP_TEXT_LINE_HEIGHT_MULTIPLIER).sp)
    val measured = remember(candidates, style, textMeasurer) {
        candidates.map { text ->
            text to textMeasurer.measure(text, style, overflow = TextOverflow.Clip, softWrap = false, maxLines = 1)
        }
    }
    val fullCue = measured.firstOrNull { it.second.size.width <= available }
    val minimumPreviewWidth = with(density) { fontSize.sp.toPx() * INLINE_CONTEXT_MIN_PREVIEW_EM }
    val cue = fullCue?.first ?: candidates.lastOrNull()?.takeIf { following && available >= minimumPreviewWidth } ?: return
    val overflow = if (fullCue == null) TextOverflow.Ellipsis else TextOverflow.Clip
    val measurement = fullCue?.second ?: textMeasurer.measure(
        cue,
        style,
        overflow = overflow,
        softWrap = false,
        maxLines = 1,
        constraints = Constraints(maxWidth = available),
    )
    Layout(
        modifier = Modifier.fillMaxWidth().height(focusHeight).clipToBounds(),
        content = {
            Text(
                cue,
                style = style,
                maxLines = 1,
                softWrap = false,
                overflow = overflow,
                modifier = Modifier.clipToBounds().testTag(if (following) "rsvp-following-cue" else "rsvp-inline-cue")
            )
        },
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(Constraints(maxWidth = available, maxHeight = constraints.maxHeight))
        val top = (focusBaselinePx - measurement.firstBaseline).roundToInt().coerceAtLeast(0)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(if (following) edge else available - placeable.width, top)
        }
    }
}

@Composable
internal fun RsvpPausedPhrase(
    text: AnnotatedString,
    fontSizeSp: Float,
    fontFamily: RsvpFontFamily,
    fontWeight: RsvpFontWeight,
) {
    val style = rememberRsvpContextTextStyle(stableContextCueFontSizeSp(fontSizeSp), fontFamily, fontWeight)
    Text(
        text = text,
        style = style,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
    )
}

private val INLINE_CONTEXT_GAP = 12.dp
internal fun inlineContextFontSizeSp(focusFontSizeSp: Float): Float =
    (focusFontSizeSp * INLINE_CONTEXT_FONT_SCALE).coerceIn(INLINE_CONTEXT_MIN_SP, INLINE_CONTEXT_MAX_SP).coerceAtMost(focusFontSizeSp)

private const val INLINE_CONTEXT_FONT_SCALE = 0.75f
private const val INLINE_CONTEXT_MIN_PREVIEW_EM = 1.5f

private const val INLINE_CONTEXT_MIN_SP = 20f
private const val INLINE_CONTEXT_MAX_SP = 36f
