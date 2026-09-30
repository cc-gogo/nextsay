package app.nextsay.history

import app.nextsay.context.ChatMessage

data class MergeResult(
    val combined: List<ChatMessage>,
    val appended: List<ChatMessage>,
    val reliableOverlap: Boolean,
)

class HistoryMerger {
    fun merge(existing: List<ChatMessage>, visible: List<ChatMessage>): MergeResult {
        if (existing.isEmpty()) return MergeResult(visible, visible, reliableOverlap = true)
        val overlap = findLongestOverlap(existing, visible)
        if (overlap == 0) return MergeResult(existing, emptyList(), reliableOverlap = false)
        val appended = visible.drop(overlap)
        return MergeResult(existing + appended, appended, reliableOverlap = true)
    }

    private fun findLongestOverlap(existing: List<ChatMessage>, visible: List<ChatMessage>): Int {
        for (size in minOf(existing.size, visible.size) downTo 1) {
            val historyStart = existing.size - size
            if ((0 until size).all { index -> sameMessage(existing[historyStart + index], visible[index]) }) {
                return size
            }
        }
        return 0
    }

    private fun sameMessage(first: ChatMessage, second: ChatMessage): Boolean =
        first.role == second.role && normalize(first.text) == normalize(second.text)

    private fun normalize(text: String): String = text.trim().replace(WHITESPACE_PATTERN, " ")

    private companion object {
        val WHITESPACE_PATTERN = Regex("\\s+")
    }
}
