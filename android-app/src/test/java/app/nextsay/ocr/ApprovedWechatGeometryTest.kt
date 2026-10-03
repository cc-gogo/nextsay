package app.nextsay.ocr

import app.nextsay.context.MessageRole
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Optional local fixture, never committed: the user-approved test screen, not private chat history. */
class ApprovedWechatGeometryTest {
    @Test
    fun `approved phone screenshot identifies all seven bubble sides`() {
        val path = System.getenv("NEXTSAY_TEST_SCREENSHOT")
        assumeTrue("Run with a user-approved screenshot fixture", !path.isNullOrEmpty())
        // Android's compile bootclasspath excludes java.desktop; the host test JVM still supplies it.
        val image = Class.forName("javax.imageio.ImageIO").getMethod("read", File::class.java).invoke(null, File(path!!))
        val imageClass = Class.forName("java.awt.image.BufferedImage")
        val width = imageClass.getMethod("getWidth").invoke(image) as Int
        val height = imageClass.getMethod("getHeight").invoke(image) as Int
        val pixels = imageClass.getMethod("getRGB", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, IntArray::class.java,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(image, 0, 0, width, height, null, 0, width) as IntArray
        val bubbles = WechatBubbleDetector().detect(pixels, width, height, 216, 2184)
        assertEquals(listOf(MessageRole.ME, MessageRole.ME, MessageRole.OTHER, MessageRole.ME,
            MessageRole.OTHER, MessageRole.ME, MessageRole.ME), bubbles.map { it.role })
    }
}
