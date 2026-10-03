package app.nextsay.contacts

data class EditedContactMaterial(val details: String, val preferences: String, val memory: String)

/** One user-facing field; retain legacy priority and fact/memory boundaries locally. */
object ContactMaterial {
    private val headers = Regex("(?:^|\n\n)(回复偏好|已确认的记忆)：\n")
    fun display(details: String, preferences: String, memory: String): String = listOf(
        details, preferences.takeIf { it.isNotBlank() }?.let { "回复偏好：\n$it" }.orEmpty(),
        memory.takeIf { it.isNotBlank() }?.let { "已确认的记忆：\n$it" }.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString("\n\n")
    fun edited(text: String): EditedContactMaterial {
        val matches = headers.findAll(text).toList()
        if (matches.isEmpty()) return EditedContactMaterial(text, "", "")
        val details = text.substring(0, matches.first().range.first)
        val preferences = mutableListOf<String>()
        val memory = mutableListOf<String>()
        matches.forEachIndexed { i, match ->
            val value = text.substring(match.range.last + 1, matches.getOrNull(i + 1)?.range?.first ?: text.length)
            (if (match.groupValues[1] == "回复偏好") preferences else memory).add(value)
        }
        return EditedContactMaterial(details, preferences.joinToString("\n\n"), memory.joinToString("\n\n"))
    }
}
