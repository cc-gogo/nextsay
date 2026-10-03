package app.nextsay.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContextNormalizerTest {
    @Test
    fun `verified qq mjo messages exclude toolbar and use avatar ownership for wide self replies`() {
        val context = ContextNormalizer().normalize("com.tencent.mobileqq", listOf(
            snapshot(text = "测试对象", viewId = "com.tencent.mobileqq:id/34v", left = 175, top = 104, right = 310, bottom = 166),
            snapshot(text = "在线 - 4G", viewId = "com.tencent.mobileqq:id/j64", left = 175, top = 171),
            snapshot(text = "对方的问题", viewId = "com.tencent.mobileqq:id/mjo", left = 140, top = 500, right = 500, bottom = 650),
            snapshot(contentDescription = "测试对象的资料卡", left = 32, top = 510, right = 140, bottom = 618),
            snapshot(text = "我的长回复横跨聊天页面中部", viewId = "com.tencent.mobileqq:id/mjo", left = 152, top = 1842, right = 940, bottom = 2116),
            snapshot(contentDescription = "我的资料卡", left = 940, top = 1863, right = 1048, bottom = 1971),
        ), 1080)
        assertEquals(listOf("对方的问题", "我的长回复横跨聊天页面中部"), context.messages.map { it.text })
        assertEquals(listOf(MessageRole.OTHER, MessageRole.ME), context.messages.map { it.role })
    }
    private val normalizer = ContextNormalizer()

    @Test
    fun `orders messages from top to bottom`() {
        val context = normalizer.normalize(
            "com.tencent.mm",
            listOf(node("later", 80, 300), node("first", 80, 100)),
            1000,
        )

        assertEquals(listOf("first", "later"), context.messages.map { it.text })
    }

    @Test
    fun `removes duplicate parent and child text`() {
        val context = normalizer.normalize(
            "com.tencent.mm",
            listOf(node("same", 80, 100), node("same", 100, 102)),
            1000,
        )

        assertEquals(listOf("same"), context.messages.map { it.text })
    }

    @Test
    fun `keeps identical text in separate message bubbles`() {
        val context = normalizer.normalize(
            "com.tencent.mm",
            listOf(node("好的", 80, 100), node("好的", 80, 300)),
            1000,
        )

        assertEquals(listOf("好的", "好的"), context.messages.map { it.text })
    }

    @Test
    fun `filters timestamps and chat chrome`() {
        val context = normalizer.normalize(
            "com.tencent.mm",
            listOf(node("12:30", 500, 40), node("返回", 30, 20), node("有效消息", 80, 100)),
            1000,
        )

        assertEquals(listOf("有效消息"), context.messages.map { it.text })
    }

    @Test
    fun `keeps editable text as draft instead of a message`() {
        val context = normalizer.normalize(
            "com.tencent.mm",
            listOf(node("收到", 80, 100), node("正在输入", 200, 700, editable = true, focused = true)),
            1000,
        )

        assertEquals(listOf("收到"), context.messages.map { it.text })
        assertEquals("正在输入", context.draft)
    }

    @Test
    fun `infers other left me right and unknown near center`() {
        val context = normalizer.normalize(
            "com.tencent.mm",
            listOf(node("left", 50, 100), node("middle", 460, 200), node("right", 750, 300)),
            1000,
        )

        assertEquals(listOf(MessageRole.OTHER, MessageRole.UNKNOWN, MessageRole.ME), context.messages.map { it.role })
    }

    @Test
    fun `rejects unsupported packages`() {
        assertThrows(UnsupportedChatPackageException::class.java) {
            normalizer.normalize("com.example.other", listOf(node("hello", 80, 100)), 1000)
        }
    }

    @Test
    fun `qq keeps message body nodes and excludes profile cards and header text`() {
        val context = normalizer.normalize(
            "com.tencent.mobileqq",
            listOf(
                snapshot(contentDescription = "小明的资料卡", left = 32, top = 300, right = 140, bottom = 408),
                snapshot(text = "小明", viewId = "com.tencent.mobileqq:id/304", left = 175, top = 104),
                snapshot(text = "在线 - 4G", viewId = "com.tencent.mobileqq:id/j64", left = 175, top = 171),
                snapshot(text = "要做什么？", viewId = "com.tencent.mobileqq:id/mjh", left = 140, top = 300, right = 488, bottom = 454),
            ),
            1080,
        )

        assertEquals(listOf("要做什么？"), context.messages.map { it.text })
    }

    @Test
    fun `qq keeps message body nodes from current client`() {
        val context = normalizer.normalize(
            "com.tencent.mobileqq",
            listOf(
                snapshot(contentDescription = "小明的资料卡", left = 32, top = 300, right = 140, bottom = 408),
                snapshot(text = "新版正文", viewId = "com.tencent.mobileqq:id/mjn", left = 140, top = 300, right = 488, bottom = 454),
            ),
            1080,
        )

        assertEquals(listOf("新版正文"), context.messages.map { it.text })
    }

    @Test
    fun `qq uses overlapping profile card to infer message side`() {
        val context = normalizer.normalize(
            "com.tencent.mobileqq",
            listOf(
                snapshot(contentDescription = "小明的资料卡", left = 32, top = 300, right = 140, bottom = 408),
                snapshot(text = "对方消息", viewId = "com.tencent.mobileqq:id/mjh", left = 140, top = 300, right = 488, bottom = 454),
                snapshot(text = "我的回复很长所以横跨屏幕中部", viewId = "com.tencent.mobileqq:id/mjh", left = 152, top = 500, right = 940, bottom = 654),
                snapshot(contentDescription = "我的资料卡", left = 940, top = 521, right = 1048, bottom = 629),
            ),
            1080,
        )

        assertEquals(listOf(MessageRole.OTHER, MessageRole.ME), context.messages.map { it.role })
    }

    private fun node(
        text: String,
        left: Int,
        top: Int,
        editable: Boolean = false,
        focused: Boolean = false,
    ) = NodeSnapshot(
        text = text,
        contentDescription = null,
        className = "android.widget.TextView",
        viewId = null,
        bounds = ScreenRect(left, top, left + 180, top + 60),
        editable = editable,
        focused = focused,
        password = false,
    )

    private fun snapshot(
        text: String? = null,
        contentDescription: String? = null,
        viewId: String? = null,
        left: Int,
        top: Int,
        right: Int = left + 180,
        bottom: Int = top + 60,
    ) = NodeSnapshot(
        text = text,
        contentDescription = contentDescription,
        className = "android.widget.TextView",
        viewId = viewId,
        bounds = ScreenRect(left, top, right, bottom),
        editable = false,
        focused = false,
        password = false,
    )
}
