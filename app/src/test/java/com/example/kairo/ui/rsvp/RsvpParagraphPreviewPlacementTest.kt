package com.example.kairo.ui.rsvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpParagraphPreviewPlacementTest {
    @Test
    fun choosesAboveOutsidePositioningWhenBelowNoLongerFits() {
        val placement =
            resolveParagraphPreviewPlacement(
                currentSide = PreviewSide.BELOW,
                isPositioningMode = false,
                anchorTop = 420f,
                previewHeightPx = 200f,
                preferredOffsetPx = 180f,
                edgePaddingPx = 20f,
                protectedTop = 430f,
                protectedBottom = 520f,
                maxTop = 580f,
                switchHysteresisPx = 18f,
                switchOverlapThresholdPx = 8f,
            )

        assertEquals(PreviewSide.ABOVE, placement.side)
        assertTrue(placement.topPx < 420f)
    }

    @Test
    fun keepsPreviewClearOfBottomControlsWhenPaused() {
        val placement =
            resolveParagraphPreviewPlacement(
                currentSide = PreviewSide.BELOW,
                isPositioningMode = false,
                anchorTop = 260f,
                previewHeightPx = 200f,
                preferredOffsetPx = 180f,
                edgePaddingPx = 20f,
                protectedTop = 290f,
                protectedBottom = 380f,
                maxTop = 340f,
                switchHysteresisPx = 18f,
                switchOverlapThresholdPx = 8f,
            )

        assertEquals(PreviewSide.ABOVE, placement.side)
        assertTrue(placement.topPx + 200f <= 540f)
    }
}
