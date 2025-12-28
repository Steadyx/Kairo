package com.example.kairo.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

data class ImportUiState(
    val isImporting: Boolean = false,
    val progress: Float = 0f,
    val fileName: String? = null,
)

@Composable
fun ImportProgressOverlay(
    state: ImportUiState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.isImporting,
        enter =
        fadeIn(animationSpec = tween(220)) +
            scaleIn(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                initialScale = 0.96f,
            ),
        exit =
        fadeOut(animationSpec = tween(180)) +
            scaleOut(
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                targetScale = 0.98f,
            ),
    ) {
        val animatedProgress by animateFloatAsState(
            targetValue = state.progress.coerceIn(0f, 1f),
            animationSpec = tween(280, easing = FastOutSlowInEasing),
            label = "importProgress",
        )
        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.secondary
        val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
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
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        brush =
                        Brush.linearGradient(
                            listOf(
                                primary.copy(alpha = 0.6f),
                                secondary.copy(alpha = 0.4f),
                            ),
                        ),
                        shape = RoundedCornerShape(28.dp),
                    ),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 30.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier.size(124.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = size.minDimension * 0.08f
                            val glowWidth = strokeWidth * 1.55f
                            val maxStroke = max(strokeWidth, glowWidth)
                            val inset = maxStroke / 2f
                            val arcSize =
                                androidx.compose.ui.geometry.Size(
                                    width = size.width - maxStroke,
                                    height = size.height - maxStroke,
                                )
                            val arcTopLeft = Offset(inset, inset)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val ringStroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            drawCircle(
                                color = trackColor,
                                radius = (size.minDimension - maxStroke) / 2f,
                                center = center,
                                style = ringStroke,
                            )
                            if (animatedProgress > 0f) {
                                drawArc(
                                    brush =
                                    Brush.sweepGradient(
                                        listOf(
                                            primary,
                                            secondary,
                                            primary,
                                        ),
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = animatedProgress * 360f,
                                    useCenter = false,
                                    topLeft = arcTopLeft,
                                    size = arcSize,
                                    style = ringStroke,
                                )
                                drawArc(
                                    color = primary.copy(alpha = 0.25f),
                                    startAngle = -90f,
                                    sweepAngle = animatedProgress * 360f,
                                    useCenter = false,
                                    topLeft = arcTopLeft,
                                    size = arcSize,
                                    style = Stroke(
                                        width = glowWidth,
                                        cap = StrokeCap.Round,
                                    ),
                                )
                            }
                            val dotRadius = strokeWidth * 0.8f
                            val angle = Math.toRadians((animatedProgress * 360f - 90f).toDouble())
                            val radius = (size.minDimension - maxStroke) / 2f
                            if (animatedProgress > 0f) {
                                val dotCenter =
                                    Offset(
                                        x = center.x + (radius * kotlin.math.cos(angle)).toFloat(),
                                        y = center.y + (radius * kotlin.math.sin(angle)).toFloat(),
                                    )
                                drawCircle(
                                    color = primary.copy(alpha = 0.9f),
                                    radius = dotRadius,
                                    center = dotCenter,
                                )
                            }
                        }
                        Text(
                            text = "${(animatedProgress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = "Importing",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.fileName ?: "Preparing your book",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Parsing chapters and assets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
