package app.nextsay.overlay

import app.nextsay.context.ChatContext
import app.nextsay.context.MessageRole

internal fun continuationNotice(context: ChatContext): String? =
    when {
        context.messages.lastOrNull()?.text == "[图片]" -> "已识别图片消息，但未读取图片内容；以下回复仅参考文字上下文"
        context.messages.lastOrNull()?.role == MessageRole.ME -> "最后一条是我发的，以下为补充表达"
        else -> null
    }

class OverlayStatusPresenter {
    fun render(state: OverlayState): String = when (state) {
        is OverlayState.Preview -> "请检查下面将要上传的可见上下文"
        is OverlayState.Loading -> "正在生成，请稍候"
        is OverlayState.Results -> continuationNotice(state.context) ?: "选择一条写入当前输入框"
        is OverlayState.Error -> state.message
        OverlayState.Idle -> ""
    }
}
