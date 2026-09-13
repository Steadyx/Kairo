package com.kairo.reader.ui.theme

import androidx.compose.ui.graphics.toArgb
import com.kairo.reader.core.model.CustomTheme
import com.kairo.reader.core.model.ReaderTheme

/** Pin editable colours and retain the preset's surfaces when starting a personal copy. */
internal fun ReaderTheme.copyAsCustom(existing: CustomTheme): CustomTheme {
    if (this == ReaderTheme.CUSTOM) return existing
    val scheme = materialColorScheme()
    return CustomTheme(
        background = scheme.background.toArgb(),
        harmony = existing.harmony,
        surface = scheme.surface.toArgb(),
        text = scheme.onBackground.toArgb(),
        primary = scheme.primary.toArgb(),
        secondary = scheme.secondary.toArgb(),
        tertiary = scheme.tertiary.toArgb(),
        sourcePreset = this,
    )
}
