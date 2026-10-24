package com.kairo.reader.ui.tutorial

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartingTutorialCoordinatorTest {
    @Test
    fun savedLaunchContextRestoresReaderAndRsvpStepsAndPositions() {
        val context = StartingTutorialLaunchContext("book/with spaces", 2, 47)
        val scope = SaverScope { it is String || it is Int }
        val saved = with(StartingTutorialLaunchContextSaver) { scope.save(context) }
        val restored = StartingTutorialLaunchContextSaver.restore(requireNotNull(saved))
        assertEquals(context, restored)
        val steps = startingTutorialSteps(includeReaderAndRsvp = true)
        val restoredSteps = startingTutorialSteps(includeReaderAndRsvp = restored != null)
        listOf(StartingTutorialRoute.READER, StartingTutorialRoute.RSVP).forEach { route ->
            val index = steps.indexOfFirst { it.route == route }
            assertEquals(index, clampTutorialStepIndex(index, restoredSteps))
            assertEquals(steps[index], restoredSteps[index])
            assertEquals(steps.getOrNull(index - 1), restoredSteps.getOrNull(index - 1))
            assertEquals(steps.getOrNull(index + 1), restoredSteps.getOrNull(index + 1))
        }
    }

    @Test
    fun clampTutorialStepIndex_keepsValidRestoredIndex() {
        val steps = startingTutorialSteps(includeReaderAndRsvp = false)

        assertEquals(2, clampTutorialStepIndex(2, steps))
    }

    @Test
    fun clampTutorialStepIndex_clampsRestoredLongSessionIndexToCurrentSteps() {
        val shorterSteps = startingTutorialSteps(includeReaderAndRsvp = false)
        val longerSteps = startingTutorialSteps(includeReaderAndRsvp = true)
        val restoredIndexFromLongerSession = longerSteps.lastIndex

        assertEquals(
            shorterSteps.lastIndex,
            clampTutorialStepIndex(restoredIndexFromLongerSession, shorterSteps),
        )
    }

    @Test
    fun clampTutorialStepIndex_returnsNullForEmptyStepList() {
        assertNull(clampTutorialStepIndex(3, emptyList()))
    }
}
