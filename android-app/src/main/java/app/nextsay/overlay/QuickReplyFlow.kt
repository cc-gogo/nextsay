package app.nextsay.overlay

import app.nextsay.capture.ContextCaptureResult
import app.nextsay.context.ChatContext
import app.nextsay.context.MessageRole
import app.nextsay.diagnostics.DiagnosticSurface

/** User-requested capture and generation; no background API calls. */
class QuickReplyFlow(
    private val controller: OverlayController,
    private val capture: suspend (String) -> ContextCaptureResult,
    private val setInstruction: (String) -> Unit,
    private val dismiss: () -> Unit,
    private val captureError: (String) -> Unit,
    private val prepare: suspend (ChatContext) -> ChatContext = { it },
    private val retainNewRoundInstruction: () -> Boolean = { false },
) {
    private var conversationKey: Pair<String, String>? = null
    private var knownMessages = emptyList<MessageKey>()
    private var viewportRevision = 0L
    fun observe(context: ChatContext): Boolean = accept(context)

    suspend fun request(packageName: String, instruction: String, collapseIfUnchanged: Boolean) {
        val ticket = controller.beginContextCheck()
        val result = capture(packageName)
        if (!controller.isCaptureCurrent(ticket)) return
        when (result) {
            is ContextCaptureResult.Success -> {
                val prepared = prepare(result.context)
                if (!controller.isCaptureCurrent(ticket)) return
                val newRound = accept(prepared)
                if (collapseIfUnchanged && !newRound) {
                    controller.dismiss()
                    dismiss()
                    return
                }
                val roundInstruction = if (newRound && !retainNewRoundInstruction()) "" else instruction
                setInstruction(roundInstruction)
                if (controller.completeCapture(ticket, prepared)) {
                    controller.generate(roundInstruction, surface = DiagnosticSurface.OVERLAY)
                }
            }
            is ContextCaptureResult.CaptureError -> captureError(result.message)
            ContextCaptureResult.Busy -> captureError("正在读取对话，请稍候")
            ContextCaptureResult.Cancelled -> {
                controller.dismiss()
                dismiss()
            }
        }
    }

    private fun accept(context: ChatContext): Boolean {
        val snapshot = context.replyRound
        val key = context.sourcePackage to (context.contactId ?: normalize(snapshot?.title.orEmpty()))
        val scrolled = (snapshot?.viewportRevision ?: 0L) != viewportRevision
        viewportRevision = snapshot?.viewportRevision ?: 0L
        val visible = (snapshot?.visibleMessages ?: context.messages).map {
            MessageKey(it.role, normalize(it.text))
        }.filter { it.text.isNotEmpty() }
        if (conversationKey != key) {
            conversationKey = key
            knownMessages = visible.takeLast(MAX_ANCHORS)
            return true
        }
        if (visible.isEmpty() || knownMessages.containsSequence(visible)) return false

        // Older prefixes and keyboard/scroll viewport subsets do not advance a round.
        val previousAt = visible.sequenceIndex(knownMessages)
        if (knownMessages.isNotEmpty() && previousAt >= 0) {
            val appended = visible.drop(previousAt + knownMessages.size)
            knownMessages = visible.takeLast(MAX_ANCHORS)
            return !scrolled && appended.any { it.role == MessageRole.OTHER }
        }
        val forwardOverlap = overlap(knownMessages, visible)
        if (forwardOverlap > 0) {
            val appended = visible.drop(forwardOverlap)
            knownMessages = (knownMessages + appended).takeLast(MAX_ANCHORS)
            return !scrolled && appended.any { it.role == MessageRole.OTHER }
        }
        val backwardOverlap = overlap(visible, knownMessages)
        if (backwardOverlap > 0) {
            knownMessages = (visible.dropLast(backwardOverlap) + knownMessages).takeLast(MAX_ANCHORS)
        }
        // No shared anchor is ambiguous (old scroll vs new page). Keep the current
        // round and instruction until an overlap or different chat establishes it.
        return false
    }

    private fun List<MessageKey>.containsSequence(other: List<MessageKey>) = sequenceIndex(other) >= 0

    private fun List<MessageKey>.sequenceIndex(other: List<MessageKey>): Int {
        if (other.isEmpty() || other.size > size) return -1
        return (0..size - other.size).firstOrNull { start ->
            other.indices.all { this[start + it] == other[it] }
        } ?: -1
    }

    private fun overlap(first: List<MessageKey>, second: List<MessageKey>): Int =
        (minOf(first.size, second.size) downTo 1).firstOrNull { size ->
            (0 until size).all { first[first.size - size + it] == second[it] }
        } ?: 0

    private data class MessageKey(val role: MessageRole, val text: String)

    private fun normalize(text: String) = text.trim().replace(WHITESPACE, " ")

    private companion object {
        const val MAX_ANCHORS = 100
        val WHITESPACE = Regex("\\s+")
    }
}
