@file:Suppress("FunctionNaming", "LongParameterList", "MagicNumber")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.BionicReadingPreferences
import com.kairo.reader.ui.bionic.BIONIC_MAX_FIXATION_STRENGTH
import com.kairo.reader.ui.bionic.BIONIC_MAX_FONT_SIZE_SP
import com.kairo.reader.ui.bionic.BIONIC_MAX_HIGHLIGHT_STRENGTH
import com.kairo.reader.ui.bionic.BIONIC_MIN_FIXATION_STRENGTH
import com.kairo.reader.ui.bionic.BIONIC_MIN_FONT_SIZE_SP
import com.kairo.reader.ui.bionic.BIONIC_MIN_HIGHLIGHT_STRENGTH
import kotlin.math.roundToInt

@Composable
fun BionicSettingsScreen(
    preferences: BionicReadingPreferences,
    onFixationStrengthChange: (Float) -> Unit,
    onHighlightStrengthChange: (Float) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onTextBrightnessChange: (Float) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(
        title = stringResource(R.string.bionic_settings_title),
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BionicExperimentalCard()
            BionicSettingsContent(
                preferences = preferences,
                onFixationStrengthChange = onFixationStrengthChange,
                onHighlightStrengthChange = onHighlightStrengthChange,
                onFontSizeChange = onFontSizeChange,
                onTextBrightnessChange = onTextBrightnessChange,
                collapseAdvanced = true,
            )
        }
    }
}

@Composable
fun BionicSettingsContent(
    preferences: BionicReadingPreferences,
    onFixationStrengthChange: (Float) -> Unit,
    onHighlightStrengthChange: (Float) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onTextBrightnessChange: (Float) -> Unit,
    collapseAdvanced: Boolean = false,
) {
    BionicDeferredSlider(
        title = stringResource(R.string.bionic_text_size_title),
        subtitle = stringResource(R.string.bionic_text_size_subtitle),
        valueLabel = { value -> stringResource(R.string.format_sp, value.roundToInt()) },
        rawValue = preferences.fontSizeSp,
        valueRange = BIONIC_MIN_FONT_SIZE_SP..BIONIC_MAX_FONT_SIZE_SP,
        steps = (BIONIC_MAX_FONT_SIZE_SP - BIONIC_MIN_FONT_SIZE_SP).roundToInt() - 1,
        onCommit = onFontSizeChange,
    )

    val searchTarget = LocalSettingsSearchTarget.current
    var showAdvanced by rememberSaveable(collapseAdvanced, searchTarget?.id) {
        mutableStateOf(!collapseAdvanced || searchTarget?.advanced == true)
    }
    if (collapseAdvanced) {
        AdvancedSettingsToggle(
            expanded = showAdvanced,
            onToggle = { showAdvanced = !showAdvanced },
            subtitle = stringResource(R.string.bionic_advanced_settings_subtitle),
        )
    }
    if (!showAdvanced) return

    BionicDeferredSlider(
        title = stringResource(R.string.bionic_fixation_title),
        subtitle = stringResource(R.string.bionic_fixation_subtitle),
        valueLabel = { value ->
            stringResource(R.string.format_percent, (value * 100f).roundToInt())
        },
        rawValue = preferences.fixationStrength,
        valueRange = BIONIC_MIN_FIXATION_STRENGTH..BIONIC_MAX_FIXATION_STRENGTH,
        onCommit = onFixationStrengthChange,
    )

    BionicDeferredSlider(
        title = stringResource(R.string.bionic_highlight_title),
        subtitle = stringResource(R.string.bionic_highlight_subtitle),
        valueLabel = { value ->
            stringResource(R.string.format_percent, (value * 100f).roundToInt())
        },
        rawValue = preferences.highlightStrength,
        valueRange = BIONIC_MIN_HIGHLIGHT_STRENGTH..BIONIC_MAX_HIGHLIGHT_STRENGTH,
        onCommit = onHighlightStrengthChange,
    )

    BionicDeferredSlider(
        title = stringResource(R.string.bionic_brightness_title),
        subtitle = stringResource(R.string.bionic_brightness_subtitle),
        valueLabel = { value ->
            stringResource(R.string.format_percent, (value * 100f).roundToInt())
        },
        rawValue = preferences.textBrightness,
        valueRange = 0.55f..1f,
        onCommit = onTextBrightnessChange,
    )
}

@Composable
private fun BionicExperimentalCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.bionic_experimental_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.bionic_settings_explanation),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.bionic_uses_rsvp_timing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BionicDeferredSlider(
    title: String,
    subtitle: String,
    valueLabel: @Composable (Float) -> String,
    rawValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
    steps: Int = 0,
) {
    var localValue by remember(rawValue) { mutableFloatStateOf(rawValue) }
    LaunchedEffect(rawValue) {
        localValue = rawValue
    }
    val safeValue = localValue.coerceIn(valueRange.start, valueRange.endInclusive)
    SettingsSliderRow(
        title = title,
        subtitle = subtitle,
        valueLabel = valueLabel(safeValue),
        value = safeValue,
        onValueChange = { localValue = it },
        onValueChangeFinished = {
            onCommit(localValue.coerceIn(valueRange.start, valueRange.endInclusive))
        },
        valueRange = valueRange,
        steps = steps,
    )
}
