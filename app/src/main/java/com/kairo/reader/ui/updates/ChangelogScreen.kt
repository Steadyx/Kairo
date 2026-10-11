package com.kairo.reader.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.ui.settings.SettingsScaffold

@Composable
internal fun ChangelogScreen(onBack: () -> Unit, onStartTour: () -> Unit) {
    SettingsScaffold(title = stringResource(R.string.whats_new_title), onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier.padding(horizontal = 16.dp).testTag("changelog"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.whats_new_subtitle), style = MaterialTheme.typography.bodyLarge)
                    if (releaseNotes.first().tour.isNotEmpty()) {
                        Button(onClick = onStartTour) { Text(stringResource(R.string.whats_new_take_tour)) }
                    }
                }
            }
            items(releaseNotes, key = { it.version ?: "upcoming" }) { release ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = release.version?.let { stringResource(R.string.whats_new_version, it) }
                                ?: stringResource(R.string.whats_new_upcoming),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        release.changes.forEach { change ->
                            Text(stringResource(change), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
