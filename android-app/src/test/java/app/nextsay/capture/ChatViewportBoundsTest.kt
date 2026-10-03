package app.nextsay.capture

import app.nextsay.context.*
import org.junit.Assert.*
import org.junit.Test

class ChatViewportBoundsTest {
    private fun input(top: Int) = NodeSnapshot("", null, "android.widget.EditText", null,
        ScreenRect(100, top, 900, top+90), true, true, false)
    @Test fun keyboardDoesNotCutTheLastBubbleAboveTheActualInputRow() {
        assertEquals(1420, ChatViewportBounds.bottom(2400, 1560, listOf(input(1420)), 216))
    }
    @Test fun emptyNodesUseABoundedFallbackWithoutClippingIntoTheHeader() {
        assertEquals(2184, ChatViewportBounds.bottom(2400, null, emptyList(), 216))
        assertEquals(1384, ChatViewportBounds.bottom(2400, 1600, emptyList(), 216))
    }
    @Test fun hiddenAndOffscreenInputsCannotEraseTheWholeConversation() {
        assertEquals(1384, ChatViewportBounds.bottom(2400, 1600, listOf(input(0), input(2600)), 216))
    }
    @Test fun keyboardWindowTopIsPreferredWhenInputNodeIsMissing() {
        assertEquals(1344, ChatViewportBounds.bottom(2400, 1560, emptyList(), 216))
    }
}
