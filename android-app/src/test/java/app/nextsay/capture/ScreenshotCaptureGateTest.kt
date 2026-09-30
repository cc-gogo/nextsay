package app.nextsay.capture

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotCaptureGateTest {
    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `rejects a second capture while the first is running`() = runTest {
        val gate = ScreenshotCaptureGate()
        val release = CompletableDeferred<Unit>()
        val first = async { gate.run { release.await(); "first" } }
        runCurrent()

        val second = gate.run { "second" }
        release.complete(Unit)

        assertNull(second)
        assertEquals("first", first.await()!!.getOrThrow())
    }

    @Test
    fun `releases gate after capture throws`() = runTest {
        val gate = ScreenshotCaptureGate()

        val failed = gate.run<String> { error("capture failed") }
        val retried = gate.run { "ok" }

        assertTrue(failed!!.isFailure)
        assertEquals("ok", retried!!.getOrThrow())
    }
}
