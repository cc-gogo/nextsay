package app.nextsay.capture

import app.nextsay.context.ChatContext

data class CapturedConversation(
    val title: String,
    val context: ChatContext,
    val persistable: Boolean,
    val tailObscured: Boolean = false,
)
