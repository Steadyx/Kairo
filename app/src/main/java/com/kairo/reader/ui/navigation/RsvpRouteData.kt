package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.Token
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal data class RsvpRouteData(
    val tokens: List<Token>,
    val chapterCount: Int,
    val savedResumePosition: ReadingPosition?,
    val languageTag: String?,
    val tokensResolved: Boolean,
    val languageResolved: Boolean,
    val metadataResolved: Boolean = false,
    val linearChapterIndices: List<Int>? = null,
) {
    val isReady: Boolean get() = tokensResolved && languageResolved && metadataResolved
}

@Composable
internal fun rememberRsvpRouteData(
    container: KairoApplication,
    bookId: String,
    chapterIndex: Int,
    safeStartIndex: Int,
): RsvpRouteData {
    val bookIdValue = remember(bookId) { BookId(bookId) }
    val launchSnapshot =
        remember(bookId, chapterIndex) {
            RsvpLaunchSnapshotStore.snapshotFor(bookId, chapterIndex)
        }
    DisposableEffect(bookId, chapterIndex) {
        onDispose {
            RsvpLaunchSnapshotStore.clear(bookId, chapterIndex)
        }
    }
    val initialRouteData =
        RsvpRouteData(
            tokens = launchSnapshot?.tokens.orEmpty(),
            chapterCount = chapterIndex + 1,
            savedResumePosition = null,
            languageTag = launchSnapshot?.languageTag,
            tokensResolved = launchSnapshot != null,
            languageResolved = launchSnapshot != null,
        )

    val routeData by produceState(
        initialValue = initialRouteData,
        bookId,
        chapterIndex,
        safeStartIndex,
    ) {
        value = initialRouteData
        coroutineScope {
            val loadedTokensDeferred =
                if (launchSnapshot == null) {
                    async {
                        runCatching {
                            container.tokenRepository.getTokens(bookIdValue, chapterIndex)
                        }.getOrElse { emptyList() }
                    }
                } else {
                    null
                }
            val chaptersDeferred =
                async {
                    runCatching {
                        container.bookRepository.getBook(bookIdValue).chapters
                    }.getOrNull()
                }
            val savedResumePositionDeferred =
                async {
                    runCatching {
                        container.readingPositionRepository.getPosition(bookIdValue)
                    }.getOrNull()
                }
            val languageTagDeferred =
                if (launchSnapshot == null) {
                    async {
                        runCatching {
                            container.bookRepository.getBookLanguageTag(bookIdValue)
                        }.getOrNull()
                    }
                } else {
                    null
                }

            if (loadedTokensDeferred != null && languageTagDeferred != null) {
                val loadedTokens = loadedTokensDeferred.await()
                val loadedLanguageTag = languageTagDeferred.await()
                value = value.withResolvedContent(loadedTokens, loadedLanguageTag)
            }

            val chapters = chaptersDeferred.await()
            value =
                value.copy(
                    chapterCount = chapters?.size ?: (chapterIndex + 1),
                    linearChapterIndices = chapters?.filter { it.isLinear }?.map { it.index },
                    savedResumePosition = savedResumePositionDeferred.await(),
                    metadataResolved = true,
                )
        }
    }

    return routeData
}

internal fun RsvpRouteData.withResolvedContent(
    loadedTokens: List<Token>,
    loadedLanguageTag: String?,
): RsvpRouteData =
    copy(
        tokens = loadedTokens.takeIf { it.isNotEmpty() } ?: tokens,
        languageTag = loadedLanguageTag,
        tokensResolved = true,
        languageResolved = true,
    )
