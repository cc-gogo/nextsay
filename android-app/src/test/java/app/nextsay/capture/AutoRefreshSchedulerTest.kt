package app.nextsay.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoRefreshSchedulerTest {
    @Test fun ordinaryChangeCanBeReadWithin300msWithoutIgnoringOcrCooldown() {
        val scheduler = AutoRefreshScheduler()
        assertEquals(1300L, scheduler.onPageChanged(QQ, 1000L))
        assertNull(scheduler.consumeDue(1299L))
        assertEquals(QQ, scheduler.consumeDue(1300L))
        scheduler.recordWechatOcrStarted(1000L)
        assertEquals(3000L, scheduler.onPageChanged(WECHAT, 1500L))
    }
    @Test fun typingEventsCannotPostponeCaptureForever() {
        val s = AutoRefreshScheduler()
        s.onPageChanged(QQ, 0)
        for (time in 500L..5000L step 500L) s.onPageChanged(QQ, time)
        assertEquals(QQ, s.consumeDue(5000))
    }
    private val scheduler = AutoRefreshScheduler(debounceMillis = 800L)

    @Test
    fun `later page event resets the 800 millisecond debounce`() {
        assertEquals(1_800L, scheduler.onPageChanged(QQ, 1_000L))
        assertEquals(2_300L, scheduler.onPageChanged(QQ, 1_500L))

        assertNull(scheduler.consumeDue(2_299L))
        assertEquals(QQ, scheduler.consumeDue(2_300L))
    }

    @Test
    fun `wechat waits for OCR cooldown as well as debounce`() {
        scheduler.recordWechatOcrStarted(1_000L)

        assertEquals(3_000L, scheduler.onPageChanged(WECHAT, 1_500L))
        assertNull(scheduler.consumeDue(2_999L))
        assertEquals(WECHAT, scheduler.consumeDue(3_000L))
    }

    @Test
    fun `qq does not use wechat OCR cooldown`() {
        scheduler.recordWechatOcrStarted(1_000L)

        assertEquals(2_300L, scheduler.onPageChanged(QQ, 1_500L))
    }

    @Test
    fun `cancel removes pending work`() {
        scheduler.onPageChanged(QQ, 1_000L)

        scheduler.cancel()

        assertNull(scheduler.consumeDue(10_000L))
    }

    @Test
    fun `page change during capture remains pending after capture completes`() {
        scheduler.onCaptureStarted()
        assertEquals(2_800L, scheduler.onPageChanged(QQ, 2_000L))
        assertNull(scheduler.consumeDue(2_800L))

        assertEquals(2_800L, scheduler.onCaptureFinished(2_500L, QQ))
        assertEquals(QQ, scheduler.consumeDue(2_800L))
    }

    private companion object {
        const val QQ = "com.tencent.mobileqq"
        const val WECHAT = "com.tencent.mm"
    }
}
