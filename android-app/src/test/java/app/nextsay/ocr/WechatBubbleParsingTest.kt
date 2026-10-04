package app.nextsay.ocr

import app.nextsay.context.MessageRole
import app.nextsay.context.ScreenRect
import org.junit.Assert.*
import org.junit.Test

class WechatBubbleParsingTest {
    private fun media(bounds: ScreenRect, role: MessageRole) = OcrBubble(bounds, role, isMedia = true)

    @Test fun `sent screenshot text is replaced with one outgoing image placeholder`() {
        val messages = parse(listOf(
            line("对象档案与自动候选", 690, 450, 850, 495),
            line("CC_GOGO", 690, 600, 850, 635),
            line("你好", 200, 1050, 350, 1095),
        ), listOf(media(ScreenRect(670, 430, 920, 950), MessageRole.ME),
            OcrBubble(ScreenRect(156, 1030, 410, 1120), MessageRole.OTHER)))
        assertEquals(listOf("[图片]", "你好"), messages.map { it.text })
        assertEquals(listOf(MessageRole.ME, MessageRole.OTHER), messages.map { it.role })
    }

    @Test fun `latest photo without OCR text remains the latest incoming message`() {
        val messages = parse(listOf(line("之前的消息", 720, 450, 890, 495)), listOf(
            OcrBubble(ScreenRect(700, 430, 920, 520), MessageRole.ME),
            media(ScreenRect(156, 700, 600, 1200), MessageRole.OTHER)))
        assertEquals(listOf("之前的消息", "[图片]"), messages.map { it.text })
        assertEquals(MessageRole.OTHER, messages.last().role)
    }

