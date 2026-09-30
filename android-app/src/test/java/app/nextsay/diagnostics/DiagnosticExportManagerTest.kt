package app.nextsay.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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

    @Test
    fun `clear removes all app owned cached exports but not unrelated files`() {
        val directory = temporaryFolder.newFolder("diagnostics")
        val manager = DiagnosticExportManager(DiagnosticFormatter())
        val first = java.io.File(directory, "nextsay-diagnostics-1.txt")
        val second = java.io.File(directory, "nextsay-diagnostics-2.txt")
        val unrelated = java.io.File(directory, "notes.txt").apply { writeText("keep") }
        manager.write(first, listOf(safeEvent()))
        manager.write(second, listOf(safeEvent()))

        assertTrue(manager.clearCachedExports(directory))

        assertFalse(first.exists())
        assertFalse(second.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `export cache is bounded and failed deletion is reported`() {
        val directory = temporaryFolder.newFolder("diagnostics")
        val manager = DiagnosticExportManager(DiagnosticFormatter())
        repeat(5) { index ->
            manager.write(java.io.File(directory, "nextsay-diagnostics-$index.txt"), listOf(safeEvent()))
        }
        assertEquals(3, directory.listFiles()!!.size)

        val blocked = java.io.File(directory, "nextsay-diagnostics-blocked.txt").apply { mkdir() }
        java.io.File(blocked, "child").writeText("cannot delete nonempty directory")
        assertFalse(manager.clearCachedExports(directory))
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
