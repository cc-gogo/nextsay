package app.nextsay.contacts

import androidx.room.Room
import androidx.room.RoomDatabase
import app.nextsay.context.ChatContext
import app.nextsay.history.db.NextSayDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Exercise real store/Room boundaries with empty text, without mocking the Android keystore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContactReplyModeStoreTest {
    private val chat = ChatContext("wechat", "com.tencent.mm", emptyList(), "", 1f)

    private fun profile(id: String, relationship: String, mode: String) = ContactSnapshot(
        ContactEntity(id, "com.tencent.mm", "default", "", relationship = relationship,
            useMemory = false, replyMode = mode), "", "", "", "",
    )

    @Test fun savedLoverModeSurvivesReopenAndReachesGenerationWithoutChangingAnotherContact() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "reply-mode-store-${java.util.UUID.randomUUID()}.db"
        context.getDatabasePath(name).parentFile!!.mkdirs()
        fun open() = Room.databaseBuilder(context, NextSayDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
        try {
            val first = open()
            try {
                val store = ContactStore(context, first)
                store.save(profile("a", "lover", "huangmao"))
                store.save(profile("b", "lover", "natural"))
            } finally { first.close() }
            val reopened = open()
            try {
                val store = ContactStore(context, reopened)
                assertEquals("huangmao", store.get("a")!!.entity.replyMode)
                assertEquals("huangmao", store.extras("a", chat).replyMode)
                assertEquals("natural", store.extras("b", chat).replyMode)
                assertEquals("natural", store.extras("a", chat.copy(sourcePackage = "com.tencent.mobileqq")).replyMode)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun changingSavedLoverToFriendClearsHuangmaoBeforeGeneration() = runBlocking {
        withStore { store ->
            store.save(profile("a", "lover", "huangmao"))
            val saved = store.get("a")!!
            store.save(saved.copy(entity = saved.entity.copy(relationship = "friend")))
            assertEquals("natural", store.get("a")!!.entity.replyMode)
            assertEquals("friend", store.extras("a", chat).relationship)
            assertEquals("natural", store.extras("a", chat).replyMode)
        }
    }

    @Test fun mergeIntoUnspecifiedTargetInheritsSourceLoverMode() = runBlocking {
        withStore { store ->
            store.save(profile("target", "unspecified", "natural"))
            store.save(profile("source", "lover", "huangmao"))
            store.merge("target", "source")
            assertEquals("lover", store.extras("target", chat).relationship)
            assertEquals("huangmao", store.extras("target", chat).replyMode)
            assertNull(store.get("source"))
        }
    }

    @Test fun mergePreservesAlreadySelectedTargetLoverMode() = runBlocking {
        withStore { store ->
            store.save(profile("target", "lover", "natural"))
            store.save(profile("source", "lover", "huangmao"))
            store.merge("target", "source")
            assertEquals("natural", store.extras("target", chat).replyMode)
            assertNull(store.get("source"))
        }
    }

    private suspend fun withStore(block: suspend (ContactStore) -> Unit) {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, NextSayDatabase::class.java).build()
        try { block(ContactStore(context, db)) } finally { db.close() }
    }
}
