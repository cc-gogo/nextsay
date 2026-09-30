package app.nextsay.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationTitleExtractorTest {
    private val extractor = ConversationTitleExtractor()

    @Test
    fun `qq uses verified header title node`() {
        val title = extractor.extract(
            "com.tencent.mobileqq",
            listOf(
                node("小明", "com.tencent.mobileqq:id/304"),
                node("在线 - 4G", "com.tencent.mobileqq:id/j64"),
            ),
        )

        assertEquals("小明", title)
    }

    @Test
    fun `qq uses current client header title node`() {
        val title = extractor.extract(
            "com.tencent.mobileqq",
            listOf(
                node("新版好友", "com.tencent.mobileqq:id/32z"),
                node("在线 - 4G", "com.tencent.mobileqq:id/j64"),
            ),
        )

        assertEquals("新版好友", title)
    }

    @Test
    fun `returns null when no verified title node exists`() {
        val title = extractor.extract(
            "com.tencent.mobileqq",
            listOf(node("随便的文字", "com.tencent.mobileqq:id/unknown")),
        )

        assertNull(title)
    }

    private fun node(text: String, viewId: String) = NodeSnapshot(
        text = text,
        contentDescription = null,
        className = "android.widget.TextView",
        viewId = viewId,
        bounds = ScreenRect(100, 100, 400, 180),
        editable = false,
        focused = false,
        password = false,
    )
}
