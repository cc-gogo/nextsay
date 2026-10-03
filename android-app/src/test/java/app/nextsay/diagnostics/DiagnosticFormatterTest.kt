package app.nextsay.diagnostics

import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticFormatterTest {
    @Test fun selfAutomaticTriggerIsExportedWithoutArbitraryTriggerStrings() {
        val event = diagnosticEvent(id = "self-safe").copy(triggerReason = "auto_self", captureStage = "ocr_start", durationMillis = 230)
        val text = DiagnosticFormatter().compact(event)
        assertTrue(text.contains("auto_self"))
        assertTrue(text.contains("230ms"))
        assertFalse(DiagnosticFormatter().export(listOf(event.copy(triggerReason = "私密消息"))).contains("私密消息"))
    }
    @Test fun automaticSkipReasonIsVisibleButArbitraryChatContentIsRejected() {
        val event = Gson().fromJson(Gson().toJson(diagnosticEvent(id = "auto-safe")).dropLast(1) +
            """, "automaticState":"tail_obscured","keyboardVisible":true,"captureBottom":1420,"pendingIncoming":true}""",
            DiagnosticEvent::class.java)
        val text = DiagnosticFormatter().compact(event)
        assertTrue(text.contains("自动生成状态：tail_obscured"))
        assertTrue(text.contains("键盘可见：true"))
        assertTrue(text.contains("读取下边界：1420"))
        assertTrue(text.contains("待处理消息：true"))
        val bad = Gson().fromJson(Gson().toJson(event).replace("tail_obscured", "对方的私密聊天"), DiagnosticEvent::class.java)
        assertFalse(DiagnosticFormatter().export(listOf(bad)).contains("私密聊天"))
    }
    @Test fun `header obstruction is exported as safe capture stage without chat text`() {
        val event = diagnosticEvent(id = "header-test").copy(captureStage = "header_obscured", captureApp = "wechat")
        assertTrue(DiagnosticFormatter().compact(event).contains("读取环节：header_obscured"))
    }
    @Test
    fun `capture failure includes safe stage and counts but rejects arbitrary strings`() {
        fun stored(stage: String) = Gson().fromJson(
            Gson().toJson(diagnosticEvent(id = "capture-test")).dropLast(1) +
                """, "captureStage":"$stage","captureApp":"qq","captureNodeCount":42,"captureTextCount":0}""",
            DiagnosticEvent::class.java,
        )
        val text = DiagnosticFormatter().compact(stored("ocr_parse"))
        assertTrue(text.contains("读取环节：ocr_parse"))
        assertTrue(text.contains("读取应用：qq"))
        assertTrue(text.contains("控件数量：42"))
        assertTrue(text.contains("文字块数量：0"))
        assertFalse(DiagnosticFormatter().export(listOf(stored("private-chat-text"))).contains("private-chat-text"))
    }
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
