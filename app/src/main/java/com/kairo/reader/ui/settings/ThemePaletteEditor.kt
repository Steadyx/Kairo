@file:Suppress("MagicNumber", "MatchingDeclarationName")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.CustomTheme
import com.kairo.reader.ui.theme.materialColorScheme

internal enum class ThemeColorRole(val labelRes: Int) {
    BACKGROUND(R.string.theme_color_background),
    SURFACE(R.string.theme_color_surface),
    TEXT(R.string.theme_color_text),
    PRIMARY(R.string.theme_color_primary),
    SECONDARY(R.string.theme_color_secondary),
    TERTIARY(R.string.theme_color_tertiary),
}

internal fun CustomTheme.roleValue(role: ThemeColorRole): Int? = when (role) {
    ThemeColorRole.BACKGROUND -> background
    ThemeColorRole.SURFACE -> surface
    ThemeColorRole.TEXT -> text
    ThemeColorRole.PRIMARY -> primary
    ThemeColorRole.SECONDARY -> secondary
    ThemeColorRole.TERTIARY -> tertiary
}

internal fun CustomTheme.withRole(role: ThemeColorRole, value: Int?): CustomTheme = when (role) {
    ThemeColorRole.BACKGROUND -> copy(background = value ?: background)
    ThemeColorRole.SURFACE -> copy(surface = value)
    ThemeColorRole.TEXT -> copy(text = value)
    ThemeColorRole.PRIMARY -> copy(primary = value)
    ThemeColorRole.SECONDARY -> copy(secondary = value)
    ThemeColorRole.TERTIARY -> copy(tertiary = value)
}

@Composable
internal fun ThemePaletteEditor(theme: CustomTheme, onValidityChange: (Boolean) -> Unit, onChange: (CustomTheme) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(ThemeColorRole.BACKGROUND) }
    val scheme = remember(theme) { theme.materialColorScheme() }
    val colors = listOf(scheme.background, scheme.surface, scheme.onBackground, scheme.primary, scheme.secondary, scheme.tertiary)
    LazyRow(Modifier.fillMaxWidth().testTag("theme-role-list"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(ThemeColorRole.entries, key = { _, role -> role.name }) { index, role ->
            val stateLabel = stringResource(if (theme.roleValue(role) == null) R.string.theme_auto else R.string.theme_pinned)
            Column(
                Modifier.semantics { stateDescription = stateLabel }.selectable(editing == role, role = Role.RadioButton, onClick = {
                    editing =
                        role
                }).padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    Modifier.size(48.dp),
                    shape = CircleShape,
                    color = colors[index],
                    border = BorderStroke(if (editing == role) 3.dp else 1.dp, MaterialTheme.colorScheme.outline)
                ) {}
                Text(stringResource(role.labelRes), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    val automatic = theme.roleValue(editing) == null
    key(editing, automatic, theme.harmony) {
        ThemeColorEditor(
            role = editing,
            initial = theme.roleValue(editing) ?: colors[editing.ordinal].toArgb(),
            automatic = automatic,
            onValidityChange = onValidityChange,
            onChange = { onChange(theme.withRole(editing, it)) },
        )
    }
}
