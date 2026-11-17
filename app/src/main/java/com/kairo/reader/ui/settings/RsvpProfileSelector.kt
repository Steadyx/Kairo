package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpConfigConstraints
import com.kairo.reader.core.model.RsvpCustomProfile
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.RsvpProfileIds
import com.kairo.reader.core.model.defaultConfig
import com.kairo.reader.core.model.profileCadenceIdentity
import com.kairo.reader.core.rsvp.MILLISECONDS_PER_MINUTE
import com.kairo.reader.core.rsvp.RsvpSpeedControl.EXTREME_MIN_TEMPO_MS_PER_WORD
import kotlin.math.roundToInt

// The selector is a single modal form; extracting its tightly coupled validation state obscures the flow.
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RsvpProfileSelector(
    selectedProfileId: String,
    customProfiles: List<RsvpCustomProfile>,
    config: RsvpConfig,
    profileComparisonConfig: RsvpConfig,
    onSelectProfile: (String) -> Unit,
    onSaveCustomProfile: (String, RsvpConfig) -> Unit,
    onDeleteCustomProfile: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var saveName by rememberSaveable { mutableStateOf("") }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val builtInOptions = remember { RsvpProfile.entries.toList() }
    val selectionState =
        remember(selectedProfileId, customProfiles, profileComparisonConfig) {
            resolveRsvpProfileSelectionState(
                selectedProfileId = selectedProfileId,
                customProfiles = customProfiles,
                profileComparisonConfig = profileComparisonConfig,
            )
        }
    val effectiveSelectedProfileId = selectionState.effectiveSelectedProfileId
    val selectedBuiltIn = selectionState.selectedBuiltIn
    val selectedCustom = selectionState.selectedCustom
    val isCustomSelected =
        effectiveSelectedProfileId == RsvpProfileIds.CUSTOM_UNSAVED || selectedCustom != null
    val isUserProfileSelected = selectedCustom != null

    val selectedLabel =
        when {
            effectiveSelectedProfileId == RsvpProfileIds.CUSTOM_UNSAVED ->
                stringResource(R.string.rsvp_profile_custom)
            selectedBuiltIn != null -> stringResource(rsvpProfileNameRes(selectedBuiltIn))
            selectedCustom != null -> selectedCustom.name
            else -> stringResource(R.string.rsvp_profile_custom)
        }
    val selectedDescription =
        when {
            effectiveSelectedProfileId == RsvpProfileIds.CUSTOM_UNSAVED ->
                stringResource(R.string.rsvp_profile_unsaved_tweaks)
            selectedBuiltIn != null -> stringResource(rsvpProfileDescriptionRes(selectedBuiltIn))
            selectedCustom != null -> stringResource(R.string.rsvp_profile_saved_profile)
            else -> stringResource(R.string.rsvp_profile_unsaved_tweaks)
        }

    val title = stringResource(R.string.rsvp_profile_title)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.settingsSearchTarget(title),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(title) },
            supportingText = { Text(selectedDescription) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
            Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            stringResource(R.string.rsvp_profile_custom),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.rsvp_profile_unsaved_tweaks),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onSelectProfile(RsvpProfileIds.CUSTOM_UNSAVED)
                },
            )

            builtInOptions.forEach { option ->
                val optionId = RsvpProfileIds.builtIn(option)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                stringResource(rsvpProfileNameRes(option)),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(rsvpProfileDescriptionRes(option)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectProfile(optionId)
                    },
                )
            }

            if (customProfiles.isNotEmpty()) {
                customProfiles.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.rsvp_profile_saved_profile),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelectProfile(option.id)
                        },
                    )
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    val label = if (isCustomSelected) R.string.rsvp_profile_save_as else R.string.rsvp_profile_save_current
                    Text(stringResource(label))
                },
                onClick = {
                    expanded = false
                    saveName = selectedCustom?.name.orEmpty()
                    showSaveDialog = true
                },
            )
            if (isUserProfileSelected) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = {
                        expanded = false
                        showDeleteDialog = true
                    },
                )
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.rsvp_profile_save_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.rsvp_profile_name_label)) },
                    )
                    Text(
                        stringResource(R.string.rsvp_profile_save_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveCustomProfile(saveName, config.asProfileIdentityConfig())
                        showSaveDialog = false
                        saveName = ""
                    },
                    enabled = saveName.trim().isNotEmpty(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDeleteDialog && isUserProfileSelected) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.rsvp_profile_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.rsvp_profile_delete_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCustomProfile(selectedProfileId)
                        showDeleteDialog = false
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

internal data class RsvpProfileSelectionState(
    val effectiveSelectedProfileId: String,
    val selectedBuiltIn: RsvpProfile?,
    val selectedCustom: RsvpCustomProfile?,
)

internal fun resolveRsvpProfileSelectionState(
    selectedProfileId: String,
    customProfiles: List<RsvpCustomProfile>,
    profileComparisonConfig: RsvpConfig,
): RsvpProfileSelectionState {
    val selectedBuiltIn = RsvpProfileIds.parseBuiltIn(selectedProfileId)
    val selectedCustom = customProfiles.firstOrNull { it.id == selectedProfileId }
    val comparableConfig = profileComparisonConfig.asProfileIdentityConfig()
    val matchesSelectedProfile =
        when {
            selectedProfileId == RsvpProfileIds.CUSTOM_UNSAVED -> false
            selectedBuiltIn != null ->
                selectedBuiltIn.defaultConfig().asProfileIdentityConfig() == comparableConfig
            selectedCustom != null ->
                selectedCustom.config.asProfileIdentityConfig() == comparableConfig
            else -> false
        }
    return if (matchesSelectedProfile) {
        RsvpProfileSelectionState(
            effectiveSelectedProfileId = selectedProfileId,
            selectedBuiltIn = selectedBuiltIn,
            selectedCustom = selectedCustom,
        )
    } else {
        RsvpProfileSelectionState(
            effectiveSelectedProfileId = RsvpProfileIds.CUSTOM_UNSAVED,
            selectedBuiltIn = null,
            selectedCustom = null,
        )
    }
}

internal fun RsvpConfig.withLiveTempo(tempoMsPerWord: Long): RsvpConfig {
    val safeTempoMsPerWord = tempoMsPerWord.coerceAtLeast(EXTREME_MIN_TEMPO_MS_PER_WORD)
    return copy(
        tempoMsPerWord = safeTempoMsPerWord,
        baseWpm =
        (MILLISECONDS_PER_MINUTE / safeTempoMsPerWord.toDouble())
            .roundToInt()
            .coerceAtLeast(1),
    )
}

internal fun RsvpConfig.asProfileIdentityConfig(): RsvpConfig = profileCadenceIdentity()

internal fun coercePhraseChunkWordLimit(value: Int): Int =
    value.coerceIn(PHRASE_CHUNK_MIN_WORDS, PHRASE_CHUNK_MAX_WORDS)

internal fun percentToMultiplier(
    percent: Float,
    minValue: Double,
    maxValue: Double,
): Double =
    (percent.toDouble() / RsvpConfigConstraints.PERCENT_SCALE).coerceIn(minValue, maxValue)
