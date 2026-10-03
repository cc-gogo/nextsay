package app.nextsay.overlay

import android.accessibilityservice.AccessibilityService
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import app.nextsay.context.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class AutomaticCandidateStabilityUiTest {
    class Host : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
        override fun onInterrupt() = Unit
    }
    private val context = ChatContext("wechat", "com.tencent.mm", listOf(ChatMessage(MessageRole.OTHER, "测试新消息", 1f)), "", 1f)
    private fun textViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun fixture(test: (OverlayWindow, QuickReplyViews) -> Unit) {
        val host = Robolectric.buildService(Host::class.java).create()
        val overlay = OverlayWindow(host.get(), PanelCallbacks({}, { _, _ -> }, {}, {}, {}, {}), {}, {}, {}, {}, {})
        try {
            overlay.setSupportedAppActive(true)
            val quick = overlay.javaClass.getDeclaredField("quick").run { isAccessible = true; get(overlay) as QuickReplyViews }
            test(overlay, quick)
        } finally { overlay.dispose(); host.destroy() }
    }
    @Test fun backgroundUncertaintyDoesNotReplaceOrRecreateGeneratedCandidates() = fixture { overlay, quick ->
        val replies = listOf(ReplyCandidate("brief", "第一条本地候选"), ReplyCandidate("warm", "第二条本地候选"), ReplyCandidate("formal", "第三条本地候选"))
        overlay.renderQuick(OverlayState.Results(context, replies))
        val candidateViews = textViews(quick.root).filter { it.text.toString() in replies.map { r -> r.text } }
        assertEquals(3, candidateViews.size)
        repeat(3) { overlay.showAutomaticStatus("正在确认最新消息归属；不会盲目生成") }
        assertTrue(overlay.isQuickOpen)
        assertTrue(candidateViews.all { it in textViews(quick.root) })
        overlay.hideQuick()
        overlay.showAutomaticStatus("最新消息被遮挡")
        overlay.showCachedQuick()
        assertTrue(textViews(quick.root).map { it.text.toString() }.containsAll(replies.map { it.text }))
    }
    @Test fun backgroundStatusCannotReplaceLoadingOrRetryableError() = fixture { overlay, quick ->
        overlay.renderQuick(OverlayState.Loading(context))
        overlay.showAutomaticStatus("正在确认归属")
        assertTrue(textViews(quick.root).any { it.text.contains("正在生成") })
        overlay.renderQuick(OverlayState.Error(context, "本地模拟网络失败", "local-id"))
        overlay.showAutomaticStatus("正在确认归属")
        assertTrue(textViews(quick.root).any { it.text == "本地模拟网络失败" })
        assertTrue(textViews(quick.root).any { it.text == "重试" })
    }
}
