package app.nextsay.ocr

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.nextsay.context.MessageRole
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Only reads an explicitly supplied, approved fixture. No API calls and no chat interaction. */
@RunWith(AndroidJUnit4::class)
class ApprovedWechatOcrTest {
    @Test
    fun allSevenBubbleSidesSurviveRealMlKitOcr() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Supply the approved test fixture", arguments.getString("approvedWechatFixture") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = BitmapFactory.decodeFile(File(context.getExternalFilesDir(null), "role-check.png").absolutePath)
        assertNotNull(bitmap)
        val engine = MlKitChineseOcrEngine()
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val bubbles = WechatBubbleDetector().detect(pixels, bitmap.width, bitmap.height, 216, 2184)
            val blocks = engine.recognize(bitmap).getOrThrow()
            val captured = WechatOcrParser().parse(blocks, bitmap.width, bitmap.height, 2184, bubbles = bubbles)
            assertNotNull(captured)
            // Assertion contains roles only; do not print screenshot contents or recognized text.
            assertEquals(listOf(MessageRole.ME, MessageRole.ME, MessageRole.OTHER, MessageRole.ME,
                MessageRole.OTHER, MessageRole.ME, MessageRole.ME), captured!!.context.messages.map { it.role })
        } finally {
            engine.close()
            bitmap.recycle()
        }
    }
}
