@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.ui.tutorial.StartingTutorialOverlay
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import com.kairo.reader.ui.tutorial.StartingTutorialTargetIds
import com.kairo.reader.ui.tutorial.startingTutorialTarget
import kotlinx.coroutines.flow.first

data class SettingsHomeActions(
    val onOpenRsvp: () -> Unit,
    val onOpenBionic: () -> Unit,
    val onOpenReader: () -> Unit,
    val onOpenFocus: () -> Unit,
    val onOpenInfo: () -> Unit,
    val onCheckForUpdates: () -> Unit,
    val onOpenLanguage: () -> Unit,
    val onOpenStartingTutorial: () -> Unit,
    val onReset: () -> Unit,
    val onClose: () -> Unit,
)

data class SettingsTutorialActions(val onNext: () -> Unit = {}, val onPrevious: () -> Unit = {}, val onSkip: () -> Unit = {},)

@Composable
fun SettingsHomeScreen(
    actions: SettingsHomeActions,
    tutorialState: StartingTutorialOverlayState? = null,
    tutorialActions: SettingsTutorialActions = SettingsTutorialActions(),
) {
    val context = LocalContext.current
    val languageLabel = resolveLanguageLabel(context, getAppLanguageTag())
    val tutorialTargets = remember { mutableStateMapOf<String, Rect>() }
    val tutorialTargetRequesters =
        remember {
            mapOf(
                StartingTutorialTargetIds.SETTINGS_LANGUAGE to BringIntoViewRequester(),
                StartingTutorialTargetIds.SETTINGS_RSVP to BringIntoViewRequester(),
                StartingTutorialTargetIds.SETTINGS_READER to BringIntoViewRequester(),
                StartingTutorialTargetIds.SETTINGS_FOCUS to BringIntoViewRequester(),
                StartingTutorialTargetIds.SETTINGS_TUTORIAL to BringIntoViewRequester(),
            )
        }
    val activeTutorialTargetId = tutorialState?.step?.targetId
    var showResetConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(activeTutorialTargetId) {
        val targetId = activeTutorialTargetId ?: return@LaunchedEffect
        val targetRequester = tutorialTargetRequesters[targetId] ?: return@LaunchedEffect
        snapshotFlow { tutorialTargets[targetId] }.first { it != null }
        targetRequester.bringIntoView()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.displaySmallEmphasized,
            )
            SettingsHomeRows(actions, languageLabel, tutorialTargets, tutorialTargetRequesters)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showResetConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_reset_defaults))
            }
            Button(onClick = actions.onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_done))
            }
        }
        tutorialState?.let { overlayState ->
            StartingTutorialOverlay(
                state = overlayState,
                targetBounds = overlayState.step.targetId?.let(tutorialTargets::get),
                onNext = tutorialActions.onNext,
                onPrevious = tutorialActions.onPrevious,
                onSkip = tutorialActions.onSkip,
            )
        }
        if (showResetConfirmation) {
            ResetSettingsDialog(
                onConfirm = {
                    showResetConfirmation = false
                    actions.onReset()
                },
                onDismiss = { showResetConfirmation = false },
            )
        }
    }
}

@Composable
private fun SettingsHomeRows(
    actions: SettingsHomeActions,
    languageLabel: String,
    tutorialTargets: MutableMap<String, Rect>,
    tutorialTargetRequesters: Map<String, BringIntoViewRequester>,
) {
    val rows =
        listOf(
            SettingsHomeRow(
                title = stringResource(R.string.settings_language_title),
                subtitle = languageLabel,
                icon = Icons.Default.Language,
                targetId = StartingTutorialTargetIds.SETTINGS_LANGUAGE,
                onClick = actions.onOpenLanguage,
            ),
            SettingsHomeRow(
                title = stringResource(R.string.rsvp_settings_title),
                subtitle = stringResource(R.string.settings_rsvp_subtitle),
                icon = Icons.Default.Settings,
                targetId = StartingTutorialTargetIds.SETTINGS_RSVP,
                onClick = actions.onOpenRsvp,
            ),
            SettingsHomeRow(
                title = stringResource(R.string.bionic_settings_title),
                subtitle = stringResource(R.string.settings_bionic_subtitle),
                icon = Icons.Default.AutoStories,
                onClick = actions.onOpenBionic,
            ),
            SettingsHomeRow(
                title = stringResource(R.string.reader_settings_title),
                subtitle = stringResource(R.string.reader_settings_subtitle),
                icon = Icons.Default.Settings,
                targetId = StartingTutorialTargetIds.SETTINGS_READER,
                onClick = actions.onOpenReader,
            ),
            SettingsHomeRow(
                title = stringResource(R.string.focus_settings_title),
                subtitle = stringResource(R.string.focus_settings_subtitle),
                icon = Icons.Default.Settings,
                targetId = StartingTutorialTargetIds.SETTINGS_FOCUS,
                onClick = actions.onOpenFocus,
            ),
            SettingsHomeRow(
                title = stringResource(R.string.update_check_title),
                subtitle = stringResource(R.string.update_check_subtitle),
                icon = Icons.Default.Refresh,
                onClick = actions.onCheckForUpdates,
            ),
            SettingsHomeRow(
                title = stringResource(R.string.info_settings_title),
                subtitle = stringResource(R.string.info_settings_subtitle),
                icon = Icons.Default.Info,
                onClick = actions.onOpenInfo,
            ),
            SettingsHomeRow(
                title = stringResource(R.string.settings_starting_tutorial_title),
                subtitle = stringResource(R.string.settings_starting_tutorial_subtitle),
                icon = Icons.Default.Info,
                targetId = StartingTutorialTargetIds.SETTINGS_TUTORIAL,
                onClick = actions.onOpenStartingTutorial,
            ),
        )
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { index, row ->
            val rowModifier =
                row.targetId?.let { targetId ->
                    Modifier.captureTutorialTarget(
                        targetId = targetId,
                        targets = tutorialTargets,
                        requester = tutorialTargetRequesters.getValue(targetId),
                    )
                } ?: Modifier
            SettingsSegmentedNavRow(
                index = index,
                count = rows.size,
                title = row.title,
                subtitle = row.subtitle,
                icon = row.icon,
                onClick = row.onClick,
                modifier = rowModifier,
            )
        }
    }
}

private data class SettingsHomeRow(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val targetId: String? = null,
    val onClick: () -> Unit,
)

private fun Modifier.captureTutorialTarget(
    targetId: String,
    targets: MutableMap<String, Rect>,
    requester: BringIntoViewRequester,
): Modifier =
    bringIntoViewRequester(requester)
        .startingTutorialTarget(targetId) { resolvedId, bounds ->
            targets[resolvedId] = bounds
        }

@Composable
private fun ResetSettingsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
        text = { Text(stringResource(R.string.settings_reset_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_reset_defaults))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
