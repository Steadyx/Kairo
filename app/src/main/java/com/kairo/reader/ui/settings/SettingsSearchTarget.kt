package com.kairo.reader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

internal val LocalSettingsSearchTarget = staticCompositionLocalOf<SettingsSearchEntry?> { null }
internal val LocalAdvancedSettingsScope = staticCompositionLocalOf { false }

@Composable
internal fun searchExpandsSection(title: String): Boolean {
    val target = LocalSettingsSearchTarget.current ?: return false
    return target.matchesScope() && target.sectionRes?.let { stringResource(it) } == title
}

@Composable
private fun SettingsSearchEntry.matchesScope(): Boolean =
    page != SettingsSearchPage.RSVP || advanced == LocalAdvancedSettingsScope.current

@Composable
internal fun Modifier.settingsSearchTarget(title: String): Modifier {
    val target = LocalSettingsSearchTarget.current ?: return this
    if (!target.matchesScope()) return this
    val highlighted = stringResource(target.titleRes) == title
    val anchor = stringResource(if (target.requiresRes != null) target.sectionRes ?: target.titleRes else target.titleRes) == title
    val decorated = if (highlighted) {
        val accent = MaterialTheme.colorScheme.primary
        val shape = MaterialTheme.shapes.medium
        background(accent.copy(alpha = SEARCH_HIGHLIGHT_TINT_ALPHA), shape)
            .border(1.dp, accent.copy(alpha = SEARCH_HIGHLIGHT_OUTLINE_ALPHA), shape)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    } else {
        this
    }
    if (!anchor) return decorated
    val requester = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var revealed by rememberSaveable(target.id, title) { mutableStateOf(false) }
    LaunchedEffect(target.id, size) {
        if (!revealed && size.height > 0) {
            // Reveal the whole control, but only the heading for a section taller than the screen.
            requester.bringIntoView(Rect(0f, 0f, size.width.toFloat(), if (highlighted) size.height.toFloat() else 1f))
            revealed = true
        }
    }
    return Modifier.bringIntoViewRequester(requester).onGloballyPositioned { size = it.size }.then(decorated)
}

private const val SEARCH_HIGHLIGHT_TINT_ALPHA = 0.06f
private const val SEARCH_HIGHLIGHT_OUTLINE_ALPHA = 0.3f
