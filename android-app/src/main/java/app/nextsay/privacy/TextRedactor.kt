package app.nextsay.privacy

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TextRedactor {
    fun redact(text: String): String = text
        .replace(EMAIL, "[邮箱]")
        .replace(MOBILE, "[手机号]")
        .replace(LONG_NUMBER) { match ->
            if (match.value.isCompactDate()) match.value else "[长数字]"
        }

    private fun String.isCompactDate(): Boolean = length == 8 && runCatching {
        LocalDate.parse(this, DateTimeFormatter.BASIC_ISO_DATE)
    }.isSuccess

    private companion object {
        val EMAIL = Regex("(?i)(?<![\\w.+-])[\\w.+-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)+")
        val MOBILE = Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)")
        val LONG_NUMBER = Regex("(?<!\\d)\\d{8,}(?!\\d)")
    }
}
