package app.nextsay.ime

import app.nextsay.overlay.ReplyCandidate

sealed interface ImeReplyState {
    data class Unavailable(val reason: ImeUnavailableReason) : ImeReplyState
    data class Ready(val targetPackage: String) : ImeReplyState
    data class Loading(val targetPackage: String) : ImeReplyState
    data class Results(
        val targetPackage: String,
        val candidates: List<ReplyCandidate>,
        val message: String? = null,
    ) : ImeReplyState
    data class Error(
        val targetPackage: String,
        val message: String,
        val diagnosticId: String? = null,
    ) : ImeReplyState
    data class Inserted(val targetPackage: String) : ImeReplyState
}
