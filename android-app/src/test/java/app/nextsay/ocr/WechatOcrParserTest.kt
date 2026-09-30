package app.nextsay.ocr

import app.nextsay.context.MessageRole
import app.nextsay.context.ScreenRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WechatOcrParserTest {
    private val parser = WechatOcrParser()

    @Test
    fun `extracts centered header title and orders left right messages`() {
        val result = parser.parse(
            blocks = listOf(
                block("我晚点回复你", 610, 640, 990, 720),
                block("小明", 430, 90, 650, 155),
                block("你到哪里了", 90, 420, 430, 500),
            ),
            screenWidth = 1080,
            screenHeight = 2400,
            contentBottom = 1280,
        )!!

        assertEquals("小明", result.title)
        assertEquals(listOf("你到哪里了", "我晚点回复你"), result.context.messages.map { it.text })
        assertEquals(listOf(MessageRole.OTHER, MessageRole.ME), result.context.messages.map { it.role })
        assertTrue(result.persistable)
    }

    @Test
    fun `filters timestamps controls keyboard and low confidence text`() {
        val result = parser.parse(
            blocks = listOf(
                block("小明", 430, 90, 650, 155),
                block("12:30", 470, 280, 610, 330),
                block("8月9日 23:50", 400, 350, 680, 400),
                block("返回", 10, 90, 120, 155),
                block("以上是打招呼的消息", 330, 405, 750, 450),
                block("有效消息", 80, 480, 400, 550),
                block("模糊文字", 80, 550, 400, 620, confidence = 0.30f),
                block("qwerty", 100, 1450, 500, 1520),
            ),
            screenWidth = 1080,
            screenHeight = 2400,
            contentBottom = 1280,
        )!!

        assertEquals(listOf("有效消息"), result.context.messages.map { it.text })
    }

    @Test
    fun `long incoming line is classified by its left edge`() {
        val result = parser.parse(
            blocks = listOf(
                block("小明", 430, 90, 650, 155),
                block("我通过了你的朋友验证请求，现在我们可以开始聊天了", 145, 430, 900, 520),
            ),
            screenWidth = 1080,
            screenHeight = 2400,
            contentBottom = 1280,
        )!!

        assertEquals(MessageRole.OTHER, result.context.messages.single().role)
    }

    @Test
    fun `adjacent OCR lines from one bubble are merged`() {
        val result = parser.parse(
            blocks = listOf(
                block("小明", 430, 90, 650, 155),
                block("我通过了你的朋友验证请求，现在", 145, 430, 850, 485),
                block("我们可以开始聊天了", 145, 490, 650, 545),
            ),
            screenWidth = 1080,
            screenHeight = 2400,
            contentBottom = 1280,
        )!!

        assertEquals(listOf("我通过了你的朋友验证请求，现在我们可以开始聊天了"), result.context.messages.map { it.text })
    }

    @Test
    fun `returns null when no stable title exists`() {
        val result = parser.parse(
            blocks = listOf(block("你好", 80, 430, 400, 500)),
            screenWidth = 1080,
            screenHeight = 2400,
            contentBottom = 1280,
        )

        assertNull(result)
    }

    @Test
    fun `group title can generate but cannot persist history`() {
        val result = parser.parse(
            blocks = listOf(
                block("项目群(8)", 390, 90, 690, 155),
                block("今晚同步一下", 80, 430, 450, 500),
            ),
            screenWidth = 1080,
            screenHeight = 2400,
            contentBottom = 1280,
        )!!

        assertEquals("项目群(8)", result.title)
        assertFalse(result.persistable)
    }

    @Test
    fun `preserves requested package for OCR fallback`() {
        val result = parser.parse(
            blocks = listOf(
                block("QQ好友", 430, 90, 650, 155),
                block("你好", 80, 430, 400, 500),
            ),
            screenWidth = 1080,
            screenHeight = 2400,
            contentBottom = 1280,
            sourcePackage = "com.tencent.mobileqq",
            sourceApp = "qq",
        )!!

        assertEquals("com.tencent.mobileqq", result.context.sourcePackage)
        assertEquals("qq", result.context.sourceApp)
    }

    private fun block(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        confidence: Float = 0.95f,
    ) = OcrTextBlock(text, ScreenRect(left, top, right, bottom), confidence)
}
