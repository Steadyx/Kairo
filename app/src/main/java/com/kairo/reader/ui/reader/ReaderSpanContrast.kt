package com.kairo.reader.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.SpanStyle
import com.kairo.reader.ui.theme.contrastSafeReadingColor

/** Matches drawing order: saved highlight, search, selection, then the focused word's span. */
internal fun readerSpanBackground(page: Color, overlays: List<Color>): Color =
    overlays.fold(page) { background, overlay -> overlay.compositeOver(background) }

internal fun readerSpanColor(text: Color, page: Color, overlays: List<Color>): Color =
    contrastSafeReadingColor(text, readerSpanBackground(page, overlays))

/** A paragraph can contain many tokens but only a handful of distinct highlight combinations. */
internal class ReaderSpanContrast(private val page: Color) {
    private val colors = mutableMapOf<Pair<Color, List<Color>>, Color>()

    fun color(text: Color, overlays: List<Color>): Color {
        if (colors.size >= MAX_CACHED_SPANS) colors.clear()
        return colors.getOrPut(text to overlays) { readerSpanColor(text, page, overlays) }
    }
}

private const val MAX_CACHED_SPANS = 128

internal data class ReaderSpanStyles(val body: Color, val focus: SpanStyle, val link: SpanStyle)

internal fun ReaderSpanContrast.style(
    overlays: MutableList<Color>?,
    styles: ReaderSpanStyles,
    focused: Boolean,
    linked: Boolean,
): SpanStyle? {
    if (overlays == null || (overlays.isEmpty() && !focused)) return null
    val requested = when {
        focused -> {
            overlays.add(styles.focus.background)
            styles.focus.color
        }
        linked -> styles.link.color
        else -> styles.body
    }
    return SpanStyle(color = color(requested, overlays))
}
