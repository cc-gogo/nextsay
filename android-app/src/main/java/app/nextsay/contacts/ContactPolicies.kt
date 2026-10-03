package app.nextsay.contacts

import app.nextsay.context.ChatContext
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole

// OCR text confidence can be below 0.70 even when the bubble side is explicit.
// Automatic generation still requires a known role, but should match the OCR floor.
const val MIN_AUTO_ROLE_CONFIDENCE = 0.55f

/** Conservative event detector: observed time is not original send time. */
class IncomingRoundDetector {
    private var key: String? = null
    private var revision = 0L
    private var anchors = emptyList<ChatMessage>()
    var advancedToSelf = false
        private set
    fun reset() { key = null; anchors = emptyList(); revision = 0L }
    fun observe(contactKey: String, context: ChatContext): Boolean {
        advancedToSelf = false
        if (context.replyRound?.tailObscured == true) return false
        val visible = context.replyRound?.visibleMessages ?: context.messages
        val nextRevision = context.replyRound?.viewportRevision ?: 0L
        if (key != contactKey || nextRevision != revision) {
            key = contactKey; revision = nextRevision; anchors = visible.takeLast(100)
            return false
        }
        if (visible.isEmpty()) return false
        // Keyboard expansion may reveal an older prefix before the existing anchor.
        val containedAt = if (anchors.isEmpty() || anchors.size > visible.size) null else
            (0..visible.size - anchors.size).firstOrNull { start ->
                anchors.indices.all { ContactEvidence.sameConsumed(anchors[it], visible[start + it], it == anchors.lastIndex) }
            }
        val overlap = (minOf(anchors.size, visible.size) downTo 1).firstOrNull { count ->
            (0 until count).all { offset ->
                val index = anchors.size - count + offset
                ContactEvidence.sameConsumed(anchors[index], visible[offset], index == anchors.lastIndex)
            }
        } ?: 0
        // OCR can change an earlier line's punctuation/line break when a new
        // bubble appears. If the visible tail is reliable, retain the longest
        // role-ordered suffix instead of discarding the whole frame because one
        // consumed line no longer matches byte-for-byte.
        val fuzzyOverlap = if (containedAt == null && overlap == 0) {
            (minOf(anchors.size, visible.size) downTo 1).firstOrNull { count ->
                val oldStart = anchors.size - count
                val freshStart = visible.size - count
                (0 until count).all { i ->
                    ContactEvidence.sameConsumed(anchors[oldStart + i], visible[freshStart + i], i == count - 1)
                }
            } ?: run {
                val reliableAnchor = anchors.indexOfLast { it.role != MessageRole.UNKNOWN && it.confidence >= MIN_AUTO_ROLE_CONFIDENCE }
                val matchingIndex = if (reliableAnchor >= 0) visible.indexOfFirst {
                    ContactEvidence.sameConsumed(anchors[reliableAnchor], it, true)
                } else -1
                if (matchingIndex >= 0 && visible.size > matchingIndex + 1) visible.size - matchingIndex else 0
            }
        } else 0
        if (containedAt == null && overlap == 0 && fuzzyOverlap == 0) {
            // OCR can rewrite every earlier line when a new bubble enters the frame.
            // An increased visible count plus an explicit, sufficiently confident tail
            // is stronger evidence than the rewritten text and should start a round.
            val latest = visible.lastOrNull()
            val prefix = if (visible.size >= anchors.size) visible.take(anchors.size) else emptyList()
            val rolePrefixMatches = anchors.dropLast(1).isNotEmpty() &&
                anchors.dropLast(1).all { it.role != MessageRole.UNKNOWN } &&
                prefix.dropLast(1).size == anchors.dropLast(1).size &&
                prefix.dropLast(1).zip(anchors.dropLast(1)).all { (fresh, old) -> fresh.role == old.role }
            if (visible.size > anchors.size && latest != null && rolePrefixMatches &&
                latest.role != MessageRole.UNKNOWN && latest.confidence >= MIN_AUTO_ROLE_CONFIDENCE
            ) {
                anchors = visible.takeLast(100)
                advancedToSelf = latest.role == MessageRole.ME
                return true
            }
            return false
        }
        val consumed = containedAt?.plus(anchors.size) ?: if (fuzzyOverlap > 0) visible.size - fuzzyOverlap else overlap
        val appended = visible.drop(consumed)
        val correctedAnchors = if (containedAt != null) visible.drop(containedAt) else anchors.dropLast(maxOf(overlap, fuzzyOverlap)) + visible
        if (appended.isEmpty()) {
            anchors = correctedAnchors.takeLast(100)
            return false
        }
        // Only the latest message selects the reply/supplement target. An older
        // uncertain line must not block a reliable new tail indefinitely.
        val tail = appended.last()
        if (tail.role == MessageRole.UNKNOWN || tail.confidence < MIN_AUTO_ROLE_CONFIDENCE) return false
        anchors = correctedAnchors.takeLast(100)
        advancedToSelf = appended.last().role == MessageRole.ME
        return true
    }
}

