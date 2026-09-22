package app.nextsay.overlay

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

class ClipboardReplyCopier(context: Context) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    fun copy(candidate: ReplyCandidate): Boolean = try {
        val clip = ClipData.newPlainText("NextSay 回复", candidate.text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard?.setPrimaryClip(clip)
        clipboard != null
    } catch (_: RuntimeException) {
        false
    }
}
