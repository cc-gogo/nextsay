package app.nextsay.insertion

import android.os.Bundle
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

sealed interface InsertResult {
    data class Success(val previousDraft: String) : InsertResult
    data class Failure(val reason: InsertDenial?) : InsertResult
}

class ReplyInserter(
    private val policy: ReplyInserterPolicy = ReplyInserterPolicy(),
    private val selector: EditableFieldSelector = EditableFieldSelector(),
) {
    @Suppress("DEPRECATION")
    fun insert(
        root: AccessibilityNodeInfo,
        expectedPackage: String,
        activePackage: String,
        text: String,
    ): InsertResult {
        val field = selectField(root) ?: return InsertResult.Failure(InsertDenial.NOT_FOCUSED)
        return try {
            val focusAccepted = field.isFocused || field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val denial = policy.check(
                expectedPackage = expectedPackage,
                activePackage = activePackage,
                text = text,
                field = FieldPolicySnapshot(field.isEditable, focusAccepted, field.isPassword),
            )
            if (denial != null) return InsertResult.Failure(denial)

            val previousDraft = field.text?.toString().orEmpty()
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                InsertResult.Success(previousDraft)
            } else {
                InsertResult.Failure(null)
            }
        } finally {
            field.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun selectField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val fields = mutableListOf<AccessibilityNodeInfo>()
        try {
            collectEditableNodes(root, fields)
            val snapshots = fields.mapIndexed { index, node ->
                val bounds = Rect().also(node::getBoundsInScreen)
                EditableFieldSnapshot(index, bounds.bottom, node.isEditable, node.isFocused, node.isPassword)
            }
            val selectedIndex = selector.select(snapshots)
            val selected = selectedIndex?.let(fields::getOrNull)
            fields.forEachIndexed { index, node ->
                if (index != selectedIndex) node.recycle()
            }
            return selected
        } catch (error: RuntimeException) {
            fields.forEach(AccessibilityNodeInfo::recycle)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun collectEditableNodes(
        node: AccessibilityNodeInfo,
        fields: MutableList<AccessibilityNodeInfo>,
    ) {
        if (node.isEditable) fields += AccessibilityNodeInfo.obtain(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                collectEditableNodes(child, fields)
            } finally {
                child.recycle()
            }
        }
    }
}
