@file:Suppress("FunctionNaming", "LongMethod")

package com.kairo.reader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kairo.reader.core.model.CustomTheme
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.RsvpFontFamily

internal val LocalCustomTheme = staticCompositionLocalOf { CustomTheme() }
internal val LocalReaderFont = staticCompositionLocalOf { RsvpFontFamily.MERRIWEATHER }
internal val LocalInterfaceFont = staticCompositionLocalOf { RsvpFontFamily.SYSTEM_SANS }

@Composable
fun KairoTheme(
    readerTheme: ReaderTheme = ReaderTheme.SEPIA,
    customTheme: CustomTheme = LocalCustomTheme.current,
    readerFont: RsvpFontFamily = LocalReaderFont.current,
    interfaceFont: RsvpFontFamily = LocalInterfaceFont.current,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(readerTheme, customTheme) {
        if (readerTheme == ReaderTheme.CUSTOM) customTheme.materialColorScheme() else readerTheme.materialColorScheme()
    }
    val type = remember(interfaceFont) { Typography.withFont(interfaceFont.composeFontFamily()) }

    CompositionLocalProvider(
        LocalCustomTheme provides customTheme,
        LocalProtectReadingContrast provides (readerTheme == ReaderTheme.CUSTOM),
        LocalReaderFont provides readerFont,
        LocalInterfaceFont provides interfaceFont,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = KairoExpressiveShapes,
            typography = type,
            content = content,
        )
    }
}

@Composable
internal fun KairoFocusedReadingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        motionScheme = MotionScheme.standard(),
        shapes = KairoFocusedReadingShapes,
        typography = FocusedReadingTypography,
        content = content,
    )
}

internal fun ReaderTheme.materialColorScheme(customTheme: CustomTheme = CustomTheme()): ColorScheme =
    if (this == ReaderTheme.CUSTOM) customTheme.materialColorScheme() else readerThemePalette().materialColorScheme()

private fun ReaderThemePalette.materialColorScheme(): ColorScheme =
    if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onPrimary,
            secondaryContainer = surfaceContainerHighest,
            onSecondaryContainer = onSurfaceVariant,
            tertiary = tertiary,
            onTertiary = onPrimary,
            tertiaryContainer = surfaceContainerHighest,
            onTertiaryContainer = onSurfaceVariant,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onBackground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = Color.Transparent,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color.Black,
            surfaceBright = surfaceBright,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceDim = surfaceDim,
            primaryFixed = primaryFixed,
            primaryFixedDim = primaryFixedDim,
            onPrimaryFixed = onPrimaryFixed,
            onPrimaryFixedVariant = onPrimaryFixedVariant,
            secondaryFixed = secondaryFixed,
            secondaryFixedDim = secondaryFixedDim,
            onSecondaryFixed = onSecondaryFixed,
            onSecondaryFixedVariant = onSecondaryFixedVariant,
            tertiaryFixed = tertiaryFixed,
            tertiaryFixedDim = tertiaryFixedDim,
            onTertiaryFixed = onTertiaryFixed,
            onTertiaryFixedVariant = onTertiaryFixedVariant,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onPrimary,
            secondaryContainer = surfaceContainerHighest,
            onSecondaryContainer = onSurfaceVariant,
            tertiary = tertiary,
            onTertiary = onPrimary,
            tertiaryContainer = surfaceContainerHighest,
            onTertiaryContainer = onSurfaceVariant,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onBackground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = Color.Transparent,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color.Black,
            surfaceBright = surfaceBright,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceDim = surfaceDim,
            primaryFixed = primaryFixed,
            primaryFixedDim = primaryFixedDim,
            onPrimaryFixed = onPrimaryFixed,
            onPrimaryFixedVariant = onPrimaryFixedVariant,
            secondaryFixed = secondaryFixed,
            secondaryFixedDim = secondaryFixedDim,
            onSecondaryFixed = onSecondaryFixed,
            onSecondaryFixedVariant = onSecondaryFixedVariant,
            tertiaryFixed = tertiaryFixed,
            tertiaryFixedDim = tertiaryFixedDim,
            onTertiaryFixed = onTertiaryFixed,
            onTertiaryFixedVariant = onTertiaryFixedVariant,
        )
    }
