package com.kairo.reader.ui.navigation

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpLaunchSnapshotStoreTest {
    @Test
    fun put_keepsOnlyTwoMostRecentSnapshots() {
        val first = listOf(word("One"))
        val second = listOf(word("Two"))
        val third = listOf(word("Three"))

        RsvpLaunchSnapshotStore.put(bookId = "bounded-1", chapterIndex = 0, tokens = first)
        RsvpLaunchSnapshotStore.put(bookId = "bounded-2", chapterIndex = 0, tokens = second)
        RsvpLaunchSnapshotStore.put(bookId = "bounded-3", chapterIndex = 0, tokens = third)

        assertTrue(RsvpLaunchSnapshotStore.tokensFor("bounded-1", 0).isEmpty())
        assertEquals(second, RsvpLaunchSnapshotStore.tokensFor("bounded-2", 0))
        assertEquals(third, RsvpLaunchSnapshotStore.tokensFor("bounded-3", 0))

        RsvpLaunchSnapshotStore.clear("bounded-2", 0)
        RsvpLaunchSnapshotStore.clear("bounded-3", 0)
    }

    @Test
    fun put_withEmptyTokensClearsExistingSnapshot() {
        val tokens = listOf(word("Cached"))

        RsvpLaunchSnapshotStore.put(bookId = "empty-clear", chapterIndex = 1, tokens = tokens)
        RsvpLaunchSnapshotStore.put(bookId = "empty-clear", chapterIndex = 1, tokens = emptyList())

        assertTrue(RsvpLaunchSnapshotStore.tokensFor("empty-clear", 1).isEmpty())
    }

    @Test
    fun snapshotDistinguishesResolvedNullLanguageFromNoSnapshot() {
        val tokens = listOf(word("Cached"))
        RsvpLaunchSnapshotStore.put(
            bookId = "resolved-null",
            chapterIndex = 0,
            tokens = tokens,
            languageTag = null,
        )

        val snapshot = RsvpLaunchSnapshotStore.snapshotFor("resolved-null", 0)

        assertNotNull(snapshot)
        assertEquals(tokens, requireNotNull(snapshot).tokens)
        assertNull(snapshot.languageTag)
        assertNull(RsvpLaunchSnapshotStore.snapshotFor("missing", 0))
        RsvpLaunchSnapshotStore.clear("resolved-null", 0)
    }

    @Test
    fun resolvedContentWaitsForResumePositionAndChapterCount() {
        val snapshotTokens = listOf(word("Snapshot"))
        val loadedTokens = listOf(word("Loaded"))
        val unresolved =
            RsvpRouteData(
                tokens = snapshotTokens,
                chapterCount = 1,
                savedResumePosition = null,
                languageTag = null,
                tokensResolved = false,
                languageResolved = false,
            )

        assertFalse(unresolved.isReady)
        val resolvedEnglish = unresolved.withResolvedContent(loadedTokens, "en-GB")
        val resolvedNull = unresolved.withResolvedContent(emptyList(), null)

        assertFalse(resolvedEnglish.isReady)
        assertEquals(loadedTokens, resolvedEnglish.tokens)
        assertEquals("en-GB", resolvedEnglish.languageTag)
        assertFalse(resolvedNull.isReady)
        assertEquals(snapshotTokens, resolvedNull.tokens)
        assertNull(resolvedNull.languageTag)
    }

    @Test
    fun cachedLaunchWaitsForMetadataBeforePlaybackCanCaptureItsCursor() {
        val initial = RsvpRouteData(
            tokens = listOf(word("Snapshot")),
            chapterCount = 1,
            savedResumePosition = null,
            languageTag = "en",
            tokensResolved = true,
            languageResolved = true,
        )
        assertFalse(initial.isReady)
        val position = ReadingPosition(BookId("book"), 0, 3, rsvpResumeCursor = 99)
        val ready = initial.copy(chapterCount = 4, savedResumePosition = position, metadataResolved = true)
        assertTrue(ready.isReady)
        assertEquals(position, ready.savedResumePosition)
        assertEquals(4, ready.chapterCount)
        assertTrue(ready.copy(savedResumePosition = null).isReady)
    }

    private fun word(text: String): Token = Token(text = text, type = TokenType.WORD)
}
