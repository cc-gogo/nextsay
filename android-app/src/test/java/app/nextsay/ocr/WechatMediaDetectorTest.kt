package app.nextsay.ocr

import app.nextsay.context.*
import org.junit.Assert.*
import org.junit.Test

class WechatMediaDetectorTest {
    @Test fun imageActionDescriptionOnACustomViewStillExcludesScreenshotContents() {
        val media = detect(node(ScreenRect(670, 430, 910, 1200), "图片，双击查看", "com.tencent.mm.ui.widget.image.CustomImageView"))
        assertEquals(1, media.size)
        assertEquals(MessageRole.ME, media.single().role)
    }
    @Test fun widePhotoOwnershipUsesTheOuterAvatarRowNotItsNestedScreenshot() {
        val media = detect(node(ScreenRect(168, 430, 910, 1200), "图片"),
            node(ScreenRect(940, 430, 1048, 538), "头像"),
            node(ScreenRect(32, 700, 140, 808), "头像"))
        assertEquals(1, media.size)
        assertEquals(MessageRole.ME, media.single().role)
    }
    @Test fun `partly scrolled attachment still excludes its visible image words`() {
        val media = detect(node(ScreenRect(156, 180, 600, 950), "图片"))
        assertEquals(1, media.size)
        assertEquals(ScreenRect(156, 216, 600, 950), media.single().bounds)
    }
    private fun node(rect: ScreenRect, description: String? = null, klass: String = "android.widget.ImageView") =
        NodeSnapshot(null, description, klass, null, rect, false, false, false)
    private fun detect(vararg nodes: NodeSnapshot) = WechatMediaDetector().detect(nodes.toList(), 1080, 216, 2240)

    @Test fun `outgoing screenshot attachment is detected without reading its contents`() {
        val media = detect(node(ScreenRect(670, 430, 910, 950)))
        assertEquals(1, media.size)
        assertTrue(media.single().isMedia)
        assertEquals(MessageRole.ME, media.single().role)
    }
    @Test fun `avatar and toolbar icon are not attachments`() {
        assertTrue(detect(node(ScreenRect(940, 430, 1048, 538), "头像"),
            node(ScreenRect(32, 430, 140, 538), "头像"), node(ScreenRect(180, 80, 300, 200))).isEmpty())
    }
    @Test fun `left attachment keeps incoming ownership`() {
        assertEquals(MessageRole.OTHER, detect(node(ScreenRect(156, 430, 580, 950), "图片", "android.view.View")).single().role)
    }
    @Test fun `wide attachment with no unique side remains uncertain`() {
        assertEquals(MessageRole.UNKNOWN, detect(node(ScreenRect(168, 430, 910, 950), "图片")).single().role)
    }
    @Test fun `nested image controls yield only one placeholder`() {
        val media = detect(node(ScreenRect(670, 430, 910, 950)), node(ScreenRect(680, 440, 900, 600)))
        assertEquals(1, media.size)
        assertEquals(ScreenRect(670, 430, 910, 950), media.single().bounds)
    }
    @Test fun `normal text node never becomes a photo even when large`() {
        assertTrue(detect(node(ScreenRect(156, 430, 580, 950), "很长的文字", "android.widget.TextView")).isEmpty())
    }
}
