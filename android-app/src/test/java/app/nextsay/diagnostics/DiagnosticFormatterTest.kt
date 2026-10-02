package app.nextsay.diagnostics

import com.google.gson.Gson
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

    @Test
    fun `response metadata is visible in compact diagnostics without response text`() {
        val event = Gson().fromJson(
            Gson().toJson(diagnosticEvent(id = "evt-9")).dropLast(1) +
                """, "finishReason":"length","contentState":"blank","reasoningPresent":true}""",
            DiagnosticEvent::class.java,
        )

        val text = DiagnosticFormatter().compact(event)

        assertTrue(text.contains("结束原因：length"))
        assertTrue(text.contains("回答状态：blank"))
        assertTrue(text.contains("包含思考输出：是"))
    }

    @Test
    fun `arbitrary response metadata is removed when presenting stored logs`() {
        val event = Gson().fromJson(
            Gson().toJson(diagnosticEvent(id = "evt-10")).dropLast(1) +
                """, "finishReason":"private-body","contentState":"secret-key","reasoningPresent":false}""",
            DiagnosticEvent::class.java,
        )

        val text = DiagnosticFormatter().export(listOf(event))

        assertFalse(text.contains("private-body"))
        assertFalse(text.contains("secret-key"))
        assertTrue(text.contains("\"reasoningPresent\":false"))
    }
}
