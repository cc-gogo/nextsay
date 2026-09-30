package app.nextsay.provider

import app.nextsay.api.ReplyCandidateDto
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

class ReplyCandidateParser(
    private val gson: Gson,
) {
    fun parse(content: String): List<ReplyCandidateDto> {
        val json = stripSingleFence(content)
        val candidates = try {
            val root = JsonParser.parseString(json)
            require(root.isJsonObject) { "Candidate JSON must be an object" }
            val entries = root.asJsonObject.get("candidates")
            require(entries != null && entries.isJsonArray) { "Candidates must be an array" }
            entries.asJsonArray.map { entry ->
                require(entry.isJsonObject) { "Candidate must be an object" }
                val candidate = entry.asJsonObject
                fun stringField(name: String): String {
                    val value = candidate.get(name)
                    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                        "Candidate field must be a string"
                    }
                    return value.asString
                }
                ReplyCandidateDto(stringField("style"), stringField("text"))
            }
        } catch (error: JsonParseException) {
            throw IllegalArgumentException("Invalid candidate JSON", error)
        }
        require(candidates.size == REQUIRED_STYLES.size) {
            "Exactly three candidates are required"
        }
        val normalized = candidates.map { candidate ->
            val style = candidate.style.trim()
            val text = candidate.text.trim()
            require(style in REQUIRED_STYLES) { "Unsupported candidate style" }
            require(text.isNotEmpty() && text.length <= MAX_TEXT_LENGTH) {
                "Invalid candidate text"
            }
            ReplyCandidateDto(style, text)
        }
        require(normalized.map { it.style }.toSet() == REQUIRED_STYLES) {
            "Each required style must appear once"
        }
        require(normalized.map { it.text }.toSet().size == normalized.size) {
            "Candidate text must be unique"
        }
        return REQUIRED_STYLES_IN_ORDER.map { style ->
            normalized.single { it.style == style }
        }
    }

    private fun stripSingleFence(content: String): String {
        val trimmed = content.trim()
        val match = FENCE_REGEX.matchEntire(trimmed)
        return match?.groupValues?.get(1)?.trim() ?: trimmed
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 500
        val REQUIRED_STYLES_IN_ORDER = listOf("concise", "tactful", "natural")
        val REQUIRED_STYLES = REQUIRED_STYLES_IN_ORDER.toSet()
        val FENCE_REGEX = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE)
    }
}
