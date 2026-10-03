package app.nextsay.contacts

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.nextsay.capture.CapturedConversation
import app.nextsay.context.*
import app.nextsay.history.db.NextSayDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimpleContactStoreTest {
    @Test fun savingAndBindingLegacyProfilePreservesEditsAndHistory() = withStore { store, db ->
        val person = store.create("com.tencent.mm", "原备注")
        store.observe(person.entity.id, capture("原备注"))
        val legacy = person.copy(entity = person.entity.copy(accountSpace = "legacy", confirmed = false))
        db.contactDao().save(legacy.entity)
        val edited = legacy.copy(entity = legacy.entity.copy(relationship = "manager"), details = "已编辑的补充资料")
        val linked = store.saveAndLink(edited, "com.tencent.mm", "当前备注")
        assertTrue(store.isLinked(person.entity.id, "com.tencent.mm", "当前备注"))
        assertEquals("manager", linked.entity.relationship)
        assertEquals("已编辑的补充资料", linked.details)
        assertEquals(3, store.messages(person.entity.id).size)
        assertEquals("manager", store.extras(person.entity.id, capture("当前备注").context).relationship)
    }

    @Test fun invalidBindingRollsBackEditedProfile() = withStore { store, _ ->
        val person = store.create("com.tencent.mm", "原备注")
        try { store.saveAndLink(person.copy(entity = person.entity.copy(relationship = "manager")), "com.tencent.mobileqq", "不同平台"); fail("Must reject cross-platform binding") }
        catch (_: IllegalArgumentException) { }
        assertEquals("unspecified", store.get(person.entity.id)!!.entity.relationship)
    }
    private val messages = listOf(
        ChatMessage(MessageRole.OTHER, "周六一起去看那场电影好吗", .95f),
        ChatMessage(MessageRole.ME, "好呀我来订下午三点的票", .95f),
        ChatMessage(MessageRole.OTHER, "看完我们再去附近吃个晚饭", .95f),
    )
    private fun capture(title: String, lines: List<ChatMessage> = messages) = CapturedConversation(title,
        ChatContext("wechat", "com.tencent.mm", lines, "", .95f), true)

    @Test fun ocrNameJitterWithReliableHistoryKeepsOneIdentity() = withStore { store, _ ->
        val person = store.create("com.tencent.mm", "CC_GOGO")
        store.observe(person.entity.id, capture("CC_GOGO"))
        assertEquals(person.entity.id, store.resolve(capture("CC_QOGO")).matched.single().entity.id)
        assertEquals(1, store.list("com.tencent.mm").size)
    }

    @Test fun nearNamesWithNoReliableHistoryRemainSeparate() = withStore { store, _ ->
        store.create("com.tencent.mm", "CC_GOGO")
        assertTrue(store.resolve(capture("CC_QOGO", listOf(ChatMessage(MessageRole.OTHER, "你好", .95f)))).matched.isEmpty())
    }

    @Test fun explicitMergePreservesHistoryAliasesAndMaterials() = withStore { store, db ->
        val target = store.create("com.tencent.mm", "CC_GOGO")
        val source = store.create("com.tencent.mm", "CC_QOGO")
        store.save(source.copy(details = "朋友喜欢电影", preferences = "回复自然", memory = "认识于2025年"))
        store.observe(target.entity.id, capture("CC_GOGO"))
        store.observe(source.entity.id, capture("CC_QOGO", listOf(ChatMessage(MessageRole.ME, "第二份记录也要保留", .95f))))
        val merged = store.merge(target.entity.id, source.entity.id)
        assertEquals(4, store.messages(merged.entity.id).size)
        assertTrue(merged.details.contains("朋友喜欢电影"))
        assertTrue(merged.preferences.contains("回复自然"))
        assertTrue(merged.memory.contains("认识于2025年"))
        assertNull(store.get(source.entity.id))
        assertEquals(target.entity.id, store.resolve(capture("CC_QOGO")).matched.single().entity.id)
        assertTrue(db.contactDao().audits(target.entity.id).any { it.action == "merge" })
    }

    @Test fun mergeRejectsAnotherPlatformWithoutDeletingData() = withStore { store, _ ->
        val a = store.create("com.tencent.mm", "朋友")
        val b = store.create("com.tencent.mobileqq", "朋友")
        try { store.merge(a.entity.id, b.entity.id); fail("Must reject cross-platform merge") } catch (_: IllegalArgumentException) { }
        assertNotNull(store.get(a.entity.id)); assertNotNull(store.get(b.entity.id))
    }

    @Test fun defaultMemoryIncludesPreviouslyObservedRelevantText() = withStore { store, _ ->
        val a = store.create("com.tencent.mm", "本地测试")
        store.observe(a.entity.id, capture("本地测试"))
        val extras = store.extras(a.entity.id, capture("本地测试", listOf(ChatMessage(MessageRole.OTHER, "电影几点开始", .95f))).context)
        assertTrue(extras.memory.contains("周六一起去看那场电影好吗"))
    }

    @Test fun legacyTargetKeepsCurrentIdentityAndSourceRelationshipAfterMerge() = withStore { store, db ->
        val old = store.create("com.tencent.mm", "CC_GOGO")
        val originalAliases = db.contactDao().contactAliases(old.entity.id)
        db.contactDao().deleteAliases(old.entity.id)
        originalAliases.forEach { db.contactDao().alias(it.copy(accountSpace = "legacy")) }
        db.contactDao().save(old.entity.copy(accountSpace = "legacy", confirmed = false))
        val current = store.create("com.tencent.mm", "CC_QOGO")
        store.save(current.copy(entity = current.entity.copy(relationship = "friend"), details = "朋友喜欢电影"))
        store.observe(current.entity.id, capture("CC_QOGO"))
        val merged = store.merge(old.entity.id, current.entity.id)
        assertTrue(merged.entity.confirmed)
        assertEquals(store.accountSpace, merged.entity.accountSpace)
        assertEquals("friend", merged.entity.relationship)
        assertEquals(old.entity.id, store.resolve(capture("CC_QOGO")).matched.single().entity.id)
        assertEquals(old.entity.id, store.resolve(capture("CC_GOGO")).matched.single().entity.id)
        assertTrue(db.contactDao().contactAliases(old.entity.id).any { it.accountSpace == "legacy" })
        assertTrue(store.extras(old.entity.id, capture("CC_QOGO").context).details.contains("电影"))
    }

    @Test fun mergeKeepsExplicitHistoryExclusion() = withStore { store, _ ->
        val target = store.create("com.tencent.mm", "CC_GOGO")
        val source = store.create("com.tencent.mm", "CC_QOGO")
        store.save(source.copy(entity = source.entity.copy(saveHistory = false, useMemory = false)))
        val merged = store.merge(target.entity.id, source.entity.id)
        assertFalse(merged.entity.saveHistory)
        assertFalse(merged.entity.useMemory)
    }

    private fun withStore(test: suspend (ContactStore, NextSayDatabase) -> Unit) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, NextSayDatabase::class.java).build()
        val store = ContactStore(context, database)
        val priorSpace = store.accountSpace
        try { store.accountSpace = "synthetic-simple-contact-test"; test(store, database) }
        finally { store.accountSpace = priorSpace; database.close() }
    }
}
