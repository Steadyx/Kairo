@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "MaxLineLength")

package com.example.kairo.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.ui.theme.CyberpunkBackground
import com.example.kairo.ui.theme.CyberpunkPrimary
import com.example.kairo.ui.theme.DarkBackground
import com.example.kairo.ui.theme.DeepPurple
import com.example.kairo.ui.theme.ForestBackground
import com.example.kairo.ui.theme.ForestPrimary
import com.example.kairo.ui.theme.LightBackground
import com.example.kairo.ui.theme.NordBackground
import com.example.kairo.ui.theme.NordPrimary
import com.example.kairo.ui.theme.SepiaBackground

@Composable
fun SettingsNavRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    onBack: (() -> Unit)?,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        content(
            Modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .padding(innerPadding),
        )
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun SettingsSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    subtitle: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(84.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                onValueChangeFinished = { onValueChangeFinished?.invoke() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun ThemeSelector(
    selected: ReaderTheme,
    onThemeChange: (ReaderTheme) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Reader theme", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        val themes =
            listOf(
                ReaderTheme.DARK,
                ReaderTheme.NORD,
                ReaderTheme.CYBERPUNK,
                ReaderTheme.FOREST,
                ReaderTheme.SEPIA,
                ReaderTheme.LIGHT,
            )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(themes, key = { it.name }) { theme ->
                val isSelected = theme == selected
                val (previewBg, previewAccent) = rememberThemePreview(theme)
                Surface(
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onThemeChange(theme) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = if (isSelected) 0.65f else 0.45f
                    ),
                    border =
                    BorderStroke(
                        1.dp,
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = 0.45f,
                            )
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(previewBg),
                            )
                            Box(
                                modifier =
                                Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(previewAccent),
                            )
                        }
                        // Label
                        Text(
                            text = theme.displayName(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberThemePreview(theme: ReaderTheme): Pair<Color, Color> =
    when (theme) {
        ReaderTheme.LIGHT -> LightBackground to DeepPurple
        ReaderTheme.SEPIA -> SepiaBackground to DeepPurple
        ReaderTheme.DARK -> DarkBackground to DeepPurple
        ReaderTheme.NORD -> NordBackground to NordPrimary
        ReaderTheme.CYBERPUNK -> CyberpunkBackground to CyberpunkPrimary
        ReaderTheme.FOREST -> ForestBackground to ForestPrimary
    }

private fun ReaderTheme.displayName(): String =
    when (this) {
        ReaderTheme.LIGHT -> "Light"
        ReaderTheme.SEPIA -> "Sepia"
        ReaderTheme.DARK -> "Dark"
        ReaderTheme.NORD -> "Nord"
        ReaderTheme.CYBERPUNK -> "Cyberpunk"
        ReaderTheme.FOREST -> "Forest"
    }
