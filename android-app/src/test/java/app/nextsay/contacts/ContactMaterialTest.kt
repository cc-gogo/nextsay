package app.nextsay.contacts

import org.junit.Assert.*
import org.junit.Test

class ContactMaterialTest {
    @Test fun smallEditAfterLongBackgroundPreservesSeparatePreferenceAndMemory() {
        val background = "相处背景".repeat(300)
        val display = ContactMaterial.display(background, "不要太正式", "认识于2025年")
        val edited = ContactMaterial.edited(display.replace("相处背景", "朋友背景"))
        assertEquals("朋友背景".repeat(300), edited.details)
        assertEquals("不要太正式", edited.preferences)
        assertEquals("认识于2025年", edited.memory)
    }
    @Test fun newFreeformNotesRemainUsableWithoutExtraFields() {
        val edited = ContactMaterial.edited("喜欢电影，回复轻松自然")
        assertEquals("喜欢电影，回复轻松自然", edited.details)
        assertEquals("", edited.preferences)
        assertEquals("", edited.memory)
    }
    @Test fun repeatedEditingDoesNotDuplicateOrDropSections() {
        val edited = ContactMaterial.edited("背景\n\n回复偏好：\n简短\n\n已确认的记忆：\n喜欢电影")
        assertEquals("背景\n\n回复偏好：\n简短\n\n已确认的记忆：\n喜欢电影", ContactMaterial.display(edited.details, edited.preferences, edited.memory))
    }
}
