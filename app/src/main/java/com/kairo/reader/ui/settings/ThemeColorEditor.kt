@file:Suppress("MagicNumber", "LongMethod")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.parseThemeColor
import com.kairo.reader.core.model.themeColorHex
import kotlin.math.roundToInt

/** Changes update the saveable theme draft immediately, while incomplete hex input stays local. */
@Composable
internal fun ThemeColorEditor(
    role: ThemeColorRole,
    initial: Int,
    automatic: Boolean,
    onChange: (Int?) -> Unit,
    onValidityChange: (Boolean) -> Unit = {}
) {
    var hex by rememberSaveable { mutableStateOf(themeColorHex(initial)) }
    LaunchedEffect(hex, automatic) { onValidityChange(automatic || parseThemeColor(hex) != null) }
    DisposableEffect(Unit) { onDispose { onValidityChange(true) } }
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(initial, hsv)
    var hue by rememberSaveable { mutableFloatStateOf(hsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(hsv[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(hsv[2]) }
    val update: (Int, Float) -> Unit = { channel, value ->
        when (channel) {
            0 -> hue = value
            1 -> saturation = value
            2 -> brightness = value
        }
        val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
        hex = themeColorHex(color)
        onChange(color)
    }
    val automaticLabel = stringResource(R.string.theme_automatic)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (role != ThemeColorRole.BACKGROUND) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.theme_automatic), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = automatic,
                    onCheckedChange = { onChange(if (it) null else initial) },
                    modifier = Modifier.semantics { contentDescription = automaticLabel }
                )
            }
        }
        if (!automatic) {
            ThemeColorSlider(stringResource(R.string.theme_hue), hue, 359f) { update(0, it) }
            ThemeColorSlider(stringResource(R.string.theme_saturation), saturation, 1f) { update(1, it) }
            ThemeColorSlider(stringResource(R.string.theme_lightness), brightness, 1f) { update(2, it) }
            OutlinedTextField(
                value = hex,
                onValueChange = { value ->
                    if (value.length <= 7) {
                        hex = value
                        parseThemeColor(value)?.let {
                            android.graphics.Color.colorToHSV(it, hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                            onChange(it)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.theme_hex)) },
                singleLine = true,
                isError = parseThemeColor(hex) == null,
                supportingText = { if (parseThemeColor(hex) == null) Text(stringResource(R.string.theme_hex_error)) },
            )
        } else {
            Text(stringResource(R.string.theme_automatic_help), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ThemeColorSlider(title: String, value: Float, maximum: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.3f))
        Slider(
            value,
            onValueChange = onChange,
            valueRange = 0f..maximum,
            modifier = Modifier.weight(0.55f).semantics { contentDescription = title }
        )
        Text(
            (
                if (maximum >
                    1f
                ) {
                    value
                } else {
                    value * 100
                }
                ).roundToInt().toString(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(0.15f)
        )
    }
}
