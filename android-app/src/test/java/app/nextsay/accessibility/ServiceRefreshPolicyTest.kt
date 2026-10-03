package app.nextsay.accessibility

import app.nextsay.capture.AutoRefreshScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ServiceRefreshPolicyTest {
    @Test fun actualServiceUsesTheShortRefreshDelayRatherThanOverridingIt() {
        val host = Robolectric.buildService(NextSayAccessibilityService::class.java).create()
        try {
            val scheduler = host.get().javaClass.getDeclaredField("autoRefreshScheduler").run { isAccessible = true; get(host.get()) as AutoRefreshScheduler }
            scheduler.onPageChanged("com.tencent.mobileqq", 1000L)
            assertEquals("The real service should start reading at 1300ms, not wait until 2500ms", "com.tencent.mobileqq", scheduler.consumeDue(1300L))
        } finally { host.destroy() }
    }
}
