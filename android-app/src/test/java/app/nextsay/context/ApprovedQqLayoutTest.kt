package app.nextsay.context

import app.nextsay.capture.CapturedConversation
import app.nextsay.capture.ContextCaptureResult
import app.nextsay.capture.ConversationContextCoordinator
import app.nextsay.history.HistoryLoadResult
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.w3c.dom.Element

/** Optional local fixture authorized by the user; never commit the phone layout or text. */
class ApprovedQqLayoutTest {
    @Test fun `approved QQ layout captures six verified self messages and one clipped message without OCR`() = runTest {
        val path = System.getenv("NEXTSAY_QQ_TEST_LAYOUT")
        assumeTrue("Run with the authorized local QQ fixture", !path.isNullOrBlank())
        val document = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(File(path!!))
        val xmlNodes = document.getElementsByTagName("node")
        val bounds = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")
        val nodes = (0 until xmlNodes.length).map { xmlNodes.item(it) as Element }
            .filter { it.getAttribute("package") == "com.tencent.mobileqq" }.map { node ->
                val box = bounds.matchEntire(node.getAttribute("bounds"))!!.groupValues.drop(1).map(String::toInt)
                NodeSnapshot(node.getAttribute("text"), node.getAttribute("content-desc"), node.getAttribute("class"),
                    node.getAttribute("resource-id"), ScreenRect(box[0], box[1], box[2], box[3]),
                    node.getAttribute("class") == "android.widget.EditText", node.getAttribute("focused") == "true", node.getAttribute("password") == "true")
            }
        var ocrCalled = false
        val coordinator = ConversationContextCoordinator(
            accessibilityCapture = { pkg ->
                val context = ContextNormalizer().normalize(pkg, nodes, 1080)
                val title = ConversationTitleExtractor().extract(pkg, nodes)
                if (context.messages.isEmpty() || title == null) null else CapturedConversation(title, context, true)
            },
            ocrCapture = { ocrCalled = true; null },
            mergeHistory = { HistoryLoadResult(it.context.messages, false) }, isPackageActive = { true },
        )
        val result = coordinator.capture("com.tencent.mobileqq")
        assertTrue("Actual QQ resource IDs must not yield an empty capture", result is ContextCaptureResult.Success)
        val context = (result as ContextCaptureResult.Success).context
        assertEquals(7, context.messages.size)
        // The top bubble is clipped and its avatar is off-screen: do not invent ownership.
        assertEquals(MessageRole.UNKNOWN, context.messages.first().role)
        assertEquals(List(6) { MessageRole.ME }, context.messages.drop(1).map { it.role })
        assertTrue(context.messages.drop(1).all { it.confidence >= .7f })
        assertFalse("Prefer reliable native QQ nodes, not screenshot text positions", ocrCalled)
        assertFalse("Avatars are role evidence, not chat messages", context.messages.any { it.text == "我的资料卡" })
        assertTrue(context.replyRound!!.title.isNotBlank())
    }
}
