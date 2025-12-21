@file:Suppress("FunctionNaming", "LongParameterList")

package com.example.kairo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.kairo.core.model.UserPreferences

@Composable
fun FocusSettingsScreen(
    preferences: UserPreferences,
    onFocusModeEnabledChange: (Boolean) -> Unit,
    onFocusHideStatusBarChange: (Boolean) -> Unit,
    onFocusPauseNotificationsChange: (Boolean) -> Unit,
    onFocusApplyInReaderChange: (Boolean) -> Unit,
    onFocusApplyInRsvpChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    SettingsScaffold(title = "Focus settings", onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FocusSettingsContent(
                focusModeEnabled = preferences.focusModeEnabled,
                focusHideStatusBar = preferences.focusHideStatusBar,
                focusPauseNotifications = preferences.focusPauseNotifications,
                focusApplyInReader = preferences.focusApplyInReader,
                focusApplyInRsvp = preferences.focusApplyInRsvp,
                onFocusModeEnabledChange = onFocusModeEnabledChange,
                onFocusHideStatusBarChange = onFocusHideStatusBarChange,
                onFocusPauseNotificationsChange = onFocusPauseNotificationsChange,
                onFocusApplyInReaderChange = onFocusApplyInReaderChange,
                onFocusApplyInRsvpChange = onFocusApplyInRsvpChange
            )
        }
    }
}
