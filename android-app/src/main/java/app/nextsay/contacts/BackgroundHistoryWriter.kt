package app.nextsay.contacts

import app.nextsay.capture.CapturedConversation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One FIFO worker: persistence never runs on the read/trigger path. */
class BackgroundHistoryWriter(
    scope: CoroutineScope,
    private val persist: suspend (String, CapturedConversation) -> Unit,
    private val onFailure: () -> Unit = {},
    private val currentEpoch: () -> Long = { 0L },
    private val persistAtEpoch: (suspend (String, CapturedConversation, Long) -> Unit)? = null,
    private val isEpochCurrent: (String, Long) -> Boolean = { _, epoch -> epoch == currentEpoch() },
) {
    private data class Pending(val id: String, val capture: CapturedConversation, val epoch: Long)
    private val pending = Channel<Pending>(64)
    init {
        val worker = scope.launch {
            for ((id, capture, epoch) in pending) {
                try {
                    if (!isEpochCurrent(id, epoch)) continue
                    if (persistAtEpoch != null) persistAtEpoch.invoke(id, capture, epoch) else persist(id, capture)
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { runCatching(onFailure) }
            }
        }
        worker.invokeOnCompletion { pending.cancel() }
    }
    fun submit(id: String, capture: CapturedConversation, epoch: Long = currentEpoch()): Boolean = pending.trySend(Pending(id, capture, epoch)).isSuccess.also {
        if (!it) runCatching(onFailure)
    }
}
