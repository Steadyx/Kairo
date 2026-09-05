@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kairo.reader.R

@Composable
internal fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.settings_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, stringResource(R.string.settings_search_clear))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
}

@Composable
internal fun SettingsSearchResults(query: String, page: SettingsSearchPage? = null, onSelect: (SettingsSearchEntry) -> Unit) {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val index = remember(configuration) { buildSettingsSearchIndex(resources) }
    val results = remember(index, query, page) {
        searchSettings(index, query).filter { page == null || it.entry.page == page }
    }
    val focus = LocalFocusManager.current
    val listState = remember(query) { LazyListState() }
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (results.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_search_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_search_empty_description))
                }
            }
        }
        items(results, key = { it.entry.id }) { result ->
            Surface(
                onClick = {
                    focus.clearFocus()
                    onSelect(result.entry)
                },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(result.location, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(result.title, style = MaterialTheme.typography.titleMedium)
                    Text(result.description, style = MaterialTheme.typography.bodyMedium)
                    result.requirement?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

internal fun SettingsHomeActions.openSearchResult(entry: SettingsSearchEntry) {
    onOpenSearchResult?.let {
        it(entry)
        return
    }
    when (entry.page) {
        SettingsSearchPage.RSVP -> onOpenRsvp()
        SettingsSearchPage.READER -> onOpenReader()
        SettingsSearchPage.BIONIC -> onOpenBionic()
        SettingsSearchPage.FOCUS -> onOpenFocus()
        SettingsSearchPage.LANGUAGE -> onOpenLanguage()
        SettingsSearchPage.INFO -> onOpenInfo()
        SettingsSearchPage.UPDATES -> onCheckForUpdates()
        SettingsSearchPage.TUTORIAL -> onOpenStartingTutorial()
        SettingsSearchPage.RESET -> Unit // The home screen opens the confirmation dialog.
    }
}