    @Test fun `media containing apparent text bubbles must not leak nested screenshot words`() {
        val messages = parse(listOf(line("截图里的不是聊天", 200, 450, 800, 495)), listOf(
            media(ScreenRect(156, 400, 920, 1000), MessageRole.OTHER),
            OcrBubble(ScreenRect(180, 430, 830, 520), MessageRole.ME)))
        assertEquals(listOf("[图片]"), messages.map { it.text })
        assertEquals(MessageRole.OTHER, messages.single().role)
    }
    private val title = OcrTextBlock("测试聊天", ScreenRect(430, 90, 650, 155), 0.95f)
    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) = OcrTextBlock(text, ScreenRect(left, top, right, bottom), 0.95f)
    private fun parse(blocks: List<OcrTextBlock>, bubbles: List<OcrBubble>) = WechatOcrParser().parse(
        listOf(title) + blocks, 1080, 2400, 2240, bubbles = bubbles,
    )!!.context.messages

    @Test
    fun `all lines inherit outgoing bubble even when left aligned and cross midpoint`() {
        val messages = parse(listOf(
            line("我写的一段长话", 200, 450, 850, 495),
            line("短末行", 200, 510, 350, 555),
        ), listOf(OcrBubble(ScreenRect(168, 430, 924, 580), MessageRole.ME)))
        assertEquals(1, messages.size)
        assertEquals(MessageRole.ME, messages.single().role)
        assertEquals("我写的一段长话短末行", messages.single().text)
    }

    @Test
    fun `consecutive bubbles on same side stay separate even with close text rows`() {
        val messages = parse(listOf(
            line("第一句", 200, 450, 350, 495),
            line("第二句", 200, 510, 350, 555),
        ), listOf(
            OcrBubble(ScreenRect(168, 430, 410, 500), MessageRole.OTHER),
            OcrBubble(ScreenRect(168, 505, 410, 570), MessageRole.OTHER),
        ))
        assertEquals(listOf("第一句", "第二句"), messages.map { it.text })
        assertEquals(listOf(MessageRole.OTHER, MessageRole.OTHER), messages.map { it.role })
    }

    @Test
    fun `unassigned centered text is uncertain rather than silently switching sides`() {
        val messages = parse(listOf(line("无法判断", 400, 450, 700, 495)), emptyList())
        assertEquals(MessageRole.UNKNOWN, messages.single().role)
        assertTrue(messages.single().confidence <= 0.4f)
    }

    @Test
    fun `time inside incoming bubble is a real latest reply not page metadata`() {
        val messages = parse(listOf(
            line("几点见", 750, 450, 860, 495),
            line("10:30", 200, 610, 350, 655),
        ), listOf(
            OcrBubble(ScreenRect(720, 430, 924, 520), MessageRole.ME),
            OcrBubble(ScreenRect(156, 590, 410, 680), MessageRole.OTHER),
        ))
        assertEquals(listOf(MessageRole.ME, MessageRole.OTHER), messages.map { it.role })
        assertEquals("10:30", messages.last().text)
    }

    @Test
    fun `control word inside bubble is not discarded but centered page metadata is`() {
        val messages = parse(listOf(
            line("发送", 200, 450, 350, 495),
            line("10:30", 460, 610, 610, 655),
        ), listOf(OcrBubble(ScreenRect(156, 430, 410, 520), MessageRole.OTHER)))
        assertEquals(listOf("发送"), messages.map { it.text })
    }

    @Test
    fun `time with ambiguous bubble association remains unknown instead of vanishing`() {
        val messages = parse(listOf(line("10:30", 200, 450, 350, 495)), listOf(
            OcrBubble(ScreenRect(156, 430, 410, 520), MessageRole.OTHER),
            OcrBubble(ScreenRect(180, 440, 380, 510), MessageRole.ME),
        ))
        assertEquals("10:30", messages.single().text)
        assertEquals(MessageRole.UNKNOWN, messages.single().role)
    }

    @Test
    fun `text inside partially occluded incoming bubble still inherits bubble role`() {
        val messages = parse(listOf(
            // OCR sees only the text area; the right side of the bubble is covered
            // by the overlay, so area overlap is intentionally well below 80%.
            line("好呀，那你什么时候来接我？", 188, 450, 520, 495),
        ), listOf(OcrBubble(ScreenRect(156, 430, 760, 530), MessageRole.OTHER)))
        assertEquals(MessageRole.OTHER, messages.single().role)
        assertTrue(messages.single().confidence >= .7f)
    }

    @Test
    fun `text between bubbles is not assigned by a large expansion`() {
        val messages = parse(listOf(
            line("界面文字", 430, 450, 650, 495),
        ), listOf(
            OcrBubble(ScreenRect(156, 300, 300, 400), MessageRole.OTHER),
            OcrBubble(ScreenRect(780, 300, 924, 400), MessageRole.ME),
        ))
        assertEquals(MessageRole.UNKNOWN, messages.single().role)
    }

    @Test
    fun `voice transcript keeps side when bubble pixels are not detected`() {
        // WeChat's converted voice text can remain visible while the colored
        // bubble is covered by the keyboard or overlay. The text is still a
        // normal left/right chat line and should not become an unknown turn.
        val messages = parse(listOf(
            line("我刚刚在路上", 180, 450, 430, 495),
        ), emptyList())
        assertEquals(MessageRole.OTHER, messages.single().role)
        assertTrue(messages.single().confidence <= .72f)
    }

    @Test
    fun `long voice transcript resolves side when geometry fragments overlap`() {
        val messages = parse(listOf(
            line("主要我手头没有其他工作嗯就只有这个发送单", 180, 450, 780, 495),
        ), listOf(
            OcrBubble(ScreenRect(150, 430, 810, 540), MessageRole.OTHER),
            OcrBubble(ScreenRect(160, 440, 800, 530), MessageRole.ME),
        ))
        assertEquals(MessageRole.OTHER, messages.single().role)
    }

    @Test
    fun `multiline voice transcript is not discarded as an image bubble`() {
        val bubble = media(ScreenRect(150, 430, 820, 760), MessageRole.OTHER)
        val messages = parse(listOf(
            line("主要我手头没有其他工作", 180, 450, 700, 495),
            line("嗯就只有这个发送单", 180, 505, 650, 550),
            line("好像可以弥补一下", 180, 560, 610, 605),
        ), listOf(bubble))
        assertEquals(1, messages.size)
        assertEquals(MessageRole.OTHER, messages.single().role)
        assertTrue(messages.single().text.contains("发送单"))
    }

    @Test
    fun `messages remain readable when overlay covers the chat title`() {
        val captured = WechatOcrParser().parse(
            blocks = listOf(line("对方的新消息", 180, 450, 520, 500)),
            screenWidth = 1080,
            screenHeight = 2400,
            contentBottom = 2240,
            bubbles = emptyList(),
            fallbackTitle = "测试聊天",
        )!!
        assertEquals("测试聊天", captured.title)
        assertEquals("对方的新消息", captured.context.messages.single().text)
    }
}
