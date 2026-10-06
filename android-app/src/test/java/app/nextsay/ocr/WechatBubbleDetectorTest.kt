package app.nextsay.ocr

import app.nextsay.context.MessageRole
import app.nextsay.context.ScreenRect
import org.junit.Assert.*
import org.junit.Test

class WechatBubbleDetectorTest {
    // Synthetic pixels only: no user's screenshot or chat is stored in the repository.
    private val width = 1080
    private val height = 2400
    private val pixels = IntArray(width * height) { 0xffededed.toInt() }
    private fun fill(rect: ScreenRect, color: Int) {
        for (y in rect.top until rect.bottom) for (x in rect.left until rect.right) pixels[y * width + x] = color
    }
    private fun detect() = WechatBubbleDetector().detect(pixels, width, height, 216, 2240)

    @Test
    fun `right tail owns a wide outgoing bubble crossing screen center`() {
        fill(ScreenRect(168, 450, 910, 660), 0xff95ec69.toInt())
        fill(ScreenRect(909, 468, 924, 480), 0xff95ec69.toInt())
        val bubbles = detect()
        assertEquals(1, bubbles.size)
        assertEquals(MessageRole.ME, bubbles.single().role)
    }

    @Test
    fun `left tail owns a wide incoming bubble`() {
        fill(ScreenRect(168, 450, 910, 660), 0xffffffff.toInt())
        fill(ScreenRect(156, 468, 170, 480), 0xffffffff.toInt())
        assertEquals(MessageRole.OTHER, detect().single().role)
    }

    @Test
    fun `white right attachment is self not inferred from bubble color`() {
        fill(ScreenRect(234, 450, 910, 660), 0xffffffff.toInt())
        fill(ScreenRect(909, 468, 924, 480), 0xffffffff.toInt())
        assertEquals(MessageRole.ME, detect().single().role)
    }

    @Test
    fun `avatar sized components are not chat bubbles`() {
        fill(ScreenRect(32, 450, 140, 558), 0xffffffff.toInt())
        fill(ScreenRect(940, 450, 1048, 558), 0xff95ec69.toInt())
        assertTrue(detect().isEmpty())
    }

    @Test
    fun `wide symmetric bubble without side evidence stays uncertain`() {
        fill(ScreenRect(168, 450, 912, 660), 0xffffffff.toInt())
        assertEquals(MessageRole.UNKNOWN, detect().single().role)
    }

    @Test
    fun `left white bubble without visible tail uses clear left placement`() {
        fill(ScreenRect(110, 700, 430, 860), 0xffffffff.toInt())
        val bubbles = detect()
        assertEquals(1, bubbles.size)
        assertEquals(MessageRole.OTHER, bubbles.single().role)
    }

    @Test
    fun `right green bubble without visible tail uses clear right placement`() {
        fill(ScreenRect(650, 700, 980, 860), 0xff95ec69.toInt())
        val bubbles = detect()
        assertEquals(1, bubbles.size)
        assertEquals(MessageRole.ME, bubbles.single().role)
    }

    @Test
    fun `dark incoming bubble is distinguished from dark page background`() {
        pixels.fill(0xff111111.toInt())
        fill(ScreenRect(168, 450, 630, 660), 0xff2c2c2c.toInt())
        fill(ScreenRect(156, 468, 170, 480), 0xff2c2c2c.toInt())
        assertEquals(MessageRole.OTHER, detect().single().role)
    }

    @Test
    fun `voice transcript below own voice bubble is self`() {
        fill(ScreenRect(488, 1580, 910, 1670), 0xff95ec69.toInt())
        fill(ScreenRect(909, 1608, 924, 1620), 0xff95ec69.toInt())
        fill(ScreenRect(168, 1680, 910, 1880), 0xffffffff.toInt())
        val bubbles = detect()
        assertEquals(listOf(MessageRole.ME, MessageRole.ME), bubbles.map { it.role })
        assertEquals(listOf(false, true), bubbles.map { it.transcript })
    }

    @Test
    fun `voice transcript below incoming voice bubble is other`() {
        fill(ScreenRect(168, 1580, 520, 1670), 0xffffffff.toInt())
        fill(ScreenRect(156, 1608, 170, 1620), 0xffffffff.toInt())
        fill(ScreenRect(168, 1682, 912, 1880), 0xffffffff.toInt())
        assertEquals(listOf(MessageRole.OTHER, MessageRole.OTHER), detect().map { it.role })
    }

    @Test
    fun `wide white box far below a voice bubble keeps unknown`() {
        fill(ScreenRect(488, 1000, 910, 1090), 0xff95ec69.toInt())
        fill(ScreenRect(909, 1028, 924, 1040), 0xff95ec69.toInt())
        fill(ScreenRect(168, 1500, 912, 1700), 0xffffffff.toInt())
        assertEquals(MessageRole.UNKNOWN, detect().last().role)
    }
}
