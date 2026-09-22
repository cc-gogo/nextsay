package app.nextsay.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticFormatterTest {
    @Test
    fun `export contains safe provider metadata and error correlation`() {
        val text = DiagnosticFormatter().export(
            listOf(
                diagnosticEvent(
                    id = "evt-7",
                    host = "api.deepseek.com",
                    model = "deepseek-chat",
                    errorCode = "API-AUTH",
                ),
            ),
        )

        assertTrue(text.contains("evt-7"))
        assertTrue(text.contains("api.deepseek.com"))
        assertTrue(text.contains("deepseek-chat"))
        assertTrue(text.contains("API-AUTH"))
        assertFalse(text.contains("Authorization"))
    }

    @Test
    fun `compact summary omits absent fields and arbitrary labels`() {
        val text = DiagnosticFormatter().compact(diagnosticEvent(id = "evt-8"))

        assertTrue(text.contains("evt-8"))
        assertFalse(text.contains("API Key"))
        assertFalse(text.contains("聊天内容"))
    }
}
