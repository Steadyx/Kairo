package com.kairo.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

internal val LocalProtectReadingContrast = staticCompositionLocalOf { false }

/** Resolve intentional opacity against its actual background before enforcing normal-text AA contrast. */
internal fun contrastSafeReadingColor(requested: Color, background: Color): Color =
    contrastingColor(requested.compositeOver(background), listOf(background))

@Composable
internal fun readingColor(requested: Color, background: Color = MaterialTheme.colorScheme.background): Color {
    val protect = LocalProtectReadingContrast.current
    return remember(requested, background, protect) {
        if (protect) contrastSafeReadingColor(requested, background) else requested
    }
}
