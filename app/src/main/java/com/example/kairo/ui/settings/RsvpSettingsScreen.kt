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
    onRsvpConfigChange: (RsvpConfig) -> Unit,
    onUnlockExtremeSpeedChange: (Boolean) -> Unit,
    onRsvpFontSizeChange: (Float) -> Unit,
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
                config = preferences.rsvpConfig,
                unlockExtremeSpeed = preferences.unlockExtremeSpeed,
                rsvpFontSizeSp = preferences.rsvpFontSizeSp,
                rsvpFontFamily = preferences.rsvpFontFamily,
                rsvpFontWeight = preferences.rsvpFontWeight,
                rsvpVerticalBias = preferences.rsvpVerticalBias,
                rsvpHorizontalBias = preferences.rsvpHorizontalBias,
                onConfigChange = onRsvpConfigChange,
                onUnlockExtremeSpeedChange = onUnlockExtremeSpeedChange,
                onRsvpFontSizeChange = onRsvpFontSizeChange,
                onRsvpFontWeightChange = onRsvpFontWeightChange,
                onRsvpFontFamilyChange = onRsvpFontFamilyChange,
                onRsvpVerticalBiasChange = onRsvpVerticalBiasChange,
                onRsvpHorizontalBiasChange = onRsvpHorizontalBiasChange
            )
        }
    }
}
