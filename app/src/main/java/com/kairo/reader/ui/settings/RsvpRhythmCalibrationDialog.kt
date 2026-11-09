package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.kairo.reader.core.rsvp.rhythmCandidate
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
internal fun RsvpRhythmCalibrationDialog(
    state: RsvpSettingsState,
    onApply: (List<RsvpRhythmChoice>) -> Unit,
    onDismiss: () -> Unit,
) {
    val baseline = remember { state.config.withLiveTempo(state.tempoMsPerWord) }
    var savedChoices by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val choices = savedChoices.map(RsvpRhythmChoice::valueOf)
    val aspect = RsvpRhythmAspect.entries.getOrNull(choices.size)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rsvp_rhythm_calibration_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (aspect == null) {
                    Text(stringResource(R.string.rsvp_rhythm_ready))
                    choices.forEachIndexed { index, choice ->
                        Text(stringResource(aspectTitle(RsvpRhythmAspect.entries[index])) + ": " + stringResource(choiceTitle(choice)))
                    }
                } else {
                    Text(stringResource(R.string.rsvp_rhythm_instructions))
                    Text(stringResource(aspectTitle(aspect)), style = MaterialTheme.typography.titleMedium)
                    key(aspect) {
                        RsvpRhythmComparison(
                            state = state,
                            config = applyRsvpRhythmChoices(baseline, choices),
                            aspect = aspect,
                            onChoose = { savedChoices = savedChoices + it.name },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (aspect == null) {
                Button(onClick = { onApply(choices) }) { Text(stringResource(R.string.rsvp_rhythm_apply)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RsvpRhythmComparison(
    state: RsvpSettingsState,
    config: RsvpConfig,
    aspect: RsvpRhythmAspect,
    onChoose: (RsvpRhythmChoice) -> Unit,
) {
    var playing by remember(aspect) { mutableStateOf<RsvpRhythmChoice?>(null) }
    var heardA by remember(aspect) { mutableStateOf(false) }
    var heardB by remember(aspect) { mutableStateOf(false) }
    var request by remember(aspect) { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, aspect) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) playing = null
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val sample = stringResource(
        if (aspect ==
            RsvpRhythmAspect.VARIATION
        ) {
            R.string.rsvp_rhythm_sample_two
        } else {
            R.string.rsvp_rhythm_sample_one
        }
    )
    RsvpRhythmPreview(
        state = state,
        config = rhythmCandidate(config, aspect, playing ?: RsvpRhythmChoice.KEEP),
        sample = sample,
        playing = playing != null,
        request = request,
        onFinished = { completed ->
            if (completed && playing == RsvpRhythmChoice.GENTLE) heardA = true
            if (completed && playing == RsvpRhythmChoice.EXPRESSIVE) heardB = true
            playing = null
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(modifier = Modifier.weight(1f), onClick = {
            playing = RsvpRhythmChoice.GENTLE
            request++
        }) {
            Text(stringResource(R.string.rsvp_rhythm_play_a))
        }
        OutlinedButton(modifier = Modifier.weight(1f), onClick = {
            playing = RsvpRhythmChoice.EXPRESSIVE
            request++
        }) {
            Text(stringResource(R.string.rsvp_rhythm_play_b))
        }
    }
    if (playing != null) {
        Text(stringResource(R.string.rsvp_rhythm_playing, if (playing == RsvpRhythmChoice.GENTLE) "A" else "B"))
        TextButton(onClick = { playing = null }) { Text(stringResource(R.string.rsvp_rhythm_stop)) }
    }
    val ready = heardA && heardB && playing == null
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(modifier = Modifier.weight(1f), enabled = ready, onClick = { onChoose(RsvpRhythmChoice.GENTLE) }) {
            Text(stringResource(R.string.rsvp_rhythm_prefer_a))
        }
        Button(modifier = Modifier.weight(1f), enabled = ready, onClick = { onChoose(RsvpRhythmChoice.EXPRESSIVE) }) {
            Text(stringResource(R.string.rsvp_rhythm_prefer_b))
        }
    }
    TextButton(onClick = { onChoose(RsvpRhythmChoice.KEEP) }) { Text(stringResource(R.string.rsvp_rhythm_keep)) }
}

@Composable
private fun RsvpRhythmPreview(
    state: RsvpSettingsState,
    config: RsvpConfig,
    sample: String,
    playing: Boolean,
    request: Int,
    onFinished: (Boolean) -> Unit,
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
            for (next in frames) {
                frame = next
                withFrameNanos { }
                delay(scaledFrameDurationMs(next, config, config.tempoMsPerWord, 1.0))
            }
            onFinished(frames.isNotEmpty())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
            onFinished(false)
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(min = 100.dp), verticalArrangement = Arrangement.Center) {
        val visible = frame
        if (failed) {
            Text(stringResource(R.string.rsvp_rhythm_preview_error), color = MaterialTheme.colorScheme.error)
        } else if (visible == null) {
            Text(stringResource(R.string.rsvp_rhythm_preview_hint))
        } else {
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

private fun choiceTitle(choice: RsvpRhythmChoice): Int = when (choice) {
    RsvpRhythmChoice.KEEP -> R.string.rsvp_rhythm_keep
    RsvpRhythmChoice.GENTLE -> R.string.rsvp_rhythm_prefer_a
    RsvpRhythmChoice.EXPRESSIVE -> R.string.rsvp_rhythm_prefer_b
}
