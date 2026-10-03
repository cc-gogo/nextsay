package app.nextsay.contacts

import androidx.room.Room
import app.nextsay.capture.CapturedConversation
import app.nextsay.context.*
import app.nextsay.history.MessageCipher
import app.nextsay.history.db.NextSayDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContactStorePerformanceTest {
    @Test fun aTokenTakenDuringDeletionIsInvalidOnceDeletionCompletes() = runBlocking {
        lateinit var store: ContactStore
        var duringDelete: Long? = null
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), NextSayDatabase::class.java)
            .setQueryCallback({ sql, _ ->
                if (sql.startsWith("DELETE FROM contact_observations WHERE contactId")) duringDelete = store.historyEpoch
            }, java.util.concurrent.Executor { it.run() }).build()
        store = ContactStore(RuntimeEnvironment.getApplication(), db, CountingCipher(), { "index:$it" })
        try {
            store.clearHistory("a")
            assertNotNull("The DAO deletion callback must observe the in-progress token", duringDelete)
            assertFalse("A reader/queued frame started during deletion must not survive its completion", store.isHistoryEpochCurrent("a", duringDelete!!))
        } finally { db.close() }
    }
    @Test fun deletingProfileDuringReadDoesNotCacheTheRemainingDeletedFields() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), NextSayDatabase::class.java).build()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var detailReads = 0
        val cipher = object : MessageCipher {
            override fun encrypt(plaintext: String) = "encrypted:$plaintext"
            override fun decrypt(ciphertext: String): String {
                if (ciphertext == "encrypted:阻塞姓名") { started.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
                if (ciphertext == "encrypted:删除的资料") detailReads++
                return ciphertext.removePrefix("encrypted:")
            }
        }
        val store = ContactStore(RuntimeEnvironment.getApplication(), db, cipher, { "index:$it" })
        try {
            db.contactDao().save(ContactEntity("a", "com.tencent.mm", "default", "encrypted:阻塞姓名", encryptedDetails = "encrypted:删除的资料"))
            val background = async(Dispatchers.IO) { store.get("a") }
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS))
                store.delete("a")
            } finally { release.countDown() }
            assertNull(background.await())
            assertEquals(0, detailReads)
        } finally { db.close() }
    }
    @Test fun corruptFarHistoryDoesNotCrashTheBackgroundWarmer() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), NextSayDatabase::class.java).build()
        val cipher = object : MessageCipher {
            override fun encrypt(plaintext: String) = "encrypted:$plaintext"
            override fun decrypt(ciphertext: String): String {
                check(ciphertext != "corrupt") { "synthetic corruption" }
                return ciphertext.removePrefix("encrypted:")
            }
        }
        val store = ContactStore(RuntimeEnvironment.getApplication(), db, cipher, { "index:$it" })
        try {
            store.saveProfile(ContactSnapshot(ContactEntity("a", "com.tencent.mm", "default", ""), "测试对象", "", "", ""))
            db.contactDao().observe(listOf(ContactObservation(contactId = "a", segmentId = "old", role = "other", encryptedText = "corrupt", confidence = 1f, observedAt = 1L)))
            assertFalse(store.warmMemory("a"))
            assertEquals("测试对象", store.get("a")!!.name)
        } finally { db.close() }
    }
    @Test fun deletingHistoryDuringColdPreparationDoesNotCacheRemainingDeletedRows() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), NextSayDatabase::class.java).build()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var secondReads = 0
        val cipher = object : MessageCipher {
            override fun encrypt(plaintext: String) = "encrypted:$plaintext"
            override fun decrypt(ciphertext: String): String {
                if (ciphertext == "encrypted:第一条阻塞") { started.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
                if (ciphertext == "encrypted:考试已删除第二条") secondReads++
                return ciphertext.removePrefix("encrypted:")
            }
        }
        val store = ContactStore(RuntimeEnvironment.getApplication(), db, cipher, { "index:$it" })
        try {
            store.saveProfile(ContactSnapshot(ContactEntity("a", "com.tencent.mm", "default", ""), "测试对象", "", "", ""))
            db.contactDao().observe(listOf("考试已删除第二条", "第一条阻塞").map { ContactObservation(contactId = "a", segmentId = "old", role = "other", encryptedText = cipher.encrypt(it), confidence = 1f, observedAt = 1L) })
            val background = async(Dispatchers.IO) { store.extras("a", frame("考试怎么样").context) }
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS))
                store.clearHistory("a")
            } finally { release.countDown() }
            assertEquals(GenerationExtras(), background.await())
            assertEquals("Do not decrypt remaining rows from a deleted snapshot", 0, secondReads)
        } finally { db.close() }
    }
    @Test fun firstReplyUsesRecentMemoryAndWarmupRestoresFartherRelevantHistory() = runBlocking {
        fixture { store, db, cipher ->
            db.contactDao().observe((0 until 40).map { ContactObservation(contactId = "a", segmentId = "old", role = "other",
                encryptedText = cipher.encrypt(if (it == 0) "旧历史小猫手术后需要复诊" else "旧历史无关记录$it"), confidence = 1f, observedAt = 1L) })
            cipher.oldHistoryReads = 0
            val context = frame("小猫手术后复诊怎么办").context
            val first = store.extras("a", context)
            assertTrue("The first request must bound cold hardware decrypt work", cipher.oldHistoryReads <= 12)
            assertFalse(first.memory.contains("小猫手术后需要复诊"))
            store.warmMemory("a")
            assertTrue(store.extras("a", context).memory.contains("小猫手术后需要复诊"))
            assertEquals(40, store.messages("a").size)
        }
    }
    @Test fun clearingHistoryInvalidatesAlreadyQueuedFramesButAcceptsNewFrames() = runBlocking {
        fixture { store, _, _ ->
            val queuedEpoch = store.historyEpoch
            store.clearHistory("a")
            store.observe("a", frame("应丢弃的旧队列"), expectedEpoch = queuedEpoch)
            assertTrue(store.messages("a").isEmpty())
            store.observe("a", frame("删除后新消息"), expectedEpoch = store.historyEpoch)
            assertEquals(listOf("删除后新消息"), store.messages("a").map { it.second.text })
        }
    }
    @Test fun deletingOneContactDoesNotDropAnotherContactsQueuedHistory() = runBlocking {
        fixture { store, _, _ ->
            store.saveProfile(ContactSnapshot(ContactEntity("b", "com.tencent.mm", "default", ""), "另一个对象", "", "", ""))
            val queuedEpoch = store.historyEpoch
            store.clearHistory("a")
            store.observe("b", frame("另一个对象待保存消息"), expectedEpoch = queuedEpoch)
            assertEquals(listOf("另一个对象待保存消息"), store.messages("b").map { it.second.text })
        }
    }
    @Test fun warmedObjectResolutionDoesNotWaitForABackgroundHistoryDecrypt() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), NextSayDatabase::class.java).build()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cipher = object : MessageCipher {
            override fun encrypt(plaintext: String) = "encrypted:$plaintext"
            override fun decrypt(ciphertext: String): String {
                if (ciphertext == "encrypted:阻塞的历史") { started.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
                return ciphertext.removePrefix("encrypted:")
            }
        }
        val store = ContactStore(RuntimeEnvironment.getApplication(), db, cipher, { "index:$it" })
        try {
            store.saveProfile(ContactSnapshot(ContactEntity("a", "com.tencent.mm", "default", ""), "测试对象", "", "", ""))
            store.resolve(frame("当前消息"))
            db.contactDao().observe(listOf(ContactObservation(contactId = "a", segmentId = "latest", role = "other", encryptedText = "encrypted:阻塞的历史", confidence = 1f, observedAt = 1L)))
            val background = async(Dispatchers.IO) { store.observe("a", frame("当前消息")) }
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS))
                assertNotNull("A warmed profile lookup must not acquire the history-writer lock", withTimeoutOrNull(1000) { store.resolve(frame("当前消息")) })
            } finally { release.countDown(); background.await() }
        } finally { db.close() }
    }
    private class CountingCipher : MessageCipher {
        var reads = 0
        var oldHistoryReads = 0
        override fun encrypt(plaintext: String) = "encrypted:$plaintext"
        override fun decrypt(ciphertext: String): String {
            reads++
            return ciphertext.removePrefix("encrypted:").also { if (it.startsWith("旧历史")) oldHistoryReads++ }
        }
    }
    private fun frame(vararg text: String) = CapturedConversation("测试对象", ChatContext("wechat", "com.tencent.mm",
        text.map { ChatMessage(MessageRole.OTHER, it, 1f) }, "", 1f, ReplyRoundSnapshot("测试对象", text.map { ChatMessage(MessageRole.OTHER, it, 1f) })), true)
    private suspend fun fixture(test: suspend (ContactStore, NextSayDatabase, CountingCipher) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), NextSayDatabase::class.java).build()
        val cipher = CountingCipher()
        val store = ContactStore(RuntimeEnvironment.getApplication(), db, cipher, { "index:$it" })
        try {
            store.saveProfile(ContactSnapshot(ContactEntity("a", "com.tencent.mm", "default", "", relationship = "friend"), "测试对象", "最新资料", "", ""))
            test(store, db, cipher)
        } finally { db.close() }
    }
    @Test fun unchangedObjectResolutionDoesNotDecryptEveryAuditAgain() = runBlocking {
        fixture { store, db, cipher ->
            repeat(40) { db.contactDao().audit(ContactBindingAudit(contactId = "a", encryptedTitle = cipher.encrypt("旧名称$it"), encryptedPreviousName = "", previousAccountSpace = "default", action = "link", observedAt = 1L)) }
            assertEquals("a", store.resolve(frame("当前文字")).matched.single().entity.id)
            cipher.reads = 0
            assertEquals("a", store.resolve(frame("当前文字")).matched.single().entity.id)
            assertEquals("Unchanged encrypted fields should not cause another hardware decrypt pass", 0, cipher.reads)
        }
    }
    @Test fun observingCurrentSegmentDoesNotDecryptUnrelatedOldSegments() = runBlocking {
        fixture { store, db, cipher ->
            db.contactDao().observe((0 until 600).map { ContactObservation(contactId = "a", segmentId = "old", role = "other", encryptedText = cipher.encrypt("旧历史$it"), confidence = 1f, observedAt = 1L) } +
                ContactObservation(contactId = "a", segmentId = "latest", role = "other", encryptedText = cipher.encrypt("最新消息"), confidence = 1f, observedAt = 2L))
            cipher.oldHistoryReads = 0
            val result = store.observe("a", frame("最新消息", "新的一句"))
            assertEquals(listOf("最新消息", "新的一句"), result.messages.map { it.text })
            assertEquals("Only the last segment is needed to append the current viewport", 0, cipher.oldHistoryReads)
            assertEquals(602, store.messages("a").size)
        }
    }
    @Test fun cacheDoesNotHideManagementEditsOrHistoryDeletion() = runBlocking {
        fixture { store, db, cipher ->
            db.contactDao().observe(listOf(ContactObservation(contactId = "a", segmentId = "old", role = "other", encryptedText = cipher.encrypt("旧历史考试复习安排"), confidence = 1f, observedAt = 1L)))
            val context = frame("考试复习怎么样").context
            assertTrue(store.prepareContext(context).generationExtras!!.memory.contains("旧历史考试复习安排"))
            store.saveProfile(store.get("a")!!.copy(entity = store.get("a")!!.entity.copy(relationship = "lover"), details = "修改后的资料"))
            val updated = store.prepareContext(context)
            assertEquals("lover", updated.generationExtras!!.relationship)
            assertEquals("修改后的资料", updated.generationExtras!!.details)
            store.clearHistory("a")
            assertFalse(store.prepareContext(context).generationExtras!!.memory.contains("旧历史考试复习安排"))
        }
    }
    @Test fun profileOnlyPreviewDoesNotLoadHistoryBeforeTheActualRequest() = runBlocking {
        fixture { store, db, cipher ->
            db.contactDao().observe(listOf(ContactObservation(contactId = "a", segmentId = "old", role = "other", encryptedText = cipher.encrypt("旧历史考试复习安排"), confidence = 1f, observedAt = 1L)))
            cipher.oldHistoryReads = 0
            val extras = store.extras("a", frame("考试复习怎么样").context, includeMemory = false)
            assertEquals("friend", extras.relationship)
            assertEquals("最新资料", extras.details)
            assertEquals("", extras.memory)
            assertEquals(0, cipher.oldHistoryReads)
            assertTrue(store.extras("a", frame("考试复习怎么样").context).memory.contains("旧历史考试复习安排"))
        }
    }
}
