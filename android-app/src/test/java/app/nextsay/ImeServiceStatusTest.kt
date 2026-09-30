package app.nextsay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeServiceStatusTest {
    private val status = ImeServiceStatus()
    private val target = "app.nextsay/app.nextsay.ime.NextSayInputMethodService"

    @Test
    fun `reports enabled only for exact input method component`() {
        assertTrue(status.isEnabled(setOf(target), target))
        assertTrue(
            status.isEnabled(
                setOf("app.nextsay/.ime.NextSayInputMethodService"),
                target,
            ),
        )
        assertFalse(status.isEnabled(emptySet(), target))
        assertFalse(
            status.isEnabled(
                setOf("app.nextsay.clone/app.nextsay.ime.NextSayInputMethodService"),
                target,
            ),
        )
    }
}
