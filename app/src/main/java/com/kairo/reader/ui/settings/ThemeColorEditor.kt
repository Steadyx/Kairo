@file:Suppress("MagicNumber", "LongMethod")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    onValidityChange: (Boolean) -> Unit = {},
    effective: Int = initial,
    onEditStart: () -> Unit = {},
    onEditFinished: () -> Unit = {},
) {
    var hex by rememberSaveable { mutableStateOf(themeColorHex(initial)) }
    LaunchedEffect(hex, automatic) { onValidityChange(automatic || parseThemeColor(hex) != null) }
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
        onEditStart()
        onChange(color)
    }
    val automaticLabel = stringResource(R.string.theme_automatic)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (role != ThemeColorRole.BACKGROUND) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.theme_automatic), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = automatic,
                    onCheckedChange = {
                        onEditFinished()
                        onChange(if (it) null else initial)
                    },
                    modifier = Modifier.semantics { contentDescription = automaticLabel }
                )
            }
        }
        if (!automatic) {
            ThemeColorSlider(
                stringResource(R.string.theme_hue),
                hue,
                359f,
                (0..6).map { Color.hsv((it * 60f).coerceAtMost(359f), 1f, 1f) },
                onEditFinished
            ) { update(0, it) }
            ThemeColorSlider(
                stringResource(R.string.theme_saturation),
                saturation,
                1f,
                listOf(Color.hsv(hue, 0f, brightness), Color.hsv(hue, 1f, brightness)),
                onEditFinished
            ) { update(1, it) }
            ThemeColorSlider(
                stringResource(R.string.theme_lightness),
                brightness,
                1f,
                listOf(Color.Black, Color.hsv(hue, saturation, 1f)),
                onEditFinished
            ) { update(2, it) }
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
                            onEditFinished()
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
            val requested = parseThemeColor(hex)
            if (requested != null && requested != effective) ThemeAdjustedColor(requested, effective)
        } else {
            Text(stringResource(R.string.theme_automatic_help), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ThemeColorSlider(
    title: String,
    value: Float,
    maximum: Float,
    colors: List<Color>,
    onFinished: () -> Unit,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.3f))
        Box(Modifier.weight(0.55f), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(8.dp).background(Brush.horizontalGradient(colors), CircleShape))
            Slider(
                value,
                onValueChange = onChange,
                onValueChangeFinished = onFinished,
                valueRange = 0f..maximum,
                colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
                modifier = Modifier.semantics { contentDescription = title },
            )
        }
        Text(
            (if (maximum > 1f) value else value * 100).roundToInt().toString(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(0.15f),
        )
    }
}

@Composable
private fun ThemeAdjustedColor(requested: Int, effective: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.theme_adjusted_help), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(R.string.theme_requested to requested, R.string.theme_displayed to effective).forEach { (label, color) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        Modifier.size(32.dp),
                        color = Color(color),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {}
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
                    Text(themeColorHex(color), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
