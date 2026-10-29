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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
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
    state: ThemeStudioState,
    onSectionChange: (ThemeStudioSection) -> Unit,
    preview: @Composable () -> Unit,
    controls: @Composable ColumnScope.(ThemeStudioDraft) -> Unit,
) {
    val stateHolder = rememberSaveableStateHolder()
    val revision = state.revision
    LaunchedEffect(stateHolder, revision) {
        val currentKeys = ThemeStudioSection.entries.map { section -> "${section.name}-$revision" }.toSet()
        stateHolder.keys.filterNot { it in currentKeys }.forEach(stateHolder::removeState)
    }
    val currentSection by rememberUpdatedState(section)
    val currentState by rememberUpdatedState(state)
    val currentOnSectionChange by rememberUpdatedState(onSectionChange)
    val currentControls by rememberUpdatedState(controls)
    val movableControls = remember(stateHolder) {
        movableContentOf<ColumnScope> { scope ->
            with(scope) {
                StudioControls(currentSection, currentState, stateHolder, currentOnSectionChange, currentControls)
            }
        }
    }
    BoxWithConstraints(modifier) {
        val previewWidth = maxWidth * 0.4f
        val previewHeight = minOf(maxHeight * 0.40f, 244.dp)
        if (maxWidth >= 640.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
                Column(Modifier.width(previewWidth).verticalScroll(rememberScrollState())) { preview() }
                Column(Modifier.weight(1f)) { movableControls(this) }
            }
        } else {
            Column {
                Column(Modifier.fillMaxWidth().height(previewHeight).verticalScroll(rememberScrollState()).padding(12.dp)) {
                    preview()
                }
                movableControls(this)
            }
        }
    }
}

@Composable
private fun ColumnScope.StudioControls(
    section: ThemeStudioSection,
    state: ThemeStudioState,
    stateHolder: SaveableStateHolder,
    onSectionChange: (ThemeStudioSection) -> Unit,
    controls: @Composable ColumnScope.(ThemeStudioDraft) -> Unit,
) {
    val revision = state.revision
    val draft = state.draft
    key(revision) {
        SecondaryScrollableTabRow(
            selectedTabIndex = section.ordinal,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ThemeStudioSection.entries.forEach { item ->
                Tab(selected = section == item, onClick = { onSectionChange(item) }, text = { Text(stringResource(item.titleRes)) })
            }
        }
        stateHolder.SaveableStateProvider("${section.name}-$revision") {
            Column(
                Modifier.weight(1f).fillMaxWidth().testTag("theme-controls").verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { controls(draft) }
        }
    }
}
