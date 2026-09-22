package app.nextsay.overlay

class QuickReplyInteraction {
    private var lastSubmittedText = ""

    fun submit(text: String): String {
        lastSubmittedText = text
        return text
    }

    fun retryText(): String = lastSubmittedText

    fun shouldDismiss(action: Int): Boolean = action == ACTION_OUTSIDE

    private companion object {
        const val ACTION_OUTSIDE = 4
    }
}
