package app.nextsay.privacy

import org.junit.Assert.assertEquals
import org.junit.Test

class TextRedactorTest {
    private val redactor = TextRedactor()

    @Test
    fun `redacts mainland mobile numbers`() {
        assertEquals("联系我：[手机号]", redactor.redact("联系我：13812345678"))
    }

    @Test
    fun `redacts email addresses`() {
        assertEquals("邮箱：[邮箱]", redactor.redact("邮箱：hello.user@example.com"))
    }

    @Test
    fun `redacts long numeric identifiers`() {
        assertEquals("订单号：[长数字]", redactor.redact("订单号：123456789012"))
    }

    @Test
    fun `keeps dates and short numbers`() {
        assertEquals("2026-08-08 20260808 会议室 302", redactor.redact("2026-08-08 20260808 会议室 302"))
    }
}
