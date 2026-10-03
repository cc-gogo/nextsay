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
        event.triggerReason?.let { appendLine("触发原因：$it") }
        event.memoryIncluded?.let { appendLine("包含所选记忆：$it") }
        event.captureApp?.let { appendLine("读取应用：$it") }
        event.captureStage?.let { appendLine("读取环节：$it") }
        event.captureNodeCount?.let { appendLine("控件数量：$it") }
        event.captureTextCount?.let { appendLine("文字块数量：$it") }
        event.automaticState?.let { appendLine("自动生成状态：$it") }
        event.keyboardVisible?.let { appendLine("键盘可见：$it") }
        event.captureBottom?.let { appendLine("读取下边界：$it") }
        event.pendingIncoming?.let { appendLine("待处理消息：$it") }
        event.exceptionClass?.let { appendLine("异常类型：$it") }
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
