package com.example.kairo.ui.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.example.kairo.R
import java.util.Locale

private val SUPPORTED_LANGUAGE_TAGS =
    listOf(
        "en",
        "es",
        "pt-BR",
        "fr",
        "de",
        "ja",
        "zh-Hans",
    )

data class LanguageOption(
    val tag: String?,
    val label: String,
)

fun getAppLanguageTag(): String? {
    val locales = AppCompatDelegate.getApplicationLocales()
    return locales[0]?.toLanguageTag()
}

fun resolveLanguageLabel(context: Context, tag: String?): String {
    if (tag.isNullOrBlank()) {
        return context.getString(R.string.settings_language_system_default)
    }
    val locale = Locale.forLanguageTag(tag)
    val displayName = locale.getDisplayName(locale)
    return displayName.replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase(locale)
        } else {
            char.toString()
        }
    }
}

private fun buildLanguageOptions(context: Context): List<LanguageOption> =
    buildList {
        add(
            LanguageOption(
                tag = null,
                label = context.getString(R.string.settings_language_system_default),
            ),
        )
        SUPPORTED_LANGUAGE_TAGS.forEach { tag ->
            add(LanguageOption(tag = tag, label = resolveLanguageLabel(context, tag)))
        }
    }

@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val options = remember(context) { buildLanguageOptions(context) }
    val configuration = LocalConfiguration.current
    var selectedTag by remember { mutableStateOf(getAppLanguageTag()) }

    LaunchedEffect(configuration) {
        selectedTag = getAppLanguageTag()
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_language_title),
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.forEach { option ->
                LanguageOptionRow(
                    option = option,
                    selected = option.tag == selectedTag,
                    onSelect = {
                        val locales =
                            if (option.tag.isNullOrBlank()) {
                                LocaleListCompat.getEmptyLocaleList()
                            } else {
                                LocaleListCompat.forLanguageTags(option.tag)
                            }
                        AppCompatDelegate.setApplicationLocales(locales)
                        selectedTag = option.tag
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageOptionRow(
    option: LanguageOption,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onSelect),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(option.label, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.size(8.dp))
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}
