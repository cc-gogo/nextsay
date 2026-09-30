package app.nextsay.insertion

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyInserterPolicyTest {
    private val policy = ReplyInserterPolicy()

    @Test
    fun `rejects blank candidate`() {
        assertEquals(InsertDenial.BLANK_TEXT, policy.check("com.tencent.mm", "com.tencent.mm", " ", field()))
    }

    @Test
    fun `rejects package change`() {
        assertEquals(InsertDenial.PACKAGE_CHANGED, policy.check("com.tencent.mm", "com.tencent.mobileqq", "收到", field()))
    }

    @Test
    fun `rejects password field`() {
        assertEquals(InsertDenial.PASSWORD_FIELD, policy.check("com.tencent.mm", "com.tencent.mm", "收到", field(password = true)))
    }

    @Test
    fun `rejects non editable field`() {
        assertEquals(InsertDenial.NOT_EDITABLE, policy.check("com.tencent.mm", "com.tencent.mm", "收到", field(editable = false)))
    }

    @Test
    fun `rejects field without focus`() {
        assertEquals(InsertDenial.NOT_FOCUSED, policy.check("com.tencent.mm", "com.tencent.mm", "收到", field(focused = false)))
    }

    @Test
    fun `allows focused editable non password field`() {
        assertEquals(null, policy.check("com.tencent.mm", "com.tencent.mm", "收到", field()))
    }

    private fun field(
        editable: Boolean = true,
        focused: Boolean = true,
        password: Boolean = false,
    ) = FieldPolicySnapshot(editable, focused, password)
}
