package app.nextsay.capture

import app.nextsay.context.ChatContext
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestContextCacheTest {
    private val cache = LatestContextCache()

    @Test
    fun `page change makes previous context dirty immediately`() {
        val firstEpoch = cache.markPageChanged(QQ)
        val first = cache.beginCapture(QQ)!!
        val context = context(QQ, "第一屏")
        assertTrue(cache.complete(first, context))
        assertSame(context, cache.fresh(QQ))

        val secondEpoch = cache.markPageChanged(QQ)

        assertTrue(secondEpoch > firstEpoch)
        assertTrue(cache.isDirty(QQ))
        assertNull(cache.fresh(QQ))
    }

    @Test
    fun `capture completion is fresh only for latest page epoch`() {
        cache.markPageChanged(QQ)
        val staleTicket = cache.beginCapture(QQ)!!
        cache.markPageChanged(QQ)

        assertFalse(cache.complete(staleTicket, context(QQ, "旧屏")))
        assertNull(cache.fresh(QQ))

        val latestTicket = cache.beginCapture(QQ)!!
        val latest = context(QQ, "新屏")
        assertTrue(cache.complete(latestTicket, latest))
        assertSame(latest, cache.fresh(QQ))
    }

    @Test
    fun `current epoch identifies the active page and changes on same app refresh`() {
        val first = cache.markPageChanged(QQ)
        assertEquals(first, cache.currentEpoch(QQ))

        val second = cache.markPageChanged(QQ)

        assertTrue(second > first)
        assertEquals(second, cache.currentEpoch(QQ))
        assertNull(cache.currentEpoch(WECHAT))
    }

    @Test
    fun `switching package clears old context`() {
        cache.markPageChanged(QQ)
        val ticket = cache.beginCapture(QQ)!!
        cache.complete(ticket, context(QQ, "QQ"))

        cache.markPageChanged(WECHAT)

        assertNull(cache.fresh(QQ))
        assertTrue(cache.isDirty(WECHAT))
    }

    @Test
    fun `capture failure leaves latest page dirty and allows retry`() {
        cache.markPageChanged(QQ)
        val failed = cache.beginCapture(QQ)!!

        cache.fail(failed)

        assertTrue(cache.isDirty(QQ))
        assertNull(cache.fresh(QQ))
        assertTrue(cache.beginCapture(QQ) != null)
    }

    private fun context(packageName: String, text: String) = ChatContext(
        sourceApp = if (packageName == WECHAT) "wechat" else "qq",
        sourcePackage = packageName,
        messages = listOf(ChatMessage(MessageRole.OTHER, text, 0.9f)),
        draft = "",
        confidence = 0.9f,
    )

    private companion object {
        const val QQ = "com.tencent.mobileqq"
        const val WECHAT = "com.tencent.mm"
    }
}
