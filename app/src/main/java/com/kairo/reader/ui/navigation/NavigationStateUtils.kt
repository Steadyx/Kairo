@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.nearestWordIndex
import com.kairo.reader.core.model.wordIndexForToken
import com.kairo.reader.core.rsvp.RsvpEstimatedReadingPace
import com.kairo.reader.core.rsvp.RsvpPaceEstimationOptions
import com.kairo.reader.ui.rsvp.RsvpResumePoint
import kotlinx.coroutines.withContext

internal data class RsvpReturnTarget(val chapterIndex: Int, val tokenIndex: Int, val resumeCursor: Int,)

internal fun resolveWordIndex(
    wordCountByToken: IntArray?,
    tokenIndex: Int,
): Int {
    if (wordCountByToken == null || wordCountByToken.isEmpty()) return -1
    return wordIndexForToken(wordCountByToken, tokenIndex)
}

@Composable
internal fun rememberReaderEstimatedWpm(
    baseConfig: RsvpConfig,
    fallbackEstimatedWpm: Int,
    dispatcherProvider: DispatcherProvider,
    languageTag: String? = null,
    paceOptions: RsvpPaceEstimationOptions = RsvpPaceEstimationOptions.DEFAULT,
): Int {
    val estimatedWpm by produceState(
        initialValue = fallbackEstimatedWpm,
        baseConfig,
        fallbackEstimatedWpm,
        languageTag,
        paceOptions,
    ) {
        value =
            withContext(dispatcherProvider.default) {
                RsvpEstimatedReadingPace.estimateWpm(
                    config = baseConfig,
                    fallbackEstimatedWpm = fallbackEstimatedWpm,
                    languageTag = languageTag,
                    paceOptions = paceOptions,
                )
            }
    }
    return estimatedWpm
}

internal fun resolveRsvpReturnTarget(
    resumePoint: RsvpResumePoint,
    currentChapterIndex: Int,
    chapterCount: Int,
    currentChapterTokens: List<Token>,
): RsvpReturnTarget {
    val lastTokenIndex = currentChapterTokens.lastIndex
    val nextChapterIndex =
        if (resumePoint.tokenIndex > lastTokenIndex && currentChapterIndex < chapterCount - 1) {
            currentChapterIndex + 1
        } else {
            currentChapterIndex
        }
    if (nextChapterIndex != currentChapterIndex) {
        return RsvpReturnTarget(
            chapterIndex = nextChapterIndex,
            tokenIndex = 0,
            resumeCursor = -1,
        )
    }

    val tokenIndex =
        when {
            currentChapterTokens.isEmpty() -> resumePoint.tokenIndex.coerceAtLeast(0)
            resumePoint.tokenIndex > lastTokenIndex ->
                currentChapterTokens.nearestWordIndex(lastTokenIndex)
            else -> currentChapterTokens.nearestWordIndex(resumePoint.tokenIndex.coerceAtLeast(0))
        }
    val resumeCursor =
        if (resumePoint.tokenIndex > lastTokenIndex) {
            -1
        } else {
            resumePoint.resumeCursor
        }
    return RsvpReturnTarget(
        chapterIndex = resumePoint.chapterIndex ?: currentChapterIndex,
        tokenIndex = tokenIndex,
        resumeCursor = resumeCursor,
    )
}
