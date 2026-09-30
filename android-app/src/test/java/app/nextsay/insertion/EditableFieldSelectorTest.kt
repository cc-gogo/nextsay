package app.nextsay.insertion

import org.junit.Assert.assertEquals
import org.junit.Test

class EditableFieldSelectorTest {
    private val selector = EditableFieldSelector()

    @Test
    fun `prefers a focused safe editor`() {
        val selected = selector.select(
            listOf(
                field(index = 0, bottom = 2100),
                field(index = 1, bottom = 900, focused = true),
            ),
        )

        assertEquals(1, selected)
    }

    @Test
    fun `selects bottom most safe editor when none is focused`() {
        val selected = selector.select(
            listOf(
                field(index = 0, bottom = 800),
                field(index = 1, bottom = 2050),
                field(index = 2, bottom = 1400),
            ),
        )

        assertEquals(1, selected)
    }

    @Test
    fun `rejects password and non editable fields`() {
        val selected = selector.select(
            listOf(
                field(index = 0, bottom = 2100, password = true),
                field(index = 1, bottom = 2200, editable = false),
            ),
        )

        assertEquals(null, selected)
    }

    private fun field(
        index: Int,
        bottom: Int,
        editable: Boolean = true,
        focused: Boolean = false,
        password: Boolean = false,
    ) = EditableFieldSnapshot(index, bottom, editable, focused, password)
}
