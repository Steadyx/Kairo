package com.example.kairo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.kairo.core.model.ReaderTheme

@Composable
fun KairoTheme(
    readerTheme: ReaderTheme = ReaderTheme.SEPIA,
    content: @Composable () -> Unit
) {
    val colorScheme = when (readerTheme) {
        ReaderTheme.DARK -> darkColorScheme(
            primary = DeepPurple,
            secondary = SoftLavender,
            background = DarkBackground,
            surface = DarkBackground,
            onBackground = DarkOnBackground,
            onSurface = DarkOnBackground
        )

        ReaderTheme.LIGHT -> lightColorScheme(
            primary = DeepPurple,
            secondary = SoftLavender,
            background = LightBackground,
            surface = LightBackground,
            onBackground = LightOnBackground,
            onSurface = LightOnBackground
        )

        ReaderTheme.SEPIA -> lightColorScheme(
            primary = DeepPurple,
            secondary = SoftLavender,
            background = SepiaBackground,
            surface = SepiaBackground,
            onBackground = SepiaOnBackground,
            onSurface = SepiaOnBackground
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
