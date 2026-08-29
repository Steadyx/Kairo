package com.kairo.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.data.books.SharedTextImport
import com.kairo.reader.data.books.WebArticleUrl
import com.kairo.reader.ui.LocalDispatcherProvider
import com.kairo.reader.ui.focus.SystemBarsStyleSideEffect
import com.kairo.reader.ui.navigation.KairoNavHost
import com.kairo.reader.ui.theme.KairoTheme
import com.kairo.reader.ui.updates.InAppUpdatePrompt
import com.kairo.reader.ui.updates.InAppUpdateUiBindings
import com.kairo.reader.ui.updates.PlayInAppUpdateCoordinator

@Composable
private fun rememberSystemDefaultPreferences(): UserPreferences {
    val isDark = isSystemInDarkTheme()
    return remember(isDark) {
        UserPreferences(
            readerTheme =
            if (isDark) {
                ReaderTheme.DARK
            } else {
                ReaderTheme.LIGHT
            },
        )
    }
}

class MainActivity : AppCompatActivity() {
    private val pendingExternalImportUriState = mutableStateOf<Uri?>(null)
    private val pendingSharedArticleUrlState = mutableStateOf<String?>(null)
    private val pendingSharedTextState = mutableStateOf<SharedTextImport?>(null)
    private val inAppUpdatePromptState = mutableStateOf<InAppUpdatePrompt?>(null)
    private lateinit var inAppUpdateCoordinator: PlayInAppUpdateCoordinator
    private val inAppUpdateFlowLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (::inAppUpdateCoordinator.isInitialized) {
                inAppUpdateCoordinator.onUpdateFlowResult(result.resultCode)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyIncomingIntent(intent)

        val container = application as KairoApplication
        inAppUpdateCoordinator =
            PlayInAppUpdateCoordinator(
                activity = this,
                onPromptChanged = { prompt -> inAppUpdatePromptState.value = prompt },
            )
        inAppUpdateCoordinator.start(inAppUpdateFlowLauncher)

        setContent {
            val fallbackPrefs = rememberSystemDefaultPreferences()
            val prefs by container.preferencesRepository.preferences.collectAsState(
                initial = null,
            )
            val effectivePrefs = prefs ?: fallbackPrefs

            CompositionLocalProvider(
                LocalDispatcherProvider provides container.dispatcherProvider
            ) {
                KairoTheme(readerTheme = effectivePrefs.readerTheme) {
                    SystemBarsStyleSideEffect(readerTheme = effectivePrefs.readerTheme)
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        if (prefs == null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            KairoNavHost(
                                container = container,
                                prefs = effectivePrefs,
                                externalImportUri = pendingExternalImportUriState.value,
                                externalArticleUrl = pendingSharedArticleUrlState.value,
                                externalSharedText = pendingSharedTextState.value,
                                onExternalImportUriConsumed = { consumedUri ->
                                    clearConsumedExternalImportIntent(consumedUri)
                                },
                                onExternalArticleUrlConsumed = { consumedUrl ->
                                    clearConsumedSharedArticleIntent(consumedUrl)
                                },
                                onExternalSharedTextConsumed = { consumedText ->
                                    clearConsumedSharedTextIntent(consumedText)
                                },
                                inAppUpdateUi =
                                InAppUpdateUiBindings(
                                    prompt = inAppUpdatePromptState.value,
                                    onAction = inAppUpdateCoordinator::perform,
                                    onDismiss = inAppUpdateCoordinator::dismiss,
                                    onCheckForUpdates = inAppUpdateCoordinator::checkForUpdates,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::inAppUpdateCoordinator.isInitialized) {
            inAppUpdateCoordinator.refreshUpdateState()
        }
    }

    override fun onDestroy() {
        if (::inAppUpdateCoordinator.isInitialized) {
            inAppUpdateCoordinator.stop()
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        applyIncomingIntent(intent)
    }

    private fun applyIncomingIntent(incomingIntent: Intent) {
        val importUri = incomingIntent.bookImportUri()
        val sharedImport = if (importUri == null) incomingIntent.sharedImport() else null
        pendingExternalImportUriState.value = importUri
        pendingSharedArticleUrlState.value =
            (sharedImport as? SharedImport.Article)?.url
        pendingSharedTextState.value =
            (sharedImport as? SharedImport.Text)?.payload
    }

    private fun clearConsumedExternalImportIntent(consumedUri: Uri) {
        if (pendingExternalImportUriState.value == consumedUri) {
            pendingExternalImportUriState.value = null
        }
        if (intent.bookImportUri() == consumedUri) {
            intent = Intent(this, MainActivity::class.java)
        }
    }

    private fun clearConsumedSharedArticleIntent(consumedUrl: String) {
        if (pendingSharedArticleUrlState.value == consumedUrl) {
            pendingSharedArticleUrlState.value = null
        }
        if ((intent.sharedImport() as? SharedImport.Article)?.url == consumedUrl) {
            intent = Intent(this, MainActivity::class.java)
        }
    }

    private fun clearConsumedSharedTextIntent(consumedText: SharedTextImport) {
        if (pendingSharedTextState.value == consumedText) {
            pendingSharedTextState.value = null
        }
        if ((intent.sharedImport() as? SharedImport.Text)?.payload == consumedText) {
            intent = Intent(this, MainActivity::class.java)
        }
    }
}

private fun Intent.bookImportUri(): Uri? =
    if (action == Intent.ACTION_VIEW) {
        data
    } else {
        null
    }

private sealed interface SharedImport {
    data class Article(val url: String) : SharedImport

    data class Text(val payload: SharedTextImport) : SharedImport
}

private fun Intent.sharedImport(): SharedImport? {
    if (action != Intent.ACTION_SEND || type?.startsWith("text/", ignoreCase = true) != true) {
        return null
    }
    val sharedText = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
    val sharedHtml = getCharSequenceExtra(Intent.EXTRA_HTML_TEXT)?.toString()?.trim().orEmpty()
    val subject =
        getCharSequenceExtra(Intent.EXTRA_SUBJECT)
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotBlank)
    val renderedHtml = htmlToReadableText(sharedHtml)
    val articleSource = sharedText.ifBlank { renderedHtml }
    val articleUrl =
        WebArticleUrl.extractBestWebUrl(articleSource)
            ?.takeIf { url ->
                val remainingText =
                    articleSource
                        .replace(url, "")
                        .trim()
                        .trim('-', '—', ':', '|')
                        .trim()
                remainingText.isBlank() ||
                    subject?.let { sharedSubject ->
                        remainingText.equals(sharedSubject, ignoreCase = true)
                    } == true
            }
    if (articleUrl != null) return SharedImport.Article(articleUrl)

    val content = sharedText.ifBlank { renderedHtml }
    return content
        .takeIf(String::isNotBlank)
        ?.let { SharedImport.Text(SharedTextImport(content = it, title = subject)) }
}

private fun htmlToReadableText(html: String): String =
    org.jsoup.Jsoup.parseBodyFragment(html).wholeText().trim()
