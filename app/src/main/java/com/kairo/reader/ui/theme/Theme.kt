@file:Suppress("FunctionNaming", "LongMethod")

package com.kairo.reader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kairo.reader.core.model.ReaderTheme

@Composable
fun KairoTheme(
    readerTheme: ReaderTheme = ReaderTheme.SEPIA,
    content: @Composable () -> Unit,
) {
    val colorScheme = readerTheme.materialColorScheme()

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = KairoExpressiveShapes,
        typography = Typography,
        content = content,
    )
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

internal fun ReaderTheme.materialColorScheme(): ColorScheme =
    readerThemePalette().materialColorScheme()

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
