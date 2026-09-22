package app.nextsay.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CrashSanitizerTest {
    @Test
    fun `captures class and app frames but never raw message`() {
        val error = IllegalStateException("Authorization: Bearer secret-key chat=private")
        error.stackTrace = arrayOf(
            StackTraceElement("app.nextsay.provider.Client", "call", "Client.kt", 40),
            StackTraceElement("java.lang.Thread", "run", "Thread.java", 1),
        )

        val safe = CrashSanitizer().sanitize(error)

        assertEquals("java.lang.IllegalStateException", safe.exceptionClass)
        assertEquals(listOf("app.nextsay.provider.Client.call(Client.kt:40)"), safe.stackFrames)
        assertFalse(safe.toString().contains("secret-key"))
        assertFalse(safe.toString().contains("private"))
    }

    @Test
    fun `keeps at most forty application frames`() {
        val error = RuntimeException("do not retain")
        error.stackTrace = Array(50) { index ->
            StackTraceElement("app.nextsay.Feature$index", "run", "Feature.kt", index + 1)
        }

        val safe = CrashSanitizer().sanitize(error)

        assertEquals(40, safe.stackFrames.size)
        assertEquals("app.nextsay.Feature39.run(Feature.kt:40)", safe.stackFrames.last())
    }
}
