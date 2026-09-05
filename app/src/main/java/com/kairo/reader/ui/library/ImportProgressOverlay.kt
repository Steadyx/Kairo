@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import kotlin.math.roundToInt

data class ImportUiState(val isImporting: Boolean = false, val progress: Float = 0f, val fileName: String? = null,)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImportProgressOverlay(
    state: ImportUiState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.isImporting,
        enter =
        fadeIn(animationSpec = tween(IMPORT_ENTER_DURATION_MS)) +
            scaleIn(
                animationSpec = tween(IMPORT_ENTER_DURATION_MS, easing = FastOutSlowInEasing),
                initialScale = 0.96f,
            ),
        exit =
        fadeOut(animationSpec = tween(IMPORT_EXIT_DURATION_MS)) +
            scaleOut(
                animationSpec = tween(IMPORT_EXIT_DURATION_MS, easing = FastOutSlowInEasing),
                targetScale = 0.98f,
            ),
    ) {
        val animatedProgress by animateFloatAsState(
            targetValue = state.progress.coerceIn(0f, 1f),
            animationSpec = tween(IMPORT_PROGRESS_DURATION_MS, easing = FastOutSlowInEasing),
            label = "importProgress",
        )
        Box(
            modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {},
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                shape = MaterialTheme.shapes.extraLargeIncreased,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 30.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ContainedLoadingIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(88.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerShape = MaterialTheme.shapes.extraExtraLarge,
                    )
                    Text(
                        text =
                        stringResource(
                            R.string.format_percent,
                            (animatedProgress * PERCENT_SCALE).roundToInt(),
                        ),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                    Text(
                        text = stringResource(R.string.import_status_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                        state.fileName
                            ?: stringResource(R.string.import_preparing_book),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.import_parsing_chapters),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private const val IMPORT_ENTER_DURATION_MS = 220
private const val IMPORT_EXIT_DURATION_MS = 180
private const val IMPORT_PROGRESS_DURATION_MS = 280
private const val PERCENT_SCALE = 100
