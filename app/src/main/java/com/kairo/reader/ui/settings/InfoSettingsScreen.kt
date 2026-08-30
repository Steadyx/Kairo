@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R

private const val KAIRO_WEBSITE_URL = "https://kairoreader.com"
private const val GITHUB_URL = "https://github.com/Steadyx/Kairo"
private const val KAIRO_CONTACT_EMAIL_URI = "mailto:kairoapp@proton.me"

@Composable
fun InfoSettingsScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    SettingsScaffold(
        title = stringResource(R.string.info_settings_title),
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsNavRow(
                title = stringResource(R.string.info_website_title),
                subtitle = stringResource(R.string.info_website_subtitle),
                icon = Icons.Default.Language,
                onClick = { uriHandler.openUri(KAIRO_WEBSITE_URL) },
            )
            SettingsNavRow(
                title = stringResource(R.string.info_contribute_title),
                subtitle = stringResource(R.string.info_contribute_subtitle),
                icon = Icons.Default.Code,
                onClick = { uriHandler.openUri(GITHUB_URL) },
            )
            SettingsNavRow(
                title = stringResource(R.string.info_contact_title),
                subtitle = stringResource(R.string.info_contact_subtitle),
                icon = Icons.Default.Email,
                onClick = { uriHandler.openUri(KAIRO_CONTACT_EMAIL_URI) },
            )
        }
    }
}
