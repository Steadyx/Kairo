package com.kairo.reader.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairo.reader.R
import com.kairo.reader.core.model.LibrarySearchResult
import com.kairo.reader.core.model.LibrarySearchResultKind
import com.kairo.reader.data.search.LibrarySearchConstraints
import com.kairo.reader.data.search.LibrarySearchState
import com.kairo.reader.data.search.normalizeLibrarySearchQuery

@Composable
fun LibrarySearchOverlay(
    title: String,
    hint: String,
    state: LibrarySearchState,
    initialQuery: String = "",
    onQuery: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenResult: (LibrarySearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val cappedInitialQuery = remember(initialQuery) { initialQuery.take(LibrarySearchConstraints.MAX_QUERY_LENGTH) }
    var query by rememberSaveable { mutableStateOf(cappedInitialQuery) }
    val cancelAndDismiss = {
        onQuery("")
        onDismiss()
    }
    LaunchedEffect(query) {
        onQuery(query)
    }
    Dialog(
        onDismissRequest = cancelAndDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = cancelAndDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(LibrarySearchConstraints.MAX_QUERY_LENGTH) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(hint) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions =
                    androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { onQuery(query) },
                    ),
                )
                SearchResults(
                    query = query,
                    state = state,
                    onRetry = onRetry,
                    onOpenResult = onOpenResult,
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    state: LibrarySearchState,
    onRetry: () -> Unit,
    onOpenResult: (LibrarySearchResult) -> Unit,
) {
    val normalizedQuery = normalizeLibrarySearchQuery(query)
    when {
        normalizedQuery.length < LibrarySearchConstraints.MIN_QUERY_LENGTH ->
            SearchMessage(stringResource(R.string.search_minimum_hint))
        (state is LibrarySearchState.Loading && state.query == normalizedQuery) ||
            (state is LibrarySearchState.Success && state.query == normalizedQuery && state.isSearching && state.results.isEmpty()) ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        state is LibrarySearchState.Error && state.query == normalizedQuery ->
            SearchError(onRetry)
        state is LibrarySearchState.Success && state.query == normalizedQuery && state.results.isEmpty() ->
            SearchMessage(stringResource(R.string.search_no_results))
        state is LibrarySearchState.Success && state.query == normalizedQuery ->
            Column {
                if (state.isSearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                SearchResultList(state.results, onOpenResult)
            }
        else -> SearchMessage(stringResource(R.string.search_minimum_hint))
    }
}

@Composable
private fun SearchResultList(
    results: List<LibrarySearchResult>,
    onOpenResult: (LibrarySearchResult) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results, key = { it.id }) { result ->
            SearchResultRow(result, onOpenResult)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun SearchError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.search_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun SearchMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchResultRow(
    result: LibrarySearchResult,
    onOpenResult: (LibrarySearchResult) -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable { onOpenResult(result) }
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = stringResource(result.kind.labelResource()),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = result.bookTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = result.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (result.snippet.isNotBlank()) {
            Text(
                text = result.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun LibrarySearchResultKind.labelResource(): Int =
    when (this) {
        LibrarySearchResultKind.BOOK -> R.string.search_book_label
        LibrarySearchResultKind.PASSAGE -> R.string.search_passage_label
        LibrarySearchResultKind.SAVED -> R.string.search_saved_label
    }
