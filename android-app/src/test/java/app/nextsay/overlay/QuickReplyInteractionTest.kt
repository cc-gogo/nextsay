package app.nextsay.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickReplyInteractionTest {
    @Test
    fun `submit preserves arbitrary text for retry`() {
        val interaction = QuickReplyInteraction()

        assertEquals("帮我委婉拒绝", interaction.submit("帮我委婉拒绝"))
        assertEquals("帮我委婉拒绝", interaction.retryText())
    }

    @Test
    fun `empty text is a valid request`() {
        val interaction = QuickReplyInteraction()

        assertEquals("", interaction.submit(""))
        assertEquals("", interaction.retryText())
    }

    @Test
    fun `outside action does not dismiss persistent quick window`() {
        val interaction = QuickReplyInteraction()

        assertFalse(interaction.shouldDismiss(0))
        assertFalse(interaction.shouldDismiss(1))
        assertFalse(interaction.shouldDismiss(4))
    }
}
