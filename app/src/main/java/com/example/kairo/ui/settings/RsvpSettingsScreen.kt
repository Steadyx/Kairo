package com.example.kairo.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.UserPreferences

@Composable
fun RsvpSettingsScreen(
    preferences: UserPreferences,
    onSelectRsvpProfile: (String) -> Unit,
    onSaveRsvpProfile: (String, RsvpConfig) -> Unit,
    onDeleteRsvpProfile: (String) -> Unit,
    onRsvpConfigChange: (RsvpConfig) -> Unit,
    onUnlockExtremeSpeedChange: (Boolean) -> Unit,
    onRsvpFontSizeChange: (Float) -> Unit,
    onRsvpTextBrightnessChange: (Float) -> Unit,
    onRsvpFontWeightChange: (RsvpFontWeight) -> Unit,
    onRsvpFontFamilyChange: (RsvpFontFamily) -> Unit,
    onRsvpVerticalBiasChange: (Float) -> Unit,
    onRsvpHorizontalBiasChange: (Float) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    SettingsScaffold(title = "RSVP settings", onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RsvpSettingsContent(
                selectedProfileId = preferences.rsvpSelectedProfileId,
                customProfiles = preferences.rsvpCustomProfiles,
                config = preferences.rsvpConfig,
                unlockExtremeSpeed = preferences.unlockExtremeSpeed,
                rsvpFontSizeSp = preferences.rsvpFontSizeSp,
                rsvpTextBrightness = preferences.rsvpTextBrightness,
                rsvpFontFamily = preferences.rsvpFontFamily,
                rsvpFontWeight = preferences.rsvpFontWeight,
                rsvpVerticalBias = preferences.rsvpVerticalBias,
                rsvpHorizontalBias = preferences.rsvpHorizontalBias,
                onSelectProfile = onSelectRsvpProfile,
                onSaveCustomProfile = onSaveRsvpProfile,
                onDeleteCustomProfile = onDeleteRsvpProfile,
                onConfigChange = onRsvpConfigChange,
                onUnlockExtremeSpeedChange = onUnlockExtremeSpeedChange,
                onRsvpFontSizeChange = onRsvpFontSizeChange,
                onRsvpTextBrightnessChange = onRsvpTextBrightnessChange,
                onRsvpFontWeightChange = onRsvpFontWeightChange,
                onRsvpFontFamilyChange = onRsvpFontFamilyChange,
                onRsvpVerticalBiasChange = onRsvpVerticalBiasChange,
                onRsvpHorizontalBiasChange = onRsvpHorizontalBiasChange
            )
        }
    }
}
