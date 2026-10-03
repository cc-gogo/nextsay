package app.nextsay.capture

import app.nextsay.context.ScreenRect
import org.junit.Assert.*
import org.junit.Test

class CaptureVisibilityTest {
    @Test fun overlayInBlankSpaceBelowACompleteBubbleDoesNotBlockNewMessage() {
        val region = CaptureVisibility.latestTail(ScreenRect(12, 260, 48, 296), 360, 750,
            listOf(ScreenRect(52, 260, 230, 320)))
        assertFalse(CaptureVisibility.blocked(region, listOf(ScreenRect(150, 380, 300, 440))))
    }
    @Test fun contourEndingAtAnOverlayStillCountsAsOccludedNotACompleteShortMessage() {
        val region = CaptureVisibility.latestTail(ScreenRect(12, 260, 48, 296), 360, 750,
            listOf(ScreenRect(52, 260, 230, 350)))
        assertTrue(CaptureVisibility.blocked(region, listOf(ScreenRect(150, 350, 300, 420))))
    }
    @Test fun latestAvatarRegionIncludesLowerLinesEvenIfBubbleContourIsMissing() {
        val region = CaptureVisibility.latestTail(ScreenRect(12, 260, 48, 296), 360, 750)
        assertTrue(CaptureVisibility.blocked(region, listOf(ScreenRect(150, 350, 300, 420))))
        assertFalse(CaptureVisibility.blocked(region, listOf(ScreenRect(150, 100, 300, 200))))
    }
    @Test fun fullyCoveredLatestMessageCannotBeTreatedAsNoNewMessage() {
        assertTrue(CaptureVisibility.blocked(ScreenRect(156, 1500, 800, 1650), listOf(ScreenRect(100, 1200, 900, 1800))))
    }
    @Test fun overlayAboveTheLastMessageDoesNotBlockItsRead() {
        assertFalse(CaptureVisibility.blocked(ScreenRect(156, 1500, 800, 1650), listOf(ScreenRect(100, 300, 900, 1100))))
    }
    @Test fun partialOcclusionAlsoRequiresRecheck() {
        assertTrue(CaptureVisibility.blocked(ScreenRect(156, 1500, 800, 1650), listOf(ScreenRect(700, 1300, 1000, 1550))))
    }
    @Test fun reliableBubbleRoleOverridesEdgeIntersection() {
        assertFalse(CaptureVisibility.blocked(
            ScreenRect(156, 1500, 800, 1650),
            listOf(ScreenRect(700, 1300, 1000, 1550)),
            reliableRole = true,
        ))
    }
}
