package app.nextsay.contacts

import android.content.Intent
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.annotation.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Before
import org.junit.After

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContactEditorUiTest {
    @Test fun savingFromHomeFeedsRelationshipModeAndMaterialToTheRealPanelWithoutAnotherBindingStep() {
        val testContext = RuntimeEnvironment.getApplication()
        testContext.getSharedPreferences("nextsay-contacts", Context.MODE_PRIVATE).edit().putBoolean("simple-memory-notice-v1", true).commit()
        val dependencies = (testContext as app.nextsay.NextSayApplication).dependencies
        val field = dependencies.javaClass.getDeclaredField("contactStore").apply { isAccessible = true }
        val original = field.get(dependencies)
        val db = androidx.room.Room.inMemoryDatabaseBuilder(testContext, app.nextsay.history.db.NextSayDatabase::class.java).build()
        val cipher = object : app.nextsay.history.MessageCipher {
            override fun encrypt(plaintext: String) = "encrypted:$plaintext"
            override fun decrypt(ciphertext: String) = ciphertext.removePrefix("encrypted:")
        }
        val store = ContactStore(testContext, db, cipher, { "test-index:$it" })
        field.set(dependencies, store)
        val legacy = ContactSnapshot(ContactEntity("sync", "com.tencent.mm", "legacy", "", confirmed = false, useMemory = false), "a CC_gogo", "喜欢简短回复", "", "")
        kotlinx.coroutines.runBlocking { store.save(legacy) }
        val controller = Robolectric.buildActivity(ContactSettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            ContactSettingsActivity::class.java.getDeclaredMethod("editor", ContactSnapshot::class.java).apply { isAccessible = true }.invoke(activity, legacy)
            fun click(label: String) = descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text == label }.performClick()
            click("恋人"); click("黄毛"); click("保存资料")
            val deadline = System.nanoTime() + 3_000_000_000L
            var stored: ContactSnapshot? = null
            while (System.nanoTime() < deadline) {
                mainDispatcher.scheduler.runCurrent(); shadowOf(android.os.Looper.getMainLooper()).idle()
                stored = kotlinx.coroutines.runBlocking { store.get("sync") }
                if (stored?.entity?.relationship == "lover" && stored.entity.confirmed) break
                Thread.yield()
            }
            assertEquals("lover", stored!!.entity.relationship)
            assertEquals("huangmao", stored.entity.replyMode)
            assertTrue(ShadowAlertDialog.getLatestAlertDialog()?.isShowing != true)
            val lines = listOf(app.nextsay.context.ChatMessage(app.nextsay.context.MessageRole.OTHER, "测试消息", 1f))
            val context = app.nextsay.context.ChatContext("wechat", "com.tencent.mm", lines, "", 1f,
                replyRound = app.nextsay.context.ReplyRoundSnapshot("aCC_gogo", lines))
            val prepared = kotlinx.coroutines.runBlocking { store.prepareContext(context) }
            assertEquals("喜欢简短回复", prepared.generationExtras!!.details)
            val panel = app.nextsay.overlay.OverlayViewFactory(testContext).panel(app.nextsay.overlay.PanelCallbacks({}, { _, _ -> }, {}, {}, {}, {}))
            panel.render(app.nextsay.overlay.OverlayState.Preview(prepared))
            assertTrue(descendants(panel.root).filterIsInstance<TextView>().any { visibleInTree(it) && it.text.toString().contains("恋人黄毛风格") })
            assertEquals(android.view.View.GONE, descendants(panel.root).filterIsInstance<app.nextsay.ui.RelationshipChoices>().single().visibility)
        } finally {
            controller.pause().stop().destroy(); field.set(dependencies, original); db.close()
        }
    }

    @Test fun dormantLegacyAutomaticSettingRequiresConsentBeforeProfileActivation() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("nextsay-contacts", Context.MODE_PRIVATE).edit().putBoolean("simple-memory-notice-v1", true).commit()
        val controller = Robolectric.buildActivity(ContactSettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val legacy = ContactSnapshot(ContactEntity("dormant", "com.tencent.mm", "legacy", "", autoEnabled = true, confirmed = false), "测试对象", "", "", "")
            ContactSettingsActivity::class.java.getDeclaredMethod("editor", ContactSnapshot::class.java).apply { isAccessible = true }.invoke(activity, legacy)
            descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text == "保存资料" }.performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull("Activating dormant automatic generation must not bypass its cost/privacy consent", dialog)
            assertTrue(shadowOf(dialog).message.toString().contains("可能收费"))
            dialog.dismiss()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun loverReplyModesAreOnlyShownForLoverAndDoNotChangeTheRelationship() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("nextsay-contacts", Context.MODE_PRIVATE).edit().putBoolean("simple-memory-notice-v1", true).commit()
        val controller = Robolectric.buildActivity(ContactSettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val profile = ContactSnapshot(ContactEntity("synthetic", "com.tencent.mm", "default", "", relationship = "friend"), "测试对象", "", "", "")
            ContactSettingsActivity::class.java.getDeclaredMethod("editor", ContactSnapshot::class.java).apply { isAccessible = true }.invoke(activity, profile)
            fun button(label: String) = descendants(activity.window.decorView).filterIsInstance<TextView>().singleOrNull { it.text.toString() == label }
            assertNotNull("The editor should offer the optional lover mode", button("黄毛"))
            assertFalse(visibleInTree(button("黄毛")!!))
            button("恋人")!!.performClick()
            assertTrue(visibleInTree(button("黄毛")!!))
            assertTrue(button("自然")!!.isSelected)
            button("黄毛")!!.performClick()
            assertTrue(button("黄毛")!!.isSelected)
            assertTrue(button("恋人")!!.isSelected)
            button("同事")!!.performClick()
            assertFalse(visibleInTree(button("黄毛")!!))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun saveFlowBlocksRepeatedClicksAndCancelAllowsEditingAgain() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("nextsay-contacts", Context.MODE_PRIVATE).edit().putBoolean("simple-memory-notice-v1", true).commit()
        val intent = Intent(app, ContactSettingsActivity::class.java).putExtra("package", "com.tencent.mm").putExtra("title", "不同的当前聊天对象")
        val controller = Robolectric.buildActivity(ContactSettingsActivity::class.java, intent).setup()
        try {
            val activity = controller.get()
            val profile = ContactSnapshot(ContactEntity("synthetic-legacy", "com.tencent.mm", "legacy", "", confirmed = false), "本地测试对象", "", "", "")
            ContactSettingsActivity::class.java.getDeclaredMethod("editor", ContactSnapshot::class.java).apply { isAccessible = true }.invoke(activity, profile)
            val save = descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text == "保存资料" }
            val link = descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text == "确认当前聊天对象" }
            val manage = descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text == "记忆与对象管理" }
            save.performClick()
            assertFalse("Do not queue duplicate stale profile writes", save.isEnabled)
            assertFalse(link.isEnabled)
            assertFalse("Navigation must not create a second editor with stale binding state", manage.isEnabled)
            val deadline = System.nanoTime() + 3_000_000_000L
            while (ShadowAlertDialog.getLatestAlertDialog() == null && System.nanoTime() < deadline) {
                mainDispatcher.scheduler.runCurrent(); shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.yield()
            }
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull(dialog)
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            mainDispatcher.scheduler.runCurrent()
            assertTrue("Cancel must allow correcting and saving the profile", save.isEnabled)
            assertTrue(link.isEnabled)
            assertTrue(manage.isEnabled)
        } finally { controller.pause().stop().destroy() }
    }
    private val mainDispatcher = StandardTestDispatcher()
    @OptIn(ExperimentalCoroutinesApi::class) @Before fun setMainDispatcher() { Dispatchers.setMain(mainDispatcher) }
    @OptIn(ExperimentalCoroutinesApi::class) @After fun resetMainDispatcher() { Dispatchers.resetMain() }
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun visibleInTree(v: View) = generateSequence(v) { it.parent as? View }.all { it.visibility == View.VISIBLE }

    @Test fun savingLegacyRelationshipOffersExplicitCurrentChatBinding() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("nextsay-contacts", Context.MODE_PRIVATE).edit().putBoolean("simple-memory-notice-v1", true).commit()
        val intent = Intent(app, ContactSettingsActivity::class.java).putExtra("package", "com.tencent.mm").putExtra("title", "不同的当前聊天对象")
        val controller = Robolectric.buildActivity(ContactSettingsActivity::class.java, intent).setup()
        try {
            val activity = controller.get()
            val profile = ContactSnapshot(ContactEntity("synthetic-legacy", "com.tencent.mm", "legacy", "", confirmed = false), "本地测试对象", "", "", "")
            ContactSettingsActivity::class.java.getDeclaredMethod("editor", ContactSnapshot::class.java).apply { isAccessible = true }.invoke(activity, profile)
            descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text == "领导" }.performClick()
            descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text == "保存资料" }.performClick()
            val deadline = System.nanoTime() + 3_000_000_000L
            while (ShadowAlertDialog.getLatestAlertDialog() == null && System.nanoTime() < deadline) {
                mainDispatcher.scheduler.runCurrent()
                shadowOf(android.os.Looper.getMainLooper()).idle()
                Thread.yield()
            }
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull("Saving an unlinked profile must offer explicit binding, not silently claim synchronization", dialog)
            assertTrue(shadowOf(dialog).message.toString().contains("同一个人"))
            assertEquals("保存并用于当前聊天", dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).text.toString())
            assertEquals("仅保存资料", dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).text.toString())
            dialog.dismiss()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun leavingEditorDuringBindingLookupDoesNotShowAnOldChatDialog() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("nextsay-contacts", Context.MODE_PRIVATE).edit().putBoolean("simple-memory-notice-v1", true).commit()
        val intent = Intent(app, ContactSettingsActivity::class.java).putExtra("package", "com.tencent.mm").putExtra("title", "不同的当前聊天对象")
        val controller = Robolectric.buildActivity(ContactSettingsActivity::class.java, intent).setup()
        try {
            val activity = controller.get()
            val profile = ContactSnapshot(ContactEntity("synthetic-legacy", "com.tencent.mm", "legacy", "", confirmed = false), "本地测试对象", "", "", "")
            ContactSettingsActivity::class.java.getDeclaredMethod("editor", ContactSnapshot::class.java).apply { isAccessible = true }.invoke(activity, profile)
            descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text == "保存资料" }.performClick()
            ContactSettingsActivity::class.java.getDeclaredMethod("reload").apply { isAccessible = true }.invoke(activity)
            val deadline = System.nanoTime() + 300_000_000L
            while (System.nanoTime() < deadline) { mainDispatcher.scheduler.runCurrent(); shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.yield() }
            assertTrue("A departed editor must not prompt to bind its old chat", ShadowAlertDialog.getLatestAlertDialog()?.isShowing != true)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun linkingUsesSameSaveConfirmationWithoutDiscardingSelectedRelationship() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("nextsay-contacts", Context.MODE_PRIVATE).edit().putBoolean("simple-memory-notice-v1", true).commit()
        val intent = Intent(app, ContactSettingsActivity::class.java).putExtra("package", "com.tencent.mm").putExtra("title", "不同的当前聊天对象")
        val controller = Robolectric.buildActivity(ContactSettingsActivity::class.java, intent).setup()
        try {
            val activity = controller.get()
            val profile = ContactSnapshot(ContactEntity("synthetic-legacy", "com.tencent.mm", "legacy", "", confirmed = false), "本地测试对象", "", "", "")
            ContactSettingsActivity::class.java.getDeclaredMethod("editor", ContactSnapshot::class.java).apply { isAccessible = true }.invoke(activity, profile)
            descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text == "领导" }.performClick()
            descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text == "确认当前聊天对象" }.performClick()
            val deadline = System.nanoTime() + 3_000_000_000L
            while (ShadowAlertDialog.getLatestAlertDialog() == null && System.nanoTime() < deadline) {
                mainDispatcher.scheduler.runCurrent(); shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.yield()
            }
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull(dialog)
            assertEquals("保存并用于当前聊天", dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).text.toString())
            assertTrue(descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text == "领导" }.isSelected)
            dialog.dismiss()
        } finally { controller.pause().stop().destroy() }
    }
}