object ContactEvidence {
    fun same(a: ChatMessage, b: ChatMessage) = a.role == b.role && normalize(a.text) == normalize(b.text)
    // Only previously consumed, earlier uncertain rows can be corrected. The
    // reliable tail must still match exactly; this is not contact identity evidence.
    internal fun sameConsumed(a: ChatMessage, b: ChatMessage, isTail: Boolean): Boolean =
        same(a, b) || (!isTail && (a.role == MessageRole.UNKNOWN || a.confidence < MIN_AUTO_ROLE_CONFIDENCE))
    fun forwardOverlap(old: List<ChatMessage>, visible: List<ChatMessage>): Int =
        (minOf(old.size, visible.size) downTo 1).firstOrNull { n ->
            (0 until n).all { same(old[old.size - n + it], visible[it]) }
        } ?: 0
    fun distinctiveOverlap(old: List<ChatMessage>, visible: List<ChatMessage>): Boolean {
        // Match contiguous, role-ordered evidence anywhere in a saved segment, not only
        // its tail. Short greetings and uncertain OCR never establish identity.
        for (start in old.indices) for (offset in visible.indices) {
            val distinct = linkedSetOf<String>()
            var i = 0
            while (start + i < old.size && offset + i < visible.size) {
                val a = old[start + i]
                val b = visible[offset + i]
                if (!same(a, b) || a.role == MessageRole.UNKNOWN || minOf(a.confidence, b.confidence) < .8f) break
                normalize(b.text).takeIf { it.length >= 8 }?.let(distinct::add)
                if (distinct.size >= 3 && distinct.sumOf { it.length } >= 30) return true
                i++
            }
        }
        return false
    }
    fun normalize(text: String) = text.trim().replace(Regex("\\s+"), " ")
}

object LocalMemorySelector {
    fun select(recent: List<ChatMessage>, history: List<String>, budget: Int = 1200): String {
        val text = recent.takeLast(6).joinToString(" ") { it.text }.lowercase()
        val terms = Regex("[a-z0-9]{3,}|[\\p{IsHan}]{2,}").findAll(text).flatMap { match ->
            val value = match.value
            if (value.all { it.code in 0x4e00..0x9fff }) value.windowed(2).asSequence() else sequenceOf(value)
        }.toSet()
        if (terms.isEmpty()) return ""
        val existing = recent.map { ContactEvidence.normalize(it.text) }.toSet()
        return history.distinct().filter { candidate ->
            ContactEvidence.normalize(candidate.substringAfter("：", candidate)) !in existing
        }.map { it to terms.count { term -> it.lowercase().contains(term) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(8)
            .joinToString("\n") { it.first }.take(budget.coerceAtLeast(0))
    }
}
