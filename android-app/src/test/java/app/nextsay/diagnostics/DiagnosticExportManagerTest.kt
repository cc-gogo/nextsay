package app.nextsay.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticExportManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `writes UTF-8 report with no secret-bearing source fields`() {
        val output = temporaryFolder.newFile("report.txt")

        DiagnosticExportManager(DiagnosticFormatter()).write(output, listOf(safeEvent()))

        val text = output.readText(Charsets.UTF_8)
        assertTrue(text.contains("api.deepseek.com"))
        assertTrue(text.contains("APP_CRASHED"))
        assertFalse(text.contains("Bearer"))
        assertFalse(text.contains("聊天内容"))
    }

    private fun safeEvent() = DiagnosticEvent(
        id = "event-1",
        timestampMillis = 1_000L,
        type = DiagnosticEventType.APP_CRASHED,
        surface = DiagnosticSurface.MAIN,
        appVersion = "0.1.0",
        buildType = "test",
        androidVersion = "14",
        device = "Test Device",
        providerHost = "api.deepseek.com",
        exceptionClass = "java.lang.IllegalStateException",
        stackFrames = listOf("app.nextsay.Main.run(Main.kt:1)"),
    )
}
