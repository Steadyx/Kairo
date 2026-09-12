@file:Suppress("MagicNumber", "MatchingDeclarationName")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R

internal enum class ThemeStudioSection(val titleRes: Int) {
    PALETTES(R.string.theme_tab_palettes),
    COLOURS(R.string.theme_tab_colours),
    FONTS(R.string.theme_tab_fonts),
}

/** Preview and controls have independent layout space; adjusting a control never scrolls the preview away. */
@Composable
internal fun ThemeStudioWorkspace(
    modifier: Modifier,
    section: ThemeStudioSection,
    revision: Int,
    onSectionChange: (ThemeStudioSection) -> Unit,
    preview: @Composable () -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val previewWidth = maxWidth * 0.4f
        val previewHeight = minOf(maxHeight * 0.30f, 196.dp)
        if (maxWidth >= 640.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
                Column(Modifier.width(previewWidth).verticalScroll(rememberScrollState())) { preview() }
                Column(Modifier.weight(1f)) { StudioControls(section, revision, onSectionChange, controls) }
            }
        } else {
            Column {
                Column(Modifier.fillMaxWidth().height(previewHeight).verticalScroll(rememberScrollState()).padding(12.dp)) {
                    preview()
                }
                StudioControls(section, revision, onSectionChange, controls)
            }
        }
    }
}

@Composable
private fun ColumnScope.StudioControls(
    section: ThemeStudioSection,
    revision: Int,
    onSectionChange: (ThemeStudioSection) -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    val stateHolder = rememberSaveableStateHolder()
    SecondaryScrollableTabRow(selectedTabIndex = section.ordinal, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.surface) {
        ThemeStudioSection.entries.forEach { item ->
            Tab(selected = section == item, onClick = { onSectionChange(item) }, text = { Text(stringResource(item.titleRes)) })
        }
    }
    stateHolder.SaveableStateProvider("${section.name}:$revision") {
        Column(
            Modifier.weight(1f).fillMaxWidth().testTag("theme-controls").verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = controls,
        )
    }
}
