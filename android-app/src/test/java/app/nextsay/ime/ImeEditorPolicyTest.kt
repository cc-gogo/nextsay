package app.nextsay.ime

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Test

class ImeEditorPolicyTest {
    private val policy = ImeEditorPolicy()

    @Test
    fun `accepts ordinary text editors in every supported chat app`() {
        val packages = listOf(
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.tencent.tim",
            "com.tencent.qqlite",
        )

        packages.forEach { packageName ->
            assertEquals(
                ImeEditorEligibility.Available(packageName),
                policy.evaluate(packageName, InputType.TYPE_CLASS_TEXT),
            )
        }
    }

    @Test
    fun `rejects unknown and missing target applications`() {
        assertEquals(
            ImeEditorEligibility.Unavailable(ImeUnavailableReason.UNSUPPORTED_APP),
            policy.evaluate("com.honor.browser", InputType.TYPE_CLASS_TEXT),
        )
        assertEquals(
            ImeEditorEligibility.Unavailable(ImeUnavailableReason.UNSUPPORTED_APP),
            policy.evaluate(null, InputType.TYPE_CLASS_TEXT),
        )
    }

    @Test
    fun `rejects every sensitive password variation`() {
        val sensitiveInputTypes = listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        )

        sensitiveInputTypes.forEach { inputType ->
            assertEquals(
                ImeEditorEligibility.Unavailable(ImeUnavailableReason.SENSITIVE_FIELD),
                policy.evaluate("com.tencent.mm", inputType),
            )
        }
    }

    @Test
    fun `unsupported application stays rejected even when field is sensitive`() {
        assertEquals(
            ImeEditorEligibility.Unavailable(ImeUnavailableReason.UNSUPPORTED_APP),
            policy.evaluate(
                "com.honor.browser",
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            ),
        )
    }
}
