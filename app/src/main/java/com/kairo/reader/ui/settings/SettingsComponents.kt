@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "MatchingDeclarationName", "MaxLineLength")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.ui.theme.readerThemePalette

enum class SettingsNavRowPresentation {
    COMPACT,
    PROMINENT,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSegmentedNavRow(
    index: Int,
    count: Int,
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes =
        ListItemDefaults.segmentedShapes(
            index = index,
            count = count,
            defaultShapes =
            ListItemDefaults.shapes(
                shape = MaterialTheme.shapes.large,
                pressedShape = MaterialTheme.shapes.medium,
            ),
        ),
        colors =
        ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            leadingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            trailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 76.dp),
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        supportingContent =
        subtitle?.takeIf(String::isNotBlank)?.let { supportingText ->
            {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    ) {
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
    }
}

@Composable
fun SettingsNavRow(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    showChevron: Boolean = true,
    presentation: SettingsNavRowPresentation = SettingsNavRowPresentation.COMPACT,
    onClick: () -> Unit,
) {
    when (presentation) {
        SettingsNavRowPresentation.COMPACT ->
            CompactSettingsNavRow(
                modifier = modifier.settingsSearchTarget(title),
                title = title,
                subtitle = subtitle,
                icon = icon,
                showChevron = showChevron,
                onClick = onClick,
            )

        SettingsNavRowPresentation.PROMINENT ->
            ProminentSettingsNavRow(
                modifier = modifier.settingsSearchTarget(title),
                title = title,
                subtitle = subtitle,
                icon = icon,
                showChevron = showChevron,
                onClick = onClick,
            )
    }
}

@Composable
private fun CompactSettingsNavRow(
    modifier: Modifier,
    title: String,
    subtitle: String?,
    icon: ImageVector,
    showChevron: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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

@Composable
private fun ProminentSettingsNavRow(
    modifier: Modifier,
    title: String,
    subtitle: String?,
    icon: ImageVector,
    showChevron: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier =
            Modifier
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    maxContentWidth: Dp = 720.dp,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactHeight = maxHeight < 480.dp
        val navigationIcon: @Composable () -> Unit = {
            if (onBack != null) {
                androidx.compose.material3.FilledTonalIconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        }
        Scaffold(
            topBar = {
                if (compactHeight) {
                    TopAppBar(title = { Text(title) }, navigationIcon = navigationIcon)
                } else {
                    MediumFlexibleTopAppBar(title = { Text(title) }, navigationIcon = navigationIcon)
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.surface,
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                content(Modifier.widthIn(max = maxContentWidth).fillMaxSize())
            }
        }
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
        Modifier.settingsSearchTarget(title)
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
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
    steps: Int = 0,
) {
    Column(modifier = Modifier.settingsSearchTarget(title).padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            valueLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = { onValueChangeFinished?.invoke() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ThemeSelector(
    selected: ReaderTheme,
    onThemeChange: (ReaderTheme) -> Unit,
) {
    Column(modifier = Modifier.settingsSearchTarget(stringResource(R.string.reader_theme_title)).padding(vertical = 8.dp)) {
        Text(stringResource(R.string.reader_theme_title), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        val themes =
            listOf(
                ReaderTheme.LIGHT,
                ReaderTheme.LINEN,
                ReaderTheme.MIST,
                ReaderTheme.SAGE,
                ReaderTheme.SEPIA,
                ReaderTheme.DARK,
                ReaderTheme.INK,
                ReaderTheme.PLUM,
                ReaderTheme.EMBER,
                ReaderTheme.NORD,
                ReaderTheme.FOREST,
                ReaderTheme.CYBERPUNK,
            )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(themes, key = { it.name }) { theme ->
                val isSelected = theme == selected
                val (previewBg, previewAccent) = rememberThemePreview(theme)
                val themeLabel = stringResource(readerThemeLabelRes(theme))
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
                            text = themeLabel,
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
private fun rememberThemePreview(theme: ReaderTheme): Pair<Color, Color> {
    val palette = theme.readerThemePalette()
    return palette.background to palette.primary
}

private fun readerThemeLabelRes(theme: ReaderTheme): Int =
    when (theme) {
        ReaderTheme.LIGHT -> R.string.reader_theme_light
        ReaderTheme.LINEN -> R.string.reader_theme_linen
        ReaderTheme.MIST -> R.string.reader_theme_mist
        ReaderTheme.SAGE -> R.string.reader_theme_sage
        ReaderTheme.SEPIA -> R.string.reader_theme_sepia
        ReaderTheme.DARK -> R.string.reader_theme_dark
        ReaderTheme.INK -> R.string.reader_theme_ink
        ReaderTheme.PLUM -> R.string.reader_theme_plum
        ReaderTheme.EMBER -> R.string.reader_theme_ember
        ReaderTheme.NORD -> R.string.reader_theme_nord
        ReaderTheme.CYBERPUNK -> R.string.reader_theme_cyberpunk
        ReaderTheme.FOREST -> R.string.reader_theme_forest
    }
