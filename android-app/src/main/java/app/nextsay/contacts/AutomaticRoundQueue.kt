package app.nextsay.contacts

import app.nextsay.context.ChatContext
import app.nextsay.context.MessageRole

/** Local pending work, not a log of chat text or a queue of API retries. Main-thread owned. */
class AutomaticRoundQueue {
    data class Ticket(val epoch: Long, val context: ChatContext)
    private var epoch = 0L
    private var pending: Ticket? = null
    private var running: Ticket? = null
    val hasPending: Boolean get() = pending != null
    fun offer(context: ChatContext) { pending = Ticket(++epoch, context) }
    fun clear() { epoch++; pending = null; running = null }
    fun isCurrent(ticket: Ticket) = pending?.epoch == ticket.epoch
    fun matches(ticket: Ticket, fresh: ChatContext) = isCurrent(ticket) && sameTail(ticket.context, fresh)
    fun begin(fresh: ChatContext): Ticket? {
        val candidate = pending ?: return null
        if (running != null || !sameTail(candidate.context, fresh)) return null
        return candidate.copy(context = fresh).also { pending = it; running = it }
    }
    fun finish(ticket: Ticket) {
        if (isCurrent(ticket)) pending = null
        if (running?.epoch == ticket.epoch) running = null
    }
    fun defer(ticket: Ticket) { if (running?.epoch == ticket.epoch) running = null }
    private fun sameTail(a: ChatContext, b: ChatContext): Boolean {
        if (b.replyRound?.tailObscured == true) return false
        if (a.replyRound?.viewportRevision != b.replyRound?.viewportRevision) return false
        val oldMessages = a.replyRound?.visibleMessages ?: a.messages
        val freshMessages = b.replyRound?.visibleMessages ?: b.messages
        val old = oldMessages.lastOrNull() ?: return false
        val fresh = freshMessages.lastOrNull() ?: return false
        val count = minOf(4, oldMessages.size, freshMessages.size)
        val evidence = oldMessages.takeLast(count).zip(freshMessages.takeLast(count)).withIndex().all { (index, pair) ->
            ContactEvidence.sameConsumed(pair.first, pair.second, index == count - 1)
        }
        return a.sourcePackage == b.sourcePackage && a.replyRound?.title == b.replyRound?.title &&
            fresh.role != MessageRole.UNKNOWN && fresh.confidence >= MIN_AUTO_ROLE_CONFIDENCE && ContactEvidence.same(old, fresh) && evidence
    }
}
