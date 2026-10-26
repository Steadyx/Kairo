package com.kairo.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSearchPageNavigationTest {
    @Test
    fun movesAcrossPageBoundariesAndStopsAtEnds() {
        assertEquals(ReaderSearchPageTarget(1, 0), readerSearchPageTarget(59, 60, 0, true, forward = true))
        assertEquals(ReaderSearchPageTarget(0, -1), readerSearchPageTarget(0, 6, 1, false, forward = false))
        assertEquals(ReaderSearchPageTarget(1, 3), readerSearchPageTarget(2, 6, 1, false, forward = true))
        assertNull(readerSearchPageTarget(0, 60, 0, true, forward = false))
        assertNull(readerSearchPageTarget(5, 6, 1, false, forward = true))
    }
}
