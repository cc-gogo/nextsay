package app.nextsay.contacts

import android.os.Bundle
import android.os.SystemClock
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.nextsay.capture.CapturedConversation
import app.nextsay.context.*
import app.nextsay.history.AndroidKeystoreMessageCipher
import app.nextsay.history.MessageCipher
import app.nextsay.history.db.NextSayDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory synthetic records + real device crypto. No real database/chat/API access. */
@RunWith(AndroidJUnit4::class)
class LocalHistoryPerformanceTest {
    @Test fun syntheticHistoryReadCosts() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, NextSayDatabase::class.java).build()
        val native = AndroidKeystoreMessageCipher()
        var decrypts = 0
        val counted = object : MessageCipher {
            override fun encrypt(plaintext: String) = native.encrypt(plaintext)
            override fun decrypt(ciphertext: String): String { decrypts++; return native.decrypt(ciphertext) }
        }
        val store = ContactStore(instrumentation.targetContext, db, counted, { "synthetic:$it" })
        val lines = listOf(ChatMessage(MessageRole.OTHER, "本地模拟考试复习新消息", 1f))
        val context = ChatContext("wechat", "com.tencent.mm", lines, "", 1f, ReplyRoundSnapshot("性能测试对象", lines))
        val frame = CapturedConversation("性能测试对象", context, true)
        suspend fun measure(stage: String, operation: suspend () -> Unit) {
            decrypts = 0
            val start = SystemClock.elapsedRealtime()
            operation()
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nLOCAL_PERF stage=$stage durationMs=${SystemClock.elapsedRealtime()-start} decryptCalls=$decrypts\n") })
        }
        try {
            store.saveProfile(ContactSnapshot(ContactEntity("local-perf", "com.tencent.mm", store.accountSpace, "", relationship = "friend"), "性能测试对象", "仅用于本地模拟", "", ""))
            db.contactDao().observe((0 until 600).map { ContactObservation(contactId = "local-perf", segmentId = "old", role = "other", encryptedText = counted.encrypt("本地模拟旧历史考试复习$it"), confidence = 1f, observedAt = 1L) } +
                ContactObservation(contactId = "local-perf", segmentId = "last", role = "other", encryptedText = counted.encrypt(lines.single().text), confidence = 1f, observedAt = 2L))
            measure("resolve_cold") { assertEquals("local-perf", store.resolve(frame).matched.single().entity.id) }
            measure("resolve_warm") { assertEquals("local-perf", store.resolve(frame).matched.single().entity.id) }
            measure("observe_cold") { assertEquals(lines, store.observe("local-perf", frame).messages) }
            measure("observe_warm") { assertEquals(lines, store.observe("local-perf", frame).messages) }
            measure("prepare_cold") { assertTrue(store.prepareContext(context).generationExtras!!.memory.contains("本地模拟旧历史")) }
            measure("prepare_warm") { assertTrue(store.prepareContext(context).generationExtras!!.memory.contains("本地模拟旧历史")) }
            measure("memory_background") { store.warmMemory("local-perf") }
            measure("prepare_after_background") { assertTrue(store.prepareContext(context).generationExtras!!.memory.contains("本地模拟旧历史")) }
            assertEquals(601, db.contactDao().recent("local-perf").size)
        } finally { db.close() }
    }
}
