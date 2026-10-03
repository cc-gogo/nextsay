package app.nextsay.capture

import app.nextsay.contacts.ContactEvidence
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole

/** Separate scroll evidence from generic content, draft and keyboard invalidation. */
class ScrollRevisionTracker {
    var revision: Long = 0L
        private set
    private var previousItemCount: Int? = null
    private var conversationKey: Pair<String, String>? = null
    private var keyboardObserved = false
    private var previousKeyboardTop: Int? = null
    private var pendingScroll = false
    private var frame = emptyList<ChatMessage>()

    fun onScroll(itemCount: Int, keyboardTop: Int? = null) {
        val previous = previousItemCount
        val resizedByKeyboard = keyboardObserved && previousKeyboardTop != keyboardTop
        keyboardObserved = true
        previousKeyboardTop = keyboardTop
        // List counts are only a hint: WeChat often omits counts for auto-scroll.
        // Wait for message evidence before invalidating an incoming round.
        if (!resizedByKeyboard && (previous == null || itemCount <= 0 || itemCount <= previous)) pendingScroll = true
        previousItemCount = itemCount.takeIf { it > 0 }
    }

    fun onFrame(messages: List<ChatMessage>) {
        val tail = messages.lastOrNull() ?: return
        if (tail.role == MessageRole.UNKNOWN || tail.confidence < .7f) return
        if (pendingScroll && frame.isNotEmpty()) {
            val unchanged = frame.size == messages.size && frame.zip(messages).all { ContactEvidence.same(it.first, it.second) }
            val containedAt = if (frame.size > messages.size) null else (0..messages.size-frame.size).firstOrNull { start ->
                frame.indices.all { ContactEvidence.sameConsumed(frame[it], messages[start+it], it == frame.lastIndex) }
            }
            val overlap = ContactEvidence.forwardOverlap(frame, messages)
            val appended = containedAt?.let { messages.size > it + frame.size } ?: (overlap > 0 && messages.size > overlap)
            val count = minOf(4, frame.size, messages.size)
            val sameTail = frame.takeLast(count).zip(messages.takeLast(count)).withIndex().all { (index, pair) ->
                ContactEvidence.sameConsumed(pair.first, pair.second, index == count-1)
            }
            if (!unchanged && !appended && !sameTail) revision++
        }
        pendingScroll = false
        frame = messages.takeLast(100)
    }

    fun resetList() { previousItemCount = null; keyboardObserved = false; previousKeyboardTop = null; pendingScroll = false; frame = emptyList() }

    fun onConversation(packageName: String, title: String, keyboardTop: Int? = null) {
        val key = packageName to title.trim()
        if (key != conversationKey) {
            if (conversationKey != null) resetList()
            conversationKey = key
        }
        // Seed the baseline even if this chat has never emitted a scroll event.
        if (!keyboardObserved) {
            keyboardObserved = true
            previousKeyboardTop = keyboardTop
        }
    }
}
