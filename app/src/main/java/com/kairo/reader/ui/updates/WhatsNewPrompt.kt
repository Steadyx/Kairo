package com.kairo.reader.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.BuildConfig
import com.kairo.reader.R
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.data.preferences.PreferencesRepository
import kotlinx.coroutines.launch

@Composable
internal fun WhatsNewPrompt(
    preferences: UserPreferences,
    repository: PreferencesRepository,
    canShow: Boolean,
    onStartTour: () -> Unit,
    onViewChangelog: () -> Unit,
) {
    val release = releaseNotes.first()
    val scope = rememberCoroutineScope()
    var dismissed by rememberSaveable { mutableStateOf(false) }

    // New installs get the starting tutorial only, even after completing it in this session.
    LaunchedEffect(Unit) {
        if (!preferences.hasSeenStartingTutorial && preferences.lastSeenReleaseVersionCode == 0) {
            dismissed = true
            repository.acknowledgeRelease(BuildConfig.VERSION_CODE)
        }
    }

    fun acknowledge(action: () -> Unit = {}) {
        dismissed = true
        scope.launch { repository.acknowledgeRelease(BuildConfig.VERSION_CODE) }
        action()
    }

    val eligible = shouldOfferReleaseTour(
        release.versionCode,
        BuildConfig.VERSION_CODE,
        preferences.lastSeenReleaseVersionCode,
        preferences.hasSeenStartingTutorial,
    )
    if (canShow && eligible && !dismissed) {
        ReleaseInvitationDialog(
            version = requireNotNull(release.version),
            hasTour = release.tour.isNotEmpty(),
            onSkip = { acknowledge() },
            onStartTour = { acknowledge(onStartTour) },
            onViewChangelog = { acknowledge(onViewChangelog) },
        )
    }
}

@Composable
internal fun ReleaseInvitationDialog(
    version: String,
    hasTour: Boolean,
    onSkip: () -> Unit,
    onStartTour: () -> Unit,
    onViewChangelog: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(stringResource(R.string.whats_new_invitation_title, version)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.whats_new_invitation_body))
                if (hasTour) {
                    TextButton(onClick = onViewChangelog) {
                        Text(stringResource(R.string.whats_new_view_changelog))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = if (hasTour) onStartTour else onViewChangelog) {
                Text(stringResource(if (hasTour) R.string.whats_new_take_tour else R.string.whats_new_view_changelog))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.action_skip)) }
        },
    )
}
