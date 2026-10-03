package app.nextsay.ocr

import app.nextsay.context.*
import org.junit.Assert.*
import org.junit.Test

class WechatMediaGeometryTest {
    @Test fun obscuredTextBubbleTailCannotBecomeReliableMedia() {
        fill(56, 260, 230, 390, 0xffffffff.toInt())
        fill(50, 272, 56, 280, 0xffffffff.toInt())
        for (y in 285..360 step 25) fill(70, y, 210, y+4, 0xff222222.toInt())
        fill(48, 268, 56, 284, 0xffededed.toInt())
        val media = WechatMediaGeometry().detect(pixels, w, h, 72, 750,
            listOf(ScreenRect(48, 268, 56, 284)))
        assertTrue(media.isEmpty())
    }
    private val w = 360
    private val h = 800
    private val pixels = IntArray(w * h) { 0xffededed.toInt() }
    private fun fill(l: Int, t: Int, r: Int, b: Int, color: Int) {
        for (y in t until b) for (x in l until r) pixels[y*w+x] = color
    }
    @Test fun unlabeledScreenshotRectangleIsMediaAndItsInternalTextIsNotConversation() {
        fill(230, 260, 302, 610, 0xfffafafa.toInt())
        for (y in 280..590 step 35) fill(238, y, 285, y+4, 0xff222222.toInt())
        val media = WechatMediaGeometry().detect(pixels, w, h, 72, 750)
        assertEquals(1, media.size)
        assertTrue(media.single().isMedia)
        assertEquals(MessageRole.ME, media.single().role)
        val capture = WechatOcrParser().parse(listOf(
            OcrTextBlock("测试", ScreenRect(145, 30, 210, 48), 1f),
            OcrTextBlock("截图内的资料", ScreenRect(238, 280, 285, 290), 1f)), w, h, 750, bubbles = media)!!
        assertEquals(listOf("[图片]"), capture.context.messages.map { it.text })
    }
    @Test fun normalWhiteTextBubbleWithATailIsNotAnImage() {
        fill(55, 260, 230, 310, 0xffffffff.toInt())
        fill(51, 272, 56, 280, 0xffffffff.toInt())
        fill(65, 275, 210, 282, 0xff222222.toInt())
        assertTrue(WechatMediaGeometry().detect(pixels, w, h, 72, 750).isEmpty())
    }
    @Test fun greenMessageAndOuterAvatarsAreNotMedia() {
        fill(170, 260, 302, 320, 0xff95ec69.toInt())
        fill(303, 272, 308, 280, 0xff95ec69.toInt())
        fill(65, 275, 110, 282, 0xff222222.toInt())
        fill(314, 260, 350, 296, 0xffdd9988.toInt())
        assertTrue(WechatMediaGeometry().detect(pixels, w, h, 72, 750).isEmpty())
    }
}
