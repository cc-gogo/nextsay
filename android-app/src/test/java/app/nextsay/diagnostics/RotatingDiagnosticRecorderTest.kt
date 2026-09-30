package app.nextsay.diagnostics

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RotatingDiagnosticRecorderTest {
    @Test
    fun `keeps newest 200 events and removes events older than seven days`() {
        val storage = MemoryDiagnosticStorage()
        val initialTime = 8L * 24 * 60 * 60 * 1000
        val sevenDays = 7L * 24 * 60 * 60 * 1000
        var now = initialTime
        val recorder = RotatingDiagnosticRecorder(
            storage, maxEvents = 200, maxBytes = 256 * 1024, nowMillis = { now },
        )
        repeat(205) { index ->
            recorder.record(diagnosticEvent(id = "e$index", timestamp = initialTime + index))
        }

        assertEquals(200, recorder.events().size)
        assertEquals("e5", recorder.events().first().id)

        now = initialTime + sevenDays + 205
        recorder.prune(nowMillis = now)

        assertTrue(recorder.events().isEmpty())
    }

    @Test
    fun `read after idle expiry prunes storage without a new event`() {
        val storage = MemoryDiagnosticStorage()
        var now = 8L * 24 * 60 * 60 * 1000
        val recorder = RotatingDiagnosticRecorder(storage, nowMillis = { now })
        recorder.record(diagnosticEvent(id = "old", timestamp = now))

        now += 8L * 24 * 60 * 60 * 1000

        assertTrue(recorder.events().isEmpty())
        assertTrue(storage.load().isEmpty())
    }

    @Test
    fun `read scrubs unsafe legacy records before they can be exported`() {
        val storage = MemoryDiagnosticStorage()
        val timestamp = 8L * 24 * 60 * 60 * 1000
        storage.save(listOf(diagnosticEvent(
            id = "legacy", timestamp = timestamp,
            model = "Authorization: Bearer review-secret-key",
        )))
        val recorder = RotatingDiagnosticRecorder(storage, nowMillis = { timestamp })

        val events = recorder.events()

        assertFalse(DiagnosticFormatter().export(events).contains("review-secret-key"))
        assertFalse(storage.load().toString().contains("review-secret-key"))
    }

    @Test
    fun `byte cap drops oldest complete events`() {
        val storage = MemoryDiagnosticStorage()
        val recorder = RotatingDiagnosticRecorder(
            storage, maxEvents = 200, maxBytes = 450,
            nowMillis = { 8L * 24 * 60 * 60 * 1000 },
        )
        repeat(10) {
            recorder.record(diagnosticEvent(id = "id-$it", model = "model-name-$it"))
        }

        assertTrue(storage.encodedSize(storage.load()) <= 450)
        assertEquals("id-9", recorder.events().last().id)
    }

    @Test
    fun `event schema has no free form secret or content fields`() {
        val names = DiagnosticEvent::class.java.declaredFields.map { it.name }.toSet()

        assertFalse(
            names.any {
                it in setOf("apiKey", "authorization", "body", "message", "prompt", "chat")
            },
        )
    }

    @Test
    fun `find clear and storage failure are safe`() {
        val storage = MemoryDiagnosticStorage()
        val recorder = RotatingDiagnosticRecorder(
            storage, nowMillis = { 8L * 24 * 60 * 60 * 1000 },
        )
        recorder.record(diagnosticEvent(id = "find-me"))

        assertEquals("find-me", recorder.find("find-me")?.id)
        assertNull(recorder.find("missing"))

        recorder.clear()
        assertTrue(recorder.events().isEmpty())

        val broken = RotatingDiagnosticRecorder(BrokenDiagnosticStorage())
        broken.record(diagnosticEvent(id = "ignored"))
        assertTrue(broken.events().isEmpty())
        assertFalse(broken.clearChecked())
    }

    private class MemoryDiagnosticStorage : DiagnosticStorage {
        private val gson = Gson()
        private var values = emptyList<DiagnosticEvent>()

        override fun load(): List<DiagnosticEvent> = values

        override fun save(events: List<DiagnosticEvent>) {
            values = events.toList()
        }

        override fun encodedSize(events: List<DiagnosticEvent>): Int =
            gson.toJson(events).toByteArray(Charsets.UTF_8).size
    }

    private class BrokenDiagnosticStorage : DiagnosticStorage {
        override fun load(): List<DiagnosticEvent> = throw IllegalStateException("broken")
        override fun save(events: List<DiagnosticEvent>) = throw IllegalStateException("broken")
        override fun encodedSize(events: List<DiagnosticEvent>): Int = 0
    }
}

internal fun diagnosticEvent(
    id: String,
    timestamp: Long = 8L * 24 * 60 * 60 * 1000,
    host: String? = null,
    model: String? = null,
    errorCode: String? = null,
) = DiagnosticEvent(
    id = id,
    timestampMillis = timestamp,
    type = DiagnosticEventType.GENERATION_FAILED,
    surface = DiagnosticSurface.OVERLAY,
    appVersion = "0.1.0",
    buildType = "debug",
    androidVersion = "14",
    device = "Test Device",
    providerScheme = host?.let { "https" },
    providerHost = host,
    model = model,
    errorCode = errorCode,
)
