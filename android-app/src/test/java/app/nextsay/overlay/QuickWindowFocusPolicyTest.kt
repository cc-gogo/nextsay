package app.nextsay.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickWindowFocusPolicyTest {
    @Test
    fun `outside handoff never hides the chat keyboard being requested`() {
        val interaction = QuickReplyInteraction()
        interaction.requestInputFocus()
        assertFalse(interaction.releaseInputFocus(hideKeyboard = false))
        assertFalse(interaction.ownsInputFocus)
    }

    @Test
    fun `copy or submit may hide only a keyboard owned by this window`() {
        val interaction = QuickReplyInteraction()
        assertFalse(interaction.releaseInputFocus())
        interaction.requestInputFocus()
        assertTrue(interaction.releaseInputFocus())
        assertFalse(interaction.releaseInputFocus())
    }
    @Test
    fun `new candidate window leaves focus with the chat`() {
        assertFalse(QuickReplyInteraction().ownsInputFocus)
    }

    @Test
    fun `only explicit instruction editing acquires focus`() {
        val interaction = QuickReplyInteraction()
        interaction.requestInputFocus()
        assertTrue(interaction.ownsInputFocus)
    }

    @Test
    fun `outside touch releases keyboard focus without dismissing candidates`() {
        val interaction = QuickReplyInteraction()
        interaction.requestInputFocus()
        interaction.releaseInputFocus()
        assertFalse(interaction.ownsInputFocus)
        assertFalse(interaction.shouldDismiss(4))
    }

    @Test
    fun `submitting instruction releases keyboard focus`() {
        val interaction = QuickReplyInteraction()
        interaction.requestInputFocus()
        interaction.submit("更简短")
        assertFalse(interaction.ownsInputFocus)
    }
}
