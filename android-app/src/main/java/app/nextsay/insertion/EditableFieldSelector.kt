package app.nextsay.insertion

data class EditableFieldSnapshot(
    val index: Int,
    val bottom: Int,
    val editable: Boolean,
    val focused: Boolean,
    val password: Boolean,
)

class EditableFieldSelector {
    fun select(fields: List<EditableFieldSnapshot>): Int? {
        val safe = fields.filter { it.editable && !it.password }
        return safe.firstOrNull { it.focused }?.index
            ?: safe.maxByOrNull { it.bottom }?.index
    }
}
