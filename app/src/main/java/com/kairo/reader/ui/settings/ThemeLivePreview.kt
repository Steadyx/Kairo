@file:Suppress("MagicNumber", "MatchingDeclarationName")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.reader.R
import com.kairo.reader.core.model.HighlightColor
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.ThemeDesign
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.reader.readerSpanColor
import com.kairo.reader.ui.rsvp.OrpAlignedText
import com.kairo.reader.ui.rsvp.OrpTextLayout
import com.kairo.reader.ui.rsvp.OrpTypography
import com.kairo.reader.ui.rsvp.rememberRsvpTextColors
import com.kairo.reader.ui.rsvp.resolveFontWeight
import com.kairo.reader.ui.saved.displayColor
import com.kairo.reader.ui.theme.composeFontFamily
import com.kairo.reader.ui.theme.readingColor

internal enum class ThemePreviewKind(val label: Int) {
    READER(R.string.theme_font_reader),
    RSVP(R.string.theme_preview_rsvp),
    INTERFACE(R.string.theme_font_interface),
}

@Composable
internal fun ThemeLivePreview(preferences: UserPreferences, design: ThemeDesign) {
    val focus = LocalFocusManager.current
    var kind by rememberSaveable { mutableStateOf(ThemePreviewKind.READER) }
    var showContrast by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.testTag("theme-live-preview"),
        color = scheme.background,
        contentColor = scheme.onBackground,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryScrollableTabRow(selectedTabIndex = kind.ordinal, edgePadding = 0.dp, containerColor = scheme.background) {
                ThemePreviewKind.entries.forEach { item ->
                    Tab(
                        selected = item == kind,
                        onClick = {
                            focus.clearFocus()
                            kind = item
                        },
                        text = { Text(stringResource(item.label)) },
                        modifier = Modifier.testTag("theme-preview-${item.name}")
                    )
                }
            }
            if (design.theme == ReaderTheme.CUSTOM) {
                TextButton(onClick = { showContrast = true }) {
                    Text(
                        stringResource(R.string.theme_contrast_aa),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onBackground
                    )
                }
            } else {
                Text(
                    stringResource(R.string.theme_preview),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = 12.dp),
                    color = scheme.primary
                )
            }
            when (kind) {
                ThemePreviewKind.READER -> ThemeReaderPreview(preferences, design)
                ThemePreviewKind.RSVP -> ThemeRsvpPreview(preferences, design)
                ThemePreviewKind.INTERFACE -> ThemeInterfacePreview()
            }
        }
    }
    if (showContrast) {
        AlertDialog(
            onDismissRequest = { showContrast = false },
            title = { Text(stringResource(R.string.theme_contrast_aa)) },
            text = { Text(stringResource(R.string.theme_reading_contrast_help)) },
            confirmButton = { TextButton(onClick = { showContrast = false }) { Text(stringResource(R.string.theme_done)) } },
        )
    }
}

@Composable
private fun ThemeReaderPreview(preferences: UserPreferences, design: ThemeDesign) {
    val scheme = MaterialTheme.colorScheme
    val body = readingColor(scheme.onBackground.copy(alpha = preferences.readerTextBrightness))
    val highlight = HighlightColor.YELLOW.displayColor().copy(alpha = 0.18f)
    val highlightedText = if (design.theme == ReaderTheme.CUSTOM) readerSpanColor(body, scheme.background, listOf(highlight)) else body
    val sample = buildAnnotatedString {
        append(stringResource(R.string.theme_preview_paragraph))
        append("\n")
        withStyle(SpanStyle(background = highlight, color = highlightedText)) { append(stringResource(R.string.theme_preview_highlight)) }
        append("  ")
        withStyle(SpanStyle(color = scheme.tertiary, textDecoration = TextDecoration.Underline)) {
            append(stringResource(R.string.theme_preview_link))
        }
    }
    Text(
        sample,
        fontFamily = design.readerFont.composeFontFamily(),
        fontSize = preferences.readerFontSizeSp.sp,
        lineHeight = (preferences.readerFontSizeSp * 1.5f).sp,
        color = body,
        modifier = Modifier.testTag("theme-reader-sample")
    )
}

@Composable
private fun ThemeRsvpPreview(preferences: UserPreferences, design: ThemeDesign) {
    val sample = stringResource(R.string.theme_preview_word)
    val config = preferences.rsvpConfig
    Column(Modifier.fillMaxWidth().heightIn(min = 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.theme_preview_paused), style = MaterialTheme.typography.labelSmall)
        OrpAlignedText(
            tokens = listOf(Token(sample, TokenType.WORD, orpIndex = (sample.length / 3).coerceAtMost(sample.lastIndex))),
            typography = OrpTypography(
                preferences.rsvpFontSizeSp,
                design.timedFont.composeFontFamily(),
                resolveFontWeight(preferences.rsvpFontWeight)
            ),
            colors = rememberRsvpTextColors(preferences.rsvpTextBrightness, config),
            layout = OrpTextLayout(
                0f,
                false,
                false,
                false,
                false,
                config.orpGuideEnabled,
                config.orpHighlightEnabled,
                config.orpGuideThickness.toFloat()
            ),
        )
    }
}

@Composable
private fun ThemeInterfacePreview() {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.theme_live_sample), style = MaterialTheme.typography.titleLarge)
        Surface(color = scheme.surfaceContainer, contentColor = scheme.onSurfaceVariant, shape = MaterialTheme.shapes.medium) {
            Text(stringResource(R.string.theme_preview_paragraph), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = scheme.primary, contentColor = scheme.onPrimary, shape = CircleShapeForPreview) {
                Text(
                    stringResource(R.string.theme_preview_continue),
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                stringResource(R.string.theme_preview_link),
                Modifier.padding(vertical = 10.dp),
                color = scheme.tertiary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

private val CircleShapeForPreview = RoundedCornerShape(50)
