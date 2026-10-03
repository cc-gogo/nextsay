package app.nextsay.provider

/** Explicit opt-in only; missing, unrecognized or non-lover settings stay natural. */
object LoverReplyModes {
    const val NATURAL = "natural"
    const val HUANGMAO = "huangmao"
    fun normalize(relationship: String?, mode: String?): String =
        if (relationship == "lover" && mode == HUANGMAO) HUANGMAO else NATURAL
    fun label(mode: String?): String = if (mode == HUANGMAO) "黄毛" else "自然"
}
