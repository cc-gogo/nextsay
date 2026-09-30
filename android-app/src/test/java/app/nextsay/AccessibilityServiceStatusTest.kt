package app.nextsay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceStatusTest {
    private val status = AccessibilityServiceStatus()
    private val target = "app.nextsay/app.nextsay.accessibility.NextSayAccessibilityService"

    @Test
    fun `reports enabled when exact component is present`() {
        assertTrue(status.isEnabled(setOf("other/Service", target), target))
    }

    @Test
    fun `does not accept a partial component match`() {
        assertFalse(status.isEnabled(setOf("app.nextsay/OtherService"), target))
    }
}
