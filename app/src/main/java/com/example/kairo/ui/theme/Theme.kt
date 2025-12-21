@file:Suppress("FunctionNaming", "LongMethod")

package com.example.kairo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.kairo.core.model.ReaderTheme

@Composable
fun KairoTheme(
    readerTheme: ReaderTheme = ReaderTheme.SEPIA,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when (readerTheme) {
            ReaderTheme.DARK ->
                darkColorScheme(
                    primary = DeepPurple,
                    secondary = SoftLavender,
                    background = DarkBackground,
                    surface = DarkBackground,
                    surfaceVariant = DarkBackground.copy(alpha = 0.85f),
                    onBackground = DarkOnBackground,
                    onSurface = DarkOnBackground,
                    onSurfaceVariant = DarkOnBackground.copy(alpha = 0.75f),
                    primaryContainer = DeepPurple.copy(alpha = 0.35f),
                    onPrimaryContainer = DarkOnBackground,
                    onPrimary = DarkBackground,
                )

            ReaderTheme.NORD ->
                darkColorScheme(
                    primary = NordPrimary,
                    secondary = NordSecondary,
                    background = NordBackground,
                    surface = NordBackground,
                    surfaceVariant = NordSurfaceVariant,
                    onBackground = NordOnBackground,
                    onSurface = NordOnBackground,
                    onSurfaceVariant = NordOnSurfaceVariant,
                    primaryContainer = NordPrimaryContainer,
                    onPrimaryContainer = NordOnPrimaryContainer,
                    onPrimary = NordBackground,
                )

            ReaderTheme.CYBERPUNK ->
                darkColorScheme(
                    primary = CyberpunkPrimary,
                    secondary = CyberpunkSecondary,
                    background = CyberpunkBackground,
                    surface = CyberpunkBackground,
                    surfaceVariant = CyberpunkSurfaceVariant,
                    onBackground = CyberpunkOnBackground,
                    onSurface = CyberpunkOnBackground,
                    onSurfaceVariant = CyberpunkOnSurfaceVariant,
                    primaryContainer = CyberpunkPrimaryContainer,
                    onPrimaryContainer = CyberpunkOnPrimaryContainer,
                    onPrimary = CyberpunkBackground,
                )

            ReaderTheme.FOREST ->
                darkColorScheme(
                    primary = ForestPrimary,
                    secondary = ForestSecondary,
                    background = ForestBackground,
                    surface = ForestBackground,
                    surfaceVariant = ForestSurfaceVariant,
                    onBackground = ForestOnBackground,
                    onSurface = ForestOnBackground,
                    onSurfaceVariant = ForestOnSurfaceVariant,
                    primaryContainer = ForestPrimaryContainer,
                    onPrimaryContainer = ForestOnPrimaryContainer,
                    onPrimary = ForestBackground,
                )

            ReaderTheme.LIGHT ->
                lightColorScheme(
                    primary = DeepPurple,
                    secondary = SoftLavender,
                    background = LightBackground,
                    surface = LightBackground,
                    surfaceVariant = LightBackground.copy(alpha = 0.92f),
                    onBackground = LightOnBackground,
                    onSurface = LightOnBackground,
                    onSurfaceVariant = LightOnBackground.copy(alpha = 0.7f),
                    primaryContainer = DeepPurple.copy(alpha = 0.15f),
                    onPrimaryContainer = LightOnBackground,
                    onPrimary = LightBackground,
                )

            ReaderTheme.SEPIA ->
                lightColorScheme(
                    primary = DeepPurple,
                    secondary = SoftLavender,
                    background = SepiaBackground,
                    surface = SepiaBackground,
                    surfaceVariant = SepiaBackground.copy(alpha = 0.92f),
                    onBackground = SepiaOnBackground,
                    onSurface = SepiaOnBackground,
                    onSurfaceVariant = SepiaOnBackground.copy(alpha = 0.7f),
                    primaryContainer = DeepPurple.copy(alpha = 0.12f),
                    onPrimaryContainer = SepiaOnBackground,
                    onPrimary = SepiaBackground,
                )
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
