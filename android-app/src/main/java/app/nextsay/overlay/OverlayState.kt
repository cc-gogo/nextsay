package app.nextsay.overlay

import app.nextsay.context.ChatContext

data class ReplyCandidate(val style: String, val text: String)

sealed interface OverlayState {
    data object Idle : OverlayState
    data class Preview(val context: ChatContext) : OverlayState
    data class Loading(val context: ChatContext) : OverlayState
    data class Results(val context: ChatContext, val candidates: List<ReplyCandidate>) : OverlayState
    data class Error(
        val context: ChatContext,
        val message: String,
        val diagnosticId: String? = null,
    ) : OverlayState
}
