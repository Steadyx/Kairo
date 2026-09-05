package com.kairo.reader.ui.settings

import android.content.res.Resources
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.RsvpFontWeight
import com.kairo.reader.core.model.RsvpProfile
import kotlin.math.roundToInt

@Composable
internal fun DeferredSliderRow(
    title: String,
    valueLabel: (Float) -> String,
    rawValue: Float,
    onCommit: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    subtitle: String? = null,
    steps: Int = 0,
) {
    var localValue by remember { mutableFloatStateOf(rawValue) }
    LaunchedEffect(rawValue) {
        localValue = rawValue
    }

    val coercedValue = localValue.coerceIn(valueRange.start, valueRange.endInclusive)
    SettingsSliderRow(
        title = title,
        subtitle = subtitle,
        valueLabel = valueLabel(coercedValue),
        value = coercedValue,
        onValueChange = { localValue = it },
        onValueChangeFinished = {
            onCommit(localValue.coerceIn(valueRange.start, valueRange.endInclusive))
        },
        valueRange = valueRange,
        steps = steps,
    )
}

@Composable
internal fun DeferredIntegerSliderRow(
    title: String,
    valueLabel: (Int) -> String,
    rawValue: Int,
    onCommit: (Int) -> Unit,
    valueRange: IntRange,
    subtitle: String? = null,
) {
    var localValue by remember { mutableFloatStateOf(rawValue.toFloat()) }
    LaunchedEffect(rawValue, valueRange) {
        localValue = rawValue.coerceIn(valueRange.first, valueRange.last).toFloat()
    }

    val coercedValue = localValue.roundToInt().coerceIn(valueRange.first, valueRange.last)
    SettingsSliderRow(
        title = title,
        subtitle = subtitle,
        valueLabel = valueLabel(coercedValue),
        value = coercedValue.toFloat(),
        onValueChange = {
            localValue = it.roundToInt().coerceIn(valueRange.first, valueRange.last).toFloat()
        },
        onValueChangeFinished = {
            onCommit(localValue.roundToInt().coerceIn(valueRange.first, valueRange.last))
        },
        valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
        steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
    )
}

@Composable
internal fun SettingsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
internal fun AdvancedSettingsToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
) {
    val rotation by
        animateFloatAsState(
            targetValue = if (expanded) EXPANDED_ROTATION_DEGREES else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "advanced-toggle",
        )
    val disclosureStateDescription =
        stringResource(
            if (expanded) {
                R.string.accessibility_state_expanded
            } else {
                R.string.accessibility_state_collapsed
            }
        )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .semantics { stateDescription = disclosureStateDescription }
            .clickable(role = Role.Button) { onToggle() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_advanced_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    subtitle ?: stringResource(R.string.settings_advanced_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ExpandableSettingsSection(
    title: String,
    summary: String,
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(defaultExpanded) }
    val rotation by
        animateFloatAsState(
            targetValue = if (expanded) EXPANDED_ROTATION_DEGREES else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "section-toggle",
        )

    val disclosureState = stringResource(
        if (expanded) R.string.accessibility_state_expanded else R.string.accessibility_state_collapsed,
    )
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
            Modifier
                .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                .padding(14.dp),
        ) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .semantics { stateDescription = disclosureState }
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                content()
            }
        }
    }
}

private const val EXPANDED_ROTATION_DEGREES = 180f
private const val PERCENT_SCALE = 100

internal fun formatPercent(resources: Resources, value: Double): String =
    resources.getString(R.string.format_percent, (value * PERCENT_SCALE).roundToInt())

internal fun formatDeltaPercent(resources: Resources, multiplier: Double): String {
    val delta = ((multiplier - 1.0) * PERCENT_SCALE).roundToInt()
    return resources.getString(R.string.format_signed_percent, delta)
}

internal fun formatMultiplier(resources: Resources, value: Double): String =
    resources.getString(R.string.format_multiplier, value)

internal fun formatBias(resources: Resources, value: Float): String =
    resources.getString(R.string.format_percent, (value * PERCENT_SCALE).roundToInt())

internal fun blinkModeLabelRes(mode: BlinkMode): Int =
    when (mode) {
        BlinkMode.OFF -> R.string.blink_mode_off
        BlinkMode.SUBTLE -> R.string.blink_mode_subtle
        BlinkMode.ADAPTIVE -> R.string.blink_mode_adaptive
    }

internal fun blinkModeDescriptionRes(mode: BlinkMode): Int =
    when (mode) {
        BlinkMode.OFF -> R.string.blink_mode_off_description
        BlinkMode.SUBTLE -> R.string.blink_mode_subtle_description
        BlinkMode.ADAPTIVE -> R.string.blink_mode_adaptive_description
    }

internal fun rsvpFontFamilyLabelRes(family: RsvpFontFamily): Int =
    when (family) {
        RsvpFontFamily.INTER -> R.string.rsvp_font_family_inter
        RsvpFontFamily.ROBOTO -> R.string.rsvp_font_family_roboto
    }

internal fun rsvpFontWeightLabelRes(weight: RsvpFontWeight): Int =
    when (weight) {
        RsvpFontWeight.LIGHT -> R.string.rsvp_font_weight_light
        RsvpFontWeight.NORMAL -> R.string.rsvp_font_weight_normal
        RsvpFontWeight.MEDIUM -> R.string.rsvp_font_weight_medium
    }

internal fun rsvpProfileNameRes(profile: RsvpProfile): Int =
    when (profile) {
        RsvpProfile.BALANCED -> R.string.rsvp_profile_balanced
        RsvpProfile.CHILL -> R.string.rsvp_profile_chill
        RsvpProfile.NARRATIVE -> R.string.rsvp_profile_narrative
        RsvpProfile.FOCUS -> R.string.rsvp_profile_focus
        RsvpProfile.FLOW -> R.string.rsvp_profile_flow
        RsvpProfile.SPRINT -> R.string.rsvp_profile_sprint
        RsvpProfile.STUDY -> R.string.rsvp_profile_study
    }

internal fun rsvpProfileDescriptionRes(profile: RsvpProfile): Int =
    when (profile) {
        RsvpProfile.BALANCED -> R.string.rsvp_profile_balanced_description
        RsvpProfile.CHILL -> R.string.rsvp_profile_chill_description
        RsvpProfile.NARRATIVE -> R.string.rsvp_profile_narrative_description
        RsvpProfile.FOCUS -> R.string.rsvp_profile_focus_description
        RsvpProfile.FLOW -> R.string.rsvp_profile_flow_description
        RsvpProfile.SPRINT -> R.string.rsvp_profile_sprint_description
        RsvpProfile.STUDY -> R.string.rsvp_profile_study_description
    }
