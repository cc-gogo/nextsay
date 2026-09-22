package app.nextsay.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LocalCrashHandlerTest {
    @Test
    fun `records sanitized crash and delegates exactly once`() {
        val recorder = MemoryDiagnosticRecorder()
        var delegated: Throwable? = null
        var delegateCalls = 0
        val handler = LocalCrashHandler(
            recorder = recorder,
            eventFactory = factory(),
            delegate = Thread.UncaughtExceptionHandler { _, error ->
                delegateCalls += 1
                delegated = error
            },
        )
        val error = IllegalStateException("Bearer secret")
        error.stackTrace = arrayOf(
            StackTraceElement("app.nextsay.Main", "run", "Main.kt", 8),
        )

        handler.uncaughtException(Thread.currentThread(), error)

        assertEquals(1, recorder.events().size)
        assertEquals("java.lang.IllegalStateException", recorder.events().single().exceptionClass)
        assertEquals(listOf("app.nextsay.Main.run(Main.kt:8)"), recorder.events().single().stackFrames)
        assertEquals(1, delegateCalls)
        assertSame(error, delegated)
    }

    @Test
    fun `recorder failure still delegates exactly once`() {
        var delegateCalls = 0
        val handler = LocalCrashHandler(
            recorder = ThrowingDiagnosticRecorder(),
            eventFactory = factory(),
            delegate = Thread.UncaughtExceptionHandler { _, _ -> delegateCalls += 1 },
        )

        handler.uncaughtException(Thread.currentThread(), RuntimeException("private"))

        assertEquals(1, delegateCalls)
    }

    private fun factory() = DiagnosticEventFactory(
        metadataProvider = { DiagnosticMetadata("0.1.0", "test", "14", "Test Device") },
        nowMillis = { 1_000L },
        newId = { "crash-1" },
    )

    private class MemoryDiagnosticRecorder : DiagnosticRecorder {
        private val values = mutableListOf<DiagnosticEvent>()
        override fun record(event: DiagnosticEvent) {
            values += event
        }
        override fun events(): List<DiagnosticEvent> = values.toList()
        override fun find(id: String): DiagnosticEvent? = values.firstOrNull { it.id == id }
        override fun clear() = values.clear()
    }

    private class ThrowingDiagnosticRecorder : DiagnosticRecorder {
        override fun record(event: DiagnosticEvent) = error("storage failed")
        override fun events(): List<DiagnosticEvent> = emptyList()
        override fun find(id: String): DiagnosticEvent? = null
        override fun clear() = Unit
    }
}
