package app.nextsay.overlay

class QuickReplyInteraction {
    private var lastSubmittedText = ""
    var ownsInputFocus: Boolean = false
        private set

    fun requestInputFocus() { ownsInputFocus = true }

    fun releaseInputFocus(hideKeyboard: Boolean = true): Boolean {
        val hadFocus = ownsInputFocus
        ownsInputFocus = false
        return hadFocus && hideKeyboard
    }

    fun submit(text: String): String {
        releaseInputFocus()
        lastSubmittedText = text
        return text
    }

    fun retryText(): String = lastSubmittedText

    // Only an explicit floating-trigger toggle dismisses the quick window.
    fun shouldDismiss(@Suppress("UNUSED_PARAMETER") action: Int): Boolean = false
}
