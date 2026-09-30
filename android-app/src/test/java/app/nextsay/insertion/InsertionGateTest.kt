package app.nextsay.insertion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertionGateTest {
    @Test
    fun `rejects repeat submission until current insertion finishes`() {
        val gate = InsertionGate()

        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        gate.finish()
        assertTrue(gate.tryStart())
    }
}
