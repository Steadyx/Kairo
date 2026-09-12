@file:Suppress("MagicNumber")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.reader.R
import com.kairo.reader.core.model.ColorHarmony
import com.kairo.reader.core.model.CustomTheme
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.ui.theme.composeFontFamily
import com.kairo.reader.ui.theme.materialColorScheme

@Composable
internal fun ThemePresetSelector(selected: ReaderTheme, custom: CustomTheme, onSelect: (ReaderTheme) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.theme_palette),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.settingsSearchTarget(stringResource(R.string.theme_palette))
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ReaderTheme.entries, key = { it.name }) { theme ->
                val scheme = remember(theme, custom) {
                    if (theme ==
                        ReaderTheme.CUSTOM
                    ) {
                        custom.materialColorScheme()
                    } else {
                        theme.materialColorScheme()
                    }
                }
                Surface(
                    modifier = Modifier.width(112.dp).selectable(theme == selected, role = Role.RadioButton, onClick = { onSelect(theme) }),
                    color = scheme.background,
                    contentColor = scheme.onBackground,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        if (theme ==
                            selected
                        ) {
                            3.dp
                        } else {
                            1.dp
                        },
                        if (theme == selected) scheme.primary else scheme.outlineVariant
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Aa", fontSize = 30.sp, color = scheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach {
                                Box(Modifier.size(12.dp).background(it, CircleShape))
                            }
                        }
                        Text(stringResource(readerThemeLabelRes(theme)), style = MaterialTheme.typography.labelMedium, minLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HarmonySelector(selected: ColorHarmony, onSelect: (ColorHarmony) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorHarmony.entries.forEach { harmony ->
            FilterChip(selected = harmony == selected, onClick = {
                onSelect(harmony)
            }, label = { Text(stringResource(harmony.labelRes())) })
        }
    }
    Text(
        stringResource(selected.descriptionRes()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

internal fun ColorHarmony.labelRes(): Int = when (this) {
    ColorHarmony.TONAL -> R.string.theme_harmony_tonal
    ColorHarmony.ANALOGOUS -> R.string.theme_harmony_analogous
    ColorHarmony.COMPLEMENTARY -> R.string.theme_harmony_complementary
    ColorHarmony.TRIADIC -> R.string.theme_harmony_triadic
}

private fun ColorHarmony.descriptionRes(): Int = when (this) {
    ColorHarmony.TONAL -> R.string.theme_harmony_tonal_help
    ColorHarmony.ANALOGOUS -> R.string.theme_harmony_analogous_help
    ColorHarmony.COMPLEMENTARY -> R.string.theme_harmony_complementary_help
    ColorHarmony.TRIADIC -> R.string.theme_harmony_triadic_help
}

@Composable
internal fun ThemeFontSelector(title: String, selected: RsvpFontFamily, onSelect: (RsvpFontFamily) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.settingsSearchTarget(title)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(rsvpFontFamilyLabelRes(selected)), fontFamily = selected.composeFontFamily())
                    Text("Aa", fontFamily = selected.composeFontFamily(), fontSize = 22.sp)
                }
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                RsvpFontFamily.entries.forEach { family ->
                    DropdownMenuItem(
                        text = { Text(stringResource(rsvpFontFamilyLabelRes(family)), fontFamily = family.composeFontFamily()) },
                        onClick = {
                            onSelect(family)
                            expanded = false
                        },
                        modifier = Modifier.selectable(family == selected, role = Role.RadioButton, onClick = {
                            onSelect(family)
                            expanded =
                                false
                        }),
                    )
                }
            }
        }
    }
}
