package app.nextsay.ime

import android.text.InputType

sealed interface ImeEditorEligibility {
    data class Available(val targetPackage: String) : ImeEditorEligibility
    data class Unavailable(val reason: ImeUnavailableReason) : ImeEditorEligibility
}

enum class ImeUnavailableReason {
    UNSUPPORTED_APP,
    SENSITIVE_FIELD,
    SERVICE_DISCONNECTED,
}

class ImeEditorPolicy {
    fun evaluate(packageName: String?, inputType: Int): ImeEditorEligibility {
        val targetPackage = packageName?.takeIf { it in SUPPORTED_PACKAGES }
            ?: return ImeEditorEligibility.Unavailable(ImeUnavailableReason.UNSUPPORTED_APP)
        if (isSensitive(inputType)) {
            return ImeEditorEligibility.Unavailable(ImeUnavailableReason.SENSITIVE_FIELD)
        }
        return ImeEditorEligibility.Available(targetPackage)
    }

    private fun isSensitive(inputType: Int): Boolean {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> variation in TEXT_PASSWORD_VARIATIONS
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private companion object {
        val SUPPORTED_PACKAGES = setOf(
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.tencent.tim",
            "com.tencent.qqlite",
        )
        val TEXT_PASSWORD_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
    }
}
