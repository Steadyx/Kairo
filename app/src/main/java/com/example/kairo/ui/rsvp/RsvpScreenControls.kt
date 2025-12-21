@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun BoxScope.RsvpBottomControls(context: RsvpUiContext) {
    val runtime = context.runtime

    AnimatedVisibility(
        visible = runtime.showControls,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(
                    MaterialTheme.colorScheme.surface.copy(
                        alpha = QUICK_SETTINGS_BACKGROUND_ALPHA
                    )
                )
                .padding(CONTROLS_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RsvpPlaybackControlsRow(context)
            Spacer(modifier = Modifier.height(CONTROLS_SPACER))
            Text(
                "${runtime.frameIndex + 1} / ${context.frameState.frames.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = PROGRESS_TEXT_ALPHA)
            )
            Spacer(modifier = Modifier.height(CONTROLS_HINT_SPACER))
            Text(
                "Tap anywhere to resume",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = RESUME_TEXT_ALPHA)
            )
        }
    }
}

@Composable
private fun RsvpPlaybackControlsRow(context: RsvpUiContext) {
    val runtime = context.runtime

    Row(
        horizontalArrangement = Arrangement.spacedBy(CONTROLS_ROW_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            runtime.frameIndex = (runtime.frameIndex - 1).coerceAtLeast(0)
            runtime.completed = false
        }) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(SKIP_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        IconButton(
            onClick = {
                runtime.isPlaying = !runtime.isPlaying
                if (runtime.isPlaying) runtime.showControls = false
            },
            modifier = Modifier
                .size(PLAY_BUTTON_SIZE)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                if (runtime.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (runtime.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(PLAY_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        IconButton(onClick = { advanceFrame(context) }) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(SKIP_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
