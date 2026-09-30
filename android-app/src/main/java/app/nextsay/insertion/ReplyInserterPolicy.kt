package app.nextsay.insertion

data class FieldPolicySnapshot(
    val editable: Boolean,
    val focused: Boolean,
    val password: Boolean,
)

enum class InsertDenial {
    BLANK_TEXT,
    PACKAGE_CHANGED,
    PASSWORD_FIELD,
    NOT_EDITABLE,
    NOT_FOCUSED,
}

class ReplyInserterPolicy {
    fun check(
        expectedPackage: String,
        activePackage: String?,
        text: String,
        field: FieldPolicySnapshot,
    ): InsertDenial? = when {
        text.isBlank() -> InsertDenial.BLANK_TEXT
        activePackage != expectedPackage -> InsertDenial.PACKAGE_CHANGED
        field.password -> InsertDenial.PASSWORD_FIELD
        !field.editable -> InsertDenial.NOT_EDITABLE
        !field.focused -> InsertDenial.NOT_FOCUSED
        else -> null
    }
}
