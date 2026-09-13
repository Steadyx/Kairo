package com.kairo.reader.ui.settings

import com.kairo.reader.core.model.CustomTheme
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.SavedTheme
import com.kairo.reader.core.model.ThemeDesign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStudioStateTest {
    private val original = ThemeStudioDraft(ThemeDesign(readerFont = RsvpFontFamily.LORA), emptyList())

    @Test
    fun aWholeSliderGestureIsOneUndoStepAndRetainsFonts() {
        val state = ThemeStudioState(original)
        state.beginEdit()
        repeat(40) { state.design(state.draft.design.copy(custom = CustomTheme(background = it))) }
        state.endEdit()
        state.undo()
        assertEquals(original, state.draft)
        assertFalse(state.canUndo)
    }

    @Test
    fun collectionDeletionAndRevertAreUndoableWithoutLosingDesigns() {
        val saved = SavedTheme("one", "Evening", original.design)
        val state = ThemeStudioState(original.copy(saved = listOf(saved)))
        state.change(state.draft.copy(saved = emptyList()))
        state.undo()
        assertEquals(listOf(saved), state.draft.saved)
        state.reset(original)
        assertTrue(state.canUndo)
        state.undo()
        assertEquals(listOf(saved), state.draft.saved)
    }

    @Test
    fun resetRefreshesIncompleteEditorEvenWithUnchangedDesign() {
        val state = ThemeStudioState(original)
        state.reset(original)
        assertEquals(1, state.revision)
        assertFalse(state.canUndo)
    }

    @Test
    fun savingLocksMutationAndHistory() {
        val state = ThemeStudioState(original)
        state.design(ThemeDesign())
        state.locked = true
        val snapshot = state.draft
        state.undo()
        state.reset(original)
        assertEquals(snapshot, state.draft)
    }
}
