package com.kairo.reader.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keeps search available in full settings pages and their in-reading dialogs. */
@Composable
internal fun SearchableSettingsContent(
    page: SettingsSearchPage,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val inheritedTarget = LocalSettingsSearchTarget.current
    val target = selectedId?.let { id -> settingsSearchEntries.find { it.id == id } } ?: inheritedTarget
    BackHandler(query.isNotEmpty()) {
        if (selectedId != null) selectedId = null else query = ""
    }
    Column(modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSearchField(query) {
            query = it
            selectedId = null
        }
        if (query.isNotBlank() && selectedId == null) {
            SettingsSearchResults(query, page = page) { selectedId = it.id }
        } else {
            key(selectedId) {
                CompositionLocalProvider(LocalSettingsSearchTarget provides target) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { content() }
                }
            }
        }
    }
}
