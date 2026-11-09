package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kairo.reader.R
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.rsvp.ComprehensionRsvpEngine
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy
import com.kairo.reader.core.rsvp.RsvpRhythmAspect
import com.kairo.reader.core.rsvp.RsvpRhythmChoice
import com.kairo.reader.core.rsvp.applyRsvpRhythmChoices
import com.kairo.reader.core.rsvp.scaledFrameDurationMs
import com.kairo.reader.core.tokenization.Tokenizer
import com.kairo.reader.ui.rsvp.OrpAlignedText
import com.kairo.reader.ui.rsvp.OrpTextLayout
import com.kairo.reader.ui.rsvp.OrpTypography
import com.kairo.reader.ui.rsvp.rememberRsvpTextColors
import com.kairo.reader.ui.rsvp.resolveFontWeight
import com.kairo.reader.ui.theme.composeFontFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun RsvpRhythmDialog(
    state: RsvpSettingsState,
    onApply: (List<RsvpRhythmChoice>) -> Unit,
    onDismiss: () -> Unit,
) {
    var savedChoices by rememberSaveable { mutableStateOf(List(RsvpRhythmAspect.entries.size) { RsvpRhythmChoice.KEEP.name }) }
    val choices = RsvpRhythmAspect.entries.map { aspect ->
        savedChoices.getOrNull(aspect.ordinal)?.let(RsvpRhythmChoice::valueOf) ?: RsvpRhythmChoice.KEEP
    }
    val config = applyRsvpRhythmChoices(state.config.withLiveTempo(state.tempoMsPerWord), choices)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rsvp_rhythm_calibration_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.rsvp_rhythm_instructions), style = MaterialTheme.typography.bodySmall)
                RsvpRhythmAspect.entries.forEach { aspect ->
                    RsvpRhythmChoiceRow(aspect, choices[aspect.ordinal]) { choice ->
                        savedChoices = choices.mapIndexed { index, current -> (if (index == aspect.ordinal) choice else current).name }
                    }
                }
                RsvpRhythmPreviewControls(state, config)
            }
        },
        confirmButton = {
            Button(onClick = { onApply(choices) }) { Text(stringResource(R.string.rsvp_rhythm_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RsvpRhythmChoiceRow(
    aspect: RsvpRhythmAspect,
    selected: RsvpRhythmChoice,
    onChoose: (RsvpRhythmChoice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(aspectTitle(aspect)), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(aspectDescription(aspect)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RsvpRhythmChoice.entries.forEach { choice ->
                FilterChip(
                    selected = selected == choice,
                    onClick = { onChoose(choice) },
                    label = { Text(stringResource(choiceTitle(aspect, choice))) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                )
            }
        }
    }
}

@Composable
private fun RsvpRhythmPreviewControls(
    state: RsvpSettingsState,
    config: RsvpConfig,
) {
    var previewVisible by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var request by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) playing = false
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (previewVisible) {
        Text(
            stringResource(R.string.rsvp_rhythm_preview_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RsvpRhythmPreview(
            state = state,
            config = config,
            sample = stringResource(R.string.rsvp_rhythm_sample),
            playing = playing,
            request = request,
            onFinished = { playing = false },
        )
    }
    TextButton(onClick = {
        if (playing) {
            playing = false
        } else {
            previewVisible = true
            playing = true
            request++
        }
    }) {
        Text(stringResource(if (playing) R.string.rsvp_rhythm_stop else R.string.rsvp_rhythm_preview))
    }
}

@Composable
private fun RsvpRhythmPreview(
    state: RsvpSettingsState,
    config: RsvpConfig,
    sample: String,
    playing: Boolean,
    request: Int,
    onFinished: () -> Unit,
) {
    var frame by remember { mutableStateOf<RsvpFrame?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(playing, request, sample, config) {
        if (!playing) return@LaunchedEffect
        frame = null
        failed = false
        try {
            val frames = withContext(Dispatchers.Default) {
                val tokens = Tokenizer().tokenize(Chapter(0, null, "", sample))
                ComprehensionRsvpEngine().generateFrames(tokens, 0, config, RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH))
            }
            check(frames.isNotEmpty())
            for (next in frames) {
                frame = next
                withFrameNanos { }
                delay(scaledFrameDurationMs(next, config, config.tempoMsPerWord, 1.0))
            }
            onFinished()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
            onFinished()
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(min = 100.dp), verticalArrangement = Arrangement.Center) {
        val visible = frame
        if (failed) {
            Text(stringResource(R.string.rsvp_rhythm_preview_error), color = MaterialTheme.colorScheme.error)
        } else if (visible != null) {
            OrpAlignedText(
                tokens = visible.tokens,
                typography = OrpTypography(state.fontSizeSp, state.fontFamily.composeFontFamily(), resolveFontWeight(state.fontWeight)),
                colors = rememberRsvpTextColors(state.textBrightness, config),
                layout = OrpTextLayout(
                    horizontalBias = 0f,
                    lockPivot = config.orpEnabled,
                    smoothTranslation = false,
                    preferWindowing = false,
                    simplifyPunctuation = false,
                    guideVisible = config.orpGuideEnabled,
                    pivotHighlightVisible = config.orpHighlightEnabled,
                    guideThickness = config.orpGuideThickness.toFloat(),
                ),
            )
        }
    }
}

private fun aspectTitle(aspect: RsvpRhythmAspect): Int = when (aspect) {
    RsvpRhythmAspect.LANDINGS -> R.string.rsvp_rhythm_landings
    RsvpRhythmAspect.VARIATION -> R.string.rsvp_rhythm_variation
    RsvpRhythmAspect.RESTART -> R.string.rsvp_rhythm_restart
}

private fun aspectDescription(aspect: RsvpRhythmAspect): Int = when (aspect) {
    RsvpRhythmAspect.LANDINGS -> R.string.rsvp_rhythm_landings_description
    RsvpRhythmAspect.VARIATION -> R.string.rsvp_rhythm_variation_description
    RsvpRhythmAspect.RESTART -> R.string.rsvp_rhythm_restart_description
}

private fun choiceTitle(aspect: RsvpRhythmAspect, choice: RsvpRhythmChoice): Int = when (choice) {
    RsvpRhythmChoice.KEEP -> R.string.rsvp_rhythm_current
    RsvpRhythmChoice.GENTLE -> when (aspect) {
        RsvpRhythmAspect.LANDINGS -> R.string.rsvp_rhythm_light
        RsvpRhythmAspect.VARIATION -> R.string.rsvp_rhythm_subtle
        RsvpRhythmAspect.RESTART -> R.string.rsvp_rhythm_quick
    }
    RsvpRhythmChoice.EXPRESSIVE -> when (aspect) {
        RsvpRhythmAspect.LANDINGS, RsvpRhythmAspect.VARIATION -> R.string.rsvp_rhythm_strong
        RsvpRhythmAspect.RESTART -> R.string.rsvp_rhythm_gradual
    }
}
