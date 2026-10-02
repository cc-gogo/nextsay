package app.nextsay.diagnostics

import com.google.gson.Gson

class DiagnosticFormatter(
    private val gson: Gson = Gson(),
) {
    fun compact(rawEvent: DiagnosticEvent): String = buildString {
        val event = DiagnosticEventSanitizer.sanitize(rawEvent)
        appendLine("NextSay 诊断信息")
        appendLine("错误编号：${event.id}")
        appendLine("时间：${event.timestampMillis}")
        appendLine("事件：${event.type}")
        appendLine("入口：${event.surface}")
        event.errorCode?.let { appendLine("错误代码：$it") }
        event.providerHost?.let { appendLine("API 域名：$it") }
        event.model?.let { appendLine("模型：$it") }
        event.httpStatus?.let { appendLine("HTTP 状态：$it") }
        event.durationMillis?.let { appendLine("耗时：${it}ms") }
        event.finishReason?.let { appendLine("结束原因：$it") }
        event.contentState?.let { appendLine("回答状态：$it") }
        event.reasoningPresent?.let { appendLine("包含思考输出：${if (it) "是" else "否"}") }
        appendLine("App：${event.appVersion} (${event.buildType})")
        append("设备：${event.device} / Android ${event.androidVersion}")
    }

    fun export(events: List<DiagnosticEvent>): String = buildString {
        appendLine("NextSay privacy-safe diagnostics")
        events.forEach { appendLine(gson.toJson(DiagnosticEventSanitizer.sanitize(it))) }
    }
}
