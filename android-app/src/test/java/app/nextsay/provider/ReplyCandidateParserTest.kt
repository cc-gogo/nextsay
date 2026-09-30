package app.nextsay.provider

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReplyCandidateParserTest {
    private val parser = ReplyCandidateParser(Gson())

    @Test
    fun `accepts fenced JSON and returns canonical style order`() {
        val result = parser.parse(
            """```json
                {"candidates":[
                  {"style":"natural","text":"行"},
                  {"style":"concise","text":"可以"},
                  {"style":"tactful","text":"好的，我会尽快完成"}
                ]}
                ```
            """.trimIndent(),
        )

        assertEquals(listOf("concise", "tactful", "natural"), result.map { it.style })
    }

    @Test
    fun `rejects empty choices duplicate text wrong style count and long text`() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse("{\"candidates\":[]}")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(envelope("好", "好", "行"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(envelopeWithStyles("brief", "tactful", "natural"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(envelope("x".repeat(501), "好", "行"))
        }
    }

    @Test
    fun `rejects blank candidate malformed JSON and extra markdown`() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(envelope(" ", "好", "行"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse("not-json")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse("说明：${envelope("可以", "好的", "行")}")
        }
    }

    @Test
    fun `rejects null missing and non-string candidate fields`() {
        for (json in listOf(
            "{\"candidates\":null}",
            "{\"candidates\":[{\"style\":\"concise\"}]}",
            "{\"candidates\":[{\"style\":\"concise\",\"text\":12}]}",
            "{\"candidates\":[{\"style\":\"concise\",\"text\":false}]}",
        )) {
            assertThrows(IllegalArgumentException::class.java) { parser.parse(json) }
        }
    }

    private fun envelope(first: String, second: String, third: String) =
        """{"candidates":[
            {"style":"concise","text":"$first"},
            {"style":"tactful","text":"$second"},
            {"style":"natural","text":"$third"}
        ]}""".trimIndent()

    private fun envelopeWithStyles(first: String, second: String, third: String) =
        """{"candidates":[
            {"style":"$first","text":"第一条"},
            {"style":"$second","text":"第二条"},
            {"style":"$third","text":"第三条"}
        ]}""".trimIndent()
}
